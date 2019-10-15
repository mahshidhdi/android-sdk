package io.hengam.lib

import io.hengam.lib.LogTag.T_TOPIC
import io.hengam.lib.messages.downstream.UpdateTopicSubscriptionMessage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import javax.inject.Inject

class TopicController @Inject constructor(
    private val topicManager: TopicManager
) {
    fun handleUpdateTopicMessage(message: UpdateTopicSubscriptionMessage) {
        Plog.debug(
            T_TOPIC, "Handling topic subscription message. Will subscribe to " +
                    "${message.subscribeTo.size} and unsubscribe from " +
                    "${message.unsubscribeFrom.size} topics"
        )

        Completable.merge(listOf(
            Completable.merge(message.subscribeTo
                .map { topicManager.subscribe(it, addSuffix = false) }
            ),
            Completable.merge(message.unsubscribeFrom
                .map { topicManager.unsubscribe(it, addSuffix = false) }
            )
        )).justDo(T_TOPIC)
    }
}