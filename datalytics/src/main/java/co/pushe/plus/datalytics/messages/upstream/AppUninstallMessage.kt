package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUninstallMessage(
        @Json(name="package_name") val packageName: String
) : TypedUpstreamMessage<AppUninstallMessage> (
        MessageType.Datalytics.Upstream.APP_UNINSTALL,
        { AppUninstallMessageJsonAdapter(it) }
)