package io.hengam.lib

import io.hengam.lib.LogTag.T_TOPIC
import io.hengam.lib.messages.downstream.UpdateTopicSubscriptionMessage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import javax.inject.Inject

class TopicController @Inject constructor(
    private val topicManager: TopicManager,
    private val appManifest: AppManifest
) {
    fun handleUpdateTopicMessage(message: UpdateTopicSubscriptionMessage) {
        Plog.debug(
            T_TOPIC, "Handling topic subscription message. Will subscribe to " +
                    "${message.subscribeTo.size} and unsubscribe from " +
                    "${message.unsubscribeFrom.size} topics"
        )

        Completable.merge(listOf(
            Completable.merge(message.subscribeTo
                .map { ApiPatch.removeTopicNamePrefix(it) }
                .map { removeAppIdSuffix(it) }
                .map { topicManager.subscribe(it) }
            ),
            Completable.merge(message.unsubscribeFrom
                .map { ApiPatch.removeTopicNamePrefix(it) }
                .map { removeAppIdSuffix(it) }
                .map { topicManager.unsubscribe(it) }
            )
        )).justDo(T_TOPIC)
    }

    private fun removeAppIdSuffix(topicFullName: String): String {
        return topicFullName.removeSuffix("_" + appManifest.appId)
    }
}