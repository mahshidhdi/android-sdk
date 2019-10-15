package io.hengam.lib.datalytics.tasks

import androidx.work.Data
import androidx.work.ListenableWorker
import io.hengam.lib.Hengam
import io.hengam.lib.datalytics.Collectable
import io.hengam.lib.datalytics.CollectorExecutor
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.datalytics.getCollectableSettings
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.HengamTask
import io.reactivex.Single
import javax.inject.Inject


/**
 * This class IS the task that gets started and scheduled.
 * It gets the collectableId and makes a task for it... Starts the task and so on.
 */
class DatalyticsCollectionTask: HengamTask() {

    @Inject lateinit var hengamConfig: HengamConfig
    @Inject lateinit var collectorExecutor: CollectorExecutor

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        val datalyticsComponent = HengamInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.DATALYTICS)

        datalyticsComponent.inject(this)

        val collectableId = inputData.getString(DATA_COLLECTABLE_ID)
                ?: throw DatalyticsCollectionTaskException("No collectable name provided for datalytics task")

        val collectable = Collectable.getCollectableById(collectableId)
                ?: throw DatalyticsCollectionTaskException("Invalid collectable id $collectableId")

        val collectableSettings = hengamConfig.getCollectableSettings(collectable)

        return collectorExecutor.collectAndSend(collectable, collectableSettings.sendPriority)
                .toSingleDefault(ListenableWorker.Result.success())
    }

    class DatalyticsCollectionTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)
    companion object {
        const val DATA_COLLECTABLE_ID = "collectable_id"
    }
}