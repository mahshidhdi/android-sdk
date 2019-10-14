package co.pushe.plus.sentry

import android.content.Context
import android.util.Log
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.*
import co.pushe.plus.sentry.messages.downstream.SentryConfigMessage
import co.pushe.plus.sentry.tasks.SentryReportTask
import co.pushe.plus.utils.days
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.millis
import co.pushe.plus.utils.rx.justDo
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import io.sentry.android.AndroidSentryClientFactory


class SentryInitializer : PusheComponentInitializer() {
    private var sentryClient: SentryClient? = null

    override fun preInitialize(context: Context) {
        try {
            val pusheConfig = PusheConfig(context, PusheMoshi())
            if (!pusheConfig.isSentryEnabled) {
                return
            }
            val sentryClient = SentryClientFactory.sentryClient(
                "${pusheConfig.sentryDsn}?stacktrace.app.packages=${Settings.INTERNAL_PACKAGE_NAME}" +
                        "&uncaught.handler.enabled=false",
                AndroidSentryClientFactory(context)
            )
            sentryClient.addBuilderHelper(SentryEventHelper(context.packageName))
            Plog.addHandler(SentryLogHandler(sentryClient, pusheConfig.sentryLogLevel, pusheConfig.sentryShouldRecordLogs))
            this.sentryClient = sentryClient
        } catch (ex: Exception) {
            Log.e("Pushe", "Could not init entry failed", ex)
        }
    }

    override fun postInitialize(context: Context) {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?:  throw ComponentNotAvailableException(Pushe.CORE)

        /* Schedule Sentry Reporter */
        val config = core.config()
        val interval = config.sentryReportInterval
        if (config.isSentryEnabled && interval != null) {
            core.pusheLifecycle().waitForWorkManagerInitialization().justDo {
                core.taskScheduler().schedulePeriodicTask(SentryReportTask.Options(interval))
            }
        } else {
            core.pusheLifecycle().waitForWorkManagerInitialization().justDo{
                core.taskScheduler().cancelTask(SentryReportTask.Options(millis(0)))
            }
        }
    }
}

fun handleSentryConfigMessage(pusheConfig: PusheConfig, message: SentryConfigMessage) {
    message.enabled?.let { pusheConfig.isSentryEnabled = it }
    message.dsn?.let { pusheConfig.sentryDsn = it }
    message.level?.let { pusheConfig.sentryLogLevel = it }
    message.reportIntervalDays?.let { pusheConfig.sentryReportInterval = days(it.toLong()) }
}