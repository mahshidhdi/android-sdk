package co.pushe.plus.utils

import co.pushe.plus.Constants
import co.pushe.plus.Pushe
import co.pushe.plus.utils.log.Plog

object ExceptionCatcher {
    fun registerUnhandledPusheExceptionCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (isItCausedByUs(e)) {
                Plog.wtf("Unhandled exception occurred in Pushe SDK", PusheUnhandledException(e),"Thread" to t.name)
            } else {
                defaultHandler.uncaughtException(t, e)
            }
        }
    }

    inline fun catchAllUnhandledErrors(threadName: String? = "", f: () -> Unit) {
        try {
            f()
        } catch (ex: Throwable) {
            Plog.wtf("Unhandled error occurred in Pushe $threadName", PusheUnhandledException(ex))
        }
    }

    private fun isItCausedByUs(e: Throwable): Boolean {
        if (e.stackTrace.find { Constants.PusheInfo.PACKAGE_NAME in it.className} != null) {
            return true
        }

        // In case the package name changes because of proguard check perform the check again this
        // time with the package name derived from the [Pushe] class name. This shouldn't really
        // happen though since the modules won't be initialized properly if the `co.pushe.plus`
        // package name isn't kept.
        val derivedPackageName = Pushe::class.java.canonicalName?.substringBeforeLast('.')
        if (derivedPackageName != null && e.stackTrace.find { derivedPackageName in it.className} != null) {
            return true
        }

        if (e.cause != null) {
            return isItCausedByUs(e.cause ?: Throwable())
        }

        return false
    }
}

class PusheUnhandledException(cause: Throwable) : Exception(cause.message, cause)