package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class BootCompletedMessage: TypedUpstreamMessage<BootCompletedMessage> (
        MessageType.Datalytics.Upstream.BOOT_COMPLETE,
        { BootCompletedMessageJsonAdapter(it) }
)