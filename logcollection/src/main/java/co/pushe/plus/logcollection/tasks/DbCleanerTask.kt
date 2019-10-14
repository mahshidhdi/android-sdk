package co.pushe.plus.logcollection.tasks

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.logcollection.dagger.LogCollectionComponent
import co.pushe.plus.logcollection.dagger.LogCollectionScope
import co.pushe.plus.logcollection.db.LogCollectionDatabaseImpl
import io.reactivex.Single
import javax.inject.Inject

@LogCollectionScope
class DbCleanerTask constructor(
    context: Context,
    workerParameters: WorkerParameters
): RxWorker(context, workerParameters){

    @Inject lateinit var database: LogCollectionDatabaseImpl

    override fun createWork(): Single<Result> {

        val logCollectionComponent = PusheInternals.getComponent(LogCollectionComponent::class.java)
        logCollectionComponent?.inject(this) ?: throw ComponentNotAvailableException(Pushe.LOG_COLLECTION)

        return database.cleanDatabase()
            .toSingleDefault(Result.success())
            .onErrorResumeNext { Single.just(Result.retry()) }
    }

    companion object{
        const val DB_CLEANER_TASK_NAME = "pushe_log_db_cleaner_task"
    }
}