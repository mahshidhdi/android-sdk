package co.pushe.plus

import co.pushe.plus.messages.upstream.DeliveryMessage
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.RawDownstreamMessage
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DeliveryControllerTest {

    private val postOffice: PostOffice = mockk(relaxed = true)
    private val deliveryController = DeliveryController(postOffice)

    @Test
    fun sendMessageDeliveryIfRequested_SendDeliveryIfRequested() {
        val messageId = "message_id"
        val message = RawDownstreamMessage(
                messageId,
                123,
                mapOf("message_id" to messageId, "request_delivery" to true)
        )
        deliveryController.sendMessageDeliveryIfRequested(message)
        verify { postOffice.sendMessage(DeliveryMessage(messageId, "delivered")) }
    }

    @Test
    fun sendMessageDeliveryIfRequested_DoNotSendDeliveryIfNotRequestedOrNoMessageId() {
        val messageId = "message_id"
        deliveryController.sendMessageDeliveryIfRequested(RawDownstreamMessage(
                messageId, 123,
                mapOf("message_id" to messageId, "request_delivery" to false)
        ))

        deliveryController.sendMessageDeliveryIfRequested(RawDownstreamMessage(
                messageId, 123,
                mapOf("message_id" to messageId)
        ))

        deliveryController.sendMessageDeliveryIfRequested(RawDownstreamMessage(
                messageId, 123,
                mapOf("request_delivery" to false)
        ))

        deliveryController.sendMessageDeliveryIfRequested(RawDownstreamMessage(
                messageId, 123,
                emptyMap<String, Any>()
        ))

        verify(exactly = 0) { postOffice.sendMessage(any()) }
    }
}