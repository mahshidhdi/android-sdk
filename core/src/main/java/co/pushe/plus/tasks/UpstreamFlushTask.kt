package co.pushe.plus.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.LogTag.T_MESSAGE
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.task.PeriodicTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.upstreamFlushInterval
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.hours
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.seconds
import io.reactivex.Single
import kotlin.reflect.KClass

/**
 * A periodic task which runs every 24h to attempt to send all stored upstream messages.
 *
 * This task is intentionally separate from the [UpstreamSenderTask], i.e, instead of scheduling a
 * periodic work for [UpstreamSenderTask] directly we use this periodic task to then schedule the
 * one-time-work for the [UpstreamSenderTask]. This is to ensure only one task instance is sending
 * messages at a time instead of having two separate tasks iterating through the upstream messages
 * and handling back-offs, etc.
 */
class UpstreamFlushTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("upstream_flush", context, workerParameters) {

    override fun perform(): Single<Result> {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Pushe.CORE)

        Plog.debug(T_MESSAGE, "Flushing upstream messages")
        core.taskScheduler().scheduleTask(UpstreamSenderTask.Options)
        return Single.just(Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun networkType(): NetworkType = NetworkType.CONNECTED
        override fun task(): KClass<out PusheTask> = UpstreamFlushTask::class
        override fun repeatInterval(): Time = pusheConfig.upstreamFlushInterval
        override fun taskId(): String? = "pushe_upstream_flush"
        override fun existingWorkPolicy(): ExistingPeriodicWorkPolicy? = ExistingPeriodicWorkPolicy.KEEP
        override fun backoffDelay(): Time? = seconds(30)
        override fun flexibilityTime() = hours(2)

    }
}


