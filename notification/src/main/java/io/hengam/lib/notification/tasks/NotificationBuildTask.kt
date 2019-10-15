package io.hengam.lib.notification.tasks

import androidx.work.*
import io.hengam.lib.Hengam
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.notification.*
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.ordinal
import io.reactivex.Single
import javax.inject.Inject

class NotificationBuildTask: HengamTask() {

    @Inject lateinit var notificationController: NotificationController
    @Inject lateinit var notificationErrorHandler: NotificationErrorHandler
    @Inject lateinit var notificationStatusReporter: NotificationStatusReporter
    @Inject lateinit var moshi: HengamMoshi

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        var message: NotificationMessage? = null
        try {
            val notificationComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.NOTIFICATION)

            notificationComponent.inject(this)

            message = parseData(inputData)
            val runAttemptCount = ordinal(inputData.getInt(DATA_TASK_RETRY_COUNT, -1) + 2)

            return notificationController.showNotification(message)
                .toSingle { ListenableWorker.Result.success() }
                .onErrorReturn { ex ->
                    if (ex is NotificationBuildException) {
                        Plog.warn(T_NOTIF, "Building notification failed in the $runAttemptCount attempt", ex, "Message Id" to message.messageId)
                        ListenableWorker.Result.retry()
                    } else {
                        Plog.error(T_NOTIF,
                            NotificationTaskException(
                                "Building notification failed with unrecoverable error",
                                ex
                            ), "Message Id" to message.messageId)
                        ListenableWorker.Result.failure()
                    }
                }
        } catch (ex: Exception) {
            Plog.error(T_NOTIF,
                NotificationTaskException(
                    "Notification Build task failed with fatal error",
                    ex
                ), "Message Data" to inputData.getString(DATA_NOTIFICATION_MESSAGE))
            if (message != null) {
                notificationErrorHandler.onNotificationBuildFailed(message, NotificationBuildStep.UNKNOWN)
                notificationStatusReporter.reportStatus(message, NotificationStatus.FAILED)
            }
            return Single.just(ListenableWorker.Result.failure())
        }
    }

    override fun onMaximumRetriesReached(inputData: Data) {
        try {
            val notificationComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Hengam.NOTIFICATION)
            notificationComponent.inject(this)
            val message = parseData(inputData)
            Plog.warn(T_NOTIF, "Maximum number of attempts reached for building notification, " +
                    "the notification will be discarded", "Message Id" to message.messageId)
            notificationStatusReporter.reportStatus(message, NotificationStatus.FAILED)
        } catch (ex: Exception) {
            Plog.error(T_NOTIF, ex)
        }
    }

    private fun parseData(inputData: Data): NotificationMessage {
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
        override fun maxAttemptsCount(): Int = hengamConfig.maxNotificationBuildAttempts
        override fun backoffPolicy(): BackoffPolicy? = hengamConfig.notificationBuildBackOffPolicy
        override fun backoffDelay(): Time? = hengamConfig.notificationBuildBackOffDelay
    }
}