package io.hengam.lib.admin

import android.support.v4.app.Fragment
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.admin.analytics.activities.*
import io.hengam.lib.admin.analytics.fragments.DuplicateFragment
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.analytics.goal.*
import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.analytics.utils.CurrentTimeGenerator
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.turnOffThreadAssertions
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLifeCycleListenerTest {
    private val sessionId = "some_id"

    private val moshi = HengamMoshi()
    private val context = RuntimeEnvironment.application
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val currentTimeGenerator: CurrentTimeGenerator = mockk(relaxed = true)
    private val hengamConfig: HengamConfig = mockk(relaxed = true)
    private val hengamLifecycle: HengamLifecycle = HengamLifecycle(context)
    private val taskScheduler: TaskScheduler = mockk(relaxed = true)
    private val goalFragmentNameExtractor = GoalFragmentObfuscatedNameExtractor(mockk(relaxed = true))


    private val sharedPreference = MockSharedPreference()
    private val storage = HengamStorage(moshi, sharedPreference)

    private lateinit var goalStore: GoalStore
    private lateinit var activityReachHandler: ActivityReachHandler
    private lateinit var fragmentReachHandler: FragmentReachHandler
    private lateinit var buttonClickHandler: ButtonClickHandler
    private lateinit var sessionFlowManager: SessionFlowManager
    private lateinit var goalProcessManager: GoalProcessManager

    private lateinit var appLifecycleListener: AppLifecycleListener

    private val cpuThread = mockCpuThread()

    private val frameLayoutActivityInnerFragmentAInfo = SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.InnerFragmentA",
            "fragmentFLContainer21",
            "MultipleFrameLayoutActivity"
    )
    private val frameLayoutActivityInnerFragmentBInfoContainer22 =
            SessionFragmentInfo(
                    "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
                    "fragmentFLContainer22",
                    "MultipleFrameLayoutActivity"
            )
    private val frameLayoutActivityFragmentWithLayout2Info =
            SessionFragmentInfo(
                    "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts2",
                    "fragmentFLContainer11",
                    "MultipleFrameLayoutActivity"
            )
    private val frameLayoutActivityInnerFragmentBInfoContainer12 =
            SessionFragmentInfo(
                    "io.hengam.lib.admin.analytics.fragments.InnerFragmentB",
                    "fragmentFLContainer12",
                    "MultipleFrameLayoutActivity"
            )
    private val frameLayoutActivityFragmentWithLayoutsInfo =
            SessionFragmentInfo(
                    "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts",
                    "flContainer",
                    "MultipleFrameLayoutActivity"
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
    private val fragmentActivityFragmentWithLayoutsInfo = SessionFragmentInfo(
            "io.hengam.lib.admin.analytics.fragments.FragmentWithLayouts",
            "flContainer",
            "FragmentActivity"
    )

    private lateinit var frameLayoutActivityFragmentWithLayouts: Fragment
    private lateinit var frameLayoutActivityFragmentWithLayout2: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer12: Fragment
    private lateinit var frameLayoutActivityInnerFragmentA: Fragment
    private lateinit var frameLayoutActivityInnerFragmentBContainer22: Fragment

    private val frameLayoutActivityInnerFragmentAFragmentContainer = FragmentContainer("MultipleFrameLayoutActivity", "fragmentFLContainer21", listOf("fragmentFLContainer11", "flContainer"))
    private val frameLayoutActivityInnerFragmentBContainer22FragmentContainer = FragmentContainer("MultipleFrameLayoutActivity", "fragmentFLContainer22", listOf("fragmentFLContainer11", "flContainer"))
    private val frameLayoutActivityFragmentWithLayout2FragmentContainer = FragmentContainer("MultipleFrameLayoutActivity", "fragmentFLContainer11", listOf("flContainer"))
    private val frameLayoutActivityInnerFragmentBContainer12FragmentContainer = FragmentContainer("MultipleFrameLayoutActivity", "fragmentFLContainer12", listOf("flContainer"))
    private val frameLayoutActivityFragmentWithLayoutsFragmentContainer = FragmentContainer("MultipleFrameLayoutActivity", "flContainer", listOf())

    @Before
    fun init() {
        Funnel.activityFunnel = mutableListOf()
        Funnel.fragmentFunnel = mutableMapOf()

        moshi.enhance { it.add(GoalFactory.build()) }

        turnOffThreadAssertions()

        goalStore = spyk(GoalStore(context, moshi, goalFragmentNameExtractor, storage))
        activityReachHandler = spyk(ActivityReachHandler(postOffice))
        fragmentReachHandler = spyk(FragmentReachHandler(postOffice))
        buttonClickHandler = spyk(ButtonClickHandler(postOffice))
        sessionFlowManager = spyk(SessionFlowManager(currentTimeGenerator, postOffice, hengamConfig, mockk(relaxed = true), storage))
        goalProcessManager = spyk(GoalProcessManager(
                activityReachHandler,
                fragmentReachHandler,
                buttonClickHandler,
                goalStore,
                moshi
        ))
        appLifecycleListener = spyk(AppLifecycleListener(
                goalProcessManager,
                sessionFlowManager,
                hengamLifecycle,
                taskScheduler,
                mockk(relaxed = true)
        ))
        context.registerActivityLifecycleCallbacks(appLifecycleListener)
        every { sessionFlowManager.sessionId } returns sessionId
    }

    @Test
    fun appLifeCycleListener_onResumeOfActivity_callbacksAreCalledAsTheyShould() {
        val multipleFrameLayoutActivityController = Robolectric
                .buildActivity(MultipleFrameLayoutActivity::class.java).create().start().resume()
        val multipleFrameLayoutActivity = multipleFrameLayoutActivityController.get()
        initializeFragments(multipleFrameLayoutActivity)

        verify(exactly = 1) { appLifecycleListener.onActivityCreated(multipleFrameLayoutActivity, null) }
        verify(exactly = 1) { appLifecycleListener.onActivityResumed(multipleFrameLayoutActivity) }
        verify(exactly = 5) { appLifecycleListener.onFragmentResumed(any(), any()) }

        verifyOrder {
            goalProcessManager.manageButtonClickGoals(
                    frameLayoutActivityInnerFragmentAInfo,
                    frameLayoutActivityInnerFragmentA,
                    sessionId
            )
            goalProcessManager.updateFragmentReachViewGoals(frameLayoutActivityInnerFragmentAInfo, frameLayoutActivityInnerFragmentA)

            goalProcessManager.manageButtonClickGoals(
                    frameLayoutActivityInnerFragmentBInfoContainer22,
                    frameLayoutActivityInnerFragmentBContainer22,
                    sessionId
            )
            goalProcessManager.updateFragmentReachViewGoals(frameLayoutActivityInnerFragmentBInfoContainer22, frameLayoutActivityInnerFragmentBContainer22)

            goalProcessManager.manageButtonClickGoals(
                    frameLayoutActivityFragmentWithLayout2Info,
                    frameLayoutActivityFragmentWithLayout2,
                    sessionId
            )
            goalProcessManager.updateFragmentReachViewGoals(frameLayoutActivityFragmentWithLayout2Info, frameLayoutActivityFragmentWithLayout2)

            goalProcessManager.manageButtonClickGoals(
                    frameLayoutActivityInnerFragmentBInfoContainer12,
                    frameLayoutActivityInnerFragmentBContainer12,
                    sessionId
            )
            goalProcessManager.updateFragmentReachViewGoals(frameLayoutActivityInnerFragmentBInfoContainer12, frameLayoutActivityInnerFragmentBContainer12)

            goalProcessManager.manageButtonClickGoals(
                    frameLayoutActivityFragmentWithLayoutsInfo,
                    frameLayoutActivityFragmentWithLayouts,
                    sessionId
            )
            goalProcessManager.updateFragmentReachViewGoals(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayouts)
        }

        verify(exactly = 0) { goalProcessManager.manageActivityReachGoals(multipleFrameLayoutActivity, sessionId) }
        verify(exactly = 0) { goalProcessManager.manageButtonClickGoals(multipleFrameLayoutActivity, sessionId) }
        verify(exactly = 0) { sessionFlowManager.updateSessionFlow("MultipleFrameLayoutActivity") }
        verify(exactly = 0) { goalProcessManager.handleFragmentReachMessage(any(), any(), sessionId) }
        verify(exactly = 0) { sessionFlowManager.updateSessionFlow(any(), any(), any()) }

        cpuThread.triggerActions()

        verify(exactly = 1) { goalProcessManager.manageActivityReachGoals(multipleFrameLayoutActivity, sessionId) }
        verify(exactly = 1) { goalProcessManager.manageButtonClickGoals(multipleFrameLayoutActivity, sessionId) }
        verify(exactly = 1) { sessionFlowManager.updateSessionFlow("MultipleFrameLayoutActivity") }

        verifyOrder {
            goalProcessManager.handleFragmentReachMessage(frameLayoutActivityInnerFragmentAInfo, frameLayoutActivityInnerFragmentAFragmentContainer, sessionId)
            sessionFlowManager.updateSessionFlow(
                    frameLayoutActivityInnerFragmentAInfo,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayout2Info),
                    false)
            goalProcessManager.handleFragmentReachMessage(frameLayoutActivityInnerFragmentBInfoContainer22, frameLayoutActivityInnerFragmentBContainer22FragmentContainer, sessionId)
            sessionFlowManager.updateSessionFlow(
                    frameLayoutActivityInnerFragmentBInfoContainer22,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayout2Info),
                    false
            )
            goalProcessManager.handleFragmentReachMessage(frameLayoutActivityFragmentWithLayout2Info, frameLayoutActivityFragmentWithLayout2FragmentContainer, sessionId)
            sessionFlowManager.updateSessionFlow(
                    frameLayoutActivityFragmentWithLayout2Info,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo),
                    true
            )
            goalProcessManager.handleFragmentReachMessage(frameLayoutActivityInnerFragmentBInfoContainer12, frameLayoutActivityInnerFragmentBContainer12FragmentContainer, sessionId)
            sessionFlowManager.updateSessionFlow(
                    frameLayoutActivityInnerFragmentBInfoContainer12,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo),
                    false
            )
            goalProcessManager.handleFragmentReachMessage(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayoutsFragmentContainer, sessionId)
            sessionFlowManager.updateSessionFlow(
                    frameLayoutActivityFragmentWithLayoutsInfo,
                    listOf(),
                    true
            )
        }
    }

    @Test
    fun appLifeCycleListener_onPauseOfActivity_callbacksAreCalledAsTheyShould() {
        val multipleFrameLayoutActivityController = Robolectric
                .buildActivity(MultipleFrameLayoutActivity::class.java).create().start().resume().pause()
        val multipleFrameLayoutActivity = multipleFrameLayoutActivityController.get()
        initializeFragments(multipleFrameLayoutActivity)

        verify(exactly = 1) { appLifecycleListener.onActivityPaused(multipleFrameLayoutActivity) }
        verify(exactly = 5) { appLifecycleListener.onFragmentPaused(any(), any()) }

        verifyOrder {
            goalProcessManager.updateFragmentViewGoals(
                    frameLayoutActivityInnerFragmentAInfo,
                    frameLayoutActivityInnerFragmentA
            )
            goalProcessManager.updateFragmentViewGoals(
                    frameLayoutActivityInnerFragmentBInfoContainer22,
                    frameLayoutActivityInnerFragmentBContainer22
            )
            goalProcessManager.updateFragmentViewGoals(
                    frameLayoutActivityFragmentWithLayout2Info,
                    frameLayoutActivityFragmentWithLayout2
            )
            goalProcessManager.updateFragmentViewGoals(
                    frameLayoutActivityInnerFragmentBInfoContainer12,
                    frameLayoutActivityInnerFragmentBContainer12
            )
            goalProcessManager.updateFragmentViewGoals(
                    frameLayoutActivityFragmentWithLayoutsInfo,
                    frameLayoutActivityFragmentWithLayouts
            )
        }
        verify(exactly = 0) { goalProcessManager.updateActivityViewGoals(multipleFrameLayoutActivity) }
        verify(exactly = 0) { sessionFlowManager.updateFragmentDuration(any(), any()) }

        cpuThread.triggerActions()

        verify(exactly = 1) { goalProcessManager.updateActivityViewGoals(multipleFrameLayoutActivity) }

        verifyOrder {
            sessionFlowManager.updateFragmentDuration(
                    frameLayoutActivityInnerFragmentAInfo,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayout2Info)
            )
            sessionFlowManager.updateFragmentDuration(
                    frameLayoutActivityInnerFragmentBInfoContainer22,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo, frameLayoutActivityFragmentWithLayout2Info)
            )
            sessionFlowManager.updateFragmentDuration(
                    frameLayoutActivityFragmentWithLayout2Info,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo)
            )
            sessionFlowManager.updateFragmentDuration(
                    frameLayoutActivityInnerFragmentBInfoContainer12,
                    listOf(frameLayoutActivityFragmentWithLayoutsInfo)
            )
            sessionFlowManager.updateFragmentDuration(
                    frameLayoutActivityFragmentWithLayoutsInfo,
                    listOf()
            )
        }
    }

    @Test
    fun onActivityResumed_updatesFunnelIfNewActivity() {
        assert(Funnel.activityFunnel.isEmpty())

        every { currentTimeGenerator.getCurrentTime() } returns 100
        val simpleActivityController = Robolectric
                .buildActivity(SimpleActivity::class.java).create().start().resume()
        cpuThread.triggerActions()
        assertEquals(1, Funnel.activityFunnel.size)
        assert(Funnel.activityFunnel.contains("SimpleActivity"))

        every { currentTimeGenerator.getCurrentTime() } returns 200
        simpleActivityController.pause().stop()

        val simpleActivity2Controller = Robolectric
                .buildActivity(SimpleActivity2::class.java).create().start().resume()
        cpuThread.triggerActions()
        assertEquals(2, Funnel.activityFunnel.size)
        assertEquals("SimpleActivity2", Funnel.activityFunnel.last())

        every { currentTimeGenerator.getCurrentTime() } returns 300
        simpleActivity2Controller.pause().stop().start().resume()
        cpuThread.triggerActions()
        assertEquals(2, Funnel.activityFunnel.size)
        assertEquals("SimpleActivity2", Funnel.activityFunnel.last())
    }

    @Test
    fun onFragmentResumed_updatesFunnelIfNewFragment() {
        assert(Funnel.fragmentFunnel.isEmpty())

        every { currentTimeGenerator.getCurrentTime() } returns 100
        val duplicateFragmentActivityController = Robolectric
                .buildActivity(DuplicateFragmentActivity::class.java).create().start().resume()
        cpuThread.triggerActions()
        val innerFragmentContainer = FragmentContainer("DuplicateFragmentActivity", "activityFragmentContainer", listOf("activityFragmentContainer2"))
        assertEquals(3, Funnel.fragmentFunnel.size)
        assertEquals(1, Funnel.fragmentFunnel[innerFragmentContainer]!!.size)

        val fragmentManager = duplicateFragmentActivityController.get().supportFragmentManager.findFragmentById(R.id.activityFragmentContainer2)!!.childFragmentManager
        fragmentManager
                .beginTransaction()
                .replace(R.id.activityFragmentContainer, DuplicateFragment())
                .addToBackStack(null)
                .commit()
        cpuThread.triggerActions()
        assertEquals(3, Funnel.fragmentFunnel.size)
        assertEquals(2, Funnel.fragmentFunnel[innerFragmentContainer]!!.size)

        // same fragment as last
        fragmentManager
                .beginTransaction()
                .replace(R.id.activityFragmentContainer, DuplicateFragment())
                .addToBackStack(null)
                .commit()
        cpuThread.triggerActions()
        assertEquals(3, Funnel.fragmentFunnel.size)
        assertEquals(2, Funnel.fragmentFunnel[innerFragmentContainer]!!.size)

    }

    @Test
    fun onActivityResumed_checksForTheActivityToBeANewOneBeforeCallingManagerGoalReachHandler() {
        val simpleActivityController = Robolectric
                .buildActivity(SimpleActivity::class.java).create().start().resume()
        cpuThread.triggerActions()
        val simpleActivity = simpleActivityController.get()
        verifyOrder {
            goalProcessManager.manageActivityReachGoals(simpleActivity, sessionId)
            goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
            sessionFlowManager.updateSessionFlow("SimpleActivity")
        }

        simpleActivityController.pause()
        cpuThread.triggerActions()
        verify(exactly = 1) { goalProcessManager.updateActivityViewGoals(simpleActivity) }
        verify(exactly = 1) { sessionFlowManager.updateActivityDuration("SimpleActivity") }

        simpleActivityController.resume()
        cpuThread.triggerActions()
        verify(exactly = 2) { appLifecycleListener.onActivityResumed(simpleActivity) }
        verify(exactly = 2) { goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId) }
        verify(exactly = 2) { sessionFlowManager.updateSessionFlow("SimpleActivity") }
        verify(exactly = 1) { goalProcessManager.manageActivityReachGoals(simpleActivity, sessionId) }

    }

    @Test
    fun onFragmentResumed_checksForTheFragmentToBeANewOneBeforeCallingManagerToHandleFragmentReachGoal() {
        val multipleFrameLayoutActivityController = Robolectric
                .buildActivity(MultipleFrameLayoutActivity::class.java).create().start().resume()
        cpuThread.triggerActions()
        val multipleFrameLayoutActivity = multipleFrameLayoutActivityController.get()
        verify(exactly = 5) {
            goalProcessManager.updateFragmentReachViewGoals(any(), any())
            goalProcessManager.handleFragmentReachMessage(any(), any(), sessionId)
            goalProcessManager.manageButtonClickGoals(any(), any(), sessionId)
            sessionFlowManager.updateSessionFlow(any(), any(), any())
        }

        multipleFrameLayoutActivityController.pause()
        cpuThread.triggerActions()
        verify(exactly = 5) { goalProcessManager.updateFragmentViewGoals(any(), any()) }
        verify(exactly = 5) { sessionFlowManager.updateFragmentDuration(any(), any()) }

        multipleFrameLayoutActivityController.resume()
        cpuThread.triggerActions()
        verify(exactly = 10) { appLifecycleListener.onFragmentResumed(multipleFrameLayoutActivity.supportFragmentManager, any()) }
        verify(exactly = 10) { goalProcessManager.manageButtonClickGoals(any(), any(), sessionId) }
        verify(exactly = 10) { sessionFlowManager.updateSessionFlow(any(), any(), any()) }
        verify(exactly = 10) { goalProcessManager.updateFragmentReachViewGoals(any(), any()) }
        verify(exactly = 5) { goalProcessManager.handleFragmentReachMessage(any(), any(), sessionId) }
    }

    @Test
    fun onFragmentResumed_fragmentFunnelsAreDistinguishedByParentFragmentsAsWell() {
        Robolectric.buildActivity(DuplicateFragmentActivity::class.java).create().start().resume()
        cpuThread.triggerActions()

        verify(exactly = 3) {
            goalProcessManager.updateFragmentReachViewGoals(any(), any())
            goalProcessManager.handleFragmentReachMessage(any(), any(), sessionId)
            goalProcessManager.manageButtonClickGoals(any(), any(), sessionId)
            sessionFlowManager.updateSessionFlow(any(), any(), any())
        }
        assertEquals(3, Funnel.fragmentFunnel.size)
    }

//    @Test
//    fun onActivityResumed_startsNewSessionWhenPauseTimeIsLongEnough() {
//        every { hengamConfig.getLong(Config.SESSION_PAUSE_LIMIT, any()) } returns 20000L
//
//        every { currentTimeGenerator.getCurrentTime() } returns 50000
//        val multipleFrameLayoutActivityController = Robolectric
//            .buildActivity(MultipleFrameLayoutActivity::class.java).create().start().resume()
//        cpuThread.triggerActions()
//        verify(exactly = 1) { sessionFlowManager.endSession() }
//
//        every { currentTimeGenerator.getCurrentTime() } returns 100000
//        multipleFrameLayoutActivityController.pause()
//        cpuThread.triggerActions()
//        every { currentTimeGenerator.getCurrentTime() } returns 110000
//        multipleFrameLayoutActivityController.resume()
//        cpuThread.triggerActions()
//        verify(exactly = 1) { sessionFlowManager.endSession() }
//
//        every { currentTimeGenerator.getCurrentTime() } returns 150000
//        multipleFrameLayoutActivityController.pause()
//        cpuThread.triggerActions()
//        every { currentTimeGenerator.getCurrentTime() } returns 180000
//        multipleFrameLayoutActivityController.resume()
//        cpuThread.triggerActions()
//        verify(exactly = 2) { sessionFlowManager.endSession() }
//    }

    @Test
    fun staticFragmentsShouldBeIgnored() {
        val fragmentActivityController = Robolectric
                .buildActivity(FragmentActivity::class.java).create().start().resume()
        cpuThread.triggerActions()
        val fragmentActivity = fragmentActivityController.get()
        verify(exactly = 1) { appLifecycleListener.onActivityResumed(fragmentActivity) }
        verify(exactly = 1) { goalProcessManager.manageActivityReachGoals(fragmentActivity, sessionId) }
        verify(exactly = 1) { goalProcessManager.manageButtonClickGoals(fragmentActivity, sessionId) }
        verify(exactly = 1) { sessionFlowManager.updateSessionFlow("FragmentActivity") }
        // there are 6 fragments in the activity
        verify(exactly = 6) { appLifecycleListener.onFragmentResumed(any(), any()) }
        // two of the fragments are static; they should be ignored
        verify(exactly = 4) {
            goalProcessManager.updateFragmentReachViewGoals(any(), any())
            goalProcessManager.handleFragmentReachMessage(any(), any(), sessionId)
            goalProcessManager.manageButtonClickGoals(any(), any(), sessionId)
            sessionFlowManager.updateSessionFlow(any(), any(), any())
        }

        // the static fragments should be ignored in the parent-fragments list as well
        verifyOrder {
            goalProcessManager.manageButtonClickGoals(
                    fragmentActivityInnerFragmentAInfo,
                    any(), sessionId
            )
            goalProcessManager.manageButtonClickGoals(
                    fragmentActivityInnerFragmentBInfoContainer22,
                    any(), sessionId
            )
            goalProcessManager.manageButtonClickGoals(
                    fragmentActivityFragmentWithLayout2Info,
                    any(), sessionId
            )
            goalProcessManager.manageButtonClickGoals(
                    fragmentActivityInnerFragmentBInfoContainer12,
                    any(), sessionId
            )
            sessionFlowManager.updateSessionFlow(
                    fragmentActivityInnerFragmentAInfo,
                    listOf(fragmentActivityFragmentWithLayout2Info),
                    false
            )
            sessionFlowManager.updateSessionFlow(
                    fragmentActivityInnerFragmentBInfoContainer22,
                    listOf(fragmentActivityFragmentWithLayout2Info),
                    false
            )
            sessionFlowManager.updateSessionFlow(
                    fragmentActivityFragmentWithLayout2Info,
                    listOf(),
                    true
            )
            sessionFlowManager.updateSessionFlow(
                    fragmentActivityInnerFragmentBInfoContainer12,
                    listOf(),
                    false
            )
        }
    }

    private fun initializeFragments(multipleFrameLayoutActivity: MultipleFrameLayoutActivity) {
        frameLayoutActivityFragmentWithLayouts = multipleFrameLayoutActivity.supportFragmentManager.findFragmentById(R.id.flContainer)!!
        frameLayoutActivityFragmentWithLayout2 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer11)!!
        frameLayoutActivityInnerFragmentBContainer12 = frameLayoutActivityFragmentWithLayouts.childFragmentManager.findFragmentById(R.id.fragmentFLContainer12)!!
        frameLayoutActivityInnerFragmentA = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer21)!!
        frameLayoutActivityInnerFragmentBContainer22 = frameLayoutActivityFragmentWithLayout2.childFragmentManager.findFragmentById(R.id.fragmentFLContainer22)!!
    }
}