package io.hengam.lib.messaging

import io.hengam.lib.LogTag.T_MESSAGE
import javax.inject.Inject
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.JsonAdapter
import io.reactivex.Observable
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@CoreScope
class UpstreamSender @Inject constructor(
        private val postOffice: PostOffice,
        private val courierLounge: CourierLounge,
        private val moshi: HengamMoshi
) {
    private val parcelAdapter: JsonAdapter<UpstreamParcel> by lazy { moshi.adapter(UpstreamParcel::class.java) }

    fun collectAndSendParcels(): Single<Boolean> {
        return postOffice.collectParcelsForSending()
                .subscribeOn(cpuThread())
                .observeOn(cpuThread())
                .doOnNext {
                    val json = parcelAdapter.toJson(it)
                    Plog.debug(T_MESSAGE, "Sending parcel",
                        "Parcel" to json,
                        "Size" to json.length,
                        "Id" to it.parcelId
                    )
                }
                .flatMapSingle { parcel ->
                    if (parcel.messages.isEmpty()) {
                        Plog.warn(T_MESSAGE,"Attempting to send empty parcel, ignoring parcel")
                        return@flatMapSingle Single.just(true)
                    }

                    // TODO order couriers by failed attempts
                    return@flatMapSingle courierLounge.outboundCouriers
                            .flatMapSingle { courier ->
                                sendParcel(parcel, courier)
                                        .doOnSuccess { logAttempt(parcel, courier, it) }
                            }
                            .takeUntil { it !is SendResult.Fail }
                            .toList()
                            .map { results ->
                                logAllAttempts(parcel, results)
                                results.any { it is SendResult.Success } || results.all { it is SendResult.TooBig }
                            }
                }
                .switchIfEmpty(
                        Observable.defer {
                            postOffice.areMessagesInFlight()
                                    .doOnSuccess { yes ->
                                        if (yes) {
                                            Plog.trace(T_MESSAGE, "Upstream Sender is run but messages are not available. " +
                                                    "Rescheduling to wait for pending messages.")
                                        } else {
                                            Plog.warn.message("Upstream Sender was run for apparently no reason")
                                                    .withTag(T_MESSAGE)
                                                    .useLogCatLevel(LogLevel.DEBUG)
                                                    .log()
                                        }
                                    }
                                    .map { yes -> !yes }
                                    .toObservable()
                        }
                )
                .all { it }
    }

    private fun sendParcel(parcel: UpstreamParcel, courier: OutboundCourier): Single<SendResult> {
        if (parcel.messages.isEmpty()) {
            return Single.just(SendResult.Success)
        }

        postOffice.onParcelInFlight(parcel, courier.id)

        return courier.sendParcel(parcel)
                .timeout(3000, TimeUnit.MILLISECONDS)
                .toSingleDefault<SendResult>(SendResult.Success)
                .onErrorResumeNext { ex ->
                    when (ex) {
                        is TimeoutException -> Single.just(SendResult.Pending)
                        is ParcelTooBigException -> Single.just(SendResult.TooBig)
                        else -> Single.just(SendResult.Fail(ex))
                    }
                }
    }

    private fun logAttempt(parcel: UpstreamParcel, courier: OutboundCourier, result: SendResult) {
        if (result is SendResult.Fail) {
            Plog.warn(T_MESSAGE, "Parcel sending attempt failed with courier ${courier.id}",
                result.cause,
                "Parcel Id" to parcel.parcelId
            )
        } else {
            Plog.trace(T_MESSAGE, "Parcel sending attempted",
                "Courier" to courier.id,
                "Parcel Id" to parcel.parcelId,
                "Result" to result.toString()
            )
        }
    }

    private fun logAllAttempts(parcel: UpstreamParcel, results: List<SendResult>) {
        if (results.all { it is SendResult.Fail }) {
            Plog.error(T_MESSAGE, "Could not send parcel with any of the available couriers", "Id" to parcel.parcelId)
        }
    }

    private sealed class SendResult(val name: String) {
        object Success : SendResult("Success")
        object TooBig : SendResult("TooBig")
        object Pending : SendResult("Pending")
        class Fail(val cause: Throwable) : SendResult("Fail")
        override fun toString(): String = name
    }
}