package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import io.hengam.lib.utils.NetworkType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class FloatingDataMessage(
        @Json(name = "lat") var lat: String?,
        @Json(name = "long") var long: String?,
        @Json(name = "ip") var ip: String?,
        @Json(name = "type") val networkType: NetworkType?,
        @Json(name = "ssid") val wifiNetworkSSID: String? = null,
        @Json(name = "sig_level") val wifiNetworkSignal: Int? = null,
        @Json(name = "mac") val wifiMac: String? = null,
        @Json(name = "network") val mobileNetworkName: String? = null
) : TypedUpstreamMessage<FloatingDataMessage>(
        MessageType.Datalytics.FLOATING_DATA,
        { FloatingDataMessageJsonAdapter(it) }
)
