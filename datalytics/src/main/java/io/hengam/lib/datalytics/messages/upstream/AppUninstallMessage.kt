package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUninstallMessage(
        @Json(name="package_name") val packageName: String
) : TypedUpstreamMessage<AppUninstallMessage> (
        MessageType.Datalytics.Upstream.APP_UNINSTALL,
        { AppUninstallMessageJsonAdapter(it) }
)