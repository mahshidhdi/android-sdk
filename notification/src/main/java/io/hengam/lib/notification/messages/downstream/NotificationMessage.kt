package io.hengam.lib.notification.messages.downstream

import io.hengam.lib.MessageFields.MESSAGE_ID
import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
import io.hengam.lib.notification.actions.Action
import io.hengam.lib.notification.actions.AppAction
import io.hengam.lib.utils.Millis
import io.hengam.lib.utils.Time
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*


@JsonClass(generateAdapter = true)
data class NotificationMessage(
        @Json(name=MESSAGE_ID) val messageId: String,
        @Json(name="title") val title: String? = null,
        @Json(name="content") val content: String? = null,
        @Json(name="big_title") val bigTitle: String? = null,
        @Json(name="big_content") val bigContent: String? = null,
        @Json(name="summary") val summary: String? = null,
        @Json(name="image") val imageUrl: String? = null,
        @Json(name="icon") val iconUrl: String? = null,
        @Json(name="notif_icon") val smallIcon: String? = null,
        @Json(name="notif_icon_url") val smallIconUrl: String? = null,
        @Json(name="big_icon") val bigIconUrl: String? = null,
        @Json(name="buttons") val buttons: List<NotificationButton> = emptyList(),

        @Json(name="action") val action: Action = DEFAULT_ACTION,

        // TODO: Should use Importance instead of priority, priority is deprecated
        @Json(name="priority") val priority: Int = DEFAULT_PRIORITY,
        @Json(name="use_hengam_mini_icon") val useHengamIcon: Boolean = false,

        @Json(name="led_color") val ledColor: String? = null,
        @Json(name="led_on") val ledOnTime: Int = DEFAULT_LED_ON_TIME,
        @Json(name="led_off") val ledOffTime: Int = DEFAULT_LED_OFF_TIME,
        @Json(name="wake_screen") val wakeScreen: Boolean = false,
        @Json(name="ticker") val ticker: String? = null,
        @Json(name="sound_url") val soundUrl: String? = null,
        @Json(name="show_app") val showNotification: Boolean = true,
        @Json(name="bg_url") val justImgUrl: String? = null, // img url for small notification that only shows an image background
        @Json(name="permanent") val permanentPush: Boolean = false,
        @Json(name="forcePublish") val forcePublish: Boolean = false,
        @Json(name="notif_channel_id") val notifChannelId: String? = null,
        @Json(name="cancel_update") val cancelUpdate: String? = null, // cancel update notifications saved
        @Json(name="delay_until") val delayUntil: String? = null, // only option is "open_app"
        @Json(name="delay") @Millis val delay: Time? = null,

        @Json(name="otk") val oneTimeKey: String? = null,
        @Json(name="tag") val tag: String? = null,
        @Json(name="scheduled_time") val scheduledTime: Date? = null,
        @Json(name="av_code") val updateToAppVersion: Long? = null,

        @Json(name="badge_count") val badgeState: Int? = null,
        @Json(name="custom_content") val customContent: Map<String, Any?>? = null,

        /**
         * If `true` allows the notification to be published even if it has a duplicate message id
         * Useful for notification messages contained within Geofence messages.
         * Is `false` by default
         */
        @Json(name="allow_multi_publish") val allowDuplicates: Boolean = false
) {
    val isUpdateNotification: Boolean get() = updateToAppVersion != null

    fun isNotificationPresentable() = !title.isNullOrBlank() || !content.isNullOrBlank()

    fun requiresNetwork(): Boolean {
        return !imageUrl.isNullOrBlank() ||
                !iconUrl.isNullOrBlank() ||
                !smallIconUrl.isNullOrBlank() ||
                !soundUrl.isNullOrBlank() ||
                !justImgUrl.isNullOrBlank()
    }

    class Parser(messageType: Int = MessageType.Notification.Downstream.NOTIFICATION) : DownstreamMessageParser<NotificationMessage>(
            messageType,
            { NotificationMessageJsonAdapter(it) }
    )

    fun getNotificationId(): Int {
        return if (tag == null || tag.isBlank()) messageId.hashCode()
        else tag.hashCode()
    }

    companion object {
        val DEFAULT_ACTION = AppAction()
        const val DEFAULT_PRIORITY = 2
        const val DEFAULT_LED_ON_TIME = 500
        const val DEFAULT_LED_OFF_TIME = 1000
    }
}

@JsonClass(generateAdapter = true)
class NotificationButton(
        @Json(name="btn_id") val id: String?,
        @Json(name="btn_action") val action: Action = NotificationMessage.DEFAULT_ACTION,
        @Json(name="btn_content") val text: String?,
        @Json(name="btn_icon") val icon: String?,
        @Json(name="btn_order") val order: Int = 0
)