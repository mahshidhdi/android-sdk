//package co.pushe.plus.messaging.lash
//
//
//import co.pushe.plus.LogTag.T_LASH
//import co.pushe.plus.LogTag.T_TOPIC
//import co.pushe.plus.internal.ioThread
//import co.pushe.plus.utils.log.Plog
//import io.reactivex.Completable
//import javax.inject.Inject
//
//class LashTopicSubscriber @Inject constructor() {
//    fun subscribeToTopic(topic: String): Completable {
//        if (!Lash.isInitialized) {
//            Plog[T_LASH, T_TOPIC].warn(LashSubscriptionException("Cannot subscribe Lash to topic, Lash has" +
//                    " not been initialized"))
//            return Completable.complete()
//        }
//
//        return Lash.subscribe(topic)
//                .subscribeOn(ioThread())
//                .doOnSubscribe {
//                    Plog[T_TOPIC, T_LASH].trace("Subscribing to topic $topic on Lash courier")
//                }
//    }
//
//    fun unsubscribeFromTopic(topic: String): Completable {
//        if (!Lash.isInitialized) {
//            Plog[T_LASH, T_TOPIC].warn(LashSubscriptionException("Cannot unsubscribe Lash from topic, Lash has" +
//                    " not been initialized"))
//            return Completable.complete()
//        }
//
//        return Lash.unsubscribe(topic)
//                .subscribeOn(ioThread())
//                .doOnSubscribe {
//                    Plog[T_TOPIC, T_LASH].trace("Unsubscribing from topic $topic on Lash courier")
//                }
//    }
//}
//
//class LashSubscriptionException(message: String) : Exception(message)