package co.pushe.plus.logcollection.tasks

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.logcollection.LogCollector
import co.pushe.plus.logcollection.dagger.LogCollectionComponent
import co.pushe.plus.logcollection.dagger.LogCollectionScope
import io.reactivex.Single
import javax.inject.Inject

@LogCollectionScope
class LogSyncerTask constructor(
    context: Context,
    workerParameters: WorkerParameters
): RxWorker(context, workerParameters){

    @Inject lateinit var logCollector: LogCollector

    override fun createWork(): Single<Result> {

        val logCollectionComponent = PusheInternals.getComponent(LogCollectionComponent::class.java)
        logCollectionComponent?.inject(this) ?: throw ComponentNotAvailableException(Pushe.LOG_COLLECTION)

        return logCollector.attemptSyncing()
            .toSingleDefault(Result.success())
            .onErrorResumeNext { Single.just(Result.retry()) }
    }

    companion object{
        const val LOG_SYNCER_TASK_NAME = "pushe_log_syncer_task"
    }
}