# Running Background Tasks

Pushe uses Android WorkManager internally for scheduling Jobs. We provide an extra layer of abstraction on top of Android WorkManager's Jobs which we call _Tasks_. In this section we will explain how to create and run Pushe Tasks.

!!! question "When should I use Tasks?"
    One thing to note is that the Pushe code is already run on a background thread, mainly the [CPU thread](/guide/threads#cpu-thread). If your purpose is to simply have a code run in the background, there is usually no need to use Tasks. 

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
To create a new task, subclass the `PusheTask` class and override the `perform()` method. 

```kotlin
class AwesomeTask : PusheTask("Awesome Task Name") {
    fun perform(): Single<Result> {
        // Perform work
        return Single.just(Result.SUCCESS)
    }
}
```

Each time the task is to be run, the `perform()` method will be called on the CPU thread. All task related operations should be done in the `perform()` method and it should return a `Single<Result>` value indicating whether the operation was successful or not.

The `Result` enum is the same class provided by Android WorkManager. It accepts the following values:

**SUCCESS**: Indicates that the task was performed successfuly

**RETRY**: Indicates that the task was failed (e.g., due to network failure) and should be retried with an exponential back-off

**FAIL**: Indicated that the task failed and should not be retried

## Creating Task Options
In order to start a task, you will need an instance of the `OneTimeTaskOptions` interface. A good convention to follow is to add a singleton implementation of this interface inside the task class.

```kotlin
class AwesomeTask : PusheTask("My Task Name") {
    fun perform(): Single<Result> {
        // Perform work
        return Single.just(Result.SUCCESS)
    }

    object Options : OneTimeTaskOptions {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = AwesomeTask::class
        override fun taskId() = "AwesomeTask"
        override fun existingWorkPolicy() = ExistingWorkPolicy.KEEP
    }
}

```

The `OneTimeTaskOptions` interface has four methods out of which the `networkType()` and `task()` methods are mandatory and the other two are optional.

**networkType()** should return the type of network connectivity the task requires in order to run.

**task()** should return the class of the task this `OneTimeTaskOptions` implementation is for

**taskId()** should return a unique identifier for the task. If this method is not overriden, then scheduling the task multiple times will result in it being run the same amount of times. If it is overriden, scheduling the task multiple times will result in a behaviour defined by the `existingWorkPolicy()` method.

**existingWorkPolicy()** should return how to handle multiple schedules of the same task. `ExistingWorkPolicy.APPEND` means that scheduling the task multiple times will result in it being run the same amount of times (similar to when no `taskId()` is provided). `ExistingWorkPolicy.REPLACE` means that if the task is scheduled when a previous was is still pending, it should replace the previous task and `ExistingWorkPolicy.KEEP` means it should keep the old task.

!!! question "What's the difference?"
    The difference between `ExistingWorkPolicy.REPLACE` and `ExistingWorkPolicy.KEEP` is only apparent when [passing data to tasks](#passing-data-to-tasks). Also, these two values are only relevant if the `taskId()` method is also provided.


## Scheduling Tasks
Tasks are scheduled to be run using the `TaskScheduler.scheduleTask()` method. You will need an instance of the `TaskScheduler` which you can obtain using Dagger's dependency injection.

The `scheduleTask()` method accepts an instance of `OneTimeTaskOptions` which describes what task to run and how to schedule it.

```kotlin
taskScheduler.scheduleTask(AwesomeTask.Options)
```

## Passing Data to Tasks

Sometimes tasks need to be passed additional data in order to operate. The `TaskScheduler.scheduleTask()` method accepts a second argument of type `Data` which could be used to pass data to the task. Use the `taskDataOf()` function for creating an instance of `Data`.

```kotlin
val taskData = taskDataOf(
    "firstData" to "awesome stuff",
    "secondData" to "other stuff"
)

taskScheduler.scheduleTask(AwesomeTask.Options, taskData)
```

Use the `inputData` variable inside the your `PusheTask` subclass to retrieve the data given when running the task.

```kotlin
class AwesomeTask : PusheTask("Awesome Task Name") {
    fun perform(): Single<Result> {
        val data = inputData.getString("firstData")
        ...
        return Single.just(Result.SUCCESS)
    }
}

```