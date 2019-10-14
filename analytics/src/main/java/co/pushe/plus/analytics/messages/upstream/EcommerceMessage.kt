package co.pushe.plus.analytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class EcommerceMessage(
        @Json(name = "name") val name: String,
        @Json(name = "price") val price: Double,
        @Json(name = "category") val category: String?,
        @Json(name = "quantity") val quantity: Long?
) : TypedUpstreamMessage<EcommerceMessage>(
    MessageType.Analytics.Upstream.ECOMMERCE_EVENT,
    { EcommerceMessageJsonAdapter(it) })