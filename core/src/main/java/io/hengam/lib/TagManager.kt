package io.hengam.lib

import io.hengam.lib.Constants.BROADCAST_TOPIC
import io.hengam.lib.LogTag.T_TAG
import io.hengam.lib.LogTag.T_TOPIC
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.messages.upstream.TagSubscriptionMessage
import io.hengam.lib.messages.upstream.TopicStatusMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.fcm.FcmTopicSubscriber
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import javax.inject.Inject

/**
 * Contains methods for setting tags to user device.
 * Also, maintains a list of currently added tags.
 */
@CoreScope
class TagManager @Inject constructor(
        private val postOffice: PostOffice,
        hengamStorage: HengamStorage
) {
    private val tagStore = hengamStorage.createStoredSet("added_tags", String::class.java)

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