package co.pushe.plus.datalytics.collectors

import co.pushe.plus.messaging.SendableUpstreamMessage
import io.reactivex.Observable

abstract class Collector {
    var isFinalAttempt: Boolean = false
    abstract fun collect(): Observable<out SendableUpstreamMessage>
}

class CollectionRetryRequiredError(message: String, cause: Throwable? = null) : Exception(message, cause)