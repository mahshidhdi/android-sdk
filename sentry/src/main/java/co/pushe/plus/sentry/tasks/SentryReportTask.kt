package co.pushe.plus.sentry.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.task.PeriodicTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single
import kotlin.reflect.KClass

/**
 * This task sends periodic reports to Sentry containing information and stats about the message
 * store, data collection tasks, storage, config, etc...
 *
 * The task should only be run in Alpha or Beta environments
 */
class SentryReportTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("sentry_report", context, workerParameters) {

    override fun perform(): Single<Result> {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

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
                core.context().getSharedPreferences(PusheStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
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
        override fun task(): KClass<out PusheTask> = SentryReportTask::class
        override fun repeatInterval(): Time = interval
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun taskId() = "pushe_sentry_report"
        override fun flexibilityTime() = hours(3)
    }
}

