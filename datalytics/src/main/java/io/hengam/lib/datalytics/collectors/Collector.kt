package io.hengam.lib.datalytics.collectors

import io.hengam.lib.messaging.SendableUpstreamMessage
import io.reactivex.Observable

abstract class Collector {
    abstract fun collect(): Observable<out SendableUpstreamMessage>
}