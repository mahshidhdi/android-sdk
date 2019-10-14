package co.pushe.plus.notification


class NotificationData(
    val messageId: String,
    val title: String?,
    val content: String?,
    val bigTitle: String?,
    val bigContent: String?,
    val summary: String?,
    val imageUrl: String?,
    val iconUrl: String?,
    val bigIconUrl: String?,
    val customContent: Map<String, Any?>?,
    val buttons: List<NotificationButtonData>
)

class NotificationButtonData(
    val id: String,
    val text: String?,
    val icon: String?
)