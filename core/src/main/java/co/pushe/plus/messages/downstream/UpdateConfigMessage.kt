package co.pushe.plus.messages.downstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.DownstreamMessageParser
import com.squareup.moshi.*

@JsonClass(generateAdapter = true)
class UpdateConfigMessage(
        @Json(name = "update") val updateValues: Map<String, String> = emptyMap(),
        @Json(name = "remove") val removeValues: List<String> = emptyList()
) {
    class Parser : DownstreamMessageParser<UpdateConfigMessage>(
            MessageType.Downstream.UPDATE_CONFIG,
            { UpdateConfigMessageJsonAdapter(it) }
    )
}

