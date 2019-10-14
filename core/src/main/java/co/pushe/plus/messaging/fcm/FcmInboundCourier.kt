package co.pushe.plus.messaging.fcm

import co.pushe.plus.messaging.DownstreamParcel
import co.pushe.plus.messaging.InboundCourier
import co.pushe.plus.messaging.PostOffice
import javax.inject.Inject


class FcmInboundCourier @Inject constructor(
    private val postOffice: PostOffice
) : InboundCourier {
    fun newParcelReceived(parcel: DownstreamParcel) {
        postOffice.onInboundParcelReceived(parcel)
    }
}

