package co.pushe.plus.notification

import android.app.IntentService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import co.pushe.plus.Pushe
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.actions.Action
import co.pushe.plus.notification.actions.ActionContextFactory
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.NetworkInfoHelper
import co.pushe.plus.utils.rx.justDo
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.subscribeBy
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
    @Inject lateinit var moshi: PusheMoshi
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
            val notifComponent = PusheInternals.getComponent(NotificationComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.NOTIFICATION)
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