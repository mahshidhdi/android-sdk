package co.pushe.plus.sentry

import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.LogLevel

var DEFAULT_SENTRY_DSN_PUBLIC = "b6fd6e67c07345f1a594d0c63135eb8d"
var DEFAULT_SENTRY_DSN_PRIVATE = "aaed9ff89ccb47c896a976225345264a"

/**
 * **sentry_enabled**
 *
 * Specifies whether sentry is enabled or not
 */
var PusheConfig.isSentryEnabled: Boolean
    get() = getBoolean("sentry_enabled", when (environment()) {
        Environment.DEVELOPMENT -> false
        else -> true
    })
    set(value) = updateConfig("sentry_enabled", value)


/**
 * **sentry_dsn**
 *
 * The Sentry DSN
 */
var PusheConfig.sentryDsn: String
    get() = getString("sentry_dsn", "https://$DEFAULT_SENTRY_DSN_PUBLIC:$DEFAULT_SENTRY_DSN_PRIVATE@cx.ronash.co/1571")
    set(value) = updateConfig("sentry_dsn", value)


/**
 * **sentry_level**
 *
 * The Sentry log level. Only logs which have this level or a higher level will be reported to
 * Sentry.
 */
var PusheConfig.sentryLogLevel: LogLevel
    get() = getObject("sentry_level", LogLevel::class.java, when(environment()) {
        Environment.STABLE -> LogLevel.WTF
        else -> LogLevel.ERROR
    })
    set(value) = updateConfig("sentry_level", LogLevel::class.java, value)


/**
 * **sentry_report_interval_days**
 *
 * Specifies the interval which the Sentry debug report should be sent with. If this config is not
 * set then a default value is returned depending on the environment the SDK is running in (e.g.,
 * beta, stable, etc.).
 *
 * If this value is set to 0 or -1 then the Sentry debug report will be disabled
 *
 * @return The interval as a [Time] object or `null` if the Sentry debug report should be disabled
 */
var PusheConfig.sentryReportInterval: Time?
    get() {
        return (getInteger("sentry_report_interval", Integer.MIN_VALUE)
                .takeIf { it != Integer.MIN_VALUE }
                ?: when (environment()) {
                    Environment.DEVELOPMENT -> null
                    Environment.ALPHA -> days(1).toMillis().toInt()
                    Environment.BETA -> days(3).toMillis().toInt()
                    Environment.STABLE -> null
                })
                ?.let { if (it > 0) millis(it.toLong()) else null }
    }
    set(value) = updateConfig("sentry_report_interval", value?.toMillis() ?: 0)


/**
 * **sentry_record_logs**
 *
 * Specifies whether sentry should record logs made by the SDK as breadcrumbs. The breadcrumbs will
 * not be sent to Sentry independently but will be sent along with any events which are sent.
 *
 * Only logs with a DEBUG level or higher are recorded as breadcrumbs.
 */
var PusheConfig.sentryShouldRecordLogs: Boolean
    get() = getBoolean("sentry_record_logs", when (environment()) {
        Environment.STABLE -> false
        else -> true
    })
    set(value) = updateConfig("sentry_record_logs", value)