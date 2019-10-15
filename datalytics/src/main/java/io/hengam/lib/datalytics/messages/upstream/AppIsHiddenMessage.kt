package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class AppIsHiddenMessage (
        @Json(name = "hidden_app") val appIsHidden: Boolean
): TypedUpstreamMessage<AppIsHiddenMessage> (
        MessageType.Datalytics.IS_APP_HIDDEN,
        { AppIsHiddenMessageJsonAdapter(it) }
)
