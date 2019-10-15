//package io.hengam.lib.messaging.lash
//
//import android.content.Context
//import io.hengam.lib.AppManifest
//import io.hengam.lib.LogTag
//import io.hengam.lib.LogTag.T_LASH
//import io.hengam.lib.dagger.CoreScope
//import io.hengam.lib.internal.HengamMoshi
//import io.hengam.lib.internal.cpuThread
//import io.hengam.lib.internal.ioThread
//import io.hengam.lib.lash.Lash
//import io.hengam.lib.messaging.CourierLounge
//import io.hengam.lib.messaging.CourierLounge_Factory
//import io.hengam.lib.messaging.DownstreamParcel
//import io.hengam.lib.utils.DeviceIDHelper
//import io.hengam.lib.utils.log.Plog
//import io.hengam.lib.utils.rx.subscribeAndShutup
//import com.squareup.moshi.JsonAdapter
//import io.reactivex.rxkotlin.subscribeBy
//import java.lang.Exception
//import java.lang.NullPointerException
//import javax.inject.Inject
//
//@CoreScope
//class LashServiceManager @Inject constructor(
//        private val context: Context,
//        private val moshi: HengamMoshi,
//        private val appManifest: AppManifest,
//        private val deviceIDHelper: DeviceIDHelper,
//        private val lashInboundCourier: LashInboundCourier
//) {
//    fun initializeLash() {
//        val parcelAdapter = moshi.adapter(DownstreamParcel::class.java)
//
//        Lash.initialize(context, deviceIDHelper.hengamId, appManifest.appId, "tcp://hengam.hadi.sh:1883")
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