package io.hengam.lib.messages.downstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
import com.squareup.moshi.*

@JsonClass(generateAdapter = true)
class RunDebugCommandMessage(
        @Json(name = "command") val command: String,
        @Json(name = "params") val params: List<String> = emptyList()
) {
    class Parser : DownstreamMessageParser<RunDebugCommandMessage>(
            MessageType.Downstream.RUN_DEBUG_COMMAND,
            { RunDebugCommandMessageJsonAdapter(it) }
    )
}

