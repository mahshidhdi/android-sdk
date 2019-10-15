package io.hengam.lib.notification.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messages.mixin.CellInfoMixin
import io.hengam.lib.messages.mixin.LocationMixin
import io.hengam.lib.messages.mixin.NetworkInfoMixin
import io.hengam.lib.messages.mixin.WifiInfoMixin
import io.hengam.lib.messaging.TypedUpstreamMessage
import io.hengam.lib.notification.NotificationBuildStep
import io.hengam.lib.notification.ValidationErrors
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