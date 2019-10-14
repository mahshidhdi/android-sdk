package co.pushe.plus.analytics.goal

import android.app.Activity
import android.support.v4.app.Fragment
import android.view.View
import android.widget.Button
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.analytics.dagger.AnalyticsScope
import co.pushe.plus.analytics.LogTag.T_ANALYTICS
import co.pushe.plus.analytics.LogTag.T_ANALYTICS_GOAL
import co.pushe.plus.analytics.SessionFragmentInfo
import co.pushe.plus.analytics.ViewExtractor
import co.pushe.plus.analytics.messages.downstream.NewGoalMessage
import co.pushe.plus.analytics.messages.downstream.RemoveGoalMessage
import co.pushe.plus.analytics.utils.ButtonOnClickListener
import co.pushe.plus.analytics.utils.getOnClickListener
import co.pushe.plus.utils.assertCpuThread
import co.pushe.plus.utils.log.Plog
import javax.inject.Inject

@AnalyticsScope
class GoalProcessManager @Inject constructor (
    private val activityReachHandler: ActivityReachHandler,
    private val fragmentReachHandler: FragmentReachHandler,
    private val buttonClickHandler: ButtonClickHandler,
    private val store: GoalStore,
    val moshi: PusheMoshi
)
{
    fun initialize() {
        store.initializeViewGoalsDataSet()
        store.initializeGoalsDataSet()
    }

    /**
     * Called when a [NewGoalMessage] is received.
     *
     * Extracts goals from the message and calls [GoalStore] to update [GoalStore.definedGoals]
     *
     * @see [GoalStore.updateGoals]
     */
    fun updateGoals(goalMessage: NewGoalMessage) {
        val goals: MutableList<Goal> = mutableListOf()
        goals.addAll(goalMessage.activityReachGoals)
        goals.addAll(goalMessage.fragmentReachGoals)
        goals.addAll(goalMessage.buttonClickGoals)

        store.updateGoals(goals)
    }

    /**
     * Called when a new activity is resumed
     * Called on CPUThread
     *
     * Gets all activityReachedGoals for the given activity from [GoalStore] and after updating their
     * viewGoals, calls [ActivityReachHandler] for sending the messages
     *
     */
    fun manageActivityReachGoals(activity: Activity, sessionId: String) {
        assertCpuThread()

        val goals = store.getActivityReachGoals(activity.javaClass.simpleName)
        goals.forEach { goal ->
            if (goal.viewGoalDataList.isNotEmpty()) {
                store.updateViewGoalValues(goal.viewGoalDataList, activity)
            }
            activityReachHandler.onGoalReached(goal, sessionId)
        }
    }

    /**
     * Called when a new fragment is resumed
     * Called on mainThread
     *
     * Gets all fragmentReachedGoals for the given sessionFragmentInfo from [GoalStore] and updates their viewGoals
     */
    fun updateFragmentReachViewGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment) {
        val goals = store.getFragmentReachGoals(sessionFragmentInfo)
        goals.forEach { goal ->
            if (goal.viewGoalDataList.isNotEmpty()) {
                store.updateViewGoalValues(goal.viewGoalDataList, fragment)
            }
        }
    }

    /**
     * Called when a new fragment is resumed
     * Called on CPUThread
     *
     * Gets all fragmentReachedGoals for the given sessionFragmentInfo from [GoalStore] and calls
     * [FragmentReachHandler] for sending the messages
     */
    fun handleFragmentReachMessage(sessionFragmentInfo: SessionFragmentInfo, fragmentContainer: FragmentContainer, sessionId: String){
        assertCpuThread()
        val goals = store.getFragmentReachGoals(sessionFragmentInfo)
        goals.forEach { goal ->
            fragmentReachHandler.onGoalReached(goal, fragmentContainer, sessionId)
        }
    }

    /**
     * Called when a new activity is resumed
     * Called on CPUThread
     *
     * Gets all buttonClickedGoals for the given activity from the store and sets listeners for each
     * target button
     *
     * @see [setButtonClickListener]
     */
    fun manageButtonClickGoals(activity: Activity, sessionId: String) {
        assertCpuThread()

        val buttonClickGoals = store.getButtonClickGoals(activity.javaClass.simpleName)
        if (buttonClickGoals.isNotEmpty()) {
            setButtonClickListener(buttonClickGoals, activity, sessionId)
        }
    }

    /**
     * Called when a new fragment is resumed
     * Called on mainThread
     *
     * Gets all buttonClickedGoals for the given sessionFragmentInfo from the store and sets listeners for
     * each target button
     *
     */
    fun manageButtonClickGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment, sessionId: String) {
        val buttonClickGoals = store.getButtonClickGoals(sessionFragmentInfo)
        if (buttonClickGoals.isNotEmpty()) {
            setButtonClickListener(buttonClickGoals, fragment, sessionId)
        }
    }

    /**
     * Called when an activity is paused
     * Called on CPUThread
     *
     * Gets all viewGoals in the given activity from [GoalStore] and updates their values
     *
     * The viewGoals are the ones with null fragmentInfo
     */
    fun updateActivityViewGoals(activity: Activity) {
        assertCpuThread()

        val viewGoals = store.viewGoalsByActivity(activity.javaClass.simpleName)

        if (viewGoals.isNotEmpty()) {
            store.updateViewGoalValues(viewGoals, activity)
        }
    }

    /**
     * Called when an activity is paused
     * Called on mainThread
     *
     * Gets all viewGoals in the given sessionFragmentInfo from [GoalStore] and updates their values
     *
     */
    fun updateFragmentViewGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment) {
        val viewGoals = store.viewGoalsByFragment(sessionFragmentInfo)

        if (viewGoals.isNotEmpty()) {
            store.updateViewGoalValues(viewGoals, fragment)
        }
    }

    fun removeGoals(goalsRemoveMessage: RemoveGoalMessage) {
        store.removeGoals(goalsRemoveMessage.GoalNames)
    }

    /**
     * For each buttonClickGoal given, extracts the target button view from the activity, retrieves
     * current listener set for that button and if current listener has been set by the host app
     * developer (not of type [ButtonOnClickListener]) sets a listener for the button
     * first invoking the current listener actions
     */
    private fun setButtonClickListener(buttonClickGoalsDataList: List<ButtonClickGoalData>, activity: Activity, sessionId: String) {
        for (goal in buttonClickGoalsDataList) {

            val view: View? = ViewExtractor.extractView(getButtonViewData(goal), activity)

            if (view != null) {
                if (view !is Button) {
                    Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL, "Setting listener for button-click goal failed, view with the given id is not a button",
                            "ID" to goal.buttonID,
                            "View Type" to view.javaClass.simpleName
                    )
                    return
                }
                val oldListener = getOnClickListener(view)
                if (oldListener !is ButtonOnClickListener){
                    view.setOnClickListener(ButtonOnClickListener {
                        oldListener?.onClick(view)
                        store.updateViewGoalValues(goal.viewGoalDataList, activity)
                        buttonClickHandler.onGoalReached(goal, sessionId)
                    })
                }
            }
        }
    }

    private fun setButtonClickListener(buttonClickGoalsDataList: List<ButtonClickGoalData>, fragment: Fragment, sessionId: String) {
        for (goal in buttonClickGoalsDataList) {

            val view: View? = ViewExtractor.extractView(getButtonViewData(goal), fragment)
            if (view != null) {
                if (view !is Button) {
                    Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL, "Setting listener for button-click goal failed, view with the given id is not a button",
                            "id" to goal.buttonID,
                            "viewType" to view.javaClass.simpleName
                    )
                    return
                }
                val oldListener = getOnClickListener(view)
                if (oldListener !is ButtonOnClickListener){
                    view.setOnClickListener(ButtonOnClickListener {
                        oldListener?.onClick(view)
                        store.updateViewGoalValues(goal.viewGoalDataList, fragment)
                        buttonClickHandler.onGoalReached(goal, sessionId)
                    })
                }
            }
        }
    }

    /**
     * Builds and returns a viewGoalData object for the button in given ButtonClickGoal
     */
    private fun getButtonViewData(goal: ButtonClickGoalData): ViewGoalData {
        return ViewGoalData(
                    parentGoalName = "",
                    targetValues = listOf(),
                    viewType = ViewGoalType.TEXT_VIEW,
                    viewID = goal.buttonID,
                    activityClassName = goal.activityClassName,
                    goalFragmentInfo = goal.goalFragmentInfo
                )
    }
}
