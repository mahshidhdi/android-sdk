package io.hengam.lib.analytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
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