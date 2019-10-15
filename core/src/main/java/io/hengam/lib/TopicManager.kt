package io.hengam.lib

import io.hengam.lib.Constants.BROADCAST_TOPIC
import io.hengam.lib.LogTag.T_TOPIC
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.messages.upstream.TopicStatusMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.fcm.FcmTopicSubscriber
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import javax.inject.Inject

/**
 * Contains methods for subscribing and unsubscribing from topics.
 * Also, maintains a list of currently subscribed topics.
 */
@CoreScope
class TopicManager @Inject constructor(
        private val fcmTopicSubscriber: FcmTopicSubscriber,
        private val postOffice: PostOffice,
        private val appManifest: AppManifest,
        hengamStorage: HengamStorage
) {
    private val topicStore = hengamStorage.createStoredSet("subscribed_topics", String::class.java)

    /**
     * Topics which the user has successfully been subscribed to
     */
    val subscribedTopics: Set<String> = topicStore

    /**
     * Same as [subscribe], but it does not use [getTopicFullName] to make it internally for this appId.
     * This function will exactly subscribe to the [topic]
     */
    fun subscribe(topic: String, addSuffix: Boolean = true): Completable {
        val topicActualName = if (addSuffix) getTopicFullName(topic) else topic
        val fcm = fcmTopicSubscriber.subscribeToTopic(topicActualName)
        return Completable.merge(listOf(fcm))
                .subscribeOn(ioThread())
                .observeOn(cpuThread())
                .doOnSubscribe { Plog.debug(T_TOPIC, "Subscribing to topic $topicActualName") }
                .doOnError {
                    Plog.error(T_TOPIC, TopicSubscriptionException("Subscribing to topic failed in at least one of the couriers", it), "Topic" to topicActualName)
                }
                .doOnComplete {
                    Plog.info(T_TOPIC, "Successfully subscribed to topic $topicActualName")
                }
                .doOnComplete { topicStore.add(topicActualName) }
                .doOnComplete { sendTopicSubscribedMessage(topicActualName) }
    }

    /**
     * Same as [unsubscribe], but it does not use [getTopicFullName] to make it internally for this appId.
     * This function will exactly unSubscribe from the [topic]
     */
    fun unsubscribe(topic: String, addSuffix: Boolean = true): Completable {
        val topicActualName = if (addSuffix) getTopicFullName(topic) else topic
        val fcm = fcmTopicSubscriber.unsubscribeFromTopic(topicActualName)

        return Completable.merge(listOf(fcm))
                .subscribeOn(ioThread())
                .observeOn(cpuThread())
                .doOnSubscribe { Plog.info(T_TOPIC, "UnSubscribing from topic", "Topic" to topicActualName) }
                .doOnError {
                    Plog.error(T_TOPIC, TopicSubscriptionException("UnSubscribing from topic failed in at least one of the couriers", it), "Topic" to topicActualName)
                }
                .doOnComplete {
                    Plog.info(T_TOPIC, "Successfully unSubscribed from topic $topicActualName")
                }
                .doOnComplete { topicStore.remove(topicActualName) }
                .doOnComplete { sendTopicUnSubscribedMessage(topicActualName) }
    }

    private fun sendTopicSubscribedMessage(topicFullName: String) {
        postOffice.sendMessage(TopicStatusMessage(topicFullName, TopicStatusMessage.STATUS_SUBSCRIBED))
    }

    private fun sendTopicUnSubscribedMessage(topicFullName: String) {
        postOffice.sendMessage(TopicStatusMessage(topicFullName, TopicStatusMessage.STATUS_UNSUBSCRIBED))
    }

    private fun getTopicFullName(topic: String) = "${topic}_${appManifest.appId}"
}

class TopicSubscriptionException(message: String, throwable: Throwable? = null) : Exception(message, throwable)