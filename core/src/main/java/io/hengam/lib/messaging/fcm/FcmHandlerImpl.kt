package io.hengam.lib.messaging.fcm

import io.hengam.lib.Constants
import io.hengam.lib.LogTag
import io.hengam.lib.MessageFields
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.log.Plog
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
        if (fcmMessage == null || fcmMessage.data[MessageFields.COURIER]?.toLowerCase() != Constants.HENGAM_COURIER_VALUE) {
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