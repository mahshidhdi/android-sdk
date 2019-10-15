package io.hengam.lib.notification.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class UserInputDataMessage(
        @Json(name="orig_msg_id") val originalMessageId: String,
        @Json(name="data") val data: Map<String,Any>?
) : TypedUpstreamMessage<UserInputDataMessage>(
        MessageType.Notification.Upstream.NOTIFICATION_USER_INPUT,
        { UserInputDataMessageJsonAdapter(it) }
)