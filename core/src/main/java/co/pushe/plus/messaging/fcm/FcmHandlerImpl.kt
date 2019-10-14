package co.pushe.plus.messaging.fcm

import co.pushe.plus.Constants
import co.pushe.plus.LogTag
import co.pushe.plus.MessageFields
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.log.Plog
import com.google.firebase.messaging.RemoteMessage
import javax.inject.Inject

@CoreScope
class FcmHandlerImpl @Inject constructor(
        private val fcmTokenStore: FcmTokenStore,
        private val fcmMessaging: FcmMessaging
) : FcmHandler {

    override fun onNewToken(token: String?) {
        cpuThread {
            if (token == null) {
                Plog.error(LogTag.T_FCM, LogTag.T_MESSAGE, "Received null token from Fcm")
            } else {
                Plog.trace(LogTag.T_FCM, "FCM token update event received")
                fcmTokenStore.refreshFirebaseToken()
            }
        }
    }

    override fun onMessageReceived(fcmMessage: RemoteMessage?): Boolean {
        if (fcmMessage == null || fcmMessage.data[MessageFields.COURIER]?.toLowerCase() != Constants.PUSHE_COURIER_VALUE) {
            return false
        }
        cpuThread { handleMessage(fcmMessage) }
        return true
    }

    override fun onSendError(messageId: String, cause: Exception?) {
        cpuThread {
            fcmMessaging.onFcmMessageFailed(messageId,
                    cause ?: FcmUnknownMessageSendException("Sending message failed for unknown reason"))
        }
    }

    override fun onMessageSent(messageId: String) {
        cpuThread { fcmMessaging.onFcmMessageSent(messageId) }
    }

    override fun onDeletedMessages() {
    }

    private fun handleMessage(fcmMessage: RemoteMessage) {
        fcmMessaging.onFcmMessageReceived(
                fcmMessage.data,
                fcmMessage.messageId,
                fcmMessage.sentTime,
                fcmMessage.priority
        )
    }
}

class FcmUnknownMessageSendException(message: String) : Exception(message)