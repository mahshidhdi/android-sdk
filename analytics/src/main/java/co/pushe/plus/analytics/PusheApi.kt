package co.pushe.plus.analytics

import co.pushe.plus.internal.PusheServiceApi
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.analytics.event.Ecommerce
import co.pushe.plus.analytics.event.Event
import co.pushe.plus.analytics.event.EventAction
import co.pushe.plus.analytics.messages.upstream.EcommerceMessage
import co.pushe.plus.analytics.messages.upstream.EventMessage
import co.pushe.plus.internal.PusheMoshi
import javax.inject.Inject

class PusheAnalytics @Inject constructor(
        private val postOffice: PostOffice,
        private val moshi: PusheMoshi
) : PusheServiceApi {
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