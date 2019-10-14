//package co.pushe.plus.messaging.lash
//
//import android.content.Context
//import co.pushe.plus.AppManifest
//import co.pushe.plus.LogTag
//import co.pushe.plus.LogTag.T_LASH
//import co.pushe.plus.dagger.CoreScope
//import co.pushe.plus.internal.PusheMoshi
//import co.pushe.plus.internal.cpuThread
//import co.pushe.plus.internal.ioThread
//import co.pushe.plus.lash.Lash
//import co.pushe.plus.messaging.CourierLounge
//import co.pushe.plus.messaging.CourierLounge_Factory
//import co.pushe.plus.messaging.DownstreamParcel
//import co.pushe.plus.utils.DeviceIDHelper
//import co.pushe.plus.utils.log.Plog
//import co.pushe.plus.utils.rx.subscribeAndShutup
//import com.squareup.moshi.JsonAdapter
//import io.reactivex.rxkotlin.subscribeBy
//import java.lang.Exception
//import java.lang.NullPointerException
//import javax.inject.Inject
//
//@CoreScope
//class LashServiceManager @Inject constructor(
//        private val context: Context,
//        private val moshi: PusheMoshi,
//        private val appManifest: AppManifest,
//        private val deviceIDHelper: DeviceIDHelper,
//        private val lashInboundCourier: LashInboundCourier
//) {
//    fun initializeLash() {
//        val parcelAdapter = moshi.adapter(DownstreamParcel::class.java)
//
//        Lash.initialize(context, deviceIDHelper.pusheId, appManifest.appId, "tcp://pushe.hadi.sh:1883")
//
//        Lash.receiveMessages()
//                .subscribeOn(ioThread())
//                .observeOn(cpuThread())
//                .doOnNext {
//                    Plog.debug("Lash Parcel Received") {
//                        tag(LogTag.T_MESSAGE, LogTag.T_FCM)
//                        "Parcel" to String(it.payload)
//                        "Topic" to it.topic
//                        "Is Retained" to it.isRetained
//                        "Is Duplicate" to it.isDuplicate
//                    }
//                }
//                .map { String(it.payload) }
//                .map { try { parcelAdapter.fromJson(it)!!} catch (ex: Exception) { throw LashParcelException(it, ex) } }
//                .subscribeAndShutup (
//                        onNext = { lashInboundCourier.newParcelReceived(it) },
//                        onError = {
//                            if (it is LashParcelException) {
//                                Plog.error("Could not parse received downstream parcel", it.exception) {
//                                    tag(LogTag.T_MESSAGE, LogTag.T_LASH)
//                                    "Parcel" to it.parcel
//                                    "Courier" to "Lash"
//                                }
//                            }
//                        }
//                )
//
//        Lash.connect()
//                .subscribeAndShutup(
//                        onComplete = { Plog[T_LASH].debug("Lash connected") },
//                        onError = { Plog[T_LASH].error("Error connecting to Lash", it) }
//                )
//    }
//
//    class LashParcelException(val parcel: String, val exception: Throwable) : Exception()
//}