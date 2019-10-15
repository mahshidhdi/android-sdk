package io.hengam.lib.messaging

import io.reactivex.Completable

interface InboundCourier

interface OutboundCourier {
    val id: String
    fun sendParcel(parcel: UpstreamParcel): Completable
}

const val COURIER_FCM = "FCM"
const val COURIER_LASH = "Lash"
const val COURIER_HTTP = "HTTP"

class ParcelSendException(message: String, cause: Throwable? = null) : Exception(message, cause)