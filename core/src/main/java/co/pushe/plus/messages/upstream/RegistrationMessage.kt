package co.pushe.plus.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class RegistrationMessage(
        @Json(name="device_id") val deviceId: String,
        @Json(name="brand") val deviceBrand: String,
        @Json(name="model") val deviceModel: String,
        @Json(name="os_version") val osVersion: String,
        @Json(name="token") val fcmToken: String,
        @Json(name="app_version") val appVersion: String,
        @Json(name="av_code") val appVersionCode: Long,
        @Json(name="pushe_version") val pusheVersion: String,
        @Json(name="pv_code") val pusheVersionCode: Int,
        @Json(name="cause") val registerCause: String,
        @Json(name = "app_sign") val appSignature: List<String>,
        @Json(name = "src") val installer: String,
        @Json(name = "fit") val firstInstallTime: Long?,
        @Json(name = "lut") val lastUpdateTime: Long?,
        @Json(name = "fresh_install") val isFreshInstall: Boolean?
) : TypedUpstreamMessage<RegistrationMessage>(
        MessageType.Upstream.REGISTRATION,
        { RegistrationMessageJsonAdapter(it) }
)