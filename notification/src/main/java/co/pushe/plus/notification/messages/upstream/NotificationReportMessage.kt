package co.pushe.plus.notification.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messages.mixin.CellInfoMixin
import co.pushe.plus.messages.mixin.LocationMixin
import co.pushe.plus.messages.mixin.NetworkInfoMixin
import co.pushe.plus.messages.mixin.WifiInfoMixin
import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.notification.NotificationBuildStep
import co.pushe.plus.notification.ValidationErrors
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NotificationReportMessage(
        @Json(name="orig_msg_id") val originalMessageId: String,
        @Json(name="status") val status: Int,
        @Json(name="build_errs") val exceptions: Map<NotificationBuildStep, Int>? = null,
        @Json(name="validation_errs") val validationErrors: Map<ValidationErrors, Int>? = null,
        @Json(name="skipped") val skippedSteps: List<NotificationBuildStep>? = null
) : TypedUpstreamMessage<NotificationReportMessage>(
        MessageType.Notification.Upstream.NOTIFICATION_REPORT,
        { NotificationReportMessageJsonAdapter(it) },
        listOf(LocationMixin(true), WifiInfoMixin(true), CellInfoMixin(true), NetworkInfoMixin(true))
)