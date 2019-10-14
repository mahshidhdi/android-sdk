package co.pushe.plus.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CheckHiddenAppUpstreamMessage(
        @Json(name="hidden_app") val isHidden: Boolean
) : TypedUpstreamMessage<CheckHiddenAppUpstreamMessage> (
        MessageType.Datalytics.IS_APP_HIDDEN,
        { CheckHiddenAppUpstreamMessageJsonAdapter(it) }
)