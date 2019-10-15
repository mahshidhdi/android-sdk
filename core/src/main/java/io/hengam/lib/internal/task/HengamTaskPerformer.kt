package io.hengam.lib.internal.task

import android.content.Context
import androidx.work.Data
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import io.hengam.lib.LogTag
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.PersistedMap
import io.hengam.lib.utils.TimeUtils
import io.hengam.lib.utils.assertCpuThread
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.millis
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf

class HengamTaskPerformer(context: Context, workerParams: WorkerParameters)
    : RxWorker(context, workerParams) {

    private val taskLastRunTimes: PersistedMap<Long> by lazy {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.CORE)
        core.storage().createStoredMap("periodic_task_last_run_times", Long::class.javaObjectType)
    }

    val isFinalAttempt: Boolean
        get() {
            val maxAttempts = inputData.getInt(HengamTask.DATA_MAX_ATTEMPTS_COUNT, -1)
            return maxAttempts == - 1 || runAttemptCount == maxAttempts - 1
        }

    override fun createWork(): Single<Result> {
        val taskClassName = inputData.getString(HengamTask.DATA_TASK_CLASS) ?: run {
            Plog.error(
                LogTag.T_TASK, "Task className was not provided in periodic task input data"
            )
            return Single.just(Result.failure())
        }
        val performer: HengamTask
        try {
            val taskClass = Class.forName(taskClassName).kotlin
            if (!taskClass.isSubclassOf(HengamTask::class)) {
                Plog.error(
                    LogTag.T_TASK, "Provided task class was not a hengam task class",
                    "Class Name" to taskClassName
                )
                return Single.just(Result.failure())
            }

            performer = taskClass.createInstance() as HengamTask
        } catch (e: Exception) {
            Plog.error(
                LogTag.T_TASK, "Unable to instantiate provided task class",
                "Class Name" to taskClassName
            )
            return Single.just(Result.failure())
        }

        val taskId = inputData.getString(HengamTask.DATA_TASK_ID) ?: taskClassName

        if (isPeriodicTask()) {
            val taskIdentifier = inputData.getString(HengamTask.DATA_TASK_ID) ?: run {
                Plog.error(
                    LogTag.T_TASK, "Task name was not provided in periodic task input data"
                )
                return Single.just(Result.failure())
            }

            if (shouldSkipPeriodicTaskExecution()) {
                return Single.just(Result.success())
            }
            taskLastRunTimes[taskIdentifier] = TimeUtils.nowMillis()
        }

        val dataMap = inputData.keyValueMap.toMutableMap()
        dataMap[HengamTask.DATA_TASK_RETRY_COUNT] = runAttemptCount
        val data = Data.Builder().putAll(dataMap).build()

        return Single.fromCallable {
            Plog.trace(
                LogTag.T_TASK, "Task $taskId started",
                "Work Id" to id.toString(),
                "Attempt" to runAttemptCount + 1
            )
            performer.perform(data)
        }
            .flatMap { it }
            .observeOn(cpuThread())
            .doOnError { Plog.error(LogTag.T_TASK, "Error occurred in task $taskId", it) }
            .onErrorReturn { Result.failure() }
            .map { result ->
                assertCpuThread()
                if (result == Result.retry() && inputData.getInt(HengamTask.DATA_MAX_ATTEMPTS_COUNT, -1) != -1
                    && (runAttemptCount + 2) >= inputData.getInt(HengamTask.DATA_MAX_ATTEMPTS_COUNT, -1)) {
                    performer.onMaximumRetriesReached(inputData)
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

                Plog.trace(LogTag.T_TASK, "Task $taskId finished with result $resultString", "Id" to id.toString())
            }
    }

    private fun isPeriodicTask(): Boolean {
        return inputData.keyValueMap.contains(HengamTask.DATA_TASK_REPEAT_INTERVAL)
    }

    private fun shouldSkipPeriodicTaskExecution(): Boolean {
        val taskId = inputData.getString(HengamTask.DATA_TASK_ID)
        val lastExecutionTime = taskLastRunTimes[taskId]
        if (lastExecutionTime != null) {
            val interval = inputData.getLong(HengamTask.DATA_TASK_REPEAT_INTERVAL, -1)
            val flexibility = inputData.getLong(HengamTask.DATA_TASK_FLEXIBILITY_TIME, -1)
            if (interval == -1L || flexibility == -1L) {
                return false
            }
            if (interval - (TimeUtils.nowMillis() - lastExecutionTime) > flexibility) {
                Plog.warn.message("Skipping periodic task $taskId")
                    .withTag(LogTag.T_TASK)
                    .withData("Task name", taskId)
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

    override fun getBackgroundScheduler(): Scheduler = cpuThread()
}