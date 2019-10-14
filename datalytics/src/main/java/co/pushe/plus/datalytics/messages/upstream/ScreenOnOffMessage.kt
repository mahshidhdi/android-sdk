package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
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