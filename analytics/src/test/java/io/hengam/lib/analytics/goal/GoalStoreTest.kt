package io.hengam.lib.analytics.goal

import android.content.Context
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.test.TestUtils
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoalStoreTest {
    private lateinit var goalStore: GoalStore

    private val context: Context = mockk(relaxed = true)
    private val moshi: HengamMoshi = mockk(relaxed = true)
    private val goalFragmentNameExtractor: GoalFragmentObfuscatedNameExtractor = mockk(relaxed = true)
    private val storage = HengamStorage(moshi, MockSharedPreference())

    private val cpuThread = TestUtils.mockCpuThread()

    @Before
    fun setUp() {
        goalStore = GoalStore(context, moshi, goalFragmentNameExtractor, storage)
        every { goalFragmentNameExtractor.getFragmentObfuscatedName(GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer")) } returns "a"
        every { goalFragmentNameExtractor.getFragmentObfuscatedName(GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer2")) } returns "a"
        every { goalFragmentNameExtractor.getFragmentObfuscatedName(GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer")) } returns "b"
        every { goalFragmentNameExtractor.getFragmentObfuscatedName(GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer2")) } returns "b"

    }

    @Test
    fun updateGoals_updatesGoalsInThePreference_updatesTheGoalsDataList() {
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal
        ))).justDo()

        cpuThread.triggerActions()
        assertEquals(2, goalStore.definedGoals.size)
        assertEquals(2, goalStore.definedGoalsDataSet.size)
        assert(goalStore.definedGoals.contains(firstActivityReachGoal))
        assert(goalStore.definedGoals.contains(secondButtonClickGoal))
    }

    @Test
    fun updateGoals_replacesNewGoalInCaseAGoalWithTheSameNameExists() {
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal
        ))).justDo()

        goalStore.updateGoals(getGoals(listOf(firstActivityReachGoalWithDifferentViewGoals))).justDo()

        cpuThread.triggerActions()
        assertEquals(2, goalStore.definedGoals.size)
        assertEquals(2, goalStore.definedGoalsDataSet.size)

        assertEquals((secondButtonClickGoal), goalStore.definedGoals[0])
        assertEquals((firstActivityReachGoalWithDifferentViewGoals), goalStore.definedGoals[1])

        // goals are identified only by their names
        assert(goalStore.definedGoals.contains(firstActivityReachGoal))

        assertEquals(1, goalStore.definedGoals[1].viewGoals.size)
        assert(goalStore.definedGoals[1].viewGoals.contains(
            ViewGoal(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                activityClassName = "SimpleActivity2"
            )))

        assert(!goalStore.definedGoals[1].viewGoals.contains(
            ViewGoal(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                activityClassName = "SimpleActivity"
            )))

        goalStore.updateGoals(getGoals(listOf(buttonClickGoalWithSameNameAsFirstActivityReachGoal))).justDo()

        cpuThread.triggerActions()
        assertEquals(2, goalStore.definedGoals.size)
        assertEquals(2, goalStore.definedGoalsDataSet.size)

        assertEquals((secondButtonClickGoal), goalStore.definedGoals[0])
        assertEquals((buttonClickGoalWithSameNameAsFirstActivityReachGoal), goalStore.definedGoals[1])
        assert(goalStore.definedGoals[1].goalType == GoalType.BUTTON_CLICK)

        // goals are identified only by their names
        assert(goalStore.definedGoals.contains(firstActivityReachGoal))
    }

    @Test
    fun updateGoals_extractsNewGoalsViewGoals() {
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal
        ))).justDo()

        cpuThread.triggerActions()
        assertEquals(7, goalStore.definedViewGoalsDataSet.size)

        goalStore.updateGoals(getGoals(listOf(firstActivityReachGoalWithDifferentViewGoals))).justDo()

        cpuThread.triggerActions()
        assertEquals(4, goalStore.definedViewGoalsDataSet.size)
    }

    @Test
    fun removeGoals_removesGoalsWithTheGivenNames_removesTheirViewGoals(){
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal,
            firstActivityReachGoalWithDifferentName,
            fragmentWithLayoutsSecondButtonClickGoal,
            fragmentBFirstReachGoal
        ))).justDo()
        cpuThread.triggerActions()

        goalStore.removeGoals(setOf(
            "firstActivityReachGoalWithDifferentName",
            "fragmentBFirstReachGoal"
        )).justDo()
        cpuThread.triggerActions()

        assertEquals(3, goalStore.definedGoals.size)
        assertEquals(3, goalStore.definedGoalsDataSet.size)

        // viewGoalDatas are removed as well
        assertEquals(12, goalStore.definedViewGoalsDataSet.size)
    }

    @Test
    fun initializeViewGoalsDataSet_extractsViewGoalsDataFromDefinedGoals() {
        goalStore.initializeViewGoalsDataSet()

        cpuThread.triggerActions()
        assertEquals(0, goalStore.definedViewGoalsDataSet.size)

        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal
        ))).justDo()

        goalStore.initializeViewGoalsDataSet()

        cpuThread.triggerActions()
        assertEquals(7, goalStore.definedViewGoalsDataSet.size)

        var viewGoals = firstActivityReachGoal.viewGoals
        for (viewGoal in viewGoals) {
            assert(goalStore.definedViewGoalsDataSet.containsKey(
                ViewGoalData(
                    parentGoalName = firstActivityReachGoal.name,
                    targetValues = viewGoal.targetValues,
                    viewType = viewGoal.viewType,
                    viewID = viewGoal.viewID,
                    activityClassName = viewGoal.activityClassName,
                    goalFragmentInfo = null
                )
            ))
        }

        viewGoals = secondButtonClickGoal.viewGoals
        for (viewGoal in viewGoals) {
            assert(goalStore.definedViewGoalsDataSet.containsKey(
                ViewGoalData(
                    parentGoalName = secondButtonClickGoal.name,
                    targetValues = viewGoal.targetValues,
                    viewType = viewGoal.viewType,
                    viewID = viewGoal.viewID,
                    activityClassName = viewGoal.activityClassName,
                    goalFragmentInfo = null
                )
            ))
        }
    }

    @Test
    fun initializeGoalsDataSet_extractsGoalsDataFromDefinedGoals() {
        goalStore.initializeViewGoalsDataSet()
        goalStore.initializeGoalsDataSet()

        cpuThread.triggerActions()
        assertEquals(0, goalStore.definedGoalsDataSet.size)

        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            secondButtonClickGoal
        ))).justDo()

        goalStore.initializeViewGoalsDataSet()
        goalStore.initializeGoalsDataSet()

        cpuThread.triggerActions()
        assertEquals(2, goalStore.definedGoalsDataSet.size)
    }

    @Test
    fun initializeViewGoalsDataSet_sameViewGoalCanBeInDifferentGoals() {
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            firstActivityReachGoalWithDifferentName
        ))).justDo()
        goalStore.initializeViewGoalsDataSet()

        cpuThread.triggerActions()
        assertEquals(6, goalStore.definedViewGoalsDataSet.size)
    }

    @Test
    fun getActivityReachGoals_returnsDefinedGoalsWithActivityReachTypeAndGivenActivityName() {
        // empty goals
        goalStore.getActivityReachGoals("activityName")
            .test()
            .assertValueCount(0)

        // no activityReachGoals
        goalStore.updateGoals(getGoals(listOf(secondButtonClickGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getActivityReachGoals("SimpleActivity")
            .test()
            .assertValueCount(0)

        // two different 'SimpleActivity' reach goals
        goalStore.updateGoals(getGoals(listOf(
            firstActivityReachGoal,
            firstActivityReachGoalWithDifferentName
        ))).justDo()
        cpuThread.triggerActions()
        goalStore.getActivityReachGoals("SimpleActivity")
            .test()
            .assertValueCount(2)

        // 'SimpleActivity' but different kind of goal
        goalStore.updateGoals(getGoals(listOf(firstButtonClickGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getActivityReachGoals("SimpleActivity")
            .test()
            .assertValueCount(2)
    }

    @Test
    fun getFragmentReachGoals_returnsDefinedGoalsWithFragmentReachTypeAndGivenFragmentInfo() {
        // empty goals
        goalStore.getFragmentReachGoals(
            SessionFragmentInfo("fragmentName", "fragmentId", "activityName")
        )
            .test()
            .assertValueCount(0)
        verify(exactly = 0) { goalFragmentNameExtractor.getFragmentObfuscatedName(any()) }

        // no fragmentReachGoals
        goalStore.updateGoals(getGoals(listOf(fragmentWithLayoutsSecondButtonClickGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getFragmentReachGoals(
            SessionFragmentInfo(
                "FragmentA",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(0)

        // two different fragment reach goals with the same FragmentInfo
        goalStore.updateGoals(getGoals(listOf(
            fragmentBFirstReachGoal,
            fragmentBFirstReachGoalWithDifferentName
        ))).justDo()
        cpuThread.triggerActions()
        goalStore.getFragmentReachGoals(
            SessionFragmentInfo(
                "FragmentB",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(2)
    }

    @Test
    fun getButtonClickGoals_returnsDefinedGoalsWithButtonClickTypeAndGivenActivityName() {
        // empty goals
        goalStore.getButtonClickGoals("activityName")
            .test()
            .assertValueCount(0)

        // no buttonClickGoals
        goalStore.updateGoals(getGoals(listOf(firstActivityReachGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getButtonClickGoals("SimpleActivity")
            .test()
            .assertValueCount(0)

        // two different ButtonClickGoals for the same button
        goalStore.updateGoals(
            listOf(
                firstButtonClickGoal,
                firstButtonClickGoal2,
                firstButtonClickGoalWithDifferentName,
                secondButtonClickGoal
            )).justDo()
        cpuThread.triggerActions()
        goalStore.getButtonClickGoals("SimpleActivity")
            .test()
            .assertValueCount(3)
    }

    @Test
    fun getButtonClickGoals_goalWithGivenActivityNameShouldHaveNullFragmentInfo() {
        // Same activity but in a fragment
        goalStore.updateGoals(getGoals(listOf(fragmentWithLayoutsSecondButtonClickGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getButtonClickGoals("MultipleFrameLayoutActivity")
            .test()
            .assertValueCount(0)
    }

    @Test
    fun getButtonClickGoals_returnsDefinedGoalsWithButtonClickTypeAndGivenFragmentInfo() {
        // empty goals
        goalStore.getButtonClickGoals(
            SessionFragmentInfo("fragmentName", "fragmentId", "activityName")
        )
            .test()
            .assertValueCount(0)
        verify(exactly = 0) { goalFragmentNameExtractor.getFragmentObfuscatedName(any()) }

        // same fragment different goalType
        goalStore.updateGoals(getGoals(listOf(fragmentWithLayoutsFirstReachGoal))).justDo()
        cpuThread.triggerActions()
        goalStore.getButtonClickGoals(
            SessionFragmentInfo(
                "FragmentA",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(0)

        // two different buttonClick goals with the same FragmentInfo
        goalStore.updateGoals(getGoals(listOf(
            firstButtonClickGoal,
            fragmentWithLayoutsSecondButtonClickGoal,
            fragmentWithLayoutsSecondButtonClickGoal2,
            fragmentWithLayoutsSecondButtonClickGoalWithDifferentName
        ))).justDo()
        cpuThread.triggerActions()
        goalStore.getButtonClickGoals(
            SessionFragmentInfo(
                "FragmentWithLayouts",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(3)
    }

    @Test
    fun viewGoalsByActivity_returnsTargetedViewGoalsInTheGivenActivity() {
        // empty goals
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByActivity("activityName")
            .test()
            .assertValueCount(0)

        // viewGoals of an activity in multiple Goals
        goalStore.updateGoals(getGoals(listOf(
            secondButtonClickGoal,
            fragmentWithLayoutsSecondButtonClickGoal
        ))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByActivity("SimpleActivity")
            .test()
            .assertValueCount(4)

        // same viewGoal in different goals
        goalStore.updateGoals(getGoals(listOf(firstButtonClickGoal2))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByActivity("SimpleActivity")
            .test()
            .assertValueCount(5)

        // target activity but in a fragment
        goalStore.updateGoals(getGoals(listOf(fragmentWithLayoutsSecondButtonClickGoal2))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByActivity("MultipleFrameLayoutActivity")
            .test()
            .assertValueCount(0)
    }

    @Test
    fun viewGoalsByActivity_viewGoalWithGivenActivityNameShouldHaveNullFragmentInfo() {
        // target activity but in a fragment
        goalStore.updateGoals(getGoals(listOf(fragmentWithLayoutsSecondButtonClickGoal2))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()

        goalStore.viewGoalsByActivity("MultipleFrameLayoutActivity")
            .test()
            .assertValueCount(0)
    }

    @Test
    fun viewGoalsByFragment_returnsTargetedViewGoalsInTheGivenFragment() {
        // empty goals
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByFragment(
            SessionFragmentInfo("fragmentName", "fragmentId", "activityName")
        )
            .test()
            .assertValueCount(0)

        // viewGoals of a fragment in multiple Goals
        goalStore.updateGoals(getGoals(listOf(
            fragmentBFirstReachGoal,
            fragmentWithLayoutsFirstReachGoal
        ))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByFragment(
            SessionFragmentInfo(
                "FragmentB",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(2)

        // same viewGoal in different goals
        goalStore.updateGoals(getGoals(listOf(fragmentBFirstReachGoalWithDifferentName))).justDo()
        goalStore.initializeViewGoalsDataSet()
        cpuThread.triggerActions()
        goalStore.viewGoalsByFragment(
            SessionFragmentInfo(
                "FragmentB",
                "flContainer",
                "MultipleFrameLayoutActivity"
            )
        )
            .test()
            .assertValueCount(3)
    }
}

private fun getGoals(goals: List<Goal>): List<Goal> {
    val newGoals = mutableListOf<Goal>()
    for (goal in goals){
        when (goal.goalType){
            GoalType.ACTIVITY_REACH ->
                newGoals.add(
                    ActivityReachGoal(
                        name = goal.name,
                        activityClassName = goal.activityClassName,
                        activityFunnel = (goal as ActivityReachGoal).activityFunnel,
                        viewGoals = goal.viewGoals
                    )
                )

            GoalType.FRAGMENT_REACH ->
                newGoals.add(
                    FragmentReachGoal(
                        name = goal.name,
                        goalMessageFragmentInfo = (goal as FragmentReachGoal).goalMessageFragmentInfo,
                        activityClassName = goal.activityClassName,
                        fragmentFunnel = goal.fragmentFunnel,
                        viewGoals = goal.viewGoals
                    )
                )

            GoalType.BUTTON_CLICK ->
                newGoals.add(
                    ButtonClickGoal(
                        name = goal.name,
                        buttonID = (goal as ButtonClickGoal).buttonID,
                        activityClassName = goal.activityClassName,
                        goalMessageFragmentInfo = goal.goalMessageFragmentInfo,
                        viewGoals = goal.viewGoals
                    )
                )
        }
    }
    return newGoals
}

private val firstActivityReachGoal = ActivityReachGoal(
    name = "firstActivityReach", activityClassName = "SimpleActivity", viewGoals = mutableSetOf (
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity2"
        )
    )
)

private val buttonClickGoalWithSameNameAsFirstActivityReachGoal = ButtonClickGoal(
    name = "firstActivityReach", activityClassName = "SimpleActivity", viewGoals = mutableSetOf (
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        )
    ),
    buttonID = "targetButton"
)

private val firstActivityReachGoalWithDifferentViewGoals = ActivityReachGoal(
    name = "firstActivityReach", activityClassName = "SimpleActivity", viewGoals = mutableSetOf (
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity2"
        )
    )
)

private val firstActivityReachGoalWithDifferentName = ActivityReachGoal(
    name = "firstActivityReachGoalWithDifferentName", activityClassName = "SimpleActivity", viewGoals = mutableSetOf (
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity"
        )
    )
)

private val secondActivityReachGoal = ActivityReachGoal(
    name = "secondActivityReach", activityClassName = "SimpleActivity2", viewGoals = mutableSetOf (
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true),
                ViewGoalTargetValue("goalValue", false)
            )
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switch1",
            activityClassName = "SimpleActivity",
            targetValues = listOf(
                ViewGoalTargetValue("true")
            )
        )
    )
)

private val firstButtonClickGoal = ButtonClickGoal(
    buttonID = "buttonTarget",
    name = "firstButtonClickGoal",
    activityClassName = "SimpleActivity",
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity"
        )
    )
)

private val firstButtonClickGoalWithDifferentName = ButtonClickGoal(
    buttonID = "buttonTarget",
    name = "firstButtonClickGoalWithDifferentName",
    activityClassName = "SimpleActivity",
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity2"
        )
    )
)

private val firstButtonClickGoal2 = ButtonClickGoal(
    buttonID = "buttonTarget2",
    name = "firstButtonClickGoal2",
    activityClassName = "SimpleActivity",
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity"
        )
    )
)

private val secondButtonClickGoal = ButtonClickGoal(
    buttonID = "buttonTarget",
    name = "secondButtonClickGoal",
    activityClassName = "SimpleActivity2",
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            targetValues = listOf(ViewGoalTargetValue("buttonGoalValue"))
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity2",
            targetValues = listOf(
                ViewGoalTargetValue("false")
            )
        )
    )
)

private val fragmentWithLayoutsSecondButtonClickGoal = ButtonClickGoal(
    buttonID = "buttonInnerFragment",
    name = "fragmentWithLayoutsSecondButtonClickGoal",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample2",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity2"
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer2")
        )
    )
)

private val fragmentWithLayoutsSecondButtonClickGoal2 = ButtonClickGoal(
    buttonID = "buttonInnerFragment2",
    name = "fragmentWithLayoutsSecondButtonClickGoal2",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample2",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity2"
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer2")
        )
    )
)

private val fragmentWithLayoutsSecondButtonClickGoalWithDifferentName = ButtonClickGoal(
    buttonID = "buttonInnerFragment",
    name = "fragmentASecondButtonClickGoalWithDifferentName",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample2",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity2"
        ),
        ViewGoal(
            viewType = ViewGoalType.SWITCH,
            viewID = "switchSample",
            activityClassName = "SimpleActivity"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer2")
        )
    )
)

private val fragmentWithLayoutsFirstReachGoal = FragmentReachGoal(
    name = "fragmentWithLayoutsFirstReachGoal",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentWithLayouts", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity2"
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        )
    )
)

private val fragmentBFirstReachGoal = FragmentReachGoal(
    name = "fragmentBFirstReachGoal",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer2")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        )
    )
)

private val fragmentBFirstReachGoalWithDifferentName = FragmentReachGoal(
    name = "fragmentBFirstReachGoalWithDifferentName",
    activityClassName = "MultipleFrameLayoutActivity",
    goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer"),
    viewGoals = mutableSetOf(
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "MultipleFrameLayoutActivity",
            goalMessageFragmentInfo = GoalMessageFragmentInfo(actualName = "FragmentB", fragmentId = "flContainer")
        ),
        ViewGoal(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity"
        )
    )
)
