package io.hengam.lib.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TopicStatusMessage(
        @Json(name="topic") val topic: String,
        @Json(name="status") val status: Int
) : TypedUpstreamMessage<TopicStatusMessage>(
        MessageType.Upstream.TOPIC_STATUS,
        { TopicStatusMessageJsonAdapter(it) }
) {
    companion object {
        const val STATUS_SUBSCRIBED = 0
        const val STATUS_UNSUBSCRIBED = 1
    }
}