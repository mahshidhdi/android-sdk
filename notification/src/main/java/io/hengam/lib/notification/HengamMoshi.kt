package io.hengam.lib.notification

import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.notification.actions.ActionFactory
import io.hengam.lib.notification.messages.downstream.NotificationButton
import io.hengam.lib.notification.messages.downstream.NotificationButtonJsonAdapter
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.messages.downstream.NotificationMessageJsonAdapter

fun extendMoshi(moshi: HengamMoshi) {
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
