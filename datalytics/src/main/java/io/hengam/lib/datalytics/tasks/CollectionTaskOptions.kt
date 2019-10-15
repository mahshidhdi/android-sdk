package io.hengam.lib.datalytics.tasks

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import io.hengam.lib.datalytics.Collectable
import io.hengam.lib.datalytics.CollectorSettings
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.Time
import kotlin.reflect.KClass

/**
 * Every [HengamTask] must have options.
 * All tasks related to datalytics module are using this class as options holder.
 * @param collectable is the option holder for tasks and initializes them.
 * @param collectableSettings handles shared pref stuff related to task interval values.
 */
class CollectionTaskOptions(
        val collectable: Collectable,
        val collectorSettings: CollectorSettings
) : PeriodicTaskOptions() {
    override fun networkType(): NetworkType = if (collectable.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED

    override fun task(): KClass<out HengamTask>  = DatalyticsCollectionTask::class

    override fun repeatInterval(): Time = collectorSettings.repeatInterval

    override fun existingWorkPolicy(): ExistingPeriodicWorkPolicy? = ExistingPeriodicWorkPolicy.KEEP

    override fun taskId(): String = "hengam_collection_${collectable.id}"

    override fun maxAttemptsCount(): Int = collectorSettings.maxAttempts

    override fun flexibilityTime(): Time = collectorSettings.flexTime
}