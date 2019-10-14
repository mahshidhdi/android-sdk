package co.pushe.plus.datalytics.tasks

import android.content.Context
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.datalytics.*
import co.pushe.plus.datalytics.collectors.CollectionRetryRequiredError
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.dagger.DatalyticsComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject


/**
 * This class IS the task that gets started and scheduled.
 * It gets the collectableId and makes a task for it... Starts the task and so on.
 */
class DatalyticsCollectionTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("data_collection", context, workerParameters) {

    @Inject lateinit var pusheConfig: PusheConfig
    @Inject lateinit var collectorExecutor: CollectorExecutor

    override fun perform(): Single<Result> {
        val datalyticsComponent = PusheInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.DATALYTICS)

        datalyticsComponent.inject(this)

        val collectableId = inputData.getString(DATA_COLLECTABLE_ID)
                ?: throw DatalyticsCollectionTaskException("No collectable name provided for datalytics task")

        val collectable = Collectable.getCollectableById(collectableId)
                ?: throw DatalyticsCollectionTaskException("Invalid collectable id $collectableId")

        val collectableSettings = pusheConfig.getCollectableSettings(collectable)

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