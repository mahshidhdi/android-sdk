package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScreenOnOffMessage(
        @Json(name="on") val onTime: String,
        @Json(name="off") val offTime: String
) : TypedUpstreamMessage<ScreenOnOffMessage> (
        MessageType.Datalytics.Upstream.SCREEN_ON_OFF,
        { ScreenOnOffMessageJsonAdapter(it) }
)