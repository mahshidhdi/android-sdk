package io.hengam.lib.messaging.fcm

import io.hengam.lib.AppManifest
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.COURIER_FCM
import io.hengam.lib.messaging.OutboundCourier
import io.hengam.lib.messaging.ParcelSendException
import io.hengam.lib.messaging.UpstreamParcel
import io.hengam.lib.utils.rx.safeSingleFromCallable
import com.google.firebase.messaging.RemoteMessage
import io.reactivex.Completable


class FcmOutboundCourier constructor(
        moshi: HengamMoshi,
        private val fcmServiceManager: FcmServiceManager,
        private val fcmMessaging: FcmMessaging,
        private val appManifest: AppManifest
) : OutboundCourier {
    private val parcelAdapter = moshi.adapter(UpstreamParcel::class.java)
    private val anyAdapter = moshi.adapter(Any::class.java).serializeNulls()

    override val id: String = COURIER_FCM

    override fun sendParcel(parcel: UpstreamParcel): Completable {
        return safeSingleFromCallable {
            if (!fcmServiceManager.isFirebaseAvailable) {
                throw ParcelSendException("Firebase services have not been initialized")
            }
            buildFcmMessageFromParcel(parcel)
        }.flatMapCompletable { fcmMessaging.sendFcmMessage(it) }
    }

    private fun buildFcmMessageFromParcel(parcel: UpstreamParcel): RemoteMessage {
        val builder = RemoteMessage.Builder("${appManifest.fcmSenderId}@gcm.googleapis.com")
        builder.setMessageId(parcel.parcelId)
        builder.setTtl(10)
        val jsonValue: Map<*, *> = parcelAdapter.toJsonValue(parcel) as Map<*, *>
        for ((key, value) in jsonValue) {
            val convertedValue = when (value) {
                is Map<*, *> -> anyAdapter.toJson(value)
                is List<*> -> anyAdapter.toJson(value)
                else -> value.toString()
            }
            builder.addData(key.toString(), convertedValue)
        }

        return builder.build()
    }

    override fun toString(): String = "FCM Courier"
}