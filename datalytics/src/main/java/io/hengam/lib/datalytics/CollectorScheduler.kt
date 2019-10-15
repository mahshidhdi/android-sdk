package io.hengam.lib.datalytics

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.workDataOf
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.tasks.CollectionTaskOptions
import io.hengam.lib.datalytics.tasks.DatalyticsCollectionTask
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.log.Plog
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This class has the task of scheduling tasks at the start of initialization or rescheduling tasks if any downstream message received.
 * @param collectableSettingsManager handles the shared pref related stuff for repeat interval values.
 * @param taskScheduler is the main scheduler
 * @see TaskScheduler
 */
class CollectorScheduler @Inject constructor(
        private val hengamConfig: HengamConfig,
        private val taskScheduler: TaskScheduler
) {

    /**
     * Gets collectable and schedules it using new values.
     * @see ExistingPeriodicWorkPolicy
     */
    fun scheduleCollector(collectable: Collectable) {
        val settings = hengamConfig.getCollectableSettings(collectable)
        val taskOptions = CollectionTaskOptions(collectable, settings)
        if (settings.repeatInterval.toMillis() > 0 ) {
            taskScheduler.schedulePeriodicTask(taskOptions, workDataOf(DatalyticsCollectionTask.DATA_COLLECTABLE_ID to collectable.id))
        } else {
            taskScheduler.cancelTask(taskOptions)
        }
    }

    /**
     * When app starts (or Sdk loads) at [DatalyticsInitializer.postInitialize] we start the tasks.
     * But the [ExistingPeriodicWorkPolicy] will be different than the time it sets by [handleByMessageType].
     * It will keep the task and just make sure it exists.
     */
    fun scheduleAllCollectablesWithInitialValue() {
        Plog.trace(T_DATALYTICS, "Datalytics tasks initializing.",
            "number of tasks" to Collectable.allCollectables.size.toString()
        )
        Collectable.allCollectables.forEach { scheduleCollector(it) }
    }

    fun cancelCollector(collectable: Collectable) {
        val settings = hengamConfig.getCollectableSettings(collectable)
        val taskOptions = CollectionTaskOptions(collectable, settings)
        taskScheduler.cancelTask(taskOptions)
    }

    fun cancelAllCollectables() {
        Plog.trace(T_DATALYTICS, "Canceling datalytics tasks.",
            "number of tasks" to Collectable.allCollectables.size.toString()
        )
        Collectable.allCollectables.forEach { cancelCollector(it) }
    }

    companion object {
        val MIN_REPEAT_INTERVAL_TIME = Time(10, TimeUnit.MINUTES)
    }
}