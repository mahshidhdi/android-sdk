package co.pushe.plus.notification.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messages.mixin.CellInfoMixin
import co.pushe.plus.messages.mixin.WifiInfoMixin
import co.pushe.plus.messages.mixin.LocationMixin
import co.pushe.plus.messages.mixin.NetworkInfoMixin
import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.utils.Millis
import co.pushe.plus.utils.Time
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NotificationActionMessage(
        @Json(name = "orig_msg_id") val originalMessageId: String,
        @Json(name = "status") val status: NotificationResponseAction,
        @Json(name = "btn_id") val responseButtonId: String? = null,
        @Json(name = "pub_time") @Millis val notificationPublishTime: Time?
) : TypedUpstreamMessage<NotificationActionMessage>(
        MessageType.Notification.Upstream.NOTIFICATION_ACTION,
        { NotificationActionMessageJsonAdapter(it) },
        listOf(LocationMixin(true), WifiInfoMixin(true), CellInfoMixin(true), NetworkInfoMixin(true))
) {
    enum class NotificationResponseAction {
        @Json(name = "clicked")
        CLICKED,
        @Json(name = "dismissed")
        DISMISSED
    }
}