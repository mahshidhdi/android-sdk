package co.pushe.plus

import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import io.mockk.mockk
import io.reactivex.schedulers.TestScheduler
import org.junit.Test

class PusheLifecycleTest {
    private val pusheLifecycle = PusheLifecycle(mockk(relaxed = true))

    val cpuThread = mockCpuThread()

    @Test
    fun waitForPreInit_CompletesWhenPreInitFinished() {
        val subscription = pusheLifecycle.waitForPreInit().test()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        pusheLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
        pusheLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }

    @Test
    fun waitForPreInit_SupportsMultipleObservers() {
        val subscriptions = (1..15).map { pusheLifecycle.waitForPreInit().test() }
        cpuThread.triggerActions()
        subscriptions.forEach { it.assertNotTerminated() }
        pusheLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscriptions.forEach { it.assertComplete() }
    }

    @Test
    fun waitForPreInit_CompletesImmediatelyIfPreInitAlreadyFinished() {
        pusheLifecycle.preInitComplete()
        val s = pusheLifecycle.waitForPreInit().test()
        cpuThread.triggerActions()
        s.assertComplete()
    }

    @Test
    fun waitForPostInit_CompletesWhenPostInitFinishesAndNotPreInit() {
        val subscription = pusheLifecycle.waitForPostInit().test()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        pusheLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        pusheLifecycle.postInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }


    @Test
    fun waitForRegistration_CompletesWhenRegistered() {
        val scheduler = TestScheduler()
        val subscription = pusheLifecycle.waitForRegistration().subscribeOn(scheduler).test()
        subscription.assertNotTerminated()
        pusheLifecycle.registrationComplete()
        scheduler.triggerActions()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }

    @Test
    fun waitForRegistration_CompletesImmediatelyIfAlreadyRegistered() {
        pusheLifecycle.registrationComplete()
        cpuThread.triggerActions()
        val subscription = pusheLifecycle.waitForRegistration().test()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }
}