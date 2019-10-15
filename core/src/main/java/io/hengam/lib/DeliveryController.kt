package io.hengam.lib

import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.messages.upstream.DeliveryMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.RawDownstreamMessage
import io.hengam.lib.utils.log.Plog
import javax.inject.Inject

/**
 * In charge of sending delivery messages for downstream messages which have the
 * `requires_delivery=true` field.
 */
class DeliveryController @Inject constructor(
        private val postOffice: PostOffice
) {
    fun sendMessageDeliveryIfRequested(rawMessage: RawDownstreamMessage) {
        val message = rawMessage.rawData as Map<*, *>

        if (message[MessageFields.REQUEST_DELIVERY] == true) {
            if (MessageFields.MESSAGE_ID !in message) {
                Plog.error(T_MESSAGE, "Cannot send delivery message because original message " +
                        "is missing ${MessageFields.MESSAGE_ID}", "Message Data" to message)
                return
            }

            val originalMessageId = message[MessageFields.MESSAGE_ID] as String
            Plog.debug(T_MESSAGE, "Sending delivery message","Original Message Id" to originalMessageId)
            postOffice.sendMessage(DeliveryMessage(originalMessageId, "delivered"))
        }
    }
}