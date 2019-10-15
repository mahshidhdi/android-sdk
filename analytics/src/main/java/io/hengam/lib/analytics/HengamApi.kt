package io.hengam.lib.analytics

import io.hengam.lib.internal.HengamServiceApi
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.analytics.event.Ecommerce
import io.hengam.lib.analytics.event.Event
import io.hengam.lib.analytics.event.EventAction
import io.hengam.lib.analytics.messages.upstream.EcommerceMessage
import io.hengam.lib.analytics.messages.upstream.EventMessage
import io.hengam.lib.internal.HengamMoshi
import javax.inject.Inject

class HengamAnalytics @Inject constructor(
        private val postOffice: PostOffice,
        private val moshi: HengamMoshi
) : HengamServiceApi {
    fun sendEvent(event: Event) {
        postOffice.sendMessage(
            message = EventMessage(
                event.name,
                event.action,
                moshi.adapter(Any::class.java).toJson(event.data)
            ),
            sendPriority = SendPriority.SOON
        )
    }

    fun sendEvent(name: String) {
        postOffice.sendMessage(
            message = EventMessage(
                name,
                EventAction.CUSTOM
            ),
            sendPriority = SendPriority.SOON
        )
    }

    fun sendEcommerceData(ecommerce: Ecommerce) {
        postOffice.sendMessage(
            message = EcommerceMessage(
                ecommerce.name,
                ecommerce.price,
                ecommerce.category,
                ecommerce.quantity
            ),
            sendPriority = SendPriority.SOON
        )
    }

    fun sendEcommerceData(name: String, price: Double) {
        postOffice.sendMessage(
            message = EcommerceMessage(
                name,
                price,
                null,
                null
            ),
            sendPriority = SendPriority.SOON
        )
    }
}