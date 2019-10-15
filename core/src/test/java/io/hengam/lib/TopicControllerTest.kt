package io.hengam.lib

import io.hengam.lib.messages.downstream.UpdateTopicSubscriptionMessage
import io.mockk.*
import org.junit.Test

class TopicControllerTest {
    private val topicManager: TopicManager = mockk(relaxed = true)
    private val topicController = TopicController(topicManager, mockk(relaxed = true))

    @Test
    fun handleUpdateTopicMessage_CallsSubscribeAndUnsubscribeForTopicsInMessage() {
        val message = UpdateTopicSubscriptionMessage(
                listOf("subscribe1", "subscribe2", "subscribe3"),
                listOf("unsubscribe1", "unsubscribe2")
        )

        topicController.handleUpdateTopicMessage(message)

        verifyAll {
            topicManager.subscribe("subscribe1")
            topicManager.subscribe("subscribe2")
            topicManager.subscribe("subscribe3")
            topicManager.unsubscribe("unsubscribe1")
            topicManager.unsubscribe("unsubscribe2")
        }
    }

}