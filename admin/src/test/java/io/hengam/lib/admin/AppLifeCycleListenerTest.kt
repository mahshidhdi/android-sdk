package io.hengam.lib.admin

import android.app.Activity
import android.support.v4.app.Fragment
import io.hengam.lib.admin.analytics.activities.MultipleFrameLayoutActivity
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.analytics.goal.Funnel
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLifeCycleListenerTest {

    private lateinit var appLifecycleListener: AppLifecycleListener

    private val cpuThread = mockCpuThread()

    private lateinit var frameLayoutActivityFragmentWithLayouts: Fragment
    private lateinit var frameLayoutActivityFragmentWithLayout2: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer12: Fragment
    private lateinit var frameLayoutActivityInnerFragmentA: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer22: Fragment

    @Before
    fun init() {
        Funnel.activityFunnel = mutableListOf()
        Funnel.fragmentFunnel = mutableMapOf()

        appLifecycleListener = AppLifecycleListener()
    }

    @Test
    fun emitsItemsOnThrottlers() {
        val multipleFrameLayoutActivity = Robolectric.setupActivity(MultipleFrameLayoutActivity::class.java)
        initializeFragments(multipleFrameLayoutActivity)

        val activityResumeSubscription = appLifecycleListener.onActivityResumed().test()
        val newActivityResumeSubscription = appLifecycleListener.onNewActivity().test()
        val activityPausedSubscription = appLifecycleListener.onActivityPaused().test()

        val fragmentResumeSubscription = appLifecycleListener.onFragmentResumed().test()
        val newFragmentResumeSubscription = appLifecycleListener.onNewFragment().test()
        val fragmentPausedSubscription = appLifecycleListener.onFragmentPaused().test()

        resumeActivity(multipleFrameLayoutActivity)
        updateFunnel(multipleFrameLayoutActivity)
        resumeActivity(multipleFrameLayoutActivity)

        resumeFragment(frameLayoutActivityInnerFragmentA)
        updateFunnel(frameLayoutActivityInnerFragmentAInfo)
        resumeFragment(frameLayoutActivityInnerFragmentBContainer22)
        updateFunnel(frameLayoutActivityInnerFragmentBInfoContainer22)
        resumeFragment(frameLayoutActivityInnerFragmentBContainer22)
        resumeFragment(frameLayoutActivityFragmentWithLayout2)
        updateFunnel(frameLayoutActivityFragmentWithLayout2Info)

        pauseFragment(frameLayoutActivityInnerFragmentA)
        pauseFragment(frameLayoutActivityInnerFragmentBContainer22)
        pauseFragment(frameLayoutActivityInnerFragmentBContainer22)
        pauseFragment(frameLayoutActivityFragmentWithLayout2)

        pauseActivity(multipleFrameLayoutActivity)
        pauseActivity(multipleFrameLayoutActivity)

        activityResumeSubscription.assertValues(multipleFrameLayoutActivity, multipleFrameLayoutActivity)
        newActivityResumeSubscription.assertValues(multipleFrameLayoutActivity)

        fragmentResumeSubscription.assertValues(
            frameLayoutActivityInnerFragmentAInfo to frameLayoutActivityInnerFragmentA,
            frameLayoutActivityInnerFragmentBInfoContainer22 to frameLayoutActivityInnerFragmentBContainer22,
            frameLayoutActivityInnerFragmentBInfoContainer22 to frameLayoutActivityInnerFragmentBContainer22,
            frameLayoutActivityFragmentWithLayout2Info to frameLayoutActivityFragmentWithLayout2
        )

        newFragmentResumeSubscription.assertValues(
            frameLayoutActivityInnerFragmentAInfo to frameLayoutActivityInnerFragmentA,
            frameLayoutActivityInnerFragmentBInfoContainer22 to frameLayoutActivityInnerFragmentBContainer22,
            frameLayoutActivityFragmentWithLayout2Info to frameLayoutActivityFragmentWithLayout2
        )

        fragmentPausedSubscription.assertValues(
            frameLayoutActivityInnerFragmentAInfo to frameLayoutActivityInnerFragmentA,
            frameLayoutActivityInnerFragmentBInfoContainer22 to frameLayoutActivityInnerFragmentBContainer22,
            frameLayoutActivityInnerFragmentBInfoContainer22 to frameLayoutActivityInnerFragmentBContainer22,
            frameLayoutActivityFragmentWithLayout2Info to frameLayoutActivityFragmentWithLayout2
        )

        activityPausedSubscription.assertValues(multipleFrameLayoutActivity, multipleFrameLayoutActivity)
    }

    private fun resumeActivity(activity: Activity) {
        appLifecycleListener.activityResumed(activity)
        cpuThread.triggerActions()
    }
    private fun resumeFragment(fragment: Fragment) {
        appLifecycleListener.fragmentResumed(fragment)
    }
    private fun pauseActivity(activity: Activity) {
        appLifecycleListener.activityPaused(activity)
        cpuThread.triggerActions()
    }
    private fun pauseFragment(fragment: Fragment) {
        appLifecycleListener.fragmentPaused(fragment)
    }

    private fun updateFunnel(activity: Activity) {
        Funnel.activityFunnel.add(activity.javaClass.simpleName)
    }

    private fun updateFunnel(fragmentInfo: SessionFragmentInfo) {
        Funnel.fragmentFunnel[fragmentInfo.containerId]?.add(fragmentInfo.fragmentName)
            ?: Funnel.fragmentFunnel.put(fragmentInfo.containerId, mutableListOf(fragmentInfo.fragmentName))
    }

    private fun initializeFragments(multipleFrameLayoutActivity: MultipleFrameLayoutActivity) {
        frameLayoutActivityFragmentWithLayouts = multipleFrameLayoutActivity.supportFragmentManager.findFragmentById(R.id.flContainer)!!
        frameLayoutActivityFragmentWithLayout2 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer11)!!
        frameLayoutActivityInnerFragmentBContainer12 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer12)!!
        frameLayoutActivityInnerFragmentA = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer21)!!
        frameLayoutActivityInnerFragmentBContainer22 = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer22)!!
    }

    private val frameLayoutActivityFragmentWithLayoutsInfo =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts",
            "flContainer",
            "MultipleFrameLayoutActivity",
            null
        )

    private val frameLayoutActivityFragmentWithLayout2Info =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts2",
            "fragmentFLContainer11",
            "MultipleFrameLayoutActivity",
            frameLayoutActivityFragmentWithLayoutsInfo
        )

    private val frameLayoutActivityInnerFragmentBInfoContainer12 =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
            "fragmentFLContainer12",
            "MultipleFrameLayoutActivity",
            frameLayoutActivityFragmentWithLayoutsInfo
        )

    private val frameLayoutActivityInnerFragmentAInfo = SessionFragmentInfo(
        "io.hengam.lib.admin.analytics.fragments.InnerFragmentA",
        "fragmentFLContainer21",
        "MultipleFrameLayoutActivity",
        frameLayoutActivityFragmentWithLayout2Info
    )
    private val frameLayoutActivityInnerFragmentBInfoContainer22 =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
            "fragmentFLContainer22",
            "MultipleFrameLayoutActivity",
            frameLayoutActivityFragmentWithLayout2Info
        )

    private val fragmentActivityInnerFragmentAInfo = SessionFragmentInfo(
        "io.hengam.lib.admin.analytics.fragments.InnerFragmentA",
        "fragmentFLContainer21",
        "FragmentActivity"
    )
    private val fragmentActivityInnerFragmentBInfoContainer22 =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
            "fragmentFLContainer22",
            "FragmentActivity"
        )
    private val fragmentActivityFragmentWithLayout2Info = SessionFragmentInfo(
        "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts2",
        "fragmentFLContainer11",
        "FragmentActivity"
    )
    private val fragmentActivityInnerFragmentBInfoContainer12 =
        SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
            "fragmentFLContainer12",
            "FragmentActivity"
        )
}