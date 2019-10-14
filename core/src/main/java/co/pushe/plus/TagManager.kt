package co.pushe.plus

import co.pushe.plus.Constants.BROADCAST_TOPIC
import co.pushe.plus.LogTag.T_TAG
import co.pushe.plus.LogTag.T_TOPIC
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.internal.ioThread
import co.pushe.plus.messages.upstream.TagSubscriptionMessage
import co.pushe.plus.messages.upstream.TopicStatusMessage
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.fcm.FcmTopicSubscriber
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.justDo
import io.reactivex.Completable
import javax.inject.Inject

/**
 * Contains methods for setting tags to user device.
 * Also, maintains a list of currently added tags.
 */
@CoreScope
class TagManager @Inject constructor(
        private val postOffice: PostOffice,
        pusheStorage: PusheStorage
) {
    private val tagStore = pusheStorage.createStoredSet("added_tags", String::class.java)

    val subscribedTags: Set<String> = tagStore

    /**
     * Add a tag
     *
     * @return A [Completable] which will complete when topic is subscribed
     */
    fun addTags(tags: List<String>): Completable {
        return Completable.fromCallable {
            postOffice.sendMessage(TagSubscriptionMessage(addedTags = tags))
        }.subscribeOn(cpuThread())
            .observeOn(cpuThread())
            .doOnSubscribe { Plog.debug(T_TAG, "Subscribing to tags $tags") }
            .doOnError {
                Plog.error(T_TAG, "Subscribing to tags failed", it,  "Tags" to tags)
            }
            .doOnComplete {
                Plog.info(T_TOPIC, "Successfully subscribed to tags $tags")
            }
            .doOnComplete { tagStore.addAll(tags) }
    }

    /**
     * Unsubscribe from a tag
     *
     * @return A [Completable] which will complete when topic is unsubscribed
     */
    fun removeTags(tags: List<String>): Completable {
        return Completable.fromCallable {
            postOffice.sendMessage(TagSubscriptionMessage(removedTags = tags))
        }.subscribeOn(cpuThread())
            .observeOn(cpuThread())
            .doOnSubscribe { Plog.debug(T_TAG, "UnSubscribing from tags $tags") }
            .doOnError {
                Plog.error(T_TAG, "UnSubscribing from tags failed", it,  "Tags" to tags)
            }
            .doOnComplete {
                Plog.info(T_TOPIC, "Successfully Unsubscribed from tags $tags")
            }
            .doOnComplete { tagStore.removeAll(tags) }
    }
}