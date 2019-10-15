package io.hengam.lib.utils

import java.util.concurrent.TimeUnit

object TimeUtils {

    /**
     * @return the current time in millis format as a long value
     */
    fun nowMillis(): Long = System.currentTimeMillis()
    fun now(): Time = Time(nowMillis(), TimeUnit.MILLISECONDS)
}

fun Long.millisToDays(): Int = (this / (1000 * 60 * 60 * 24)).toInt()
fun Long.millisToHours(): Int = (this / (1000 * 60 * 60)).toInt()

