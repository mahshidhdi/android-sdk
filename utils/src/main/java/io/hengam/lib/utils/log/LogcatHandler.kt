package io.hengam.lib.utils.log

class LogcatLogHandler(
        private val logTag: String,
        private val level: LogLevel?,
        private val includeLogData: Boolean,
        private val useFullTags: Boolean
) : LogHandler {
    override fun onLog(logItem: Plogger.LogItem) {
        if ((level == null || level > (logItem.logCatLevel ?: logItem.level))) {
            return
        }

        var nonNullTag: String = if (!useFullTags) logTag else (logTag + " " + logItem.tags.joinToString(" , "))
        if (nonNullTag.length > 23) {
            nonNullTag = nonNullTag.substring(0, 23)
        }

        var message = logItem.message
        val t = logItem.throwable
        if (includeLogData) {
            message += "  ${logItem.logData}"
        }
        if (t == null) {
            when (logItem.logCatLevel ?: logItem.level) {
                LogLevel.TRACE -> android.util.Log.v(nonNullTag, message)
                LogLevel.DEBUG -> android.util.Log.d(nonNullTag, message)
                LogLevel.INFO -> android.util.Log.i(nonNullTag, message)
                LogLevel.WARN -> android.util.Log.w(nonNullTag, message)
                LogLevel.ERROR -> android.util.Log.e(nonNullTag, message)
                LogLevel.WTF -> android.util.Log.wtf(nonNullTag, message)
            }
        } else {
            when (logItem.logCatLevel ?: logItem.level) {
                LogLevel.TRACE -> android.util.Log.v(nonNullTag, message, t)
                LogLevel.DEBUG -> android.util.Log.d(nonNullTag, message, t)
                LogLevel.INFO -> android.util.Log.i(nonNullTag, message, t)
                LogLevel.WARN -> android.util.Log.w(nonNullTag, message, t)
                LogLevel.ERROR -> android.util.Log.e(nonNullTag, message, t)
                LogLevel.WTF -> if (message == null) {
                    android.util.Log.wtf(nonNullTag, t)
                } else {
                    android.util.Log.wtf(nonNullTag, message, t)
                }
            }
        }
    }
}
