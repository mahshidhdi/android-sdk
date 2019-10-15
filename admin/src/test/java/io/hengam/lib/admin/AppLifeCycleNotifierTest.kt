package io.hengam.lib.admin

import android.support.v4.app.Fragment
import io.hengam.lib.admin.analytics.activities.MultipleFrameLayoutActivity
import io.hengam.lib.admin.analytics.activities.SimpleActivity2
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.AppLifecycleNotifier
import io.hengam.lib.analytics.goal.Funnel
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLifeCycleNotifierTest {
    private val context = RuntimeEnvironment.application

    private lateinit var appLifecycleNotifier: AppLifecycleNotifier
    private lateinit var appLifecycleListener: AppLifecycleListener

    private lateinit var frameLayoutActivityFragmentWithLayouts: Fragment
    private lateinit var frameLayoutActivityFragmentWithLayout2: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer12: Fragment
    private lateinit var frameLayoutActivityInnerFragmentA: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer22: Fragment

    private lateinit var inLayoutFragment: Fragment

    @Before
    fun init() {
        Funnel.activityFunnel = mutableListOf()
        Funnel.fragmentFunnel = mutableMapOf()

        appLifecycleListener = spyk(AppLifecycleListener())

        appLifecycleNotifier = spyk(AppLifecycleNotifier(
            appLifecycleListener
        ))

        context.registerActivityLifecycleCallbacks(appLifecycleNotifier)
    }

    @Test
    fun onResumeOfActivity_callbacksAreCalledInTheExpectedOrder() {
        val multipleFrameLayoutActivityController = Robolectric.buildActivity(MultipleFrameLayoutActivity::class.java).create().start().resume()
        val multipleFrameLayoutActivity = multipleFrameLayoutActivityController.get()
        initializeFragments(multipleFrameLayoutActivity)

        verify(exactly = 1) { appLifecycleNotifier.onActivityCreated(multipleFrameLayoutActivity, null) }
        verify(exactly = 1) { appLifecycleNotifier.onActivityResumed(multipleFrameLayoutActivity) }
        verify(exactly = 5) { appLifecycleNotifier.onFragmentResumed(any(), any()) }

        verifyOrder {
            appLifecycleListener.activityResumed(multipleFrameLayoutActivity)
            appLifecycleListener.fragmentResumed(frameLayoutActivityInnerFragmentA)
            appLifecycleListener.fragmentResumed(frameLayoutActivityInnerFragmentBContainer22)
            appLifecycleListener.fragmentResumed(frameLayoutActivityFragmentWithLayout2)
            appLifecycleListener.fragmentResumed(frameLayoutActivityInnerFragmentBContainer12)
            appLifecycleListener.fragmentResumed(frameLayoutActivityFragmentWithLayouts)
        }
    }

    @Test
    fun onResumeOfFragment_IgnoresInLayoutFragments() {
        val simpleActivity2Controller = Robolectric.buildActivity(SimpleActivity2::class.java).create().start().resume()
        val simpleActivity2 = simpleActivity2Controller.get()
        inLayoutFragment = simpleActivity2.supportFragmentManager.findFragmentById(R.id.fragmentContainer)!!

        verify(exactly = 1) { appLifecycleNotifier.onActivityCreated(simpleActivity2, null) }
        verify(exactly = 1) { appLifecycleNotifier.onActivityResumed(simpleActivity2) }
        verify(exactly = 1) { appLifecycleNotifier.onFragmentResumed(any(), any()) }

        verify(exactly = 1) { appLifecycleListener.activityResumed(simpleActivity2) }
        verify(exactly = 0) { appLifecycleListener.fragmentResumed(any()) }
    }

    private fun initializeFragments(multipleFrameLayoutActivity: MultipleFrameLayoutActivity) {
        frameLayoutActivityFragmentWithLayouts = multipleFrameLayoutActivity.supportFragmentManager.findFragmentById(R.id.flContainer)!!
        frameLayoutActivityFragmentWithLayout2 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer11)!!
        frameLayoutActivityInnerFragmentBContainer12 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer12)!!
        frameLayoutActivityInnerFragmentA = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer21)!!
        frameLayoutActivityInnerFragmentBContainer22 = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer22)!!
    }
}