package co.pushe.plus.messages.upstream

import co.pushe.plus.messages.MessageType.Upstream.CUSTOM_ID_UPDATE
import co.pushe.plus.messaging.TypedUpstreamMessage
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