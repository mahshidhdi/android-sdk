package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.utils.NetworkType
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
