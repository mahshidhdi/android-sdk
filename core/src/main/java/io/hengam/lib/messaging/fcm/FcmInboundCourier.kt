package io.hengam.lib.messaging.fcm

import io.hengam.lib.messaging.DownstreamParcel
import io.hengam.lib.messaging.InboundCourier
import io.hengam.lib.messaging.PostOffice
import javax.inject.Inject


class FcmInboundCourier @Inject constructor(
    private val postOffice: PostOffice
) : InboundCourier {
    fun newParcelReceived(parcel: DownstreamParcel) {
        postOffice.onInboundParcelReceived(parcel)
    }
}

