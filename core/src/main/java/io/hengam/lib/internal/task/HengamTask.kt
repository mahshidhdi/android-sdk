package io.hengam.lib.internal.task

import androidx.work.*
import io.hengam.lib.LogTag.T_TASK
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import kotlin.reflect.KClass

abstract class HengamTask {
    abstract fun perform(inputData: Data): Single<ListenableWorker.Result>
    open fun onMaximumRetriesReached(inputData: Data) {
        Plog.warn(T_TASK, "Maximum number of attempts reached for task ${this.javaClass.simpleName}, the task will be aborted")
    }

    companion object {
        const val DATA_MAX_ATTEMPTS_COUNT = "%max_attempts_count"
        const val DATA_TASK_ID = "%task_id"
        const val DATA_TASK_REPEAT_INTERVAL = "%task_repeat_interval"
        const val DATA_TASK_FLEXIBILITY_TIME = "%task_flexibility_time"
        const val DATA_TASK_CLASS = "%task_class_name"
        const val DATA_TASK_RETRY_COUNT = "%task_retry_count"
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
    abstract override fun taskId(): String
}

fun taskDataOf(vararg pairs: Pair<String, Any?>): Data = workDataOf(*pairs)

class TaskExecutionException(message: String, cause: Throwable? = null) : Exception(message, cause)