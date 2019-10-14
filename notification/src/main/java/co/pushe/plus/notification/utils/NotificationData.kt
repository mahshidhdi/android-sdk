package co.pushe.plus.notification.utils

import co.pushe.plus.notification.messages.downstream.NotificationButton

fun getNotificationButtonIds(buttons: List<NotificationButton>): List<String> {
    return buttons.mapIndexed { index, it -> it.id ?: "Button#$index" }
}