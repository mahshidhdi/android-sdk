# Threads

Hengam code should not be executed on the Main Thread unless absolutely necessary. Running code on the Main Thread could cause the following two problems:

1. Executing on the Main Threads slows down the hosting application
2. Any unhandled exceptions which occur in the Main Thread will result in an [ANR](https://developer.android.com/topic/performance/vitals/anr)

Instead, Hengam code should be run on one of it's own dedicated threads. Hengam defines two types of threads which code could be executed on:


#### CPU Thread
The CPU thread is basically the main thread of Hengam. Any code which is not an IO task should be run on the CPU thread. There is only one active CPU thread at any given time. As long as you make sure that you only access your data sctructures on the CPU thread, you can avoid doing concurrency checks and synchronizations.

There are two ways to run code on the CPU thread:

1. When using RxJava, you could call `subscribeOn` or `observeOn` on an IO thread
    ```kotlin
    receiveMessage()
        .observeOn(cpuThread())
        .subscribe {
            println("This is running on the CPU thread")
        }
    ```

2. You can schedule a code to be run on an IO thread directly by calling `cpuThread()` and passing a lambda as an argument
   ```kotlin
    cpuThread {
        println("This is running on the CPU thread")
    }
   ```


!!! tip "Don't create your own threads"
    Avoid creating your own threads, stick to using only the defined IO and CPU threads.

    If you need to run code asynchronously, you simply need to call the `cpuThread()` method
    ```kotlin
    cpuThread { ayncCode() }
    ```




#### IO Thread
The IO threads should be used for blocking or long running IO tasks, such as performing manual network requests or reading from files. There may be multiple IO threads running in the application at the same time.

There are two ways to run code on an IO thread:

1. When using RxJava, you could call `subscribeOn` or `observeOn` on an IO thread
   ```kotlin
    readFromFile()
        .subscribeOn(ioThread())
        .subscribe {
            println("Read the file using IO thread")
        }
   ```

2. You can schedule a code to be run on an IO thread directly by calling `ioThread()` and passing a lambda as an argument
   ```kotlin
    ioThread {
        println("This is running on the IO thread")
    }
   ```
