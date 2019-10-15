package io.hengam.lib.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeliveryMessage(
        @Json(name="orig_msg_id") val originalMessageId: String,
        @Json(name="status") val status: String
) : TypedUpstreamMessage<DeliveryMessage> (
        MessageType.Upstream.DELIVERY,
        { DeliveryMessageJsonAdapter(it) }
)