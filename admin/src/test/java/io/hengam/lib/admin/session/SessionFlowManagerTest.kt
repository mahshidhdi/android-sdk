package io.hengam.lib.admin.session

import android.app.Activity
import android.content.SharedPreferences
import android.support.v4.app.Fragment
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.admin.analytics.activities.SimpleActivity
import io.hengam.lib.admin.analytics.activities.SimpleActivity2
import io.hengam.lib.analytics.*
import io.hengam.lib.analytics.goal.Funnel
import io.hengam.lib.analytics.session.SessionActivity
import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.analytics.session.SessionFragment
import io.hengam.lib.analytics.session.SessionIdProvider
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.analytics.utils.CurrentTimeGenerator
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.PublishRelay
import io.hengam.lib.utils.test.TestUtils
import io.hengam.lib.utils.test.TestUtils.turnOffThreadAssertions
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionFlowManagerTest {
    private lateinit var sessionFlowManager: SessionFlowManager

    private val cpuThread = TestUtils.mockCpuThread()

    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val currentTimeGenerator: CurrentTimeGenerator = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val hengamConfig: HengamConfig = mockk(relaxed = true)
    private val hengamLifecycle: HengamLifecycle = mockk(relaxed = true)
    private val taskScheduler: TaskScheduler = mockk(relaxed = true)
    private val appLifecycleListener: AppLifecycleListener = mockk(relaxed = true)
    private val sessionIdProvider: SessionIdProvider = mockk(relaxed = true)
    private val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
    private val moshi = HengamMoshi()
    private val storage = HengamStorage(moshi, sharedPreferences)

    private val activityResumeThrottler = PublishRelay.create<Activity>()
    private val newActivityResumeThrottler = PublishRelay.create<Activity>()
    private val fragmentResumeThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()
    private val newFragmentResumeThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()
    private val activityPauseThrottler = PublishRelay.create<Activity>()
    private val fragmentPauseThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()

    private val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
    private val simpleActivity2 = Robolectric.setupActivity(SimpleActivity2::class.java)

    @Before
    fun setUp() {

        mockkObject(HengamInternals)

        val coreComponent: CoreComponent = mockk(relaxed = true)
        every { HengamInternals.getComponent(CoreComponent::class.java) } returns coreComponent
        every { coreComponent.config() } returns hengamConfig

        sessionFlowManager = SessionFlowManager(
            currentTimeGenerator,
            postOffice,
            hengamConfig,
            hengamLifecycle,
            taskScheduler,
            appLifecycleListener,
            sessionIdProvider,
            applicationInfoHelper,
            storage
        )

        every { appLifecycleListener.onActivityResumed() } returns activityResumeThrottler
        every { appLifecycleListener.onNewActivity() } returns newActivityResumeThrottler
        every { appLifecycleListener.onFragmentResumed() } returns fragmentResumeThrottler
        every { appLifecycleListener.onNewFragment() } returns newFragmentResumeThrottler
        every { appLifecycleListener.onActivityPaused() } returns activityPauseThrottler
        every { appLifecycleListener.onFragmentPaused() } returns fragmentPauseThrottler

        every { hengamConfig.sessionFragmentFlowDepthLimit } returns 5
        every { hengamConfig.sessionFragmentFlowEnabled } returns true
        every { hengamConfig.sessionFragmentFlowExceptionList } returns emptyList()

        sessionFlowManager.initializeSessionFlow()
    }

    private fun setSessionFlow(sessionFlow: MutableList<SessionActivity>) {
        sessionFlowManager.sessionFlow.clear()
        sessionFlowManager.sessionFlow.addAll(sessionFlow)
        sessionFlowManager.sessionFlow.save()
    }

    @Test
    fun onResumeOfActivity_addsASessionActivityToSessionFlowIfANewOne() {
        // empty SessionFlow
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        assertEquals(1, sessionFlowManager.sessionFlow.size)

        activityResumeThrottler.accept(simpleActivity2)
        cpuThread.triggerActions()
        assertEquals(2, sessionFlowManager.sessionFlow.size)

        // already existing activity
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        assertEquals(3, sessionFlowManager.sessionFlow.size)

        // same activity as last, should not be added
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        assertEquals(3, sessionFlowManager.sessionFlow.size)
    }

    @Test
    fun onResumeOfActivity_updatesStartTimeIfSameActivityAsLast() {
        // startTime is set
        every { currentTimeGenerator.getCurrentTime() } returns 400
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        assertEquals(400, sessionFlowManager.sessionFlow.last().startTime)

        every { currentTimeGenerator.getCurrentTime() } returns 1000
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        assertEquals(1000, sessionFlowManager.sessionFlow.last().startTime)
    }

    @Test
    fun onResumeOfNewActivity_SendsSessionMessageForTheLastActivity() {
        // empty sessionFlow
        sessionFlowManager.sessionFlow.clear()
        newActivityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        verify(exactly = 0) { postOffice.sendMessage(any(), any()) }

        sessionFlowManager.sessionFlow.add(SessionActivity("SimpleActivity", 1000, 1000, 1000))
        newActivityResumeThrottler.accept(simpleActivity2)
        cpuThread.triggerActions()
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }
    }

    @Test
    fun onResumeOfNewActivity_UpdatesFunnel() {
        Funnel.activityFunnel.clear()
        Funnel.fragmentFunnel.clear()

        Funnel.activityFunnel.add("SimpleActivity")
        Funnel.fragmentFunnel["containerId"] = mutableListOf("fragment01")

        newActivityResumeThrottler.accept(simpleActivity2)
        cpuThread.triggerActions()
        assertEquals(2, Funnel.activityFunnel.size)
        assertEquals(0, Funnel.fragmentFunnel.size)
    }

    @Test
    fun onResumeOfNewFragment_UpdatesFunnel() {
        Funnel.activityFunnel.clear()
        Funnel.fragmentFunnel.clear()

        Funnel.fragmentFunnel["SecondActivity_id02_"] = mutableListOf("fragment01")

        newFragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithoutParents) to Fragment())
        newFragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithOneParents) to Fragment())
        cpuThread.triggerActions()
        assertEquals(2, Funnel.fragmentFunnel.size)
        assertEquals(2, Funnel.fragmentFunnel["SecondActivity_id02_"]?.size)
        assertEquals(1, Funnel.fragmentFunnel["SecondActivity_id12_id02_"]?.size)
    }

    @Test
    fun onResumeOfFragment_addsAFragmentWithAllParentsToSessionFlow() {
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)

        resumeFragments()

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.size
        )
    }

    @Test
    fun onResumeOfFragment_doesNotDuplicateAlreadyAddedParents() {
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)

        resumeFragments()

        assertEquals(2, sessionFlowManager.sessionFlow.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last().fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.size)

        assertEquals(1, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id32"]!!.size)
    }

    @Test
    fun onResumeOfFragment_updatesStartTimeIfFragmentAlreadyAdded() {
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)

        // startTime is set
        every { currentTimeGenerator.getCurrentTime() } returns 500
        resumeFragments()

        assertEquals(500, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().startTime)

        every { currentTimeGenerator.getCurrentTime() } returns 1500
        resumeFragments()

        assertEquals(1500, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().startTime)
    }

    @Test
    fun onPauseOfActivity_updatesDurationIfSameActivityAsLast() {
        every { currentTimeGenerator.getCurrentTime() } returns 400
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        every { currentTimeGenerator.getCurrentTime() } returns 1000
        activityPauseThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        assertEquals(600, sessionFlowManager.sessionFlow.last().duration)

        every { currentTimeGenerator.getCurrentTime() } returns 1500
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        every { currentTimeGenerator.getCurrentTime() } returns 2000
        activityPauseThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        assertEquals(1100, sessionFlowManager.sessionFlow.last().duration)
    }

    @Test
    fun onPauseOfFragment_updatesDurationIfSameFragmentAsLast() {
        every { currentTimeGenerator.getCurrentTime() } returns 400
        setSessionFlow(sampleSessionFlowWithoutFragment)
        resumeFragments()

        every { currentTimeGenerator.getCurrentTime() } returns 1000
        pauseFragments()

        assertEquals(600L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]?.last()?.duration)

        assertEquals(600L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last().duration)

        assertEquals(600L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last().duration)

        assertEquals(600L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().duration)

        every { currentTimeGenerator.getCurrentTime() } returns 1500
        resumeFragments()

        every { currentTimeGenerator.getCurrentTime() } returns 2000
        pauseFragments()

        assertEquals(1100L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]?.last()?.duration)

        assertEquals(1100L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last().duration)

        assertEquals(1100L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last().duration)

        assertEquals(1100L, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().duration)
    }

    @Test
    fun onResumeOfFragment_ChecksForTheFragmentToBeEnabled_AddsParentsIfTheyAreEnabled() {
        setSessionFlow(sampleSessionFlowWithoutFragment)

        // fragmentFlows disabled
        every { hengamConfig.sessionFragmentFlowEnabled } returns false
        resumeFragments()

        assertEquals(0, sessionFlowManager.sessionFlow[1].fragmentFlows.size)
    }

    @Test
    fun onResumeOfFragment_ChecksForTheFragmentToBeEnabled_AddsParentsIfTheyAreEnabled_() {
        // exceptions when disabled
        setSessionFlow(sampleSessionFlowWithoutFragment)
        every { hengamConfig.sessionFragmentFlowEnabled } returns false
        every { hengamConfig.sessionFragmentFlowExceptionList } returns listOf("SecondActivity_id12_id02_")

        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithOneParents) to Fragment())
        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithoutParents) to Fragment())
        cpuThread.triggerActions()

        assertEquals(1, sessionFlowManager.sessionFlow[1].fragmentFlows.size)
        assertEquals(1, sessionFlowManager.sessionFlow[1].fragmentFlows["id12"]!!.size)
        assertNull(sessionFlowManager.sessionFlow[1].fragmentFlows["id02"])
    }

    @Test
    fun onResumeOfFragment_ChecksForTheFragmentToBeEnabled_AddsParentsIfTheyAreEnabled__() {
        // depth limit, with exceptions
        setSessionFlow(sampleSessionFlowWithoutFragment)
        every { hengamConfig.sessionFragmentFlowEnabled } returns true
        every { hengamConfig.sessionFragmentFlowDepthLimit } returns 2
        every { hengamConfig.sessionFragmentFlowExceptionList } returns listOf("SecondActivity_id12_id02_")

        resumeFragments()

        val firstFlow = sessionFlowManager.sessionFlow[1].fragmentFlows["id02"]
        assertNotNull(firstFlow)
        assertEquals(1, firstFlow!!.size)
        assertEquals("Fragment022", firstFlow[0].name)

        // meets the depth limit but is in exceptions
        val secondFlow = firstFlow[0].fragmentFlows["id12"]
        assertNull(secondFlow)
        assertEquals(0, firstFlow[0].fragmentFlows.size)
    }

    private fun resumeFragments() {
        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithThreeParents) to Fragment())
        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithTwoParents) to Fragment())
        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithOneParents) to Fragment())
        fragmentResumeThrottler.accept(newFragmentInfo(sessionFragmentInfoWithoutParents) to Fragment())
        cpuThread.triggerActions()
    }

    private fun pauseFragments() {
        fragmentPauseThrottler.accept(newFragmentInfo(sessionFragmentInfoWithThreeParents) to Fragment())
        fragmentPauseThrottler.accept(newFragmentInfo(sessionFragmentInfoWithTwoParents) to Fragment())
        fragmentPauseThrottler.accept(newFragmentInfo(sessionFragmentInfoWithOneParents) to Fragment())
        fragmentPauseThrottler.accept(newFragmentInfo(sessionFragmentInfoWithoutParents) to Fragment())
        cpuThread.triggerActions()
    }

    private fun newFragmentInfo(fragmentInfo: SessionFragmentInfo): SessionFragmentInfo {
        return SessionFragmentInfo(
            fragmentInfo.fragmentName,
            fragmentInfo.fragmentId,
            fragmentInfo.activityName,
            fragmentInfo.parentFragment
        )
    }

    private val sessionFragmentInfoWithoutParents =
        SessionFragmentInfo(
            "Fragment022",
            "id02",
            "SecondActivity"
        )

    private val sessionFragmentInfoWithOneParents =
        SessionFragmentInfo(
            "Fragment122",
            "id12",
            "SecondActivity",
            newFragmentInfo(sessionFragmentInfoWithoutParents)
        )

    private val sessionFragmentInfoWithTwoParents =
        SessionFragmentInfo(
            "Fragment222",
            "id22",
            "SecondActivity",
            newFragmentInfo(sessionFragmentInfoWithOneParents)
        )

    private val sessionFragmentInfoWithThreeParents =
        SessionFragmentInfo(
            "Fragment312",
            "id31",
            "SecondActivity",
            newFragmentInfo(sessionFragmentInfoWithTwoParents)
        )

    private val sampleSessionFlowWithoutFragment = mutableListOf(
        SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
        SessionActivity(
            "SecondActivity",
            255,
            255,
            0,
            mutableMapOf()
        )
    )

    private val sampleSessionFlowWithOneLevelFragment = mutableListOf(
        SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
        SessionActivity(
            "SecondActivity",
            255,
            255,
            0,
            mutableMapOf(
                "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
                "id02" to mutableListOf(
                    SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                    SessionFragment("Fragment022", 500, 500, 0, mutableMapOf())
                )
            )
        )
    )

    private val sampleSessionFlowWithTwoLevelFragments = mutableListOf(
        SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
        SessionActivity(
            "SecondActivity",
            255,
            255,
            0,
            mutableMapOf(
                "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
                "id02" to mutableListOf(
                    SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                    SessionFragment("Fragment022", 500, 500, 0,
                        mutableMapOf(
                            "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                            "id12" to mutableListOf(
                                SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                                SessionFragment("Fragment122", 700, 700, 0, mutableMapOf())

                            )
                        )
                    )
                )
            )
        )
    )

    private val sampleSessionFlowWithThreeLevelFragments = mutableListOf(
        SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
        SessionActivity(
            "SecondActivity",
            255,
            255,
            0,
            mutableMapOf(
                "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
                "id02" to mutableListOf(
                    SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                    SessionFragment("Fragment022", 500, 500, 0,
                        mutableMapOf(
                            "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                            "id12" to mutableListOf(
                                SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                                SessionFragment("Fragment122", 700, 700, 0,
                                    mutableMapOf(
                                        "id21" to mutableListOf(SessionFragment("Fragment211", 700, 700, 0, mutableMapOf())),
                                        "id22" to mutableListOf(
                                            SessionFragment("Fragment221", 700, 700, 0, mutableMapOf()),
                                            SessionFragment("Fragment222", 900, 900, 0, mutableMapOf())
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    private val sampleSessionFlowWithFourLevelFragments = mutableListOf(
        SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
        SessionActivity(
            "SecondActivity",
            255,
            255,
            0,
            mutableMapOf(
                "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
                "id02" to mutableListOf(
                    SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                    SessionFragment("Fragment022", 500, 500, 0,
                        mutableMapOf(
                            "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                            "id12" to mutableListOf(
                                SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                                SessionFragment("Fragment122", 700, 700, 0,
                                    mutableMapOf(
                                        "id21" to mutableListOf(SessionFragment("Fragment211", 700, 700, 0, mutableMapOf())),
                                        "id22" to mutableListOf(
                                            SessionFragment("Fragment221", 700, 700, 0, mutableMapOf()),
                                            SessionFragment("Fragment222", 900, 900, 0,
                                                mutableMapOf(
                                                    "id31" to mutableListOf(SessionFragment("Fragment311", 900, 900, 0, mutableMapOf())),
                                                    "id32" to mutableListOf(SessionFragment("Fragment321", 900, 900, 0, mutableMapOf()))
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}