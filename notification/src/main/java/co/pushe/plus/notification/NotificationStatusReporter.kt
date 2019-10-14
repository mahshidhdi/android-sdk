package co.pushe.plus.notification

import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.messages.upstream.NotificationReportMessage
import javax.inject.Inject

/**
 * Sends a notification's publish status to the server. (i.e., whether it has been successfully
 * shown to the user or not due to failures or notifications being disabled)
 */
class NotificationStatusReporter @Inject constructor(
        private val postOffice: PostOffice,
        private val notificationErrorHandler: NotificationErrorHandler
) {
    fun reportStatus(message: NotificationMessage, status: NotificationStatus) = reportStatus(message.messageId, status)

    fun reportStatus(messageId: String, status: NotificationStatus) {
        val reportMessage = NotificationReportMessage(
            originalMessageId = messageId,
            status = status.statusCode,
            exceptions = notificationErrorHandler.getNotificationBuildErrorStats(messageId)?.takeIf { it.isNotEmpty() },
            validationErrors = notificationErrorHandler.getNotificationValidationErrorStats(messageId)?.takeIf { it.isNotEmpty() },
            skippedSteps = notificationErrorHandler.getNotificationSkippedSteps(messageId).takeIf { it.isNotEmpty() }
        )

        postOffice.sendMessage(reportMessage)
        notificationErrorHandler.removeNotificationStats(messageId)
    }
}

enum class NotificationStatus(val statusCode: Int) {
    PUBLISHED(1),
    FAILED(2),
    APP_DISABLED(3), // Disabled using the [PusheNotification.disableNotifications] method
    SYSTEM_DISABLED(4), // Disabled from Android settings
    PUSHE_DISABLED(5), // Disabled from Pushe Config
    PARSE_FAILED(6),
    NOT_PUBLISHED_OTK(7),
    NOT_PUBLISHED_UPDATE(8),
    EXPIRED(9)
}