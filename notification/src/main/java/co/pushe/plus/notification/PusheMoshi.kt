package co.pushe.plus.notification

import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.notification.actions.ActionFactory
import co.pushe.plus.notification.messages.downstream.NotificationButton
import co.pushe.plus.notification.messages.downstream.NotificationButtonJsonAdapter
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.messages.downstream.NotificationMessageJsonAdapter

fun extendMoshi(moshi: PusheMoshi) {
    moshi.enhance {
        it.add(ActionFactory.build())
        it.add { type, _, moshi ->
            when (type) {
                NotificationButton::class.java -> NotificationButtonJsonAdapter(moshi)
                NotificationMessage::class.java -> NotificationMessageJsonAdapter(moshi) // Needed by NotificationStorage
                else -> null
            }
        }
    }
}
