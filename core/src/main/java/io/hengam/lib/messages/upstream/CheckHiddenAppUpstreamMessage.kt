package io.hengam.lib.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CheckHiddenAppUpstreamMessage(
        @Json(name="hidden_app") val isHidden: Boolean
) : TypedUpstreamMessage<CheckHiddenAppUpstreamMessage> (
        MessageType.Datalytics.IS_APP_HIDDEN,
        { CheckHiddenAppUpstreamMessageJsonAdapter(it) }
)