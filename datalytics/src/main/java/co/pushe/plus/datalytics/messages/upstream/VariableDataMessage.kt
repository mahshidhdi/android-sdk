package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class VariableDataMessage(
        @Json(name = "os_version") val osVersion: String,
        @Json(name = "app_version") val appVersion: String,
        @Json(name = "av_code") val appVersionCode: Long,
        @Json(name = "pushe_version") val pusheVersion: String,
        @Json(name = "pv_code") val pusheVersionCode: String,
        @Json(name = "gplay_version") val googlePlayVersion: String?,
        @Json(name = "operator") val operator: String?,
        @Json(name = "operator_2") val operator2: String?,
        @Json(name = "installer") val installer: String?
) : TypedUpstreamMessage<VariableDataMessage>(
        MessageType.Datalytics.VARIABLE_DATA,
        { VariableDataMessageJsonAdapter(it) }
)
