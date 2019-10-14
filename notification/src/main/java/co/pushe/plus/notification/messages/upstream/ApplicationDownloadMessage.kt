package co.pushe.plus.notification.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.utils.Seconds
import co.pushe.plus.utils.Time
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ApplicationDownloadMessage(
        @Json(name = "orig_msg_id") val originalMessageId: String,
        @Json(name = "package_name") val packageName: String? = null,
        @Json(name = "pub_time") @Seconds val publishedAt: Time? = null,
        @Json(name = "click_time") @Seconds val clickedAt: Time? = null,
        @Json(name = "dl_time") @Seconds val downloadedAt: Time? = null
) : TypedUpstreamMessage<ApplicationDownloadMessage>(
        MessageType.Notification.Upstream.APPLICATION_DOWNLOAD,
        { ApplicationDownloadMessageJsonAdapter(it) }
)