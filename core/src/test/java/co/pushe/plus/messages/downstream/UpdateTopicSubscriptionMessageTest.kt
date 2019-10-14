package co.pushe.plus.messages.downstream

import co.pushe.plus.internal.PusheMoshi
import org.junit.Assert.*
import org.junit.Test

class UpdateTopicSubscriptionMessageTest {
    private val moshi = PusheMoshi()

    @Test
    fun jsonParsing_CorrectlyParsesMessage() {
        val json = """
            {
                "subscribe_to": ["t1", "t2"],
                "unsubscribe_from": ["t3"]
            }
        """.trimIndent()

        val message = UpdateTopicSubscriptionMessage.Parser().parseMessage(moshi, json)
        assertEquals(listOf("t1", "t2"), message?.subscribeTo)
        assertEquals(listOf("t3"), message?.unsubscribeFrom)
    }

    @Test
    fun jsonParsing_AllowsEmptyValuesForSubscribeAndUnsubscribe() {
        val json = """
            {
                "subscribe_to": ["t1", "t2"]
            }
        """.trimIndent()

        val message = UpdateTopicSubscriptionMessageJsonAdapter(moshi.moshi).fromJson(json)
        assertEquals(2, message?.subscribeTo?.size)
        assertEquals(0, message?.unsubscribeFrom?.size)

        val json2 = """
            {
                "unsubscribe_from": ["t1", "t2"]
            }
        """.trimIndent()

        val message2 = UpdateTopicSubscriptionMessageJsonAdapter(moshi.moshi).fromJson(json2)
        assertEquals(0, message2?.subscribeTo?.size)
        assertEquals(2, message2?.unsubscribeFrom?.size)
    }
}