package io.hengam.lib.sentry

import android.content.Context
import android.util.Log
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.*
import io.hengam.lib.sentry.messages.downstream.SentryConfigMessage
import io.hengam.lib.sentry.tasks.SentryReportTask
import io.hengam.lib.utils.days
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.rx.justDo
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import io.sentry.android.AndroidSentryClientFactory


class SentryInitializer : HengamComponentInitializer() {
    private var sentryClient: SentryClient? = null

    override fun preInitialize(context: Context) {
        try {
            val hengamConfig = HengamConfig(context, HengamMoshi())
            if (!hengamConfig.isSentryEnabled) {
                return
            }
            val sentryClient = SentryClientFactory.sentryClient(
                "${hengamConfig.sentryDsn}?stacktrace.app.packages=${Settings.INTERNAL_PACKAGE_NAME}" +
                        "&uncaught.handler.enabled=false",
                AndroidSentryClientFactory(context)
            )
            sentryClient.addBuilderHelper(SentryEventHelper(context.packageName))
            Plog.addHandler(SentryLogHandler(sentryClient, hengamConfig.sentryLogLevel, hengamConfig.sentryShouldRecordLogs))
            this.sentryClient = sentryClient
        } catch (ex: Exception) {
            Log.e("Hengam", "Could not init entry failed", ex)
        }
    }

    override fun postInitialize(context: Context) {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?:  throw ComponentNotAvailableException(Hengam.CORE)

        /* Schedule Sentry Reporter */
        val config = core.config()
        val interval = config.sentryReportInterval
        if (config.isSentryEnabled && interval != null) {
            core.hengamLifecycle().waitForWorkManagerInitialization().justDo {
                core.taskScheduler().schedulePeriodicTask(SentryReportTask.Options(interval))
            }
        } else {
            core.hengamLifecycle().waitForWorkManagerInitialization().justDo{
                core.taskScheduler().cancelTask(SentryReportTask.Options(millis(0)))
            }
        }
    }
}

fun handleSentryConfigMessage(hengamConfig: HengamConfig, message: SentryConfigMessage) {
    message.enabled?.let { hengamConfig.isSentryEnabled = it }
    message.dsn?.let { hengamConfig.sentryDsn = it }
    message.level?.let { hengamConfig.sentryLogLevel = it }
    message.reportIntervalDays?.let { hengamConfig.sentryReportInterval = days(it.toLong()) }
}