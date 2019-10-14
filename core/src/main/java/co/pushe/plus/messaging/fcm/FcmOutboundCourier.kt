package co.pushe.plus.messaging.fcm

import co.pushe.plus.AppManifest
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.COURIER_FCM
import co.pushe.plus.messaging.OutboundCourier
import co.pushe.plus.messaging.ParcelSendException
import co.pushe.plus.messaging.UpstreamParcel
import co.pushe.plus.utils.rx.safeSingleFromCallable
import com.google.firebase.messaging.RemoteMessage
import io.reactivex.Completable


class FcmOutboundCourier constructor(
        moshi: PusheMoshi,
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