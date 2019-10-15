package io.hengam.lib.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TagSubscriptionMessage(
        @Json(name="added_tags") val addedTags: List<String> = emptyList(),
        @Json(name="removed_tags") val removedTags: List<String> = emptyList()
) : TypedUpstreamMessage<TagSubscriptionMessage>(
        MessageType.Upstream.TAG_SUBSCRIPTION,
        { TagSubscriptionMessageJsonAdapter(it) }
) {
    companion object {
    }
}