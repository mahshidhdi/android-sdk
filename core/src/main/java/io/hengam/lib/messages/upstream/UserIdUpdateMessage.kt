package io.hengam.lib.messages.upstream

import io.hengam.lib.messages.MessageType.Upstream.CUSTOM_ID_UPDATE
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class UserIdUpdateMessage(
        @Json(name="cid") val customId: String?,
        @Json(name="email") val email: String?,
        @Json(name="pn") val phoneNumber: String?
): TypedUpstreamMessage<UserIdUpdateMessage>(
        CUSTOM_ID_UPDATE,
        { UserIdUpdateMessage.jsonAdapter(it) }
) {
    companion object
}