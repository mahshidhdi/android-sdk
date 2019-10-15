package io.hengam.lib.notification

import android.app.IntentService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.hengam.lib.Hengam
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.actions.Action
import io.hengam.lib.notification.actions.ActionContextFactory
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.utils.NetworkInfoHelper
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.subscribeBy
import io.reactivex.Completable
import javax.inject.Inject

/**
 * A service which is executed when the user clicks or dismisses a notification or when a
 * notification button is clicked.
 *
 * Handling actions is performed on the same thread the IntentService is run on (and not the
 * [cpuThread]) except when creating and sending an upstream message. This is to avoid
 * incurring any delays when handling notification actions.
 */
class NotificationActionService : IntentService("NotificationActionService") {
    @Inject lateinit var moshi: HengamMoshi
    @Inject lateinit var context: Context
    @Inject lateinit var postOffice: PostOffice
    @Inject lateinit var networkInfo: NetworkInfoHelper
    @Inject lateinit var actionContextFactory: ActionContextFactory
    @Inject lateinit var notificationInteractionReporter: NotificationInteractionReporter


    override fun onHandleIntent(intent: Intent?) {
       handelIntent(intent)
    }

    fun handelIntent(intent: Intent?){
        Plog.debug(T_NOTIF, T_NOTIF_ACTION, "Running Action Service")

        val data = intent?.extras
        if (intent == null || data == null) {
            Plog.error(T_NOTIF, T_NOTIF_ACTION, "No intent data received in Action Service")
            return
        }

        try {
            val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Hengam.NOTIFICATION)
            notifComponent.inject(this)
            handleActionData(data)
        } catch (ex: Exception) {
            Plog.error(T_NOTIF, T_NOTIF_ACTION, "Unhandled error occurred while handling " +
                    "notification action", ex)
        }
    }

    private fun handleActionData(data: Bundle) {
        val actionJson = data.getString(INTENT_DATA_ACTION)
        val notificationJson = data.getString(INTENT_DATA_NOTIFICATION)

        val action = actionJson?.let { moshi.adapter(Action::class.java).fromJson(actionJson) }
        val notification = notificationJson?.let {
            moshi.adapter(NotificationMessage::class.java).fromJson(notificationJson)
        }

        if (notification == null) {
            Plog.error(T_NOTIF, T_NOTIF_ACTION, "Notification was null in Action Service")
            return
        }

        /* Execute Action */
        action?.let {
            action.executeAsCompletable(actionContextFactory.createActionContext(notification))
                    .subscribeBy(
                            onError = {error ->
                                Plog.error(T_NOTIF, T_NOTIF_ACTION, error,"Action Data" to actionJson)
                            }
                    )
        }

        /* Send Message */
        sendNotificationActionMessage(notification, data)
            .subscribeOn(cpuThread())
            .justDo(T_NOTIF, T_NOTIF_ACTION)

        dismissNotifIfButtonClick(notification, data)
            .subscribeOn(cpuThread())
            .justDo(T_NOTIF, T_NOTIF_ACTION)
    }

    private fun dismissNotifIfButtonClick(notification: NotificationMessage, data: Bundle): Completable {
        return Completable.fromCallable {
            val responseAction = data.getString(INTENT_DATA_RESPONSE_ACTION)
            val buttonId = if (data.containsKey(INTENT_DATA_BUTTON_ID)) data.getString(INTENT_DATA_BUTTON_ID, "") else null

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (responseAction == RESPONSE_ACTION_CLICK && buttonId != null) {
                notificationManager.cancel(notification.getNotificationId())
            }
        }
    }

    private fun sendNotificationActionMessage(notification: NotificationMessage, data: Bundle): Completable {
        return Completable.fromCallable {
            val responseAction = data.getString(INTENT_DATA_RESPONSE_ACTION)
            val buttonId = if (data.containsKey(INTENT_DATA_BUTTON_ID)) data.getString(INTENT_DATA_BUTTON_ID, "") else null

            when (responseAction) {
                RESPONSE_ACTION_CLICK -> notificationInteractionReporter.onNotificationClicked(notification, buttonId)
                RESPONSE_ACTION_DISMISS -> notificationInteractionReporter.onNotificationDismissed(notification)
                else -> Plog.error(T_NOTIF, T_NOTIF_ACTION, "Invalid notification action received in Action Service: $responseAction")
            }
        }
    }

    companion object {
        const val INTENT_DATA_ACTION = "action"
        const val INTENT_DATA_NOTIFICATION = "notification"
        const val INTENT_DATA_RESPONSE_ACTION = "response_action"
        const val INTENT_DATA_BUTTON_ID = "button_id"

        const val RESPONSE_ACTION_CLICK = "clicked"
        const val RESPONSE_ACTION_DISMISS = "dismissed"
    }
}