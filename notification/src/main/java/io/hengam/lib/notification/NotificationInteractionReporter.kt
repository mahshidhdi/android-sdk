package io.hengam.lib.notification

import android.util.Log
import io.hengam.lib.internal.uiThread
import io.hengam.lib.messages.common.ApplicationDetail
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.actions.DownloadAppAction
import io.hengam.lib.notification.dagger.NotificationScope
import io.hengam.lib.notification.messages.downstream.NotificationButton
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.messages.upstream.ApplicationDownloadMessage
import io.hengam.lib.notification.messages.upstream.ApplicationInstallMessage
import io.hengam.lib.notification.messages.upstream.NotificationActionMessage
import io.hengam.lib.notification.utils.getNotificationButtonIds
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import javax.inject.Inject

/**
 * Reports user and device interaction events that occur with a notification to the server.
 *
 * Contains methods for the following events (events with a `*` are reported to the server):
 * - Notification published (shown to the user)
 * - * Notification clicked
 * - * Notification dismissed
 * - * Notification application download successful (see [NotificationAppInstaller])
 * -   Notification application download failed (see [NotificationAppInstaller])
 * - * Notification application app installed (see [NotificationAppInstaller])
 * - * Notification application app not installed (see [NotificationAppInstaller])
 */
@NotificationScope
class NotificationInteractionReporter @Inject constructor(
        private val postOffice: PostOffice,
        private val notificationSettings: NotificationSettings,
        hengamStorage: HengamStorage
) {
    /**
     * Holds statistics for published notifications.
     * Some notification interaction event messages require information about previous events to be
     * sent as well (e.g, publish time). So for notifications which have not gone through their whole
     * lifecycle we store their stats in the storage.
     *
     * @see NOTIFICATION_STATS_EXPIRATION_TIME to see when the stats are removed from storage
     */
    private val notificationStats = hengamStorage.createStoredMap(
            "notification_interactions",
            InteractionStats::class.java,
            InteractionStats.Adapter(),
            NOTIFICATION_STATS_EXPIRATION_TIME
    )

    /**
     * Should be called when a notification is successfully shown to the user
     *
     * @param messageId The message id for the [NotificationMessage] which has been show
     */
    fun onNotificationPublished(messageId: String) {
        notificationStats[messageId] = InteractionStats(
                messageId,
                publishTime = TimeUtils.now()
        )
    }

    /**
     * Should be called when a notification has been clicked.
     *
     * Sends an upstream [NotificationActionMessage] to the server.
     *
     * @param notification The [NotificationMessage] which has been clicked
     * @param buttonId If a notification button was clicked instead of the actual notification
     *                 should specify the button id
     */
    fun onNotificationClicked(notification: NotificationMessage, buttonId: String?) {

        val messageId = notification.messageId

        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Sending notification clicked event to server",
            "Message Id" to messageId,
            buttonId?.let { "Button Id" to buttonId }
        )

        val notificationInteraction = getNotificationInteractionStats(messageId)

        val message = NotificationActionMessage(
                originalMessageId = messageId,
                status = NotificationActionMessage.NotificationResponseAction.CLICKED,
                responseButtonId = buttonId,
                notificationPublishTime = notificationInteraction?.publishTime
        )
        postOffice.sendMessage(message)

        notificationStats[messageId] = notificationInteraction?.copy(clickTime = TimeUtils.now())
            ?: InteractionStats(messageId, clickTime = TimeUtils.now())

        if (buttonId == null) {
            invokeNotificationClickCallback(notification)
        } else {
            invokeNotificationButtonClickCallback(notification, buttonId)
        }
    }

    /**
     * Should be called if a notification has been dismissed by the user.
     *
     * Sends an upstream [NotificationActionMessage] to the server.
     *
     * @param notification The [NotificationMessage] which was dismissed
     */
    fun onNotificationDismissed(notification: NotificationMessage) {
        val messageId = notification.messageId

        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Sending notification dismissed event to server", "Message Id" to messageId)

        val notificationStats = getNotificationInteractionStats(messageId)
        val message = NotificationActionMessage(
                originalMessageId = messageId,
                status = NotificationActionMessage.NotificationResponseAction.DISMISSED,
                notificationPublishTime = notificationStats?.publishTime
        )
        postOffice.sendMessage(message)

        this.notificationStats.remove(messageId)

        invokeNotificationDismissCallback(notification)
    }

    /**
     * Should be called if a notification-triggered application download has been successful.
     *
     * Some notification actions (e.g, [DownloadAppAction]) trigger an application download and
     * install on behalf of the user when he or she clicks the notification. This method should be
     * called if an action has triggered a download and the download has been successful.
     *
     * Sends an upstream [ApplicationDownloadMessage] to the server.
     *
     * @param messageId The message id for the [NotificationMessage] which triggered the download
     * @param packageName The package name of the application which was downloaded
     */
    fun onApkDownloadSuccess(messageId: String, packageName: String) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Sending notification apk download success event to server", "Message Id" to messageId)

        val notificationInteraction = getNotificationInteractionStats(messageId)
        val downloadedAt = TimeUtils.now()

        val message = ApplicationDownloadMessage(
                originalMessageId = messageId,
                packageName = packageName,
                publishedAt = notificationInteraction?.publishTime,
                clickedAt = notificationInteraction?.clickTime,
                downloadedAt = downloadedAt
        )
        postOffice.sendMessage(message)

        notificationStats[messageId] = notificationInteraction?.copy(apkDownloadTime = downloadedAt)
                ?: InteractionStats(messageId, apkDownloadTime = downloadedAt)
    }

    /**
     * Should be called if a notification-triggered application download has failed.
     * Does not send any messages to the server.
     *
     * @see [onApkDownloadSuccess]
     * @param messageId The message id for the [NotificationMessage] which triggered the download
     * @param packageName The package name of the application being downloaded
     */
    fun onApkDownloadFailed(messageId: String, packageName: String) {
        // Doing nothing for failed downloads
        notificationStats.remove(messageId)
    }

    /**
     * Should be called when a notification-triggered application install has been successfully
     * installed by the user.
     *
     * Sends an upstream [ApplicationInstallMessage] to the server.
     *
     * @param messageId The message id for the [NotificationMessage] which triggered the application install
     * @param appInfo An [ApplicationDetail] containing information about the newly installed package
     * @param previousVersion If the application was already installed prior to receiving the notification,
     *                        should contain the application's previous version
     */
    fun onApkInstalled(messageId: String, appInfo: ApplicationDetail, previousVersion: String?) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Sending notification apk install success event to server", "Message Id" to messageId)

        val notificationInteraction = getNotificationInteractionStats(messageId)

        val message = ApplicationInstallMessage(
                originalMessageId = messageId,
                status = ApplicationInstallMessage.InstallStatus.INSTALLED,
                previousVersion = previousVersion,
                appInfo = appInfo,
                publishedAt = notificationInteraction?.publishTime,
                clickedAt = notificationInteraction?.clickTime,
                downloadedAt = notificationInteraction?.apkDownloadTime,
                installCheckedAt = TimeUtils.now()
        )
        postOffice.sendMessage(message)

        notificationStats.remove(messageId)
    }

    /**
     * Should be called if a notification-triggered application install was not installed by the user.
     *
     * Sends an upstream [ApplicationInstallMessage] message to the server.
     *
     * @param messageId The message id of the [NotificationMessage] which triggered the installation
     * @param previousVersion If a version of the application already exists on the user's device,
     *                        this parameter should contain the app's version
     */
    fun onApkNotInstalled(messageId: String, previousVersion: String?) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Sending notification apk not installed event to server","Message Id" to messageId)

        val notificationInteraction = getNotificationInteractionStats(messageId)

        val message = ApplicationInstallMessage(
                originalMessageId = messageId,
                status = ApplicationInstallMessage.InstallStatus.NOT_INSTALLED,
                previousVersion = previousVersion,
                publishedAt = notificationInteraction?.publishTime,
                clickedAt = notificationInteraction?.clickTime,
                downloadedAt = notificationInteraction?.apkDownloadTime,
                installCheckedAt = TimeUtils.now()
        )
        postOffice.sendMessage(message)

        notificationStats.remove(messageId)
    }

    /**
     * Get a stored [InteractionStats] object or return `null` if it doesn't exist.
     *
     * If an instance with the given `messageId` doesn't exist it will also report an error.
     */
    private fun getNotificationInteractionStats(messageId: String): InteractionStats? {
        val notificationInteraction = notificationStats[messageId]

        if (notificationInteraction == null) {
            Plog.error(T_NOTIF, T_NOTIF_ACTION, NotificationInteractionException("Notification interaction object missing"),"Message Id" to messageId)
        }

        return notificationInteraction
    }

    fun getNotificationData(notification: NotificationMessage): NotificationData {
        return NotificationData(
            messageId = notification.messageId,
            title = notification.title,
            content = notification.content,
            bigTitle = notification.bigTitle,
            bigContent = notification.bigContent,
            bigIconUrl = notification.bigIconUrl,
            summary = notification.summary,
            imageUrl = notification.imageUrl,
            iconUrl = notification.iconUrl,
            customContent = notification.customContent,
            buttons = getButtonsData(notification.buttons)
        )
    }

    private fun getButtonsData(buttons: List<NotificationButton>): List<NotificationButtonData>{
        val buttonIds = getNotificationButtonIds(buttons)
        return buttons.mapIndexed { index, it -> NotificationButtonData(buttonIds[index], it.text, it.icon) }
    }

    private fun invokeNotificationButtonClickCallback(notification: NotificationMessage, buttonId: String) {
        if (notificationSettings.hengamNotificationListener != null) {
            Plog.info(T_NOTIF, T_NOTIF_ACTION, "Delivering notification button click event to notification listener",
                "Message Id" to notification.messageId,
                "Button Id" to buttonId
            )

            val listener = notificationSettings.hengamNotificationListener

            val notificationData = getNotificationData(notification)
            val buttonData = notificationData.buttons.find { it.id == buttonId } ?: NotificationButtonData(buttonId, null, null)

            uiThread {
                try {
                    listener?.onNotificationButtonClick(buttonData, notificationData)
                } catch (e: Exception) {
                    Log.e("Hengam", "Unhandled exception occurred in HengamNotificationListener", e)
                }
            }
        }
    }

    private fun invokeNotificationClickCallback(notification: NotificationMessage) {
        if (notificationSettings.hengamNotificationListener != null) {
            Plog.info(T_NOTIF, T_NOTIF_ACTION, "Delivering notification click event to notification listener", "Message Id" to notification.messageId)
            val listener = notificationSettings.hengamNotificationListener

            val notificationData = getNotificationData(notification)
            uiThread {
                try {
                    listener?.onNotificationClick(notificationData)
                } catch (e: Exception) {
                    Log.e("Hengam", "Unhandled exception occurred in HengamNotificationListener", e)
                }
            }
        }
    }

    private fun invokeNotificationDismissCallback(notification: NotificationMessage) {
        if (notificationSettings.hengamNotificationListener != null) {
            Plog.info(T_NOTIF, T_NOTIF_ACTION, "Delivering notification dismiss event to notification listener", "Message Id" to notification.messageId)
            val listener = notificationSettings.hengamNotificationListener

            val notificationData = getNotificationData(notification)
            uiThread {
                try {
                    listener?.onNotificationDismiss(notificationData)
                } catch (e: Exception) {
                    Log.e("Hengam", "Unhandled exception occurred in HengamNotificationListener", e)
                }
            }
        }
    }

    companion object {
        /**
         * In some cases the notifications stored in [notificationStats] are removed manually (e.g,
         * when a notification is dismissed) but in other cases such as on a notification clicked
         * event, the [NotificationInteractionReporter] does not know whether it should remove the
         * stats or whether further events will come for the notification.
         *
         * The [notificationStats] will use an expiration time defined by [NOTIFICATION_STATS_EXPIRATION_TIME]
         * to clear the storage from stats which were not removed manually
         */
        val NOTIFICATION_STATS_EXPIRATION_TIME = days(7)
    }
}

/**
 * Holds information about the interaction events which have occurred with a notification.
 *
 * @param messageId The message id of the [NotificationMessage] which triggered the notification
 * @param publishTime The time at which the notification was shown to the user (in epoch millis)
 * @param clickTime The time at which the notification was clicked by the user (in epoch millis)
 * @param apkDownloadTime The time at which an APK download was successfully completed for the
 *                        notification action (if it had a download apk action) (in epoch millis)
 */
data class InteractionStats(
        val messageId: String,
        val publishTime: Time? = null,
        val clickTime: Time? = null,
        val apkDownloadTime: Time? = null
) {
    class Adapter {
        /**
         * The @JvmSuppressWildcards is needed, see here: https://github.com/square/moshi/issues/573
         */
        @ToJson fun toJson(interactionStats: InteractionStats): Map<String, @JvmSuppressWildcards Any?> = mapOf(
                "message_id" to interactionStats.messageId,
                "publish_time" to interactionStats.publishTime?.toMillis(),
                "click_time" to interactionStats.clickTime?.toMillis(),
                "download_time" to interactionStats.apkDownloadTime?.toMillis()
        )
        @FromJson fun fromJson(json: Map<String, @JvmSuppressWildcards Any?>): InteractionStats = InteractionStats(
                messageId = json["message_id"] as? String ?: throw JsonDataException("Missing 'message_id' field"),
                publishTime = (json["publish_time"] as Long?)?.let { millis(it) },
                clickTime = (json["click_time"] as Long?)?.let { millis(it) },
                apkDownloadTime = (json["download_time"] as Long?)?.let { millis(it) }
        )
    }
}

class NotificationInteractionException(message: String, cause: Throwable? = null) : Exception(message, cause)