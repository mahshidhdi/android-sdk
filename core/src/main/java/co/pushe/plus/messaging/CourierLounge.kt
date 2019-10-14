package co.pushe.plus.messaging

import co.pushe.plus.AppManifest
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.fcm.FcmMessaging
import co.pushe.plus.messaging.fcm.FcmOutboundCourier
import co.pushe.plus.messaging.fcm.FcmServiceManager
import io.reactivex.Observable
import javax.inject.Inject

@CoreScope
class CourierLounge @Inject constructor(
        private val moshi: PusheMoshi,
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
