package co.pushe.plus.internal

import co.pushe.plus.utils.ExceptionCatcher
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.rx.RxAndroid
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


object PusheSchedulers {
    val cpuExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * The Pushe Main thread. Almost anything code running related to Pushe should be executed
     * on this thread. Each application is guaranteed to only have one CPU thread running at
     * any given time, so any code which is running on the CPU thread does not need to perform
     * concurrency checks and synchronization.
     */
    val cpu: Scheduler = PScheduler("computation thread", Schedulers.from(cpuExecutor))

    /**
     * The Pushe IO thread. This thread should be used for tasks in the Pushe code which perform
     * blocking IO operations (e.g, downloading notification images) in order to prevent blocking
     * the CPU thread for such tasks. Note however, code running in the IO thread should not
     * access any data structures. Instead after the IO operation has completed, it should schedule
     * the rest of the code to be run on the CPU thread.
     */
    val io: Scheduler = PScheduler("io thread", Schedulers.from(Executors.newFixedThreadPool(2)))

    /**
     * The Android Main thread. Instead of assigning this directly we will use a getter
     * to obtain the scheduler through a `AndroidSchedulers.mainThread()`. This is because
     * the `AndroidSchedulers.mainThread()` has a chance of throwing a `NullPointerException`
     * in rare cases, and we should avoid allowing that exception to propagate on Application start.
     */
    val ui: Scheduler get() = PScheduler("ui thread", RxAndroid.mainThread())
}

fun ioThread() = PusheSchedulers.io
fun cpuThread() = PusheSchedulers.cpu
fun uiThread() = PusheSchedulers.ui

fun ioThread(f: () -> Unit) = PusheSchedulers.io.scheduleDirect(f)
fun cpuThread(f: () -> Unit) = PusheSchedulers.cpu.scheduleDirect(f)
fun uiThread(f: () -> Unit) = PusheSchedulers.ui.scheduleDirect(f)
fun ioThread(delay: Time, f: () -> Unit) =
        PusheSchedulers.io.scheduleDirect(f, delay.toMillis(), TimeUnit.MILLISECONDS)
fun cpuThread(delay: Time, f: () -> Unit) =
        PusheSchedulers.cpu.scheduleDirect(f, delay.toMillis(), TimeUnit.MILLISECONDS)
fun uiThread(delay: Time, f: () -> Unit)
        = PusheSchedulers.ui.scheduleDirect(f, delay.toMillis(), TimeUnit.MILLISECONDS)


class PScheduler(private val name: String, private val scheduler: Scheduler) : Scheduler() {
    override fun createWorker(): Worker = PWorker(name, scheduler.createWorker())

    fun scheduleDirect(f: () -> Unit) =  scheduler.scheduleDirect(f)

    fun scheduleDirect(f: () -> Unit, delay: Long, unit: TimeUnit) = scheduler.scheduleDirect(f, delay, unit)

    fun scheduleDirect(f: () -> Unit, delay: Time) = scheduleDirect(f, delay.toMillis(), TimeUnit.MILLISECONDS)
}

class PWorker(private val name: String, private val worker: Scheduler.Worker) : Scheduler.Worker() {
    override fun isDisposed(): Boolean = worker.isDisposed

    override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable {
        return worker.schedule({
            ExceptionCatcher.catchAllUnhandledErrors(name) { run.run() }
        }, delay, unit)
    }

    override fun dispose() = worker.dispose()

}