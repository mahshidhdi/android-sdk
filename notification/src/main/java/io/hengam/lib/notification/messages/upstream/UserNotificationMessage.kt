package io.hengam.lib.notification.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class UserNotificationMessage(
        @Json(name="user_msg") val userMessage: Map<String, Any>,
        @Json(name="receiver_gaid") val userAdvertisementId: String? = null,
        @Json(name="receiver_aid") val userAndroidId: String? = null,
        @Json(name="receiver_cid") val userCustomId: String? = null
) : TypedUpstreamMessage<UserNotificationMessage>(
        MessageType.Notification.Upstream.SEND_NOTIF_TO_USER,
        { UserNotificationMessageJsonAdapter(it) }
)