package co.pushe.plus.sentry

import android.util.Log
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.messaging.fcm.TokenState
import co.pushe.plus.utils.log.LogHandler
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plogger
import io.sentry.SentryClient
import io.sentry.event.Breadcrumb
import io.sentry.event.BreadcrumbBuilder
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import io.sentry.event.interfaces.ExceptionInterface

class SentryLogHandler(
        private val sentry: SentryClient,
        private val level: LogLevel,
        private val shouldRecordLogs: Boolean
) : LogHandler {
    override fun onLog(logItem: Plogger.LogItem) {
        if (logItem.forceReport || logItem.level >= level) {
            val builder = EventBuilder()

            builder.withLevel(when(logItem.level) {
                LogLevel.TRACE -> Event.Level.DEBUG
                LogLevel.DEBUG -> Event.Level.DEBUG
                LogLevel.INFO -> Event.Level.INFO
                LogLevel.WARN -> Event.Level.WARNING
                LogLevel.ERROR -> Event.Level.ERROR
                LogLevel.WTF -> Event.Level.FATAL
            })

            logItem.message?.let { builder.withMessage(it) }
            logItem.throwable?.let { builder.withSentryInterface(ExceptionInterface(it)) }
            builder.withTimestamp(logItem.timestamp)
            builder.withExtra("Log Data", logItem.logData)

            try {
                PusheInternals.getComponent(CoreComponent::class.java)?.let { core ->
                    builder.withExtra("Token Status", when (core.fcmTokenStore().tokenState) {
                        TokenState.UNAVAILABLE -> "Unavailable"
                        TokenState.NO_TOKEN -> "No Token"
                        TokenState.GENERATED -> "Generated"
                        TokenState.SYNCING -> "Syncing"
                        TokenState.SYNCED -> "Synced"
                    })
                }

                builder.withExtra("Is Proguarded", Pushe::class.java.canonicalName != "co.pushe.plus.Pushe")
            } catch (ex: Exception) {
                Log.e("Pushe", "Encountered error while reporting to Sentry", ex)
            }

            getCulprit(logItem)?.let { builder.withTransaction(it) }

            sentry.sendEvent(builder)
        }

        if ((shouldRecordLogs && logItem.level >= LogLevel.DEBUG) || logItem.isBreadcrumb) {
            sentry.context.recordBreadcrumb(buildBreadcrumb(logItem))
        }
    }

    private fun buildBreadcrumb(logItem: Plogger.LogItem): Breadcrumb {
        val builder = BreadcrumbBuilder()

        if (logItem.message.isNullOrBlank() && logItem.throwable != null) {
            builder.setMessage(logItem.throwable?.message)
        } else {
            builder.setMessage(logItem.message)

            if (logItem.throwable != null) {
                builder.withData("Error Message", logItem.throwable?.message ?: "")
            }
        }

        builder.setLevel(when(logItem.level) {
            LogLevel.TRACE -> Breadcrumb.Level.DEBUG
            LogLevel.DEBUG -> Breadcrumb.Level.DEBUG
            LogLevel.INFO -> Breadcrumb.Level.INFO
            LogLevel.WARN -> Breadcrumb.Level.WARNING
            LogLevel.ERROR -> Breadcrumb.Level.ERROR
            LogLevel.WTF -> Breadcrumb.Level.CRITICAL
        })

        builder.setTimestamp(logItem.timestamp)
        if (logItem.tags.isNotEmpty()) {
            getCulprit(logItem)?.let { builder.setCategory(it) }
        }

        logItem.logData.forEach { builder.withData(it.key, it.value.toString()) }

        return builder.build()
    }

    private fun getCulprit(logItem: Plogger.LogItem): String? {
        val items = ArrayList(logItem.tags)
        logItem.culprit?.let { items.add(it) }
        return if (items.isNotEmpty()) {
            items.joinToString(":")
        } else {
            null
        }
    }
}