package co.pushe.plus.analytics.utils

import co.pushe.plus.analytics.dagger.AnalyticsScope
import javax.inject.Inject

/**
 * A utility class to generate the current time
 *
 * Caches the generated time.
 * If the current time is less than one second different than the cached one, returns the cached time.
 *
 */
@AnalyticsScope
class CurrentTimeGenerator @Inject constructor(){

    private var lastGeneratedTime: Long = 0

    fun getCurrentTime(): Long{
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGeneratedTime > 1000) lastGeneratedTime = currentTime
            return lastGeneratedTime
    }
}