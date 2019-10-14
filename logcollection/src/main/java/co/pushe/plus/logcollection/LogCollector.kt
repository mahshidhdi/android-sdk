package co.pushe.plus.logcollection

import androidx.work.*
import co.pushe.plus.internal.PusheException
import co.pushe.plus.internal.ioThread
import co.pushe.plus.logcollection.db.LogCollectionDatabaseImpl
import co.pushe.plus.logcollection.db.LogEntity
import co.pushe.plus.logcollection.network.LogError
import co.pushe.plus.logcollection.network.NetworkManager
import co.pushe.plus.logcollection.network.RequestLogItem
import co.pushe.plus.logcollection.tasks.DbCleanerTask
import co.pushe.plus.logcollection.tasks.LogSyncerTask
import co.pushe.plus.utils.log.LogHandler
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.log.Plogger
import co.pushe.plus.utils.rx.PublishRelay
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LogCollector @Inject constructor(
    private val networkManager: NetworkManager,
    private val database: LogCollectionDatabaseImpl
): LogHandler {

    private val logThrottler = PublishRelay.create<Plogger.LogItem?>()
    private var debounceCounter = 0

    fun initialize() {
        Plog.addHandler(this)
        initializeThrottler()
    }

    fun runDbCleaner() {
        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                DbCleanerTask.DB_CLEANER_TASK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DbCleanerTask>(DB_CLEANING_INTERVAL, TimeUnit.MILLISECONDS)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, PeriodicWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build()
            )
    }

    private fun initializeThrottler() {
        val disposable = logThrottler
            .debounce {
                if (debounceCounter >= 100) {
                    debounceCounter = 0
                    Observable.empty()
                } else {
                    debounceCounter += 1
                    Observable.timer(SYNC_INTERVAL, TimeUnit.MILLISECONDS, ioThread())
                }
            }
            .observeOn(ioThread())
            .flatMapSingle {
                attemptSyncing().toSingleDefault(true).onErrorResumeNext {
                    Single.just(false)
                }
            }
            .subscribe { success ->
                if (success) {
                    WorkManager.getInstance().cancelUniqueWork(LogSyncerTask.LOG_SYNCER_TASK_NAME)
                } else {
                    WorkManager.getInstance()
                        .enqueueUniqueWork(
                            LogSyncerTask.LOG_SYNCER_TASK_NAME,
                            ExistingWorkPolicy.KEEP,
                            OneTimeWorkRequestBuilder<LogSyncerTask>().build()
                        )
                }

            }
    }

    fun attemptSyncing(limit: Int = 200): Completable {
        if (limit == 0) {
            return Completable.error(MaxLogSyncingAttemptException())
        }
        return database.getNewLogs()
            .flatMapCompletable { it ->
                if (it.isEmpty()) {
                    return@flatMapCompletable Completable.complete()
                }
                networkManager.synchronizeLogs(
                    it.map {
                        RequestLogItem(
                            id = it.id,
                            message = it.message,
                            level = it.level,
                            tags = it.tags,
                            data = it.logData,
                            time = it.time,
                            error = it.error
                        )
                    }
                )
                .andThen(flagLogs(it))
                .andThen(attemptSyncing(limit - 1))
            }
    }

    private fun flagLogs(logs: List<LogEntity>?): Completable {
        if (logs.isNullOrEmpty()) return Completable.complete()
        return database.updateDatabase(logs.map {
                it.apply { isSent = true }
            })
    }

    override fun onLog(logItem: Plogger.LogItem?) {
        if (logItem != null && logItem.level >= LogLevel.DEBUG){
            val disposable = database.insertLogs(createLogItem(logItem)).subscribe {
                logThrottler.accept(logItem)
            }
        }
    }

    private fun createLogItem(logItem: Plogger.LogItem?): LogEntity? {
        return if (logItem != null)
            LogEntity(
                message = logItem.message,
                tags = logItem.tags.toList(),
                level = logItem.level.toString().toLowerCase(),
                error = if (logItem.throwable != null) {
                    val sw = StringWriter()
                    logItem.throwable?.printStackTrace(PrintWriter(sw))
                    LogError(logItem.throwable?.message, sw.toString())
                } else null,
                logData = logItem.logData.entries.map {
                    it.key to it.value.toString()
                }.toMap(),
                time = logItem.timestamp.time
            ) else null
    }

    companion object {
        private const val SYNC_INTERVAL = 20000L
        private const val DB_CLEANING_INTERVAL = 3 * 24 * 3600 * 1000L
    }
}

class MaxLogSyncingAttemptException: PusheException("too many attempts to sync the log collection data")