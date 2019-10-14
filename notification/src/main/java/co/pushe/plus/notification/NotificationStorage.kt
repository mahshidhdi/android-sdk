package co.pushe.plus.notification

import co.pushe.plus.internal.PusheException
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.dagger.NotificationScope
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.TimeUtils.nowMillis
import co.pushe.plus.utils.days
import co.pushe.plus.utils.log.Plog
import javax.inject.Inject

@NotificationScope
class NotificationStorage @Inject constructor(
        private val applicationInfoHelper: ApplicationInfoHelper,
        pusheStorage: PusheStorage
) {
    private val emptyMessage = NotificationMessage("\$empty\$", null, null)
    private var delayedNotificationStore = pusheStorage.storedObject("delayed_notification", emptyMessage, NotificationMessage::class.java)
    private var updateNotificationStore = pusheStorage.storedObject("update_notification", emptyMessage, NotificationMessage::class.java)
    private var delayedNotificationTime = pusheStorage.storedLong("delayed_notification_time", -1L)
    private var updateNotificationTime = pusheStorage.storedLong("update_notification_time", -1L)
    private var updatedNotificationLastShownTime by pusheStorage.storedLong("update_notification_show_time", -1L)

    private val scheduledNotificationsStore = pusheStorage.createStoredMap("scheduled_notifications", NotificationMessage::class.java)

    var delayedNotification: NotificationMessage?
        get() {
            val storedTime = delayedNotificationTime.get()
            val isMessageExpired = (nowMillis() - storedTime) > days(7).toMillis()
            if (storedTime != -1L && isMessageExpired) {
                removeDelayedNotification()
            }
            return if (storedTime == -1L || isMessageExpired) {
                null
            } else {
                delayedNotificationStore.get().run {
                    if (messageId == emptyMessage.messageId && title == null && content == null) {
                        null
                    } else {
                        this
                    }
                }
            }
        }
        set(message) {
            if (message != null) {
                delayedNotificationStore.set(message)
                delayedNotificationTime.set(nowMillis())
            } else {
                removeDelayedNotification()
            }
        }

    var updateNotification: NotificationMessage?
        get() {
            val storedTime = updateNotificationTime.get()
            val isMessageExpired = nowMillis() - storedTime >= days(7).toMillis()
            if (storedTime != -1L && isMessageExpired) {
                removeUpdateNotification()
            }
            return if (storedTime == -1L || isMessageExpired) {
                null
            } else {
                val message = updateNotificationStore.get().run {
                    if (messageId == emptyMessage.messageId && title == null && content == null) {
                        null
                    } else {
                        this
                    }
                }
                val currentVersion = applicationInfoHelper.getApplicationVersionCode() ?:
                        throw PusheException("Could not obtain application version code")
                if (message?.updateToAppVersion ?: -1 < currentVersion) {
                    removeUpdateNotification()
                    null
                } else {
                    message
                }
            }
        }
        set(message) {
            if (message != null) {
                updateNotificationStore.set(message)
                updateNotificationTime.set(nowMillis())
            } else {
                removeUpdateNotification()
            }

        }

    val scheduledNotifications: List<NotificationMessage>
        get() = scheduledNotificationsStore.values.toList()

    /**
     * @return True if notif should be shown, False if it was not about to be shown
     */
    fun shouldShowUpdatedNotification() = updatedNotificationLastShownTime == -1L || nowMillis() - updatedNotificationLastShownTime >= days(1).toMillis()

    /**
     * Must be called when updated notification was about to be shown
     */
    fun onUpdateNotificationShown() {
        updatedNotificationLastShownTime = nowMillis()
    }

    /**
     * By calling this function delayedMessage and it's time will be removed from storage
     */
    fun removeDelayedNotification() {
        Plog.trace(T_NOTIF, "Removing stored delayed notification")
        delayedNotificationStore.delete()
        delayedNotificationTime.delete()
    }

    /**
     * By calling this function updated notification will be removed from storage
     */
    fun removeUpdateNotification() {
        Plog.trace(T_NOTIF,"Removing stored update notification")
        updateNotificationStore.delete()
        updateNotificationTime.delete()
    }

    fun saveScheduledNotificationMessage(message: NotificationMessage) {
        scheduledNotificationsStore[message.messageId] = message
        Plog.debug(T_NOTIF, "Scheduled notification added to store",
            "Notification Message Id" to message.messageId,
            "Store Size" to scheduledNotificationsStore.size
        )
    }

    fun removeScheduledNotificationMessage(message: NotificationMessage) {
        scheduledNotificationsStore.remove(message.messageId)
        Plog.debug(T_NOTIF, "Scheduled notification removed from store",
            "Notification Message Id" to message.messageId,
            "Store Size" to scheduledNotificationsStore.size
        )
    }
}

