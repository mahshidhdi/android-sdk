package io.hengam.lib.admin.goal

import android.support.v4.app.Fragment
import android.widget.Button
import android.widget.TextView
import io.hengam.lib.admin.R
import io.hengam.lib.admin.analytics.activities.MultipleFrameLayoutActivity
import io.hengam.lib.admin.analytics.activities.SimpleActivity
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.analytics.ViewExtractor
import io.hengam.lib.analytics.goal.*
import io.hengam.lib.analytics.utils.getOnClickListener
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.test.TestUtils.turnOffThreadAssertions
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalProcessManagerTest {
    private val sessionId = "some_id"

    private val moshi = HengamMoshi()

    private val goalStore: GoalStore = mockk(relaxed = true)
    private val activityReachHandler: ActivityReachHandler = mockk(relaxed = true)
    private val fragmentReachHandler: FragmentReachHandler = mockk(relaxed = true)
    private val buttonClickHandler: ButtonClickHandler = mockk(relaxed = true)

    private lateinit var goalProcessManager: GoalProcessManager

    private lateinit var fragmentB: Fragment
    private val fragmentBInfo = SessionFragmentInfo(
            "FragmentB",
            "flContainer",
            "MultipleFrameLayoutActivity")
    private val fragmentBContainer = FragmentContainer("MultipleFrameLayoutActivity", "flContainer", listOf())

    private lateinit var multipleFrameLayoutActivity: MultipleFrameLayoutActivity

    private fun initializeMultipleFrameLayoutActivityWithFragmentB() {
        multipleFrameLayoutActivity = Robolectric.setupActivity(MultipleFrameLayoutActivity::class.java)
        multipleFrameLayoutActivity.findViewById<Button>(R.id.buttonFragment).performClick()
        fragmentB = multipleFrameLayoutActivity.supportFragmentManager.findFragmentById(R.id.flContainer)!!
    }

    @Before
    fun init() {

        mockkObject(ViewExtractor)

        turnOffThreadAssertions()

        goalProcessManager = spyk(
                GoalProcessManager(
                        activityReachHandler,
                        fragmentReachHandler,
                        buttonClickHandler,
                        goalStore,
                        moshi
                )
        )
    }

    @Test
    fun manageActivityReachGoals_callsActivityReachedGoalHandlerIfThereIsAGoal_viewGoalsUpdated() {
        // no goals defined
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)

        every { goalStore.getActivityReachGoals("SimpleActivity") } returns listOf()
        goalProcessManager.manageActivityReachGoals(simpleActivity, sessionId)
        verify(exactly = 1) { goalStore.getActivityReachGoals("SimpleActivity") }
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), simpleActivity) }
        verify(exactly = 0) { activityReachHandler.onGoalReached(any(), sessionId) }

        // multiple activityReachGoal for 'SimpleActivity'
        every { goalStore.getActivityReachGoals("SimpleActivity") } returns listOf(
                firstActivityReachGoal,
                firstActivityReachGoalWithDifferentName
        )
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } just runs
        goalProcessManager.manageActivityReachGoals(simpleActivity, sessionId)
        verifyOrder {
            goalStore.getActivityReachGoals("SimpleActivity")
            goalStore.updateViewGoalValues(firstActivityReachGoal.viewGoalDataList, simpleActivity)
            activityReachHandler.onGoalReached(firstActivityReachGoal, sessionId)
            activityReachHandler.onGoalReached(firstActivityReachGoalWithDifferentName, sessionId)
        }
    }

    @Test
    fun updateFragmentReachViewGoals_callsStoreToUpdateFragmentReachGoalViewGoalsIfAny() {
        // no goals defined
        initializeMultipleFrameLayoutActivityWithFragmentB()

        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns listOf()
        goalProcessManager.updateFragmentReachViewGoals(fragmentBInfo, fragmentB)
        verify(exactly = 1) { goalStore.getFragmentReachGoals(fragmentBInfo) }
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), fragmentB) }

        // multiple fragmentReachGoal for 'FragmentB'
        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns listOf(
                fragmentBReachGoal,
                fragmentBReachGoalWithDifferentName
        )
        every { goalStore.updateViewGoalValues(any(), fragmentB) } just runs
        goalProcessManager.updateFragmentReachViewGoals(fragmentBInfo, fragmentB)
        verifyOrder {
            goalStore.getFragmentReachGoals(fragmentBInfo)
            goalStore.updateViewGoalValues(fragmentBReachGoal.viewGoalDataList, fragmentB)
        }
    }

    @Test
    fun handleFragmentReachMessage_callsFragmentReachedGoalHandlerIfThereIsAGoal() {
        // no goals defined
        initializeMultipleFrameLayoutActivityWithFragmentB()

        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns listOf()
        goalProcessManager.handleFragmentReachMessage(fragmentBInfo, fragmentBContainer, sessionId)
        verify(exactly = 1) { goalStore.getFragmentReachGoals(fragmentBInfo) }
        verify(exactly = 0) { fragmentReachHandler.onGoalReached(any(), fragmentBContainer, sessionId) }
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), fragmentB) }

        // multiple fragmentReachGoal for 'FragmentB'
        every { goalStore.getFragmentReachGoals(fragmentBInfo) } returns listOf(
                fragmentBReachGoal,
                fragmentBReachGoalWithDifferentName
        )
        goalProcessManager.handleFragmentReachMessage(fragmentBInfo, fragmentBContainer, sessionId)
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), fragmentB) }
        verifyOrder {
            goalStore.getFragmentReachGoals(fragmentBInfo)
            fragmentReachHandler.onGoalReached(fragmentBReachGoal, fragmentBContainer, sessionId)
            fragmentReachHandler.onGoalReached(fragmentBReachGoalWithDifferentName, fragmentBContainer, sessionId)
        }
    }

    @Test
    fun manageActivityButtonClickGoals_setsButtonClickListenersForEachGoalButtonInActivity() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)

        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)
        val secondTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget2)

        // multiple buttonClickGoals for 'SimpleActivity'
        every { goalStore.getButtonClickGoals("SimpleActivity") } returns listOf(
                firstButtonClickGoalSimpleActivity,
                secondButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } just runs

        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)

        firstTargetButton.performClick()
        verifyOrder {
            goalStore.updateViewGoalValues(firstButtonClickGoalSimpleActivity.viewGoalDataList, simpleActivity)
            buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity, sessionId)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity, sessionId) }

        secondTargetButton.performClick()
        verifyOrder {
            goalStore.updateViewGoalValues(listOf(), simpleActivity)
            buttonClickHandler.onGoalReached(secondButtonClickGoalSimpleActivity, sessionId)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(secondButtonClickGoalSimpleActivity, sessionId) }
    }

    @Test
    fun manageFragmentButtonClickGoals_setsButtonClickListenersForEachGoalButtonInFragment() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)
        val secondTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget2)

        // multiple buttonClickGoals inside FragmentB
        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns listOf(
                firstButtonClickGoalFragmentB,
                secondButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } just runs

        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)

        firstTargetButton.performClick()
        verifyOrder {
            goalStore.updateViewGoalValues(firstButtonClickGoalFragmentB.viewGoalDataList, fragmentB)
            buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB, sessionId)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB, sessionId) }

        secondTargetButton.performClick()
        verifyOrder {
            goalStore.updateViewGoalValues(listOf(), fragmentB)
            buttonClickHandler.onGoalReached(secondButtonClickGoalFragmentB, sessionId)
        }
        verify(exactly = 1) { buttonClickHandler.onGoalReached(secondButtonClickGoalFragmentB, sessionId) }
    }

    @Test
    fun manageActivityButtonClickGoals_retrievesAndExecutesUserListenerInNewListener() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns listOf(firstButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } just runs

        // setting a listener for the button
        val tvSample = simpleActivity.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity, sessionId) }
    }

    @Test
    fun manageFragmentButtonClickGoals_retrievesAndExecutesUserListenerInNewListener() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns listOf(firstButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } just runs

        // setting a listener for the button
        val tvSample = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)
        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB, sessionId) }
    }

    @Test
    fun manageActivityButtonClickGoals_checksIfListenerHasAlreadyBeenAdded() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
        val firstTargetButton: Button = simpleActivity.findViewById(R.id.buttonTarget)

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns listOf(firstButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } just runs

        // setting a listener for the button
        val tvSample = simpleActivity.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        // multiple calls to setClickListener
        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalSimpleActivity, sessionId) }
    }

    @Test
    fun manageFragmentButtonClickGoals_checksIfListenerHasAlreadyBeenAdded() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        val firstTargetButton: Button = fragmentB.view!!.findViewById(R.id.buttonInnerTarget)

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns listOf(firstButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } just runs

        // setting a listener for the button
        val tvSample = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)
        tvSample.text = "Before Clicking Button"
        firstTargetButton.setOnClickListener {
            tvSample.text = "After Clicking Button"
        }

        // multiple calls to setClickListener
        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)
        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)
        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)
        assertEquals("Before Clicking Button", tvSample.text)
        firstTargetButton.performClick()

        assertEquals("After Clicking Button", tvSample.text)
        verify(exactly = 1) { buttonClickHandler.onGoalReached(firstButtonClickGoalFragmentB, sessionId) }
    }

    @Test
    fun manageActivityButtonClickGoals_checksIfViewWithGivenButtonIdIsAButton() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)

        mockkStatic("io.hengam.lib.analytics.utils.GetOnClickListenerKt")

        every { goalStore.getButtonClickGoals("SimpleActivity") } returns listOf(firstButtonClickGoalSimpleActivity)
        every { goalStore.updateViewGoalValues(any(), simpleActivity) } just runs

        val textView = simpleActivity.findViewById<TextView>(R.id.tvSample)

        // multiple calls to setClickListener
        goalProcessManager.manageButtonClickGoals(simpleActivity, sessionId)
        verify(exactly = 0) { getOnClickListener(textView) }
    }

    @Test
    fun manageFragmentButtonClickGoals_checksIfViewWithGivenButtonIdIsAButton() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        mockkStatic("io.hengam.lib.analytics.utils.GetOnClickListenerKt")

        every { goalStore.getButtonClickGoals(fragmentBInfo) } returns listOf(firstButtonClickGoalFragmentB)
        every { goalStore.updateViewGoalValues(any(), fragmentB) } just runs

        val textView = fragmentB.view!!.findViewById<TextView>(R.id.tvSample)

        // multiple calls to setClickListener
        goalProcessManager.manageButtonClickGoals(fragmentBInfo, fragmentB, sessionId)
        verify(exactly = 0) { getOnClickListener(textView) }
    }

    @Test
    fun updateActivityViewGoals_callsStoreToUpdateActivityViewGoalValues() {
        val simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)

        // no viewGoals in the activity
        every { goalStore.viewGoalsByActivity("SimpleActivity") } returns listOf()
        goalProcessManager.updateActivityViewGoals(simpleActivity)
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), simpleActivity) }

        // some viewGoals in the activity!!
        every { goalStore.viewGoalsByActivity("SimpleActivity") } returns viewGoalDataList_simpleActivity
        goalProcessManager.updateActivityViewGoals(simpleActivity)
        verify(exactly = 1) { goalStore.updateViewGoalValues(viewGoalDataList_simpleActivity, simpleActivity) }
    }

    @Test
    fun updateFragmentViewGoals_callsStoreToUpdateFragmentViewGoalValues() {
        initializeMultipleFrameLayoutActivityWithFragmentB()

        // no viewGoals in the fragment
        every { goalStore.viewGoalsByFragment(fragmentBInfo) } returns listOf()
        goalProcessManager.updateFragmentViewGoals(fragmentBInfo, fragmentB)
        verify(exactly = 0) { goalStore.updateViewGoalValues(any(), fragmentB) }

        // some viewGoals in the fragment!!
        every { goalStore.viewGoalsByFragment(fragmentBInfo) } returns viewGoalDataList_fragmentB
        goalProcessManager.updateFragmentViewGoals(fragmentBInfo, fragmentB)
        verify(exactly = 1) { goalStore.updateViewGoalValues(viewGoalDataList_fragmentB, fragmentB) }
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