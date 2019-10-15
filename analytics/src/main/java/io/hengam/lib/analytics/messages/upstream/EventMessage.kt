package io.hengam.lib.analytics.messages.upstream

import io.hengam.lib.analytics.event.EventAction
import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
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