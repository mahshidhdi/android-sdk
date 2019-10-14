package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
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
