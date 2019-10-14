package co.pushe.plus.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.pushe.plus.Pushe
import co.pushe.plus.internal.*
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.log.Plog

class ScheduledNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        cpuThread {
            Plog.debug(T_NOTIF, "Scheduled notification has been triggered, attempting to show notification")

            val notifComponent = PusheInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.CORE)

            val moshi = notifComponent.moshi()
            val notificationController = notifComponent.notificationController()

            val notifAdapter = moshi.adapter(NotificationMessage::class.java)
            val message = notifAdapter.fromJson(intent.getStringExtra(DATA_MESSAGE))

            if (message != null) {
                notificationController.handleScheduledNotification(message)
            }
        }
    }

    companion object {
        const val DATA_MESSAGE = "message"
    }

    class ScheduledNotificationException(message: String, cause: Throwable? = null) : Exception(message, cause)
}