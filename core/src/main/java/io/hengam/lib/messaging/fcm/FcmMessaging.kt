package io.hengam.lib.messaging.fcm

import io.hengam.lib.ApiPatch
import io.hengam.lib.LogTag.T_FCM
import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.messaging.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.PublishRelay
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.SendException
import com.squareup.moshi.JsonAdapter
import io.reactivex.Completable
import javax.inject.Inject

@CoreScope
class FcmMessaging @Inject constructor(
        private val postOffice: PostOffice,
        private val moshi: HengamMoshi,
        private val fcmInboundCourier: FcmInboundCourier,
        private val fcmServiceManager: FcmServiceManager
) {
    private val messageRelay = PublishRelay.create<MessageEvent>()
    private val parcelAdapter: JsonAdapter<DownstreamParcel> by lazy { moshi.adapter(DownstreamParcel::class.java) }
    private val anyAdapter: JsonAdapter<Any> by lazy {  moshi.adapter(Any::class.java) }

    fun onFcmMessageSent(parcelId: String) {
        postOffice.onParcelAck(parcelId, COURIER_FCM)
        messageRelay.accept(SuccessMessageEvent(parcelId))
    }

    fun onFcmMessageFailed(parcelId: String, cause: Exception) {
        val betterCause = if (cause is SendException && cause.errorCode == SendException.ERROR_SIZE) {
            ParcelTooBigException("FCM message is too big, unable to send", cause)
        } else {
            cause
        }

        postOffice.onParcelError(
                parcelId,
                COURIER_FCM,
                betterCause
        )
        messageRelay.accept(FailMessageEvent(parcelId, betterCause))
    }

    fun sendFcmMessage(remoteMessage: RemoteMessage): Completable {
        return messageRelay
                .subscribeOn(cpuThread())
                .observeOn(cpuThread())
                .filter { it.messageId == remoteMessage.messageId }
                .take(1)
                .doOnNext { messageEvent ->
                    if (messageEvent is FailMessageEvent) {
                        throw messageEvent.cause
                    }
                }
                .ignoreElements()
                .doOnSubscribe {
                    fcmServiceManager.firebaseMessaging.send(remoteMessage)
                }
    }

    fun onFcmMessageReceived(message: Map<String, String>, parcelId: String?,
                             sentTime: Long? = null, priority: Int? = null) {
        var patchedData = ApiPatch.parseDownstreamParcelObjectStrings(message, anyAdapter)
        patchedData = ApiPatch.extractLegacyStyleMessageFromRoot(patchedData, anyAdapter)
        patchedData = ApiPatch.injectMessageIdInParcel(patchedData, "FCMID_$parcelId")
        val json = anyAdapter.toJson(patchedData)

        Plog.debug(T_MESSAGE, T_FCM, "FCM Parcel Received",
            "Parcel" to json,
            "Priority" to priority,
            "Sent Time" to sentTime,
            "FCM Id" to parcelId
        )

        val parcel = try {
            parcelAdapter.fromJson(json) ?: throw NullPointerException()
        } catch (ex: ParcelParseException) {
            Plog.error(T_MESSAGE, T_FCM, "Could not parse received downstream parcel", ex,
                "Courier" to "FCM",
                "Parcel" to json
            )
            null
        } catch (ex: NullPointerException) {
            Plog.error(T_MESSAGE, T_FCM, "Downstream Parcel parsing returned null", ex,
                "Courier" to "FCM",
                "Message" to json
            )
            null
        }

        parcel?.let { fcmInboundCourier.newParcelReceived(parcel) }
    }
}

private sealed class MessageEvent(val messageId: String)
private class SuccessMessageEvent(messageId: String) : MessageEvent(messageId)
private class FailMessageEvent(messageId: String, val cause: Exception) : MessageEvent(messageId)