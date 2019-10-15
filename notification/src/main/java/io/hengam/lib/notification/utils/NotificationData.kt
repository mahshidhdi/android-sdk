package io.hengam.lib.notification.utils

import io.hengam.lib.notification.messages.downstream.NotificationButton

fun getNotificationButtonIds(buttons: List<NotificationButton>): List<String> {
    return buttons.mapIndexed { index, it -> it.id ?: "Button#$index" }
}