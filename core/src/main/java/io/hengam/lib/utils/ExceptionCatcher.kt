package io.hengam.lib.utils

import io.hengam.lib.Constants
import io.hengam.lib.Hengam
import io.hengam.lib.utils.log.Plog

object ExceptionCatcher {
    fun registerUnhandledHengamExceptionCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (isItCausedByUs(e)) {
                Plog.wtf("Unhandled exception occurred in Hengam SDK", HengamUnhandledException(e),"Thread" to t.name)
            } else {
                defaultHandler.uncaughtException(t, e)
            }
        }
    }

    inline fun catchAllUnhandledErrors(threadName: String? = "", f: () -> Unit) {
        try {
            f()
        } catch (ex: Throwable) {
            Plog.wtf("Unhandled error occurred in Hengam $threadName", HengamUnhandledException(ex))
        }
    }

    private fun isItCausedByUs(e: Throwable): Boolean {
        if (e.stackTrace.find { Constants.HengamInfo.PACKAGE_NAME in it.className} != null) {
            return true
        }

        // In case the package name changes because of proguard check perform the check again this
        // time with the package name derived from the [Hengam] class name. This shouldn't really
        // happen though since the modules won't be initialized properly if the `io.hengam.lib`
        // package name isn't kept.
        val derivedPackageName = Hengam::class.java.canonicalName?.substringBeforeLast('.')
        if (derivedPackageName != null && e.stackTrace.find { derivedPackageName in it.className} != null) {
            return true
        }

        if (e.cause != null) {
            return isItCausedByUs(e.cause ?: Throwable())
        }

        return false
    }
}

class HengamUnhandledException(cause: Throwable) : Exception(cause.message, cause)