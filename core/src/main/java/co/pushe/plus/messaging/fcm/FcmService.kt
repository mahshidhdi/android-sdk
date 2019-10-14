package co.pushe.plus.messaging.fcm

import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheInternals
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FcmService : FirebaseMessagingService () {
    private val core
        get() = PusheInternals.getComponent(CoreComponent::class.java)

    override fun onNewToken(token: String?) {
        super.onNewToken(token)
        core?.fcmHandler()?.onNewToken(token)
    }

    override fun onMessageReceived(fcmMessage: RemoteMessage?) {
        core?.fcmHandler()?.onMessageReceived(fcmMessage)
    }

    override fun onSendError(messageId: String, cause: Exception?) {
        core?.fcmHandler()?.onSendError(messageId, cause)
    }

    override fun onMessageSent(messageId: String) {
        core?.fcmHandler()?.onMessageSent(messageId)
    }

    override fun onDeletedMessages() {
        core?.fcmHandler()?.onDeletedMessages()
    }
}
