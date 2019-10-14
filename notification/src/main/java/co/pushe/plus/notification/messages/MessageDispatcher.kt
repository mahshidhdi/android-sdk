package co.pushe.plus.notification.messages

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.NotificationController
import co.pushe.plus.notification.NotificationStatus
import co.pushe.plus.notification.NotificationStatusReporter
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import javax.inject.Inject

class MessageDispatcher @Inject constructor(
    private val postOffice: PostOffice,
    private val notificationCtrl: NotificationController,
    private val notificationStatusReporter: NotificationStatusReporter

    ) {

    fun listenForMessages() {
        /* Handle NotificationMessage */
        postOffice.mailBox(NotificationMessage.Parser(),
                handler = { notificationCtrl.handleNotificationMessage(it) },
                parseErrorHandler = { notificationStatusReporter.reportStatus(it["message_id"] as String, NotificationStatus.PARSE_FAILED) }
        )

        /* Handle NotificationMessage with type `t30` */
        postOffice.mailBox(NotificationMessage.Parser(MessageType.Notification.Downstream.NOTIFICATION_ALTERNATE),
                handler = { notificationCtrl.handleNotificationMessage(it) },
                parseErrorHandler = { notificationStatusReporter.reportStatus(it["message_id"] as String, NotificationStatus.PARSE_FAILED) }
        )
    }
}
