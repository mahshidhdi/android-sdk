package io.hengam.lib.messaging.fcm

import io.hengam.lib.LogTag.T_FCM
import io.hengam.lib.LogTag.T_TOPIC
import io.hengam.lib.internal.ioThread
import io.hengam.lib.utils.log.Plog
import io.reactivex.Completable
import javax.inject.Inject

class FcmTopicSubscriber @Inject constructor(
        private val fcmServiceManager: FcmServiceManager
) {
    /**
     * Subscribe Fcm to a topic
     *
     * @param topicCode A topic code to subscribe to in the `<topicName>_<packageName>' format
     * @return A completable which will succeed if the Fcm topic subscription task was successful
     */
    fun subscribeToTopic(topicCode: String): Completable {
        if (!fcmServiceManager.isFirebaseAvailable) {
            return Completable.error(FcmSubscriptionException("Cannot subscribe FCM to topic, Firebase has" +
                    " not been initialized"))
        }

        val completable = Completable.create { completable ->
            fcmServiceManager.firebaseMessaging
                    .subscribeToTopic(topicCode)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                           completable.onComplete()
                        } else {
                            completable.onError(FcmSubscriptionException("Subscribing FCM to topic failed", task.exception))
                        }
                    }
        }

        return completable
            .subscribeOn(ioThread())
                .doOnSubscribe {
                    Plog.trace(T_TOPIC, T_FCM, "Subscribing to topic $topicCode on Fcm courier")
                }

    }

    /**
     * Unsubscribe Fcm from a topic
     *
     * @param topicCode A topic code to unsubscribe from in the `<topicName>_<packageName>' format
     * @return A completable which will succeed if the Fcm topic unsubscription task was successful
     */
    fun unsubscribeFromTopic(topicCode: String): Completable {
        if (!fcmServiceManager.isFirebaseAvailable) {
            return Completable.error(FcmSubscriptionException("Cannot unsubscribe FCM from topic, Firebase has" +
                    " not been initialized"))
        }

        val completable = Completable.create { completable ->
            fcmServiceManager.firebaseMessaging
                    .unsubscribeFromTopic(topicCode)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            completable.onComplete()
                        } else {
                            completable.onError(FcmSubscriptionException("Unsubscribing Fcm from topic failed", task.exception))
                        }
                    }
        }

        return completable
                .subscribeOn(ioThread())
                .doOnSubscribe {
                    Plog.trace(T_TOPIC, T_FCM, "Unsubscribing from topic $topicCode on Fcm courier")
                }
    }

    class FcmSubscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
}