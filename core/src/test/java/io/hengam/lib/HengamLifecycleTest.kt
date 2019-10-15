package io.hengam.lib

import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.mockk.mockk
import io.reactivex.schedulers.TestScheduler
import org.junit.Test

class HengamLifecycleTest {
    private val hengamLifecycle = HengamLifecycle(mockk(relaxed = true))

    val cpuThread = mockCpuThread()

    @Test
    fun waitForPreInit_CompletesWhenPreInitFinished() {
        val subscription = hengamLifecycle.waitForPreInit().test()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        hengamLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
        hengamLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }

    @Test
    fun waitForPreInit_SupportsMultipleObservers() {
        val subscriptions = (1..15).map { hengamLifecycle.waitForPreInit().test() }
        cpuThread.triggerActions()
        subscriptions.forEach { it.assertNotTerminated() }
        hengamLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscriptions.forEach { it.assertComplete() }
    }

    @Test
    fun waitForPreInit_CompletesImmediatelyIfPreInitAlreadyFinished() {
        hengamLifecycle.preInitComplete()
        val s = hengamLifecycle.waitForPreInit().test()
        cpuThread.triggerActions()
        s.assertComplete()
    }

    @Test
    fun waitForPostInit_CompletesWhenPostInitFinishesAndNotPreInit() {
        val subscription = hengamLifecycle.waitForPostInit().test()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        hengamLifecycle.preInitComplete()
        cpuThread.triggerActions()
        subscription.assertNotTerminated()
        hengamLifecycle.postInitComplete()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }


    @Test
    fun waitForRegistration_CompletesWhenRegistered() {
        val scheduler = TestScheduler()
        val subscription = hengamLifecycle.waitForRegistration().subscribeOn(scheduler).test()
        subscription.assertNotTerminated()
        hengamLifecycle.registrationComplete()
        scheduler.triggerActions()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }

    @Test
    fun waitForRegistration_CompletesImmediatelyIfAlreadyRegistered() {
        hengamLifecycle.registrationComplete()
        cpuThread.triggerActions()
        val subscription = hengamLifecycle.waitForRegistration().test()
        cpuThread.triggerActions()
        subscription.assertComplete()
    }
}