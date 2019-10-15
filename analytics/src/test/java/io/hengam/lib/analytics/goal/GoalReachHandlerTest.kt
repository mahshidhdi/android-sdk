package io.hengam.lib.analytics.goal

import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.analytics.Constants.ANALYTICS_ERROR_VIEW_GOAL
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.messages.upstream.GoalReachedMessage
import io.hengam.lib.analytics.session.SessionIdProvider
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.test.TestUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoalReachHandlerTest {
    private val postOffice: PostOffice = mockk(relaxed = true)

    private lateinit var activityReachHandler: ActivityReachHandler
    private lateinit var fragmentReachHandler: FragmentReachHandler
    private lateinit var buttonClickHandler: ButtonClickHandler

    private val sessionIdProvider: SessionIdProvider = mockk(relaxed = true)

    private val sessionId = "some_id"
    private val fragmentContainerId = "someContainerId"

    private val cpuThread = TestUtils.mockCpuThread()

    @Before
    fun setUp(){

        every { sessionIdProvider.sessionId } returns sessionId

        activityReachHandler = ActivityReachHandler(postOffice, sessionIdProvider)
        fragmentReachHandler = FragmentReachHandler(postOffice, sessionIdProvider)
        buttonClickHandler = ButtonClickHandler(postOffice, sessionIdProvider)
    }

    @Test
    fun onGoalReached_checksForViewGoalsCurrentValues() {
        Funnel.fragmentFunnel.clear()
        Funnel.activityFunnel.clear()
        Funnel.activityFunnel.add("ActivityName")
        Funnel.fragmentFunnel[fragmentContainerId] = mutableListOf("FragmentName")

        // empty viewGoals: reached
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }

        buttonGoalReached(buttonClickGoal)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // no targetValues: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 4) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 5) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 6) { postOffice.sendMessage(any(), any()) }

        // targetValues match current values: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 7) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 8) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 9) { postOffice.sendMessage(any(), any()) }

        // all viewGoals have not been seen, no target value: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_notSeen
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 10) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_notSeen
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 11) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_notSeen
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 12) { postOffice.sendMessage(any(), any()) }

        // all viewGoals have errors: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 13) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 14) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 15) { postOffice.sendMessage(any(), any()) }

        // some viewGoals have errors: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 16) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 17) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // testing the targetValues being checked according to their 'ignoreCase' config: not_reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // not targeted current values: not reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // viewGoals have not been seen, there are target values: not reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }
    }

    private fun buttonGoalReached(goal: ButtonClickGoalData) {
        buttonClickHandler.onGoalReached(goal).justDo()
        cpuThread.triggerActions()
    }

    private fun fragmentGoalReached(goal: FragmentReachGoalData) {
        fragmentReachHandler.onGoalReached(goal, fragmentContainerId).justDo()
        cpuThread.triggerActions()
    }

    private fun activityGoalReached(goal: ActivityReachGoalData) {
        activityReachHandler.onGoalReached(goal).justDo()
        cpuThread.triggerActions()
    }

    @Test
    fun onGoalReached_ignoresErroredViewGoalsInTheMessage_sendsThemInASeparateList() {
        Funnel.fragmentFunnel.clear()
        Funnel.fragmentFunnel[fragmentContainerId] = mutableListOf("FragmentName")
        Funnel.activityFunnel.clear()
        Funnel.activityFunnel.add("ActivityName")

        // no errored viewGoals
        val messageSlot = slot<GoalReachedMessage>()
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 1) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 2) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 3) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)

        // all viewGoals have errors
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 4) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 5) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 6) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)

        // some viewGoals have errors
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 7) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 8) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        buttonGoalReached(buttonClickGoal)
        verify(exactly = 9) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)
    }

    @Test
    fun onGoalReached_activity_checksForGoalActivityFunnel() {
        Funnel.activityFunnel = mutableListOf("FirstActivity", "SecondActivity", "ActivityName")

        // empty goal activityFunnel
        activityGoalReached(activityReachGoalWithoutFunnel)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        // seen funnel
        activityGoalReached(activityReachGoalWithFunnel_reached)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }
        activityGoalReached(activityReachGoalWithFunnel_reached2)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // not seen Funnel
        activityGoalReached(activityReachGoalWithFunnel_notReached)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
        activityGoalReached(activityReachGoalWithFunnel_notReached2)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
    }

    @Test
    fun onGoalReached_fragment_checksForGoalFragmentFunnel() {
        Funnel.fragmentFunnel.clear()

        // null fragment funnel
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 0) { postOffice.sendMessage(any()) }

        Funnel.fragmentFunnel[fragmentContainerId] =
                mutableListOf("FirstFragment", "SecondFragment", "FragmentName")

        // empty goal fragment funnel
        fragmentGoalReached(fragmentReachGoalWithoutFunnel)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        // seen funnel
        fragmentGoalReached(fragmentReachGoalWithFunnel_reached)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }
        fragmentGoalReached(fragmentReachGoalWithFunnel_reached2)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // not seen Funnel
        fragmentGoalReached(fragmentReachGoalWithFunnel_notReached)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
        fragmentGoalReached(fragmentReachGoalWithFunnel_notReached2)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
    }
}

private val viewGoalDataList_reached_notSeen =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(),
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(),
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_reached_withoutTargetValues =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(),
            currentValue = "targetValue",
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(),
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )


private val viewGoalDataList_reached_withTargetValues =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", false),
                ViewGoalTargetValue("targetValue2", true)
            ),
            currentValue = "targetValue",
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true)
            ),
            currentValue = "TargetValue",
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_NotReached_targetValuesCaseMisMatch =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", false),
                ViewGoalTargetValue("targetValue2")
            ),
            currentValue = "TargetValue",
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true)
            ),
            currentValue = "TargetValue",
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_NotReached =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", false),
                ViewGoalTargetValue("targetValue2")
            ),
            currentValue = "value",
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true)
            ),
            currentValue = "TargetValue",
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_NotReached_NotSeen =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", false),
                ViewGoalTargetValue("targetValue2")
            ),
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true)
            ),
            currentValue = "TargetValue",
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_reached_erroredViewGoals =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true),
                ViewGoalTargetValue("targetValue2")
            ),
            currentValue = ANALYTICS_ERROR_VIEW_GOAL,
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(),
            currentValue = ANALYTICS_ERROR_VIEW_GOAL,
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val viewGoalDataList_reached_erroredViewGoals2 =
    listOf(
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "tvSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue", true),
                ViewGoalTargetValue("targetValue2")
            ),
            currentValue = ANALYTICS_ERROR_VIEW_GOAL,
            goalFragmentInfo = null
        ),
        ViewGoalData(
            viewType = ViewGoalType.TEXT_VIEW,
            viewID = "editTextSample",
            activityClassName = "SimpleActivity",
            parentGoalName = "goalName",
            targetValues = listOf(
                ViewGoalTargetValue("targetValue")
            ),
            currentValue = "targetValue",
            goalFragmentInfo = GoalFragmentInfo("fragmentName", "a", "fragmentID", "activityName")
        )
    )

private val activityReachGoalWithoutFunnel = ActivityReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf()
)

private val activityReachGoalWithFunnel_reached = ActivityReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    activityFunnel = listOf("FirstActivity", "SecondActivity")
)

private val activityReachGoalWithFunnel_notReached = ActivityReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    activityFunnel = listOf("FirstActivity")
)

private val activityReachGoalWithFunnel_reached2 = ActivityReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    activityFunnel = listOf("SecondActivity")
)

private val activityReachGoalWithFunnel_notReached2 = ActivityReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    activityFunnel = listOf("FirstActivity", "SecondActivity", "ThirdActivity")
)

private val fragmentReachGoalWithoutFunnel = FragmentReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    goalFragmentInfo = GoalFragmentInfo(
        actualName = "fragmentName",
        obfuscatedName = "a",
        fragmentId = "fragmentId",
        activityName = "ActivityName"
    )
)

private val fragmentReachGoalWithFunnel_reached = FragmentReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    fragmentFunnel = listOf("FirstFragment", "SecondFragment"),
    goalFragmentInfo = GoalFragmentInfo(
        actualName = "fragmentName",
        obfuscatedName = "a",
        fragmentId = "fragmentId",
        activityName = "ActivityName"
    )
)

private val fragmentReachGoalWithFunnel_notReached = FragmentReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    fragmentFunnel = listOf("FirstFragment"),
    goalFragmentInfo = GoalFragmentInfo(
        actualName = "fragmentName",
        obfuscatedName = "a",
        fragmentId = "fragmentId",
        activityName = "ActivityName"
    )
)

private val fragmentReachGoalWithFunnel_reached2 = FragmentReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    fragmentFunnel = listOf("SecondFragment"),
    goalFragmentInfo = GoalFragmentInfo(
        actualName = "fragmentName",
        obfuscatedName = "a",
        fragmentId = "fragmentId",
        activityName = "ActivityName"
    )
)

private val fragmentReachGoalWithFunnel_notReached2 = FragmentReachGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    fragmentFunnel = listOf("FirstFragment", "SecondFragment", "ThirdFragment"),
    goalFragmentInfo = GoalFragmentInfo(
        actualName = "fragmentName",
        obfuscatedName = "a",
        fragmentId = "fragmentId",
        activityName = "ActivityName"
    )
)

private val buttonClickGoal = ButtonClickGoalData(
    name = "goalName",
    activityClassName = "ActivityName",
    viewGoalDataList = listOf(),
    buttonID = "buttonId"
)
