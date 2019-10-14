package co.pushe.plus.analytics.goal

import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.analytics.Constants.ANALYTICS_ERROR_VIEW_GOAL
import co.pushe.plus.analytics.GoalFragmentInfo
import co.pushe.plus.analytics.messages.upstream.GoalReachedMessage
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
    private val sessionId = "some_id"

    @Before
    fun setUp(){
        activityReachHandler = ActivityReachHandler(postOffice)
        fragmentReachHandler = FragmentReachHandler(postOffice)
        buttonClickHandler = ButtonClickHandler(postOffice)
    }

    @Test
    fun onGoalReached_checksForViewGoalsCurrentValues() {
        // empty viewGoals: reached
        Funnel.fragmentFunnel.clear()
        val fragmentContainer = FragmentContainer("ActivityName", "fragmentId", listOf())
        Funnel.fragmentFunnel[fragmentContainer] = mutableListOf("FragmentName")
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }

        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // no targetValues: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 4) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 5) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 6) { postOffice.sendMessage(any(), any()) }

        // targetValues match current values: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 7) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 8) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withTargetValues
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 9) { postOffice.sendMessage(any(), any()) }

        // all viewGoals have not been seen, no target value: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_notSeen
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 10) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_notSeen
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 11) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_notSeen
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 12) { postOffice.sendMessage(any(), any()) }

        // all viewGoals have errors: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 13) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 14) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 15) { postOffice.sendMessage(any(), any()) }

        // some viewGoals have errors: reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 16) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 17) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // testing the targetValues being checked according to their 'ignoreCase' config: not_reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached_targetValuesCaseMisMatch
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // not targeted current values: not reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        // viewGoals have not been seen, there are target values: not reached
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }

        buttonClickGoal.viewGoalDataList = viewGoalDataList_NotReached_NotSeen
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 18) { postOffice.sendMessage(any(), any()) }
    }

    @Test
    fun onGoalReached_ignoresErroredViewGoalsInTheMessage_sendsThemInASeparateList() {
        Funnel.fragmentFunnel.clear()
        val fragmentContainer = FragmentContainer("ActivityName", "fragmentId", listOf())
        Funnel.fragmentFunnel[fragmentContainer] = mutableListOf("FragmentName")
        Funnel.activityFunnel.clear()
        Funnel.activityFunnel.add("ActivityName")

        // no errored viewGoals
        val messageSlot = slot<GoalReachedMessage>()
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 1) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 2) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_withoutTargetValues
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(capture(messageSlot), any()) }
        assert(messageSlot.captured.viewGoalsWithError.isEmpty())
        assertEquals(2, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)

        // all viewGoals have errors
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 4) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 5) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 6) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(2, messageSlot.captured.viewGoalsWithError.size)
        assert(messageSlot.captured.viewGoals.isEmpty())
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)

        // some viewGoals have errors
        activityReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 7) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.ACTIVITY_REACH)

        fragmentReachGoalWithoutFunnel.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 8) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.FRAGMENT_REACH)

        buttonClickGoal.viewGoalDataList = viewGoalDataList_reached_erroredViewGoals2
        buttonClickHandler.onGoalReached(buttonClickGoal, sessionId)
        verify(exactly = 9) { postOffice.sendMessage(capture(messageSlot), any()) }
        assertEquals(1, messageSlot.captured.viewGoalsWithError.size)
        assertEquals(1, messageSlot.captured.viewGoals.size)
        assert(messageSlot.captured.goalType == GoalType.BUTTON_CLICK)

    }

    @Test
    fun onGoalReached_activity_checksForGoalActivityFunnel() {
        Funnel.activityFunnel = mutableListOf("FirstActivity", "SecondActivity", "ActivityName")

        // empty goal activityFunnel
        activityReachHandler.onGoalReached(activityReachGoalWithoutFunnel, sessionId)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        // seen funnel
        activityReachHandler.onGoalReached(activityReachGoalWithFunnel_reached, sessionId)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }
        activityReachHandler.onGoalReached(activityReachGoalWithFunnel_reached2, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // not seen Funnel
        activityReachHandler.onGoalReached(activityReachGoalWithFunnel_notReached, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
        activityReachHandler.onGoalReached(activityReachGoalWithFunnel_notReached2, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
    }

    @Test
    fun onGoalReached_fragment_checksForGoalFragmentFunnel() {
        Funnel.fragmentFunnel.clear()
        // null fragment funnel
        val fragmentContainer = FragmentContainer("ActivityName", "fragmentId", listOf())
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 0) { postOffice.sendMessage(any()) }

        Funnel.fragmentFunnel[fragmentContainer] =
                mutableListOf("FirstFragment", "SecondFragment", "FragmentName")

        // empty goal fragment funnel
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithoutFunnel, fragmentContainer, sessionId)
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        // seen funnel
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithFunnel_reached, fragmentContainer, sessionId)
        verify(exactly = 2) { postOffice.sendMessage(any(), any()) }
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithFunnel_reached2, fragmentContainer, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }

        // not seen Funnel
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithFunnel_notReached, fragmentContainer, sessionId)
        verify(exactly = 3) { postOffice.sendMessage(any(), any()) }
        fragmentReachHandler.onGoalReached(fragmentReachGoalWithFunnel_notReached2, fragmentContainer, sessionId)
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
