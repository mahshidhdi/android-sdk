package io.hengam.lib.datalytics.tasks

import android.content.Context
import androidx.work.WorkerParameters
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.datalytics.*
import io.hengam.lib.datalytics.collectors.CollectionRetryRequiredError
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject


/**
 * This class IS the task that gets started and scheduled.
 * It gets the collectableId and makes a task for it... Starts the task and so on.
 */
class DatalyticsCollectionTask(context: Context, workerParameters: WorkerParameters)
    : HengamTask("data_collection", context, workerParameters) {

    @Inject lateinit var hengamConfig: HengamConfig
    @Inject lateinit var collectorExecutor: CollectorExecutor

    override fun perform(): Single<Result> {
        val datalyticsComponent = HengamInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.DATALYTICS)

        datalyticsComponent.inject(this)

        val collectableId = inputData.getString(DATA_COLLECTABLE_ID)
                ?: throw DatalyticsCollectionTaskException("No collectable name provided for datalytics task")

        val collectable = Collectable.getCollectableById(collectableId)
                ?: throw DatalyticsCollectionTaskException("Invalid collectable id $collectableId")

        val collectableSettings = hengamConfig.getCollectableSettings(collectable)

        return collectorExecutor.collectAndSend(collectable, collectableSettings.sendPriority, finalAttempt = isFinalAttempt)
                .toSingleDefault(Result.success())
                .onErrorReturn { ex ->
                    if (ex is CollectionRetryRequiredError) {
                        Plog.warn.message("Data collection failed for $collectableId, scheduling retry attempt")
                                .withError(ex)
                                .withTag(T_DATALYTICS)
                                .useLogCatLevel(LogLevel.DEBUG)
                                .log()
                        Result.retry()
                    } else {
                        throw ex
                    }
                }
    }

    class DatalyticsCollectionTaskException(message: String, cause: Throwable? = null) : Exception(message, cause)
    companion object {
        const val DATA_COLLECTABLE_ID = "collectable_id"
    }
}