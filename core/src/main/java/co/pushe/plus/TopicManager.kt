package co.pushe.plus

import co.pushe.plus.Constants.BROADCAST_TOPIC
import co.pushe.plus.LogTag.T_TOPIC
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.internal.ioThread
import co.pushe.plus.messages.upstream.TopicStatusMessage
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.fcm.FcmTopicSubscriber
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.justDo
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
        pusheStorage: PusheStorage
) {
    private val topicStore = pusheStorage.createStoredSet("subscribed_topics", String::class.java)

    /**
     * Topics which the user has successfully been subscribed to
     */
    val subscribedTopics: Set<String> = topicStore

    /**
     * Subscribe to a topic
     *
     * Will subscribe to a topic on all courier services (currently only FCM).
     * The returned [Completable] will succeed when subscribing succeeds in all courier services.
     *
     * The subscribe operation is persisted and will be retried internally until successful.
     *
     * If the topic is successfully subscribed, the topic will be added to the topic list and
     * a [TopicStatusMessage] message will be sent.
     *
     * @return A [Completable] which will complete when topic is subscribed
     */
    fun subscribe(topic: String): Completable {
        /* Note: If you add further courier services here, keep in mind that the
           subscription should be persisted and retried internally if failed  */
        val topicFullName = getTopicFullName(topic)
        val fcm = fcmTopicSubscriber.subscribeToTopic(topicFullName)

        return Completable.merge(listOf(fcm))
                .subscribeOn(ioThread())
                .observeOn(cpuThread())
                .doOnSubscribe { Plog.debug(T_TOPIC, "Subscribing to topic $topicFullName") }
                .doOnError {
                    Plog.error(T_TOPIC, TopicSubscriptionException("Subscribing to topic failed in at least one of the couriers", it),  "Topic" to topicFullName)
                }
                .doOnComplete {
                    Plog.info(T_TOPIC, "Successfully subscribed to topic $topicFullName")
                }
                .doOnComplete { topicStore.add(topic) }
                .doOnComplete { sendTopicSubscribedMessage(topicFullName) }
    }

    /**
     * Unsubscribe from a topic
     *
     * Will unsubscribe from a topic on all courier services (currently only FCM).
     * The returned [Completable] will succeed when unsubscribing succeeds in all courier services.
     *
     * The unsubscribe operation is persisted and will be retried internally until successful.
     *
     * If the topic is successfully unsubscribed, the topic will be removed from the topic list and
     * a [TopicStatusMessage] message will be sent.
     *
     * @return A [Completable] which will complete when topic is unsubscribed
     */
    fun unsubscribe(topic: String): Completable {

        val topicFullName = getTopicFullName(topic)
        val fcm = fcmTopicSubscriber.unsubscribeFromTopic(topicFullName)

        return Completable.merge(listOf(fcm))
                .subscribeOn(ioThread())
                .observeOn(cpuThread())
                .doOnSubscribe { Plog.info(T_TOPIC, "Unsubscribing from topic", "Topic" to topicFullName) }
                .doOnError {
                    Plog.error(T_TOPIC,  TopicSubscriptionException("Unsubscribing from topic failed in at least one of the couriers", it), "Topic" to topicFullName)
                }
                .doOnComplete {
                    Plog.info(T_TOPIC, "Successfully unSubscribed from topic $topicFullName")
                }
                .doOnComplete { topicStore.remove(topic) }
                .doOnComplete { sendTopicUnSubscribedMessage(topicFullName) }
    }

    /**
     * Subscribe to the broadcast topic
     *
     * Should be called after every successful registration
     */
    fun subscribeToBroadcast(){
        subscribe(BROADCAST_TOPIC)
            .justDo()
    }

    private fun sendTopicSubscribedMessage(topicFullName: String) {
        postOffice.sendMessage(TopicStatusMessage(topicFullName, TopicStatusMessage.STATUS_SUBSCRIBED))
    }

    private fun sendTopicUnSubscribedMessage(topicFullName: String) {
        postOffice.sendMessage(TopicStatusMessage(topicFullName, TopicStatusMessage.STATUS_UNSUBSCRIBED))
    }

    private fun getTopicFullName(topic: String): String {
        return topic + "_" + appManifest.appId
    }
}

class TopicSubscriptionException(message: String, throwable: Throwable? = null) : Exception(message, throwable)