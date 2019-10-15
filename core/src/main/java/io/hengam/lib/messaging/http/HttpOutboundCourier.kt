package io.hengam.lib.messaging.http

import io.hengam.lib.messaging.COURIER_HTTP
import io.hengam.lib.messaging.OutboundCourier
import io.hengam.lib.messaging.UpstreamParcel
import io.reactivex.Completable
import javax.inject.Inject


class HttpOutboundCourier @Inject constructor() : OutboundCourier {
    override val id: String = COURIER_HTTP

    override fun sendParcel(parcel: UpstreamParcel): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String = "Http Courier"
}