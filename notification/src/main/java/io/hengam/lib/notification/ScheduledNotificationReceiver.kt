package io.hengam.lib.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hengam.lib.Hengam
import io.hengam.lib.internal.*
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.utils.log.Plog

class ScheduledNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        cpuThread {
            Plog.debug(T_NOTIF, "Scheduled notification has been triggered, attempting to show notification")

            val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Hengam.CORE)

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