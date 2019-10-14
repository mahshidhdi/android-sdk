package co.pushe.plus.analytics.messages.upstream

import co.pushe.plus.analytics.event.EventAction
import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class EventMessage(
        @Json(name = "name") val name: String,
        @Json(name = "action") val action: EventAction,
        @Json(name = "data") val value: String? = null
) : TypedUpstreamMessage<EventMessage>(
    MessageType.Analytics.Upstream.EVENT,
    { EventMessageJsonAdapter(it) })