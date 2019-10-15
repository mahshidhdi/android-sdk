package io.hengam.lib.sentry.messages.downstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
import io.hengam.lib.utils.log.LogLevel
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SentryConfigMessage(
        @Json(name = "dsn") val dsn: String?,
        @Json(name = "enabled") val enabled: Boolean?,
        @Json(name = "level") val level: LogLevel?,
        @Json(name = "report_interval_days") val reportIntervalDays: Int?
) {
    class Parser : DownstreamMessageParser<SentryConfigMessage>(
            MessageType.Downstream.SENTRY_CONFIG,
            { SentryConfigMessageJsonAdapter(it) }
    )
}