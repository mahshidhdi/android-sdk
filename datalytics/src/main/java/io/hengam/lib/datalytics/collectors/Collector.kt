package io.hengam.lib.datalytics.collectors

import io.hengam.lib.messaging.SendableUpstreamMessage
import io.reactivex.Observable

abstract class Collector {
    var isFinalAttempt: Boolean = false
    abstract fun collect(): Observable<out SendableUpstreamMessage>
}

class CollectionRetryRequiredError(message: String, cause: Throwable? = null) : Exception(message, cause)