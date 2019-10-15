package io.hengam.lib.notification.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messages.mixin.CellInfoMixin
import io.hengam.lib.messages.mixin.WifiInfoMixin
import io.hengam.lib.messages.mixin.LocationMixin
import io.hengam.lib.messages.mixin.NetworkInfoMixin
import io.hengam.lib.messaging.TypedUpstreamMessage
import io.hengam.lib.utils.Millis
import io.hengam.lib.utils.Time
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