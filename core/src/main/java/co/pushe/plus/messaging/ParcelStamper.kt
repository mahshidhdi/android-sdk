package co.pushe.plus.messaging

import android.content.Context
import co.pushe.plus.AppManifest
import co.pushe.plus.BuildConfig
import co.pushe.plus.UserCredentials
import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.fcm.FcmTokenStore
import co.pushe.plus.utils.TimeUtils
import co.pushe.plus.utils.DeviceIDHelper
import io.reactivex.Single
import javax.inject.Inject

class ParcelStamper @Inject constructor(
        private val fcmTokenStore: FcmTokenStore,
        private val deviceId: DeviceIDHelper,
        private val appManifest: AppManifest,
        private val userCredentials: UserCredentials,
        private val context: Context
) {
    fun stampParcel(parcel: UpstreamParcel): Single<UpstreamStampedParcel> {
        return createStamp(parcel)
                .map { addRegistrationToken(parcel, it) }
                .map { UpstreamStampedParcel(parcel, it) }
    }

    private fun createStamp(parcel: UpstreamParcel): Single<Map<String, Any>> {
        return Single.just(listOfNotNull(
                "platform" to 1,
                "message_id" to parcel.parcelId,
                "instance_id" to fcmTokenStore.instanceId,
                "android_id" to deviceId.androidId,
                "gaid" to deviceId.advertisementId,
                "app_id" to appManifest.appId,
                "package_name" to context.packageName,
                "pvc" to BuildConfig.VERSION_CODE,
                ("cid" to userCredentials.customId).takeIf { it.second.isNotBlank() },
                ("email" to userCredentials.email).takeIf { it.second.isNotBlank() },
                ("pn" to userCredentials.phoneNumber).takeIf { it.second.isNotBlank() },
                "time" to TimeUtils.nowMillis()
        ).toMap())
    }

    /**
     * For upstream registration messages, the token should also be supplied in
     * the parcel root. Until this is fixed in the API, we will add the token
     * to parcel root here
     */
    private fun addRegistrationToken(parcel: UpstreamParcel, stamp: Map<String, Any>): Map<String, Any> {
        for (message in parcel.messages) {
            if (message.messageType == MessageType.Upstream.REGISTRATION) {
                val newStamp = stamp.toMutableMap()
                newStamp["token"] = fcmTokenStore.token
                return newStamp
            }
        }
        return stamp
    }
}