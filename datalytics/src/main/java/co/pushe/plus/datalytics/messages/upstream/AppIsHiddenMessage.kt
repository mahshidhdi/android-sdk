package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class AppIsHiddenMessage (
        @Json(name = "hidden_app") val appIsHidden: Boolean
): TypedUpstreamMessage<AppIsHiddenMessage> (
        MessageType.Datalytics.IS_APP_HIDDEN,
        { AppIsHiddenMessageJsonAdapter(it) }
)
