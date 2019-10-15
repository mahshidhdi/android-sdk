package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class BootCompletedMessage: TypedUpstreamMessage<BootCompletedMessage> (
        MessageType.Datalytics.Upstream.BOOT_COMPLETE,
        { BootCompletedMessageJsonAdapter(it) }
)