package co.pushe.plus.messaging.lash

import co.pushe.plus.messaging.DownstreamParcel
import co.pushe.plus.messaging.InboundCourier
import co.pushe.plus.messaging.PostOffice
import javax.inject.Inject


class LashInboundCourier @Inject constructor(
    private val postOffice: PostOffice
) : InboundCourier {
    fun newParcelReceived(parcel: DownstreamParcel) {
        postOffice.onInboundParcelReceived(parcel)
    }
}