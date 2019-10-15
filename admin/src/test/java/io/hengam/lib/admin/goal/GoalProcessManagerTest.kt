package io.hengam.lib.admin.goal

import android.app.Activity
import android.support.v4.app.Fragment
import android.widget.Button
import android.widget.TextView
import io.hengam.lib.admin.R
import io.hengam.lib.admin.analytics.activities.MultipleFrameLayoutActivity
import io.hengam.lib.admin.analytics.activities.SimpleActivity
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.analytics.ViewExtractor
import io.hengam.lib.analytics.goal.*
import io.hengam.lib.analytics.utils.getOnClickListener
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.rx.PublishRelay
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.mockk.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalProcessManagerTest {
    private val cpuThread = mockCpuThread()
    private val moshi = HengamMoshi()

    private val goalStore: GoalStore = mockk(relaxed = true)
    private val activityReachHandler: ActivityReachHandler = mockk(relaxed = true)
    private val fragmentReachHandler: FragmentReachHandler = mockk(relaxed = true)
    private val buttonClickHandler: ButtonClickHandler = mockk(relaxed = true)

    private val appLifecycleListener: AppLifecycleListener = mockk(relaxed = true)

    private val activityResumeThrottler = PublishRelay.create<Activity>()
    private val newActivityResumeThrottler = PublishRelay.create<Activity>()
    private val fragmentResumeThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()
    private val newFragmentResumeThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()
    private val activityPauseThrottler = PublishRelay.create<Activity>()
    private val fragmentPauseThrottler = PublishRelay.create<Pair<SessionFragmentInfo, Fragment>>()

    private lateinit var goalProcessManager: GoalProcessManager

    private lateinit var fragmentB: Fragment
    private val fragmentBInfo = SessionFragmentInfo(
            "FragmentB",
            "flContainer",
            "MultipleFrameLayoutActivity",
            parentFragment = null
    )

    private lateinit var multipleFrameLayoutActivity: MultipleFrameLayoutActivity
    private val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)


    private fun initializeMultipleFrameLayoutActivityWithFragmentB() {
        multipleFrameLayoutActivity = Robolectric.setupActivity(MultipleFrameLayoutActivity::class.java)
        multipleFrameLayoutActivity.findViewById<Button>(R.id.buttonFragment).performClick()
        fragmentB = multipleFrameLayoutActivity.supportFragmentManager.findFragmentById(R.id.flContainer)!!
    }

    @Before
    fun init() {

        mockkObject(ViewExtractor)

        every { appLifecycleListener.onActivityResumed() } returns activityResumeThrottler
        every { appLifecycleListener.onNewActivity() } returns newActivityResumeThrottler
        every { appLifecycleListener.onFragmentResumed() } returns fragmentResumeThrottler
        every { appLifecycleListener.onNewFragment() } returns newFragmentResumeThrottler
        every { appLifecycleListener.onActivityPaused() } returns activityPauseThrottler
        every { appLifecycleListener.onFragmentPaused() } returns fragmentPauseThrottler

        goalProcessManager =
                GoalProcessManager(
                    appLifecycleListener,
                    activityReachHandler,
                    fragmentReachHandler,
                    buttonClickHandler,
                    goalStore,
                    moshi
                )

        goalProcessManager.initialize()
    }

    @Test
    fun onResumeOfNewActivity_CallsActivityReachedGoalHandlerIfThereIsAGoal_ViewGoalsUpdated__NoGoals() {
        // no goals defined
        every { goalStore.getActivityReachGoals("SimpleActivity") } returns Observable.empty()
        newActivityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        verify(exactly = 1) { goalStore.getActivityReachGoals("SimpleActivity") }
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), simpleActivity) }
        verify(exactly = 0) { activityReachHandler.onGoalReached(any()) }
    }

    @Test
    fun onResumeOfNewActivity_CallsActivityReachedGoalHandlerIfThereIsAGoal_ViewGoalsUpdated__TwoGoals() {
        // multiple activityReachGoal for 'SimpleActivity'
        every { goalStore.getActivityReachGoals("SimpleActivity") } returns Observable.just(
                firstActivityReachGoal,
                firstActivityReachGoalWithDifferentName
        )
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } returns Completable.complete()
        newActivityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        verifyOrder {
            goalStore.getActivityReachGoals("SimpleActivity")
            goalStore.updateViewGoalValues(firstActivityReachGoal.viewGoalDataList, simpleActivity)
            activityReachHandler.onGoalReached(firstActivityReachGoal)
            goalStore.updateViewGoalValues(firstActivityReachGoalWithDifferentName.viewGoalDataList, simpleActivity)
            activityReachHandler.onGoalReached(firstActivityReachGoalWithDifferentName)
        }

        verify(exactly = 1) { goalStore.getActivityReachGoals("SimpleActivity") }
        verify(exactly = 2) { goalStore.updateViewGoalValues(any(), simpleActivity) }
        verify(exactly = 2) { activityReachHandler.onGoalReached(any()) }
    }

    @Test
    fun onResumeOfNewFragment_callsFragmentReachedGoalHandlerIfThereIsAGoal_viewGoalsUpdated__NoGoals() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        // no goals defined
        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns Observable.empty()
        newFragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        verify(exactly = 1) { goalStore.getFragmentReachGoals(fragmentBInfo) }
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), fragmentB) }
        verify(exactly = 0) { fragmentReachHandler.onGoalReached(any(), fragmentBInfo.containerId) }
    }

    @Test
    fun onResumeOfNewFragment_callsFragmentReachedGoalHandlerIfThereIsAGoal_viewGoalsUpdated__WithGoals() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        // multiple fragmentReachGoal for 'FragmentB'
        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns Observable.just(
                fragmentBReachGoal,
                fragmentBReachGoalWithDifferentName
        )
        every { goalStore.updateViewGoalValues(any(), fragmentB) } returns Completable.complete()
        newFragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        verifyOrder {
            goalStore.getFragmentReachGoals(fragmentBInfo)
            goalStore.updateViewGoalValues(fragmentBReachGoal.viewGoalDataList, fragmentB)
            fragmentReachHandler.onGoalReached(fragmentBReachGoal, fragmentBInfo.containerId)
            goalStore.updateViewGoalValues(fragmentBReachGoalWithDifferentName.viewGoalDataList, fragmentB)
            fragmentReachHandler.onGoalReached(fragmentBReachGoalWithDifferentName, fragmentBInfo.containerId)
        }

        verify(exactly = 1) { goalStore.getFragmentReachGoals(fragmentBInfo) }
        verify(exactly = 2) { goalStore.updateViewGoalValues(any(), fragmentB) }
        verify(exactly = 2) { fragmentReachHandler.onGoalReached(any(), fragmentBInfo.containerId) }
    }

    @Test
    fun onResumeOfActivity_SetsButtonClickListenersForEachGoalButtonInActivity() {
        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)
        val secondTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget2)

        // multiple buttonClickGoals for 'SimpleActivity'
        every { goalStore.getButtonClickGoals("SimpleActivity") } returns Observable.just(
                firstButtonClickGoalSimpleActivity,
                secondButtonClickGoalSimpleActivity
        )
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } returns Completable.complete()

        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        firstTargetButton.performClick()
        cpuThread.triggerActions()

        verifyOrder {
            goalStore.updateViewGoalValues(firstButtonClickGoalSimpleActivity.viewGoalDataList, simpleActivity)
            buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity) }

        secondTargetButton.performClick()
        cpuThread.triggerActions()
        verifyOrder {
            goalStore.updateViewGoalValues(listOf(), simpleActivity)
            buttonClickHandler.onGoalReached(secondButtonClickGoalSimpleActivity)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(secondButtonClickGoalSimpleActivity) }
    }

    @Test
    fun onResumeOfFragment_setsButtonClickListenersForEachGoalButtonInFragment() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)
        val secondTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget2)

        // multiple buttonClickGoals inside FragmentB
        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns Observable.just(
                firstButtonClickGoalFragmentB,
                secondButtonClickGoalFragmentB
        )
        every { goalStore.updateViewGoalValues(any(), fragmentB) } returns Completable.complete()
        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        firstTargetButton.performClick()
        cpuThread.triggerActions()
        verifyOrder {
            goalStore.updateViewGoalValues(firstButtonClickGoalFragmentB.viewGoalDataList, fragmentB)
            buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB) }

        secondTargetButton.performClick()
        cpuThread.triggerActions()
        verifyOrder {
            goalStore.updateViewGoalValues(listOf(), fragmentB)
            buttonClickHandler.onGoalReached(secondButtonClickGoalFragmentB)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(secondButtonClickGoalFragmentB) }
    }

    @Test
    fun manageActivityButtonClickGoals_retrievesAndExecutesUserListenerInNewListener() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns Observable.just(firstButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } returns Completable.complete()

        // setting a listener for the button
        val tvSample = simpleActivity.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()
        cpuThread.triggerActions()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity) }
    }

    @Test
    fun manageFragmentButtonClickGoals_retrievesAndExecutesUserListenerInNewListener() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns Observable.just(firstButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } returns Completable.complete()

        // setting a listener for the button
        val tvSample = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()
        cpuThread.triggerActions()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB) }
    }

    @Test
    fun manageActivityButtonClickGoals_checksIfListenerHasAlreadyBeenAdded() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns Observable.just(firstButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } returns Completable.complete()

        // setting a listener for the button
        val tvSample = simpleActivity.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        // multiple calls to setClickListener
        activityResumeThrottler.accept(simpleActivity)
        activityResumeThrottler.accept(simpleActivity)
        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()
        cpuThread.triggerActions()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity) }
    }

    @Test
    fun manageFragmentButtonClickGoals_checksIfListenerHasAlreadyBeenAdded() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns Observable.just(firstButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } returns Completable.complete()

        // setting a listener for the button
        val tvSample = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        // multiple calls to setClickListener
        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)
        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)
        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()
        cpuThread.triggerActions()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB) }
    }

    @Test
    fun manageActivityButtonClickGoals_checksIfViewWithGivenButtonIdIsAButton() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)

        mockkStatic("io.hengam.lib.analytics.utils.GetOnClickListenerKt")

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns Observable.just(firstButtonClickGoalSimpleActivity)

        val textView = simpleActivity.findViewById<TextView>(R.id.tvSample)
        every { ViewExtractor.extractView(any(), simpleActivity) } returns Single.just(textView)

        activityResumeThrottler.accept(simpleActivity)
        cpuThread.triggerActions()
        verify(exactly = 0) { getOnClickListener(textView) }
    }

    @Test
    fun manageFragmentButtonClickGoals_checksIfViewWithGivenButtonIdIsAButton() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        mockkStatic("io.hengam.lib.analytics.utils.GetOnClickListenerKt")

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns Observable.just(firstButtonClickGoalFragmentB)

        val textView = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)
        every { ViewExtractor.extractView(any(), fragmentB) } returns Single.just(textView)

        fragmentResumeThrottler.accept(fragmentBInfo to fragmentB)

        verify(exactly = 0) { getOnClickListener(textView) }
    }

    @Test
    fun onPauseOfActivity_callsStoreToUpdateActivityViewGoalValues() {
        every { goalStore.viewGoalsByActivity("SimpleActivity") } returns Observable.fromIterable(viewGoalDataList_simpleActivity)
        activityPauseThrottler.accept(simpleActivity)
        cpuThread.triggerActions()

        viewGoalDataList_simpleActivity.forEach {
            verify(exactly = 1) { goalStore.updateViewGoalValues(listOf(it), simpleActivity) }
        }
    }

    @Test
    fun onPauseOfFragment_callsStoreToUpdateFragmentViewGoalValues() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        every { goalStore.viewGoalsByFragment(fragmentBInfo) } returns Observable.fromIterable(viewGoalDataList_fragmentB)

        fragmentPauseThrottler.accept(fragmentBInfo to fragmentB)
        viewGoalDataList_fragmentB.forEach {
            verify(exactly = 1) { goalStore.updateViewGoalValues(listOf(it), fragmentB) }
        }
    }
}

// activityReach
private val firstActivityReachGoal = ActivityReachGoalData(
        name = "firstActivityReachGoal", activityClassName = "SimpleActivity", viewGoalDataList = listOf(
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "firstActivityReachGoal"
        ),
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "firstActivityReachGoal"
        )
    )
)

private val firstActivityReachGoalWithDifferentName = ActivityReachGoalData(
        name = "firstActivityReachGoalWithDifferentName", activityClassName = "SimpleActivity", viewGoalDataList = listOf())

private val viewGoalDataList_simpleActivity =
        listOf(
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "tvSample",
                        activityClassName = "SimpleActivity",
                        parentGoalName = "firstActivityReachGoal",
                        targetValues = listOf(),
                        goalFragmentInfo = null
                ),
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "editTextSample",
                        activityClassName = "SimpleActivity",
                        parentGoalName = "firstActivityReachGoal",
                        targetValues = listOf(),
                        goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.fragmentName", "io.hengam.lib.admin.analytics.fragments.aa", "fragmentID", "activityName")
                )
        )

// fragmentReach
private val fragmentBReachGoal = FragmentReachGoalData(
        name = "fragmentBReachGoal", activityClassName = "MultipleFrameLayoutActivity", viewGoalDataList = listOf(
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "MultipleFrameLayoutActivity",
                parentGoalName = "fragmentBReachGoal",
                goalFragmentInfo = GoalFragmentInfo(actualName = "io.hengam.lib.admin.analytics.fragments.FragmentB", obfuscatedName = null, fragmentId = "flContainer", activityName = "MultipleFrameLayoutActivity")
        ),
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                parentGoalName = "fragmentBReachGoal",
                activityClassName = "MultipleFrameLayoutActivity",
                goalFragmentInfo = GoalFragmentInfo(actualName = "io.hengam.lib.admin.analytics.fragments.FragmentB", obfuscatedName = null, fragmentId = "flContainer", activityName = "MultipleFrameLayoutActivity")
        )
),
        goalFragmentInfo = GoalFragmentInfo(actualName = "io.hengam.lib.admin.analytics.fragments.FragmentB", obfuscatedName = null, fragmentId = "flContainer", activityName = "MultipleFrameLayoutActivity")
)

private val fragmentBReachGoalWithDifferentName = FragmentReachGoalData(
        name = "fragmentBReachGoalWithDifferentName",
        activityClassName = "MultipleFrameLayoutActivity",
        viewGoalDataList = listOf(),
        goalFragmentInfo = GoalFragmentInfo(actualName = "io.hengam.lib.admin.analytics.fragments.FragmentB", obfuscatedName = null, fragmentId = "flContainer", activityName = "MultipleFrameLayoutActivity")
)

private val viewGoalDataList_fragmentB =
        listOf(
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "tvSample",
                        activityClassName = "MultipleFrameLayoutActivity",
                        parentGoalName = "fragmentBReachGoal",
                        targetValues = listOf(),
                        goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity")
                ),
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "editTextSample",
                        activityClassName = "MultipleFrameLayoutActivity",
                        parentGoalName = "fragmentBReachGoal",
                        targetValues = listOf(),
                        goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity")
                )
        )

// buttonClick in activity
private val firstButtonClickGoalSimpleActivity = ButtonClickGoalData(
        name = "firstButtonClickGoalSimpleActivity", activityClassName = "SimpleActivity", viewGoalDataList = listOf(
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "firstButtonClickGoalSimpleActivity"
        ),
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "firstButtonClickGoalSimpleActivity"
        )
),
        buttonID = "buttonTarget"
)

private val secondButtonClickGoalSimpleActivity = ButtonClickGoalData(
        name = "secondButtonClickGoalSimpleActivity", activityClassName = "SimpleActivity", viewGoalDataList = listOf(), buttonID = "buttonTarget2")

// buttonClick in fragmentB
private val firstButtonClickGoalFragmentB = ButtonClickGoalData(
        name = "firstButtonClickGoalFragmentB", activityClassName = "MultipleFrameLayoutActivity", viewGoalDataList = listOf(
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "MultipleFrameLayoutActivity",
                goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity"),
                parentGoalName = "firstButtonClickGoalFragmentB"
        ),
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                parentGoalName = "firstButtonClickGoalFragmentB",
                activityClassName = "MultipleFrameLayoutActivity",
                goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity")
        )
),
        goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity"),
        buttonID = "buttonInnerTarget"
)

private val secondButtonClickGoalFragmentB = ButtonClickGoalData(
        name = "secondButtonClickGoalFragmentB",
        activityClassName = "MultipleFrameLayoutActivity",
        viewGoalDataList = listOf(),
        goalFragmentInfo = GoalFragmentInfo("io.hengam.lib.admin.analytics.fragments.FragmentB", "io.hengam.lib.admin.analytics.fragments.b", "flContainer", "MultipleFrameLayoutActivity"),
        buttonID = "buttonInnerTarget2"
)