package co.pushe.plus.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
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