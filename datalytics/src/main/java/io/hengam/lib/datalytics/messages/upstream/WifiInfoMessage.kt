package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class WifiInfoMessage(
        @Json(name = "ssid") val wifiSSID: String,
        @Json(name = "mac") val wifiMac: String,
        @Json(name = "sig_level") val wifiSignal: Int,
        @Json(name = "lat") val wifiLat: String?,
        @Json(name = "long") val wifiLng: String?
) : TypedUpstreamMessage<WifiInfoMessage>(
        MessageType.Datalytics.WIFI_LIST,
        { WifiInfoMessageJsonAdapter(it) }
)
