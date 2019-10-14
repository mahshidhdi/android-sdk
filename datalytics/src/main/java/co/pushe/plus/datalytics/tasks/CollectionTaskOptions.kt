package co.pushe.plus.datalytics.tasks

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import co.pushe.plus.datalytics.Collectable
import co.pushe.plus.datalytics.CollectorSettings
import co.pushe.plus.internal.task.PeriodicTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.utils.Time
import kotlin.reflect.KClass

/**
 * Every [PusheTask] must have options.
 * All tasks related to datalytics module are using this class as options holder.
 * @param collectable is the option holder for tasks and initializes them.
 * @param collectableSettings handles shared pref stuff related to task interval values.
 */
class CollectionTaskOptions(
        val collectable: Collectable,
        val collectorSettings: CollectorSettings
) : PeriodicTaskOptions() {
    override fun networkType(): NetworkType = if (collectable.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED

    override fun task(): KClass<out PusheTask>  = DatalyticsCollectionTask::class

    override fun repeatInterval(): Time = collectorSettings.repeatInterval

    override fun existingWorkPolicy(): ExistingPeriodicWorkPolicy? = ExistingPeriodicWorkPolicy.KEEP

    override fun taskId(): String? = "pushe_collection_${collectable.id}"

    override fun maxAttemptsCount(): Int = collectorSettings.maxAttempts

    override fun flexibilityTime(): Time = collectorSettings.flexTime
}