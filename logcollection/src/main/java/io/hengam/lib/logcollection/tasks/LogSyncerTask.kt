package io.hengam.lib.logcollection.tasks

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import io.hengam.lib.Hengam
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.logcollection.LogCollector
import io.hengam.lib.logcollection.dagger.LogCollectionComponent
import io.hengam.lib.logcollection.dagger.LogCollectionScope
import io.reactivex.Single
import javax.inject.Inject

@LogCollectionScope
class LogSyncerTask constructor(
    context: Context,
    workerParameters: WorkerParameters
): RxWorker(context, workerParameters){

    @Inject lateinit var logCollector: LogCollector

    override fun createWork(): Single<Result> {

        val logCollectionComponent = HengamInternals.getComponent(LogCollectionComponent::class.java)
        logCollectionComponent?.inject(this) ?: throw ComponentNotAvailableException(Hengam.LOG_COLLECTION)

        return logCollector.attemptSyncing()
            .toSingleDefault(Result.success())
            .onErrorResumeNext { Single.just(Result.retry()) }
    }

    companion object{
        const val LOG_SYNCER_TASK_NAME = "hengam_log_syncer_task"
    }
}