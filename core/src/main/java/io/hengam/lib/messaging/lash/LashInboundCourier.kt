package io.hengam.lib.messaging.lash

import io.hengam.lib.messaging.DownstreamParcel
import io.hengam.lib.messaging.InboundCourier
import io.hengam.lib.messaging.PostOffice
import javax.inject.Inject


class LashInboundCourier @Inject constructor(
    private val postOffice: PostOffice
) : InboundCourier {
    fun newParcelReceived(parcel: DownstreamParcel) {
        postOffice.onInboundParcelReceived(parcel)
    }
}