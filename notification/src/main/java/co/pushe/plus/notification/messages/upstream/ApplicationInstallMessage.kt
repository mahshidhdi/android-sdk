package co.pushe.plus.notification.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.utils.Seconds
import co.pushe.plus.utils.Time
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ApplicationInstallMessage(
        @Json(name = "orig_msg_id") val originalMessageId: String,
        @Json(name = "status") val status: InstallStatus,
        @Json(name = "prev_version") val previousVersion: String? = null,
        @Json(name = "app_info") val appInfo: ApplicationDetail? = null,
        @Json(name = "pub_time") @Seconds val publishedAt: Time? = null,
        @Json(name = "click_time") @Seconds val clickedAt: Time? = null,
        @Json(name = "dl_time") @Seconds val downloadedAt: Time? = null,
        @Json(name = "install_check_time") @Seconds val installCheckedAt: Time? = null
) : TypedUpstreamMessage<ApplicationInstallMessage>(
        MessageType.Notification.Upstream.APPLICATION_INSTALL,
        { ApplicationInstallMessageJsonAdapter(it) }
) {
    enum class InstallStatus {
        @Json(name = "installed") INSTALLED,
        @Json(name = "not_installed") NOT_INSTALLED
    }
}
