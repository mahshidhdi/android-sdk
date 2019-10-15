package io.hengam.lib.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.app.NotificationManagerCompat
import androidx.work.workDataOf
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.internal.*
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.dagger.NotificationScope
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.tasks.NotificationBuildTask
import io.hengam.lib.notification.utils.ImageDownloader
import io.hengam.lib.notification.utils.ScreenWaker
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.assertCpuThread
import io.hengam.lib.utils.days
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import io.reactivex.Single
import java.util.*
import javax.inject.Inject

/**
 * Handles downstream messages of type [NotificationMessage]
 *
 * The controller will build the notification and to show it. In some cases notification
 * building may fail (e.g., if the notification has an image which needs to be downloaded and
 * network is not available). If the building does fail, the [NotificationBuildTask] will be
 * scheduled to run which will attempt to build the notification with an exponential back-off.
 *
 * There are several cases in which a notification will not be shown to the user:
 * - Notifications have been disabled, either by the hosting application or by the Android settings
 * - The notification has a one-time-key (a.k.a. otk) and a notification with the same key has
 *   previously been shown
 * - The notification is an update notification but the version is older or the same as the current
 *   app version
 *
 * Aside from showing the notification, the following actions will also be taken:
 * - A [NotificationReportMessage] will be sent specifying whether the notification was published
 * or the reason for it not being published.
 * - If the hosting application has provided notification callbacks, they will be called
 */
@NotificationScope
class NotificationController @Inject constructor(
        private val context: Context,
        private val notificationBuilderFactory: NotificationBuilderFactory,
        private val notificationStatusReporter: NotificationStatusReporter,
        private val notificationInteractionReporter: NotificationInteractionReporter,
        private val screenWaker: ScreenWaker,
        private val taskScheduler: TaskScheduler,
        private val moshi: HengamMoshi,
        private val notificationSettings: NotificationSettings,
        private val notificationStorage: NotificationStorage,
        private val notificationErrorHandler: NotificationErrorHandler,
        private val hengamLifecycle: HengamLifecycle,
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val imageDownloader: ImageDownloader,
        private val hengamConfig: HengamConfig,
        private val hengamStorage: HengamStorage
) {

    /**
     * The message id's of notifications which have been seen/published will be stored for a limited
     * amount of time. If the notification message with the same message id is received in this period
     * it will be ignored. Also, if we attempt to publish a  notification which has already been
     * published it will fail with a [DuplicateNotificationError] exception.
     */
    private val notificationStatus = hengamStorage.createStoredMap(
            "notification_status",
            Int::class.javaObjectType,
            expirationTime = days(3)
    )

    /**
     * Handle downstream [NotificationMessage]
     *
     * If the notification is not a scheduled notification will attempt to build the notification
     * and show it. If notification building fails, will schedule a [NotificationBuildTask]
     * to attempt later.
     *
     * If it is a scheduled notification, will store the notification and schedule the
     * [ScheduledNotificationReceiver] to be called at the specified time.
     *
     * If notification's are disabled, will skip the message and send a report message. However, the
     * hosting application's notification callbacks will still be called.
     */
    fun handleNotificationMessage(message: NotificationMessage) {
        assertCpuThread()

        Plog.debug(T_NOTIF, "Handling notification message", "Message Id" to message.messageId)

        if (shouldIgnoreBecauseOfDuplicateMessageId(message)) {
            return
        }

        if(shouldIgnoreBecauseOfUpdateMessage(message)){
            notificationStatusReporter.reportStatus(message, NotificationStatus.NOT_PUBLISHED_UPDATE)
            return
        }

        if(shouldIgnoreBecauseOfOneTimeKey(message)){
            notificationStatusReporter.reportStatus(message, NotificationStatus.NOT_PUBLISHED_OTK)
            return
        }

        // Conditions used to determine whether notification should be shown
        val notificationEnabledByApp = notificationSettings.isNotificationEnabled || message.forcePublish
        val notificationEnabledBySystem = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val notificationEnabledByHengam = hengamConfig.isNotificationEnabled
        val shouldShowNotification = message.showNotification && message.title != null
        val forcePublish = message.forcePublish
        var willBeScheduled = false

        if (message.delayUntil == "open_app" && !hengamLifecycle.isAppOpened) {
            notificationStorage.delayedNotification = message
        } else {
            if (shouldShowNotification && (notificationEnabledByApp || forcePublish)
                    && notificationEnabledBySystem && notificationEnabledByHengam) {
                if (message.scheduledTime != null && message.scheduledTime.after(Date())) {
                    willBeScheduled = true
                    notificationStorage.saveScheduledNotificationMessage(message)
                    scheduleNotification(message)
                } else {
                    runNotificationBuilder(message)
                }
            } else if (shouldShowNotification && !notificationEnabledByApp) {
                notificationStatusReporter.reportStatus(message, NotificationStatus.APP_DISABLED)
            } else if (shouldShowNotification && !notificationEnabledBySystem) {
                notificationStatusReporter.reportStatus(message, NotificationStatus.SYSTEM_DISABLED)
            } else if (shouldShowNotification && !notificationEnabledByHengam) {
                notificationStatusReporter.reportStatus(message, NotificationStatus.HENGAM_DISABLED)
            }
        }

        imageDownloader.purgeOutdatedCache()

        // Notification callbacks for scheduled notifications will be called once the scheduled time
        // arrives. Also, callbacks will not be called if notifications are disabled through Hengam
        // config.
        if (!willBeScheduled && notificationEnabledByHengam) {
            invokeNotificationListeners(message)
        }
    }

    /**
     * Attempt to build the notification
     * If successful, show the notification to the user and send a notification report message
     *
     * Performs operation on [ioThread] but results will be observed on [cpuThread]
     *
     * @throws NotificationBuildException If the notification building failed
     * @return A [Completable] that will complete if notification is created and shown and will
     *         fail with a [NotificationBuildException] if notification building fails.
     */
    @Throws(NotificationBuildException::class)
    fun showNotification(message: NotificationMessage): Completable {
        val notifId = message.getNotificationId()

        val builder = Single.fromCallable {
            if (!message.isUpdateNotification && !message.allowDuplicates &&
                    notificationStatus[message.messageId] == NOTIFICATION_PUBLISHED) {
                throw DuplicateNotificationError("Attempted to show an already published notification")
            }
            notificationBuilderFactory.createNotificationBuilder(message)
        }

        return builder
                .flatMap { notificationBuilder -> notificationBuilder.build() }
                .doOnSuccess { notification ->
                    Plog.info(T_NOTIF, "Notification successfully created, showing notification to user", "Notification Message Id" to message.messageId)

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(notifId, notification)
                    notificationStatus[message.messageId] = NOTIFICATION_PUBLISHED

                    if (message.wakeScreen) screenWaker.wakeScreen()

                    notificationInteractionReporter.onNotificationPublished(message.messageId)
                    notificationStatusReporter.reportStatus(message, NotificationStatus.PUBLISHED)
                }
                .doOnError { ex ->
                    if (ex !is NotificationBuildException) {
                        notificationErrorHandler.onNotificationBuildFailed(message, NotificationBuildStep.UNKNOWN)
                        notificationStatusReporter.reportStatus(message, NotificationStatus.FAILED)
                    }
                }
                .ignoreElement()
    }

    /**
     * Schedule a [NotificationBuildTask] task to run. The task will build and the show notification
     */
    fun runNotificationBuilder(message: NotificationMessage) {
        val messageAdapter = moshi.adapter(NotificationMessage::class.java)
        taskScheduler.scheduleTask(
                NotificationBuildTask.Options(message),
                workDataOf(NotificationBuildTask.DATA_NOTIFICATION_MESSAGE to messageAdapter.toJson(message)),
                initialDelay = message.delay
        )
    }

    /**
     * Schedule notification to be shown at a later time.
     *
     * Uses the [AlarmManager] to trigger a [ScheduledNotificationReceiver] at the desired time.
     */
    fun scheduleNotification(message: NotificationMessage) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        val intent = Intent(context, ScheduledNotificationReceiver::class.java)
        val notificationAdapter = moshi.adapter(NotificationMessage::class.java)
        val jsonNotif = notificationAdapter.toJson(message)
        intent.putExtra(ScheduledNotificationReceiver.DATA_MESSAGE, jsonNotif)

        val pendingIntent =
                PendingIntent.getBroadcast(context, message.messageId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val calendar = Calendar.getInstance()
        calendar.time = message.scheduledTime
        alarmManager?.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

        Plog.debug(T_NOTIF, "Notification scheduled for ${calendar.time}", "Notification Id" to message.messageId)
    }

    /**
     * Should be called with a scheduled notification once the notification's scheduled time has
     * arrived.
     */
    fun handleScheduledNotification(message: NotificationMessage) {
        runNotificationBuilder(message)
        invokeNotificationListeners(message)
        notificationStorage.removeScheduledNotificationMessage(message)
    }

    /**
     * If the developer has provided callbacks for notifications, call them
     */
    fun invokeNotificationListeners(message: NotificationMessage) {
        if (notificationSettings.hengamNotificationListener != null) {
            val listener = notificationSettings.hengamNotificationListener
            if (message.isNotificationPresentable()) {
                val notificationData = notificationInteractionReporter.getNotificationData(message)

                uiThread {
                    try {
                        listener?.onNotification(notificationData)
                    } catch (e: Exception) {
                        Plog.warn.message("Unhandled exception occurred in HengamNotificationListener")
                                .withTag(T_NOTIF)
                                .withError(e)
                                .useLogCatLevel(LogLevel.ERROR)
                                .log()
                    }
                }
            }

            if (message.customContent != null) {
                Plog.info(T_NOTIF, "Delivering custom content to notification listener")
                val customContent = message.customContent

                uiThread {
                    try {
                        listener?.onCustomContentNotification(customContent)
                    } catch (e: Exception) {
                        Plog.warn.message("Unhandled exception occurred in HengamCustomContentListener")
                                .withTag(T_NOTIF)
                                .withError(e)
                                .useLogCatLevel(LogLevel.ERROR)
                                .log()
                    }
                }
            }
        }
    }

    fun rescheduleNotificationsOnBootComplete() {
        hengamLifecycle.onBootCompleted
            .justDo(T_NOTIF) {
                val scheduledNotifications = notificationStorage.scheduledNotifications
                for (message in scheduledNotifications) {
                    if (message.scheduledTime != null && message.scheduledTime.after(Date())) {
                        scheduleNotification(message)
                    } else {
                        runNotificationBuilder(message)
                        invokeNotificationListeners(message)
                        notificationStorage.removeScheduledNotificationMessage(message)
                    }
                }

                if (scheduledNotifications.isNotEmpty()) {
                    Plog.debug(T_NOTIF, "${scheduledNotifications.size} notifications rescheduled on system boot")
                }
            }
    }

    /**
     * Checks whether the message should be ignored because a notification with the same message id
     * has already been processed.
     */
    private fun shouldIgnoreBecauseOfDuplicateMessageId(message: NotificationMessage): Boolean {
        if (!message.allowDuplicates && message.messageId in notificationStatus) {
            Plog.warn(T_NOTIF, "Skipping notification due to duplicate message Id", "Message Id" to message.messageId)
            return true
        }
        notificationStatus[message.messageId] = NOTIFICATION_SEEN
        return false
    }

    /**
     * Checks whether the message contains a one-time-key and skips the message if the
     * one-time-key has already been seen
     *
     * @return true if message should be ignored
     */
    private fun shouldIgnoreBecauseOfOneTimeKey(message: NotificationMessage): Boolean {
        if (message.oneTimeKey.isNullOrBlank()) {
            return false
        }

        return if (message.oneTimeKey !in notificationSettings.usedOneTimeKeys) {
            Plog.debug(T_NOTIF, "Notification one-time-key seen for the first time", "One Time Key" to message.oneTimeKey)
            notificationSettings.usedOneTimeKeys.add(message.oneTimeKey)
            false
        } else {
            Plog.debug(T_NOTIF, "Notification with one-time-key received but key has " +
                    "already been seen, skipping notification","One Time Key" to message.oneTimeKey)
            true
        }
    }

    /**
     * Checks whether the message is an update app notification and ignores the app if the version
     * is lower or equal to the current app version.
     *
     * @return true if the message should be ignored
     */
    private fun shouldIgnoreBecauseOfUpdateMessage(message: NotificationMessage): Boolean {
        if (message.cancelUpdate != null) {
            notificationStorage.removeUpdateNotification()
        }

        if (message.updateToAppVersion == null) {
            return false
        }

        val currentVersion = applicationInfoHelper.getApplicationVersionCode() ?: -1

        return if (message.updateToAppVersion <= currentVersion) {
            Plog.debug(T_NOTIF, "Ignoring update notification since the version is lower than " +
                    "the current application version",
                "Current Version" to currentVersion,
                "Message Version" to message.updateToAppVersion
            )
            true
        } else {
            notificationStorage.updateNotification = message
            false
        }
    }

    companion object {
        const val NOTIFICATION_SEEN = 1
        const val NOTIFICATION_PUBLISHED = 2
    }
}

class DuplicateNotificationError(message: String, cause: Throwable? = null) : Exception(message, cause)