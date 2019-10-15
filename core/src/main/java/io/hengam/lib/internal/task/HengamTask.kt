package io.hengam.lib.internal.task

import android.content.Context
import androidx.work.*
import androidx.work.NetworkType
import io.hengam.lib.LogTag.T_TASK
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlin.reflect.KClass

abstract class HengamTask(
        private val taskName: String,
        context: Context,
        workerParams: WorkerParameters
) : RxWorker(context, workerParams) {
    abstract fun perform(): Single<Result>
    open fun onMaximumRetriesReached() {
        Plog.warn(T_TASK, "Maximum number of attempts reached for task $taskName, the task will be aborted")
    }

    private val taskLastRunTimes: PersistedMap<Long> by lazy {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.CORE)
        core.storage().createStoredMap("periodic_task_last_run_times", Long::class.javaObjectType)
    }

    val isFinalAttempt: Boolean
        get() {
            val maxAttempts = inputData.getInt(DATA_MAX_ATTEMPTS_COUNT, -1)
            return maxAttempts == - 1 || runAttemptCount == maxAttempts - 1
        }

    override fun createWork(): Single<Result> {
        if (isPeriodicTask()){
            if (shouldSkipPeriodicTaskExecution()) {
                return Single.just(Result.success())
            }

            val taskName = inputData.getString(DATA_TASK_NAME) ?: run {
                Plog.error(T_TASK, "Task name was not provided in periodic task input data",
                    "Task name" to taskName
                )
                return Single.just(Result.failure())
            }
            taskLastRunTimes[taskName] = TimeUtils.nowMillis()
        }

        return Single.fromCallable {
                    Plog.trace(T_TASK, "Task $taskName started",
                        "Work Id" to id.toString(),
                        "Unique Name" to inputData.getString(DATA_TASK_NAME),
                        "Attempt" to runAttemptCount + 1
                    )
                    perform()
                }
                .flatMap { it }
                .observeOn(cpuThread())
                .doOnError { Plog.error(T_TASK, "Error occurred in task $taskName", it) }
                .onErrorReturn { Result.failure() }
                .map { result ->
                    assertCpuThread()
                    if (result == Result.retry() && inputData.getInt(DATA_MAX_ATTEMPTS_COUNT, -1) != -1
                            && (runAttemptCount + 1) >= inputData.getInt(DATA_MAX_ATTEMPTS_COUNT, -1)) {
                        onMaximumRetriesReached()
                        Result.failure()
                    } else {
                        result
                    }
                }
                .doOnSuccess { result ->
                    val resultString = when (result) {
                        is Result.Retry -> "Retry"
                        is Result.Failure -> "Failure"
                        is Result.Success -> "Success"
                        else -> "Unknown"
                    }

                    Plog.trace(T_TASK, "Task $taskName finished with result $resultString", "Id" to id.toString())
                }
    }

    private fun isPeriodicTask(): Boolean {
        return inputData.keyValueMap.contains(DATA_TASK_REPEAT_INTERVAL)
    }

    override fun getBackgroundScheduler(): Scheduler = cpuThread()

    private fun shouldSkipPeriodicTaskExecution(): Boolean {
        val lastExecutionTime = taskLastRunTimes[inputData.getString(DATA_TASK_NAME)]
        if (lastExecutionTime != null) {
            val interval = inputData.getLong(DATA_TASK_REPEAT_INTERVAL, -1)
            val flexibility = inputData.getLong(DATA_TASK_FLEXIBILITY_TIME, -1)
            if (interval == -1L || flexibility == -1L) {
                return false
            }
            if (interval - (TimeUtils.nowMillis() - lastExecutionTime) > flexibility) {
                Plog.warn.message("Skipping periodic task ${inputData.getString(DATA_TASK_NAME)}")
                    .withTag(T_TASK)
                    .withData("Task name", inputData.getString(DATA_TASK_NAME))
                    .withData("Repeat interval", interval)
                    .withData("Prev Collection", lastExecutionTime)
                    .useLogCatLevel(LogLevel.DEBUG)
                    .aggregate("skipping-periodic-tasks", millis(1000)) {
                        message("Skipping ${logs.size} periodic tasks")
                        withData("tasks", logs.map { log -> log.logData["Task name"] })
                    }
                    .log()
                return true
            }
        }
        return false
    }

    companion object {
        const val DATA_MAX_ATTEMPTS_COUNT = "%max_attempts_count"
        const val DATA_TASK_NAME = "%task_name"
        const val DATA_TASK_REPEAT_INTERVAL = "%task_repeat_interval"
        const val DATA_TASK_FLEXIBILITY_TIME = "%task_flexibility_time"
    }
}

abstract class TaskOptions {
    lateinit var hengamConfig: HengamConfig

    abstract fun networkType(): NetworkType
    abstract fun task(): KClass<out HengamTask>
    open fun taskId(): String? = null
    open fun maxAttemptsCount(): Int = -1
    open fun backoffDelay(): Time? = null
    open fun backoffPolicy(): BackoffPolicy? = null
}

abstract class OneTimeTaskOptions : TaskOptions() {
    open fun existingWorkPolicy(): ExistingWorkPolicy? = ExistingWorkPolicy.APPEND
}

abstract class PeriodicTaskOptions : TaskOptions() {
    open fun existingWorkPolicy(): ExistingPeriodicWorkPolicy? = ExistingPeriodicWorkPolicy.KEEP
    abstract fun repeatInterval(): Time
    abstract fun flexibilityTime(): Time
}

fun taskDataOf(vararg pairs: Pair<String, Any?>): Data = workDataOf(*pairs)

class TaskExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)