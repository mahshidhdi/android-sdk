package co.pushe.plus.messaging.http

import co.pushe.plus.messaging.COURIER_HTTP
import co.pushe.plus.messaging.OutboundCourier
import co.pushe.plus.messaging.UpstreamParcel
import io.reactivex.Completable
import javax.inject.Inject


class HttpOutboundCourier @Inject constructor() : OutboundCourier {
    override val id: String = COURIER_HTTP

    override fun sendParcel(parcel: UpstreamParcel): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toString(): String = "Http Courier"
}