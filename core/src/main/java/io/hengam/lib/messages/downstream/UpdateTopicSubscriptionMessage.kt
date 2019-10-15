package io.hengam.lib.messages.downstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class UpdateTopicSubscriptionMessage(
    @Json(name = "subscribe_to") val subscribeTo: List<String> = emptyList(),
    @Json(name = "unsubscribe_from") val unsubscribeFrom: List<String> = emptyList()
) {
    class Parser : DownstreamMessageParser<UpdateTopicSubscriptionMessage>(
            MessageType.Downstream.UPDATE_TOPIC,
            { UpdateTopicSubscriptionMessageJsonAdapter(it) }
    )
}