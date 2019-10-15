package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
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
