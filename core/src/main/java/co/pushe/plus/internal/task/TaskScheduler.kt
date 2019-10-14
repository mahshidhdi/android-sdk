package co.pushe.plus.internal.task

import androidx.work.*
import co.pushe.plus.LogTag.T_TASK
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.millis
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@CoreScope
class TaskScheduler @Inject constructor(
        private val pusheConfig: PusheConfig,
        pusheStorage: PusheStorage
) {

    private val periodicTaskIntervals = pusheStorage.createStoredMap(
            "periodic_task_intervals",
            Long::class.javaObjectType
    )

    fun scheduleTask(taskOptions: OneTimeTaskOptions, data: Data? = null, initialDelay: Time? = null) {
        taskOptions.pusheConfig = pusheConfig

        val constraints = Constraints.Builder()
                .setRequiredNetworkType(taskOptions.networkType())
                .build()

        val taskRequestBuilder = OneTimeWorkRequest.Builder(taskOptions.task().java)
                .addTag(DEFAULT_WORK_TAG)
                .setConstraints(constraints)

        if (initialDelay != null) {
            taskRequestBuilder.setInitialDelay(initialDelay.toSeconds(), TimeUnit.SECONDS)
        }

        val backoffPolicy = taskOptions.backoffPolicy()
        val backoffDelay = taskOptions.backoffDelay()
        if (backoffPolicy != null || backoffDelay != null) {
            taskRequestBuilder.setBackoffCriteria(
                    backoffPolicy ?: BackoffPolicy.EXPONENTIAL,
                    backoffDelay?.toMillis() ?: WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS
            )
        }

        if (data != null) {
            val dataMap = data.keyValueMap.toMutableMap()
            dataMap[PusheTask.DATA_MAX_ATTEMPTS_COUNT] = taskOptions.maxAttemptsCount()
            dataMap[PusheTask.DATA_TASK_NAME] = taskOptions.taskId()
            taskRequestBuilder.setInputData(Data.Builder().putAll(dataMap).build())
        }else{
            taskRequestBuilder.setInputData(workDataOf(
                PusheTask.DATA_MAX_ATTEMPTS_COUNT to taskOptions.maxAttemptsCount(),
                PusheTask.DATA_TASK_NAME to taskOptions.taskId()
            ))
        }

        val uniqueWorkName = taskOptions.taskId()
        if (uniqueWorkName == null) {
            WorkManager.getInstance().enqueue(taskRequestBuilder.build())
        } else {
            WorkManager.getInstance().beginUniqueWork(
                    uniqueWorkName,
                    taskOptions.existingWorkPolicy() ?: ExistingWorkPolicy.KEEP,
                    taskRequestBuilder.build()
            ).enqueue()
        }
    }

    fun schedulePeriodicTask(taskOptions: PeriodicTaskOptions, data: Data? = null) {
        Plog.trace(T_TASK, "Scheduling periodic task ${taskOptions.taskId()}")
        taskOptions.pusheConfig = pusheConfig

        val constraints = Constraints.Builder()
                .setRequiredNetworkType(taskOptions.networkType())
                .build()
        val taskBuilder = PeriodicWorkRequest.Builder(
                taskOptions.task().java,
                taskOptions.repeatInterval().time,
                taskOptions.repeatInterval().timeUnit
        )
        .addTag(DEFAULT_WORK_TAG)
        .setConstraints(constraints)

        val backoffPolicy = taskOptions.backoffPolicy()
        val backoffDelay = taskOptions.backoffDelay()
        if (backoffPolicy != null || backoffDelay != null) {
            taskBuilder.setBackoffCriteria(
                    backoffPolicy ?: BackoffPolicy.EXPONENTIAL,
                    backoffDelay?.toMillis() ?: WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                    TimeUnit.MILLISECONDS
            )
        }

        if (data != null) {
            val dataMap = data.keyValueMap.toMutableMap()
            dataMap[PusheTask.DATA_MAX_ATTEMPTS_COUNT] = taskOptions.maxAttemptsCount()
            dataMap[PusheTask.DATA_TASK_NAME] = taskOptions.taskId()
            dataMap[PusheTask.DATA_TASK_REPEAT_INTERVAL] = taskOptions.repeatInterval().toMillis()
            dataMap[PusheTask.DATA_TASK_FLEXIBILITY_TIME] = taskOptions.flexibilityTime().toMillis()
            taskBuilder.setInputData(Data.Builder().putAll(dataMap).build())
        }else{
            taskBuilder.setInputData(workDataOf(
                    PusheTask.DATA_MAX_ATTEMPTS_COUNT to taskOptions.maxAttemptsCount(),
                    PusheTask.DATA_TASK_NAME to taskOptions.taskId(),
                    PusheTask.DATA_TASK_REPEAT_INTERVAL to taskOptions.repeatInterval().toMillis(),
                    PusheTask.DATA_TASK_FLEXIBILITY_TIME to taskOptions.flexibilityTime().toMillis()
            ))
        }

        val uniqueWorkName = taskOptions.taskId()
        if (uniqueWorkName == null) {
            WorkManager.getInstance().enqueue(taskBuilder.build())
        } else {
            // If policy is KEEP, check if interval has changed and if it has use REPLACE instead
            var existingWorkPolicy = taskOptions.existingWorkPolicy() ?: ExistingPeriodicWorkPolicy.KEEP
            if (existingWorkPolicy == ExistingPeriodicWorkPolicy.KEEP) {
                val oldInterval = periodicTaskIntervals[uniqueWorkName]
                val newInterval = taskOptions.repeatInterval().toMillis()
                if (oldInterval == null || oldInterval != newInterval) {
                    periodicTaskIntervals[uniqueWorkName] = newInterval
                }
                if (oldInterval != null && oldInterval != newInterval) {
                    existingWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
                    Plog.debug(T_TASK, "Updated repeat interval for task $uniqueWorkName",
                        "Old Interval" to millis(oldInterval).bestRepresentation(),
                        "New Interval" to millis(newInterval).bestRepresentation()
                    )
                }
            }

            WorkManager.getInstance().enqueueUniquePeriodicWork(
                    uniqueWorkName,
                    existingWorkPolicy,
                    taskBuilder.build()
            )
        }
    }

    fun cancelTask(taskId: String) {
        WorkManager.getInstance().cancelUniqueWork(taskId)
    }

    fun cancelTask(taskOptions: TaskOptions) {
        val taskId = taskOptions.taskId()

        if (taskId == null) {
            Plog.warn(T_TASK, "Cannot cancel task with no id")
            return
        }

        WorkManager.getInstance().cancelUniqueWork(taskId)
    }

    companion object {
        const val DEFAULT_WORK_TAG = "pushe"
    }
}