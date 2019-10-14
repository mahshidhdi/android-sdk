package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ConstantDataMessage(
        @Json(name = "brand") val brand: String,
        @Json(name = "model") val model: String,
        @Json(name = "screen") val screen: String
) : TypedUpstreamMessage<ConstantDataMessage>(
        MessageType.Datalytics.CONSTANT_DATA,
        { ConstantDataMessageJsonAdapter(it) }
)
