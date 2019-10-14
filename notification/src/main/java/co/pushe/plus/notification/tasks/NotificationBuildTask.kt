package co.pushe.plus.notification.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.notification.*
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.ordinal
import io.reactivex.Single
import javax.inject.Inject

class NotificationBuildTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("notification_build", context, workerParameters) {

    @Inject lateinit var notificationController: NotificationController
    @Inject lateinit var notificationErrorHandler: NotificationErrorHandler
    @Inject lateinit var notificationStatusReporter: NotificationStatusReporter
    @Inject lateinit var moshi: PusheMoshi

    override fun perform(): Single<Result> {
        var message: NotificationMessage? = null
        try {
            val notificationComponent = PusheInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.NOTIFICATION)

            notificationComponent.inject(this)

            message = parseData()

            return notificationController.showNotification(message)
                    .toSingle { Result.success() }
                    .onErrorReturn { ex ->
                        if (ex is NotificationBuildException) {
                            Plog.warn(T_NOTIF, "Building notification failed in the ${ordinal(runAttemptCount + 1)} attempt", ex, "Message Id" to message.messageId)
                            Result.retry()
                        } else {
                            Plog.error(T_NOTIF, NotificationTaskException("Building notification failed with unrecoverable error", ex), "Message Id" to message.messageId)
                            Result.failure()
                        }
                    }
        } catch (ex: Exception) {
            Plog.error(T_NOTIF, NotificationTaskException("Notification Build task failed with fatal error", ex), "Message Data" to inputData.getString(DATA_NOTIFICATION_MESSAGE))
            if (message != null) {
                notificationErrorHandler.onNotificationBuildFailed(message, NotificationBuildStep.UNKNOWN)
                notificationStatusReporter.reportStatus(message, NotificationStatus.FAILED)
            }
            return Single.just(Result.failure())
        }

    }

    override fun onMaximumRetriesReached() {
        try {
            val notificationComponent = PusheInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.NOTIFICATION)
            notificationComponent.inject(this)
            val message = parseData()
            Plog.warn(T_NOTIF, "Maximum number of attempts reached for building notification, " +
                    "the notification will be discarded", "Message Id" to message.messageId)
            notificationStatusReporter.reportStatus(message, NotificationStatus.FAILED)
        } catch (ex: Exception) {
            Plog.error(T_NOTIF, ex)
        }
    }


    private fun parseData(): NotificationMessage {
        val messageData = inputData.getString(DATA_NOTIFICATION_MESSAGE)
                ?: throw NotificationTaskException("NotificationBuildTask was run with no message data")

        val messageAdapter = moshi.adapter(NotificationMessage::class.java)
        return messageAdapter.fromJson(messageData)
                ?: throw NotificationTaskException("Could not parse message json data in Notification Build Task")
    }

    class NotificationTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        const val DATA_NOTIFICATION_MESSAGE = "notification_message"
    }

    class Options(
            private val message: NotificationMessage
    ) : OneTimeTaskOptions() {
        override fun networkType() = if (message.requiresNetwork()) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
        override fun task() = NotificationBuildTask::class
        override fun taskId() = if (message.tag.isNullOrBlank()) message.messageId else message.tag
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun maxAttemptsCount(): Int =  pusheConfig.maxNotificationBuildAttempts
        override fun backoffPolicy(): BackoffPolicy? = pusheConfig.notificationBuildBackOffPolicy
        override fun backoffDelay(): Time? = pusheConfig.notificationBuildBackOffDelay
    }
}
