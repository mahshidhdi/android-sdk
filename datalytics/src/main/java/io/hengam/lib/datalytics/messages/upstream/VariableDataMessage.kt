package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class VariableDataMessage(
        @Json(name = "os_version") val osVersion: String,
        @Json(name = "app_version") val appVersion: String,
        @Json(name = "av_code") val appVersionCode: Long,
        @Json(name = "hengam_version") val hengamVersion: String,
        @Json(name = "pv_code") val hengamVersionCode: String,
        @Json(name = "gplay_version") val googlePlayVersion: String?,
        @Json(name = "operator") val operator: String?,
        @Json(name = "operator_2") val operator2: String?,
        @Json(name = "installer") val installer: String?
) : TypedUpstreamMessage<VariableDataMessage>(
        MessageType.Datalytics.VARIABLE_DATA,
        { VariableDataMessageJsonAdapter(it) }
)
