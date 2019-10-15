package io.hengam.lib.sentry.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import kotlin.reflect.KClass

/**
 * This task sends periodic reports to Sentry containing information and stats about the message
 * store, data collection tasks, storage, config, etc...
 *
 * The task should only be run in Alpha or Beta environments
 */
class SentryReportTask(context: Context, workerParameters: WorkerParameters)
    : HengamTask("sentry_report", context, workerParameters) {

    override fun perform(): Single<Result> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        val sharedPreferences = core.sharedPreferences()

        val now = TimeUtils.now()

        val installationBirthday = sharedPreferences.getLong("installation_birthday", now.toMillis())
        val reportCount = sharedPreferences.getInt("sentry_report_count", 0)

        val messages = core.messageStore().allMessages
        val messageStoreData = mapOf(
                "Messages" to messages.map { mapOf(
                        "type" to it.message.messageType,
                        "size" to it.messageSize,
                        "time" to timeAgo(now - it.message.time)
                ) },
                "Message Count" to messages.size,
                "Total Message Size" to messages.sumBy { it.messageSize }
        )

        val collectionTimes = core.storage()
                .createStoredMap("collection_last_run_times", Long::class.javaObjectType)
                .mapValues { timeAgo(millis(now.toMillis() - it.value)) }

        val anyAdapter = core.moshi().adapter(Any::class.java)
        val storage =
                core.context().getSharedPreferences(HengamStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                        .all
                        .mapValues {
                            val stringValue = it.value.toString()
                            if (stringValue.startsWith("{") || stringValue.startsWith("[")) {
                                anyAdapter.fromJson(stringValue)
                            } else {
                                stringValue
                            }
                        }

        Plog.info.message("Sentry Report")
                .reportToSentry()
                .withData("Message Store", messageStoreData)
                .withData("Prev Collection At", collectionTimes)
                .withData("Storage", storage)
                .withData("Config", core.config().allConfig)
                .withData("Age", timeAgo(millis(now.toMillis() - installationBirthday)))
                .withData("Report Number", reportCount)
                .log()

        sharedPreferences.edit()
                .putLong("installation_birthday", now.toMillis())
                .putInt("sentry_report_count", reportCount + 1)
                .apply()

        return Single.just(Result.success())
    }

    private fun timeAgo(timeAgo: Time): String {
        return when {
            timeAgo < seconds(1) -> "$timeAgo millis"
            timeAgo < minutes(1) -> "${timeAgo.toSeconds()} seconds"
            timeAgo < hours(1) -> "${timeAgo.toMinutes()} minutes"
            timeAgo < days(1) -> "${timeAgo.toHours()} hours"
            else -> "${timeAgo.toDays()} days"
        }
    }

    class Options(private val interval: Time) : PeriodicTaskOptions() {
        override fun networkType(): NetworkType = NetworkType.NOT_REQUIRED
        override fun task(): KClass<out HengamTask> = SentryReportTask::class
        override fun repeatInterval(): Time = interval
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun taskId() = "hengam_sentry_report"
        override fun flexibilityTime() = hours(3)
    }
}

