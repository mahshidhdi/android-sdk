package io.hengam.lib.messaging

import io.hengam.lib.AppManifest
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.fcm.FcmMessaging
import io.hengam.lib.messaging.fcm.FcmOutboundCourier
import io.hengam.lib.messaging.fcm.FcmServiceManager
import io.reactivex.Observable
import javax.inject.Inject

@CoreScope
class CourierLounge @Inject constructor(
        private val moshi: HengamMoshi,
        private val fcmServiceManager: FcmServiceManager,
        private val fcmMessaging: FcmMessaging,
        private val appManifest: AppManifest
) {

//    val httpInboundCourier = HttpInboundCourier()
//    val fcmInboundCourier = FcmInboundCourier(postOffice)
//    val lashInboundCourier = LashInboundCourier(postOffice)

    private val availableOutboundCouriers = mutableSetOf<OutboundCourier>()

//    val inboundCouriers: Observable<InboundCourier> = Observable.just(fcmInboundCourier)
    val outboundCouriers: Observable<OutboundCourier> = Observable.defer { Observable.fromIterable(availableOutboundCouriers) }

    fun initialize() {
        initializeOutboundCouriers()
    }

    private fun initializeOutboundCouriers() {
        /* Outbound couriers added in order of their priority */

        if (fcmServiceManager.isFirebaseAvailable) {
            availableOutboundCouriers.add(FcmOutboundCourier(moshi, fcmServiceManager, fcmMessaging, appManifest))
        }

//        availableOutboundCouriers.add(HttpOutboundCourier())
    }
}
