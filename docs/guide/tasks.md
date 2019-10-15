# Running Background Tasks

Hengam uses Android WorkManager internally for scheduling Jobs. We provide an extra layer of abstraction on top of Android WorkManager's Jobs which we call _Tasks_. In this section we will explain how to create and run Hengam Tasks.

!!! question "When should I use Tasks?"
    One thing to note is that the Hengam code is already run on a background thread, mainly the [CPU thread](/guide/threads#cpu-thread). If your purpose is to simply have a code run in the background, there is usually no need to use Tasks. 

    Also, if you simply want to have a piece of code run asynchronous to your current flow, use `cpuThread()` to add a function to the execution queue.

    ```kotlin
    // Some operations in the cpuThread

    cpuThread {
        // Code to be run asynchronously on the CPU thread
    }

    // Continuing previous code

    ```

    There are several reasons why you may choose to use tasks over the CPU thread:

    - You want a certain piece of code to be run only when the network becomes available
    - You want to make sure only a single instance of a code will be scheduled to run even if you schedule multiple times
    - You want your code to retry with exponential back-offs if it fails (e.g., due to network failures)
    - You want a code to run periodically (even if application closes (? not sure))


## Creating a Task
To create a new task, subclass the `HengamTask` class and override the `perform()` method. 

```kotlin
class AwesomeTask : HengamTask() {
    fun perform(): Single<Result> {
        // Perform work
        return Single.just(Result.success())
    }
}
```

Each time the task is to be run, the `perform()` method will be called on the CPU thread. All task related operations should be done in the `perform()` method and it should return a `Single<Result>` value indicating whether the operation was successful or not.

The `Result` class is the same class provided by Android WorkManager. You may use one of the following
methods in order to specify what the task result was:

**success()**: Indicates that the task was performed successfuly

**retry()**: Indicates that the task was failed (e.g., due to network failure) and should be retried with an exponential back-off

**fail()**: Indicated that the task failed and should not be retried

## Creating Task Options
You can schedule a task to be run a single time or schedule it to be run periodically in predefined intervals.

### One-time tasks
In order to start a task, you will need an instance of the `OneTimeTaskOptions` abstract class. A good convention to follow is to add a singleton implementation of this interface inside the task class.

```kotlin
class AwesomeTask : HengamTask("My Task Name") {
    fun perform(inputData): Single<Result> {
        // Perform work
        return Single.just(Result.SUCCESS)
    }

    object Options : OneTimeTaskOptions {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = AwesomeTask::class
        override fun taskId() = "AwesomeTask"
        override fun existingWorkPolicy() = ExistingWorkPolicy.KEEP
        override fun maxAttemptsCount(): Int = 5
        override fun backoffPolicy(): BackoffPolicy? = null
        override fun backoffDelay(): Time? = null
    }
}

```

You may configure how the task should be run by overriding the methods of the `OneTimeTaskOptions` interface.
Overriding the `networkType()` and `task()` methods are mandatory and the others are optional.

**networkType()** should return the type of network connectivity the task requires in order to run.

**task()** should return the class of the task which should be performed once the task is scheduled

**taskId()** by overriding this method you may assign an identifier to the task. If another task with the same 
identifier is scheduled the `existingWorkPolicy` will define whether both are allowed to run or not. If the task is
 not given an identifier then multiple instances of the same task may run at the same time.
 
**existingWorkPolicy()** should return how to handle multiple schedules of the same task (with the same `taskId`). `ExistingWorkPolicy.APPEND` means that scheduling the task multiple times will result in it being run the same amount of times (similar to when no `taskId()` is provided). `ExistingWorkPolicy.REPLACE` means that if the task is scheduled when a previous was is still pending, it should replace the previous task and `ExistingWorkPolicy.KEEP` means it should keep the old task.

**maxAttemptsCount()** determines how many time the task should be retried (if the `Retry.retry()` result is returned) before
giving up and failing the task. If not given then the task will retry indefinitely until it either succeeds or fails.

**backOffPolicy()** determines how the backoff times are computed if the task needs to be retried. Can be one of
`BackoffPolicy.LINEAR` or `BackoffPolicy.EXPONENTIAL`. The default value is `BackoffPolicy.EXPONENTIAL`.

**backOffDelay()** determines the base value to use when computing backoff times if the task needs to be retried. 
If the backoff policy is linear then this value will increase linearly as the retry attempt increases. If it exponential
then the value will increase exponentially with each retry.


### Periodic tasks
Starting periodic tasks requires an instance of the `PeriodicTaskOptions` class. This interface is similar to 
the `OneTimeTaskOptions` with two additional fields.

```kotlin
    object Options : PeriodicTaskOptions {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = AwesomeTask::class
        override fun taskId() = "AwesomeTask"
        override fun existingWorkPolicy() = ExistingWorkPolicy.KEEP
        override fun maxAttemptsCount(): Int = 5
        override fun backoffPolicy(): BackoffPolicy? = null
        override fun backoffDelay(): Time? = null

        override fun repeatInterval(): Time
        override fun flexibilityTime(): Time
    }
```

**repeatInterval()** specifies the interval at which the task should be run. Should not be lower than every
fifteen minutes.

**flexibilityTime()** specifies how flexible the WorkManager is allowed to be in running the tasks on time. For example
a flexibility time of 30 minutes means that it is ok if the taks is run 30 minutes sooner or later than it's defined interval.


## Scheduling Tasks
Tasks are scheduled to be run using the `TaskScheduler.scheduleTask()` method. You will need an instance of the `TaskScheduler` which you can obtain using Dagger's dependency injection.

The `scheduleTask()` method accepts an instance of `OneTimeTaskOptions` which describes what task to run and how to schedule it.

```kotlin
taskScheduler.scheduleTask(AwesomeTask.Options)
```

!!! tip "Updating periodic task intervals"
    If you want to update the interval at which a periodic task is run you could just call the `TaskScheduler.scheduleTask()`
    method again and pass the task options with the new intervals. This would work even if the existing-work-policy is `KEEP`. 

## Passing Data to Tasks

Sometimes tasks need to be passed additional data in order to operate. The `TaskScheduler.scheduleTask()` method accepts a second argument of type `Data` which could be used to pass data to the task. Use the `taskDataOf()` function for creating an instance of `Data`.

```kotlin
val taskData = taskDataOf(
    "firstData" to "awesome stuff",
    "secondData" to "other stuff"
)

taskScheduler.scheduleTask(AwesomeTask.Options, taskData)
```

Use the `inputData` variable which is given as a parameter to the `perform` method to retrieve the data in your task.

```kotlin
class AwesomeTask : HengamTask() {
    fun perform(inputData): Single<Result> {
        val data = inputData.getString("firstData")
        ...
        return Single.just(Result.SUCCESS)
    }
}

```