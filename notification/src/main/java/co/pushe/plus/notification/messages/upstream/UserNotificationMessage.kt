package co.pushe.plus.notification.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
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