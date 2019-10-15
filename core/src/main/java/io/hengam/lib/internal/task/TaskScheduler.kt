package io.hengam.lib.internal.task

import androidx.work.*
import io.hengam.lib.LogTag.T_TASK
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.rx.justDo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.isSubclassOf

@CoreScope
class TaskScheduler @Inject constructor(
        private val hengamConfig: HengamConfig,
        hengamStorage: HengamStorage
) {

    private val oneTimeTasks = hengamStorage.createStoredList(
        "onetime_tasks",
        StoredTaskInfo::class.java
    )

    private val periodicTaskIntervals = hengamStorage.createStoredMap(
            "periodic_task_intervals",
            Long::class.javaObjectType
    )

    fun scheduleTask(taskOptions: OneTimeTaskOptions, data: Data? = null, initialDelay: Time? = null) {
        Plog.trace(T_TASK, "Executing one-time task: ${taskOptions.taskId()}")

        taskOptions.hengamConfig = hengamConfig

        val taskInfo = StoredTaskInfo(
            taskOptions.existingWorkPolicy(),
            taskOptions.networkType(),
            taskOptions.task().qualifiedName,
            taskOptions.taskId(),
            taskOptions.maxAttemptsCount(),
            taskOptions.backoffDelay(),
            taskOptions.backoffPolicy(),
            data?.keyValueMap
        )
        oneTimeTasks.add(taskInfo)

        val inputData: Data
        if (data != null) {
            val dataMap = data.keyValueMap.toMutableMap()
            dataMap[HengamTask.DATA_MAX_ATTEMPTS_COUNT] = taskOptions.maxAttemptsCount()
            dataMap[HengamTask.DATA_TASK_ID] = taskOptions.taskId()
            dataMap[HengamTask.DATA_TASK_CLASS] = taskOptions.task().qualifiedName
            inputData = Data.Builder().putAll(dataMap).build()
        } else {
            inputData = workDataOf(
                HengamTask.DATA_MAX_ATTEMPTS_COUNT to taskOptions.maxAttemptsCount(),
                HengamTask.DATA_TASK_ID to taskOptions.taskId(),
                HengamTask.DATA_TASK_CLASS to taskOptions.task().qualifiedName
            )
        }

        val performer = try {
            taskOptions.task().createInstance()
        } catch (e: Exception){
            Plog.error(T_TASK, "Could not instantiate the performer class of oneTimeTask. It will be scheduled to run by WorkManager", e,
                "Task" to taskOptions.taskId()
            )
            null
        }

        if (performer == null) {
            scheduleOneTimeTask(taskOptions, inputData, initialDelay)
            oneTimeTasks.remove(taskInfo)
            return
        }

        performTask(performer, inputData)
            .subscribeOn(cpuThread())
            .delaySubscription(initialDelay?.time ?: 0L, TimeUnit.MILLISECONDS)
            .justDo(T_TASK) { result ->
                if (result == ListenableWorker.Result.retry()) {
                    Plog.trace(T_TASK, "Failure trying to run one-time task. Scheduling the task to be run by workManager",
                        "Task Id" to taskOptions.taskId()
                    )
                    scheduleOneTimeTask(taskOptions, inputData, millis(ONE_TIME_TASK_FIRST_RETRY_DELAY))
                    oneTimeTasks.remove(taskInfo)
                } else {
                    val resultString = when (result) {
                        is ListenableWorker.Result.Failure -> "Failure"
                        is ListenableWorker.Result.Success -> "Success"
                        else -> "Unknown"
                    }
                    Plog.trace(T_TASK, "Task finished with result $resultString",
                        "Task Id" to taskOptions.taskId()
                    )
                    oneTimeTasks.remove(taskInfo)
                }
            }
    }

    private fun performTask(task: HengamTask, inputData: Data): Single<ListenableWorker.Result> {
        return task.perform(inputData)
            .onErrorReturn {
                ListenableWorker.Result.retry()
            }
    }

    private fun scheduleOneTimeTask(taskOptions: OneTimeTaskOptions, inputData: Data, initialDelay: Time? = null) {
        Plog.trace(T_TASK, "Scheduling one-time task",
            "Task Id" to inputData.getString(HengamTask.DATA_TASK_ID)
        )
        taskOptions.hengamConfig = hengamConfig
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(taskOptions.networkType())
            .build()

        val taskRequestBuilder = OneTimeWorkRequest.Builder(HengamTaskPerformer::class.java)
            .addTag(DEFAULT_WORK_TAG)
            .addTag(taskOptions.taskId() ?: taskOptions.task().qualifiedName ?: "")
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

        taskRequestBuilder.setInputData(inputData)

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
        taskOptions.hengamConfig = hengamConfig

        val taskId = taskOptions.taskId()

        val constraints = Constraints.Builder()
                .setRequiredNetworkType(taskOptions.networkType())
                .build()
        val taskBuilder = PeriodicWorkRequest.Builder(
                HengamTaskPerformer::class.java,
                taskOptions.repeatInterval().time,
                taskOptions.repeatInterval().timeUnit
        )
            .addTag(DEFAULT_WORK_TAG)
            .addTag(taskOptions.taskId())
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
            dataMap[HengamTask.DATA_MAX_ATTEMPTS_COUNT] = taskOptions.maxAttemptsCount()
            dataMap[HengamTask.DATA_TASK_ID] = taskOptions.taskId()
            dataMap[HengamTask.DATA_TASK_REPEAT_INTERVAL] = taskOptions.repeatInterval().toMillis()
            dataMap[HengamTask.DATA_TASK_FLEXIBILITY_TIME] = taskOptions.flexibilityTime().toMillis()
            dataMap[HengamTask.DATA_TASK_CLASS] = taskOptions.task().qualifiedName
            taskBuilder.setInputData(Data.Builder().putAll(dataMap).build())
        }else{
            taskBuilder.setInputData(workDataOf(
                    HengamTask.DATA_MAX_ATTEMPTS_COUNT to taskOptions.maxAttemptsCount(),
                    HengamTask.DATA_TASK_ID to taskOptions.taskId(),
                    HengamTask.DATA_TASK_REPEAT_INTERVAL to taskOptions.repeatInterval().toMillis(),
                    HengamTask.DATA_TASK_FLEXIBILITY_TIME to taskOptions.flexibilityTime().toMillis(),
                    HengamTask.DATA_TASK_CLASS to taskOptions.task().qualifiedName
            ))
        }

        // If policy is KEEP, check if interval has changed and if it has use REPLACE instead
        var existingWorkPolicy = taskOptions.existingWorkPolicy() ?: ExistingPeriodicWorkPolicy.KEEP
        if (existingWorkPolicy == ExistingPeriodicWorkPolicy.KEEP) {
            val oldInterval = periodicTaskIntervals[taskId]
            val newInterval = taskOptions.repeatInterval().toMillis()
            if (oldInterval == null || oldInterval != newInterval) {
                periodicTaskIntervals[taskId] = newInterval
            }
            if (oldInterval != null && oldInterval != newInterval) {
                existingWorkPolicy = ExistingPeriodicWorkPolicy.REPLACE
                Plog.debug(T_TASK, "Updated repeat interval for task $taskId",
                    "Old Interval" to millis(oldInterval).bestRepresentation(),
                    "New Interval" to millis(newInterval).bestRepresentation()
                )
            }
        }

        WorkManager.getInstance().enqueueUniquePeriodicWork(
                taskId,
                existingWorkPolicy,
                taskBuilder.build()
        )
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

    fun scheduleStoredTasks() {
        val tasks = mutableListOf<StoredTaskInfo>()
        tasks.addAll(oneTimeTasks)

        oneTimeTasks.clear()
        for (task in tasks){
            if (task.taskClassName == null) continue
            val taskClass = Class.forName(task.taskClassName).kotlin
            if (!taskClass.isSubclassOf(HengamTask::class)) continue

            scheduleTask(object : OneTimeTaskOptions(){
                override fun networkType(): NetworkType = task.networkType
                override fun task(): KClass<out HengamTask> = Class.forName(task.taskClassName).kotlin as KClass<out HengamTask>
                override fun existingWorkPolicy(): ExistingWorkPolicy? = task.existingWorkPolicy
                override fun taskId(): String? = task.taskId
                override fun maxAttemptsCount(): Int = task.maxAttemptsCount
                override fun backoffDelay(): Time? = task.backoffDelay
                override fun backoffPolicy(): BackoffPolicy? = task.backoffPolicy
            }, Data.Builder().putAll(task.inputData ?: mutableMapOf()).build())
        }
    }

    companion object {
        const val DEFAULT_WORK_TAG = "hengam"
        const val ONE_TIME_TASK_FIRST_RETRY_DELAY = 30 * 1000L
    }
}

@JsonClass(generateAdapter = true)
data class StoredTaskInfo(
    @Json(name="ewp") val existingWorkPolicy: ExistingWorkPolicy? = ExistingWorkPolicy.APPEND,
    @Json(name="network_type") val networkType: NetworkType,
    @Json(name="class_name") val taskClassName: String?,
    @Json(name="task_id") val taskId: String?,
    @Json(name="max_attempts") val maxAttemptsCount: Int = -1,
    @Json(name="backoff_delay") val backoffDelay: Time? = null,
    @Json(name="backoff_policy") val backoffPolicy: BackoffPolicy? = null,
    @Json(name="input_data") val inputData: Map<String, Any>?
)