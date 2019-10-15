package io.hengam.lib.tasks

import android.content.Context
import androidx.work.*
import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.upstreamFlushInterval
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.hours
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.seconds
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
class UpstreamFlushTask: HengamTask() {

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.CORE)

        Plog.debug(T_MESSAGE, "Flushing upstream messages")
        core.taskScheduler().scheduleTask(UpstreamSenderTask.Options)
        return Single.just(ListenableWorker.Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun networkType(): NetworkType = NetworkType.CONNECTED
        override fun task(): KClass<out HengamTask> = UpstreamFlushTask::class
        override fun repeatInterval(): Time = hengamConfig.upstreamFlushInterval
        override fun taskId(): String = "hengam_upstream_flush"
        override fun existingWorkPolicy(): ExistingPeriodicWorkPolicy? = ExistingPeriodicWorkPolicy.KEEP
        override fun backoffDelay(): Time? = seconds(30)
        override fun flexibilityTime() = hours(2)

    }
}


