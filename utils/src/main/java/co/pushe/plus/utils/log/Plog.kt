package co.pushe.plus.utils.log

import co.pushe.plus.utils.Time
import co.pushe.plus.utils.rx.justDo
import com.squareup.moshi.Json
import io.reactivex.Scheduler
import io.reactivex.annotations.CheckReturnValue
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


open class Plogger(
        private val parent: Plogger? = null,
        var levelFilter: LogLevel = LogLevel.INFO
) {
    val aggregationDebouncers: MutableMap<String, PublishSubject<Boolean>> = mutableMapOf()
    val aggregationLogs: MutableMap<String, MutableList<LogItem>> = mutableMapOf()
    var aggregationScheduler: Scheduler = Schedulers.computation()

    val logHandlers = ArrayList<LogHandler>()

    @Synchronized
    fun addHandler(handler: LogHandler) = logHandlers.add(handler)

    @Synchronized
    fun removeAllHandlers() = logHandlers.clear()

    private fun aggregate(logItem: LogItem) = aggregationScheduler.scheduleDirect {
        val aggregationKey = logItem.aggregationKey
        val aggregationTime = logItem.aggregationTime

        if (aggregationKey == null || aggregationTime == null) return@scheduleDirect

        if (aggregationKey !in aggregationLogs) {
            aggregationLogs[aggregationKey] = mutableListOf()
        }
        aggregationLogs[aggregationKey]?.add(logItem)

        if (aggregationKey !in aggregationDebouncers) {
            val debouncer = PublishSubject.create<Boolean>()
            debouncer
                    .debounce(aggregationTime, TimeUnit.MILLISECONDS, aggregationScheduler)
                    .firstElement()
                    .justDo {
                        try {
                            val logItems = aggregationLogs[aggregationKey]!!
                            if (logItems.size < 2) {
                                broadcastLog(logItem)
                            } else {
                                val aggregatedLog = AggregatedLogItem(
                                        logItems,
                                        message = logItem.message,
                                        level = logItem.level,
                                        tags = logItem.tags,
                                        throwable = logItem.throwable,
                                        logCatLevel = logItem.logCatLevel
                                )
                                val aggregatorContext = logItem.aggregator
                                aggregatorContext?.let { aggregatedLog.aggregatorContext() }
                                broadcastLog(aggregatedLog)
                            }
                        } catch (ex: Exception) {
                            log(LogLevel.ERROR, ex.message ?: "", ex)
                        }
                        debouncer.onComplete()
                        aggregationDebouncers.remove(aggregationKey)
                        aggregationLogs.remove(aggregationKey)
                    }
            aggregationDebouncers[aggregationKey] = debouncer
        }
        aggregationDebouncers[aggregationKey]?.onNext(true)
    }


    private fun broadcastLog(logItem: LogItem) {
        if (logItem.level < levelFilter) {
            return
        }

        for (logHandler in logHandlers) {
            logHandler.onLog(logItem)
        }
        parent?.log(logItem)
    }

    @Synchronized
    private fun log(logItem: LogItem) {
        if (logItem.level < levelFilter) {
            return
        }

        if (logItem.aggregationKey != null) {
            aggregate(logItem)
        } else {
            broadcastLog(logItem)
        }
    }

    private fun log(level: LogLevel, msg: String, t: Throwable? = null) =
            log(LogItem(message = msg, level = level, throwable = t))

    fun trace(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.TRACE, logData = data.filterNotNull().toMap()))
    fun trace(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.TRACE, logData = data.filterNotNull().toMap()))

    fun debug(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.DEBUG, logData = data.filterNotNull().toMap()))
    fun debug(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.DEBUG, logData = data.filterNotNull().toMap()))

    fun info(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.INFO, logData = data.filterNotNull().toMap()))
    fun info(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.INFO, logData = data.filterNotNull().toMap()))

    fun warn(tag: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(tags = mutableSetOf(tag), level = LogLevel.WARN, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun warn(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.WARN, logData = data.filterNotNull().toMap()))
    fun warn(tag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.WARN, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun warn(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.WARN, logData = data.filterNotNull().toMap()))
    fun warn(firstTag: String, secondTag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.WARN, throwable = throwable, logData = data.filterNotNull().toMap()))

    fun error(tag: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(tags = mutableSetOf(tag), level = LogLevel.ERROR, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun error(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.ERROR, logData = data.filterNotNull().toMap()))
    fun error(tag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.ERROR, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun error(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.ERROR, logData = data.filterNotNull().toMap()))
    fun error(firstTag: String, secondTag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.ERROR, throwable = throwable, logData = data.filterNotNull().toMap()))

    fun wtf(tag: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(tags = mutableSetOf(tag), level = LogLevel.WTF, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun wtf(tag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.WTF, logData = data.filterNotNull().toMap()))
    fun wtf(tag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(tag), level = LogLevel.WTF, throwable = throwable, logData = data.filterNotNull().toMap()))
    fun wtf(firstTag: String, secondTag: String, message: String, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.WTF, logData = data.filterNotNull().toMap()))
    fun wtf(firstTag: String, secondTag: String, message: String, throwable: Throwable?, vararg data: Pair<String, Any?>?) = log(LogItem(message = message, tags = mutableSetOf(firstTag, secondTag), level = LogLevel.WTF, throwable = throwable, logData = data.filterNotNull().toMap()))

    val trace get() = LogItem(level = LogLevel.TRACE)
    val debug get() = LogItem(level = LogLevel.DEBUG)
    val info get() = LogItem(level = LogLevel.INFO)
    val warn get() = LogItem(level = LogLevel.WARN)
    val error get() = LogItem(level = LogLevel.ERROR)
    val wtf get() = LogItem(level = LogLevel.WTF)

    open inner class LogItem(
            var message: String? = "",
            val tags: MutableSet<String> = mutableSetOf(),
            val level: LogLevel,
            var throwable: Throwable? = null,
            var logCatLevel: LogLevel? = null,
            var logData: Map<String, Any?> = emptyMap()
    ) {
        val timestamp: Date = Calendar.getInstance().time
        var isBreadcrumb = false
            internal set
        var forceReport = false
            internal set
        var culprit: String? = null
            internal set

        internal var aggregationKey: String? = null
        internal var aggregationTime: Long? = null
        internal var aggregator: LogAggregatorContext? = null

        @CheckReturnValue
        open fun withData(key: String, value: Any?): LogItem {
            if (logData !is MutableMap) {
                logData = logData.toMutableMap()
            }
            (logData as MutableMap)[key] = value
            return this
        }

        @CheckReturnValue
        open fun message(value: String): LogItem {
            message = value
            return this
        }

        @CheckReturnValue
        fun withError(value: Throwable): LogItem {
            throwable = value
            return this
        }

        @CheckReturnValue
        fun withTag(vararg values: String): LogItem {
            tags.addAll(values)
            return this
        }

        @CheckReturnValue
        fun withBreadcrumb(): LogItem {
            isBreadcrumb = true
            return this
        }

        @CheckReturnValue
        fun reportToSentry(): LogItem {
            forceReport = true
            return this
        }

        @CheckReturnValue
        fun useLogCatLevel(logLevel: LogLevel): LogItem {
            logCatLevel = logLevel
            return this
        }

        fun culprit(value: String): LogItem {
            culprit = value
            return this
        }

        fun log() {
            this@Plogger.log(this)
        }

        @CheckReturnValue
        open fun aggregate(key: String, time: Time, aggregator: LogAggregatorContext): LogItem {
            this.aggregationKey = key
            this.aggregationTime = time.toMillis()
            this.aggregator = aggregator
            return this
        }

        @CheckReturnValue
        open fun aggregate(key: String, time: Long, timeUnits: TimeUnit, aggregator: LogAggregatorContext): LogItem {
            this.aggregationKey = key
            this.aggregationTime = timeUnits.toMillis(time)
            this.aggregator = aggregator
            return this
        }
    }

    inner class AggregatedLogItem(
            val logs: List<LogItem>,
            message: String?,
            tags: MutableSet<String>,
            level: LogLevel,
            throwable: Throwable?,
            logCatLevel: LogLevel?
    ) : Plogger.LogItem(message, tags, level, throwable, logCatLevel) {
        override fun aggregate(key: String, time: Long, timeUnits: TimeUnit, aggregator: LogAggregatorContext): LogItem {
            return this
        }

        override fun aggregate(key: String, time: Time, aggregator:LogAggregatorContext): LogItem {
            return this
        }
    }
}

object Plog : Plogger()

enum class LogLevel {
    @Json(name = "trace") TRACE,
    @Json(name = "debug") DEBUG,
    @Json(name = "info") INFO,
    @Json(name = "warn") WARN,
    @Json(name = "error") ERROR,
    @Json(name = "wtf") WTF
}



typealias LogAggregatorContext = Plogger.AggregatedLogItem.() -> Unit
