package io.hengam.lib.notification.messages

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.notification.NotificationController
import io.hengam.lib.notification.NotificationStatus
import io.hengam.lib.notification.NotificationStatusReporter
import io.hengam.lib.notification.messages.downstream.NotificationMessage
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
