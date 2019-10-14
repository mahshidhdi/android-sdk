package co.pushe.plus.analytics.goal

import android.app.Activity
import android.content.Context
import android.support.v4.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import co.pushe.plus.analytics.Constants
import co.pushe.plus.analytics.GoalFragmentInfo
import co.pushe.plus.analytics.LogTag.T_ANALYTICS
import co.pushe.plus.analytics.LogTag.T_ANALYTICS_GOAL
import co.pushe.plus.analytics.SessionFragmentInfo
import co.pushe.plus.analytics.ViewExtractor
import co.pushe.plus.analytics.dagger.AnalyticsScope
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.log.Plog
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AnalyticsScope
class GoalStore @Inject constructor(
        val context: Context,
        val moshi: PusheMoshi,
        private val goalFragmentObfuscatedNameExtractor: GoalFragmentObfuscatedNameExtractor,
        pusheStorage: PusheStorage
) {
    val definedGoals: MutableList<Goal> = pusheStorage.createStoredList("defined_goals", Goal::class.java)

    /**
     * A list containing all the [ViewGoalData]s for currently defined goals
     *
     * For the purpose of thread-safety, a [ConcurrentHashMap] is used instead of list.
     * The value for all the elements in the map is false and should be ignored
     *
     * Gets its data on initialize of the app
     * @see [extractViewGoalsDataSet]
     */
    var definedViewGoalsDataSet = ConcurrentHashMap<ViewGoalData, Boolean>()

    /**
     * A list containing all the [GoalData]s for currently defined goals
     *
     * For the purpose of thread-safety, a [ConcurrentHashMap] is used instead of list.
     * The value for all the elements in the map is false and should be ignored
     *
     * Gets its data on initialize of the app
     * @see [extractGoalsDataSet]
     */
    var definedGoalsDataSet = ConcurrentHashMap<GoalData, Boolean>()

    /**
     * must be called on initialize
     */
    fun initializeViewGoalsDataSet() {
        extractViewGoalsDataSet(definedGoals)
    }
    /**
     * must be called on initialize, after [initializeViewGoalsDataSet]
     */
    fun initializeGoalsDataSet() {
        extractGoalsDataSet(definedGoals)
    }

    /**
     * Called by [GoalProcessManager] when a newGoalMessage in received (@link [GoalProcessManager.updateGoals])
     *
     * Adds the new goals to [definedGoals] list
     * If there is already a goal with the same name, the goal will be replaced
     * ViewGoals of the new goals will be extracted and added to [definedViewGoalsDataSet]
     * GoalsDatas of the new goals will be built and added to [definedGoalsDataSet]
     *
     */
    fun updateGoals(goals: List<Goal>) {
        for (goal in goals) {
            val currentGoals = definedGoals.filter {it.name == goal.name}
            if (currentGoals.isNotEmpty()) {
                definedGoals.remove(currentGoals[0])
                val goalData = definedGoalsDataSet.keys.filter { it.name == currentGoals[0].name }
                definedGoalsDataSet.remove(goalData[0])
                val goalViewGoalsDataSet = definedViewGoalsDataSet.keys.filter { it.parentGoalName == currentGoals[0].name }
                for (viewGoalData in goalViewGoalsDataSet){
                    definedViewGoalsDataSet.remove(viewGoalData)
                }
            }
            definedGoals.add(goal)
        }
        extractViewGoalsDataSet(goals)
        extractGoalsDataSet(goals)
        Plog.info(T_ANALYTICS, T_ANALYTICS_GOAL, "Analytics goals have been updated",
            "Number of Goals" to definedGoals.size,
            "Goals" to definedGoals
        )
    }

    /**
     * Called by [GoalProcessManager] when a removeGoalMessage in received (@link [GoalProcessManager.removeGoals])
     *
     * Removes the given goals from [definedGoals] list and [definedGoalsDataSet]
     * Removes the ViewGoalDatas of the given goals from [definedViewGoalsDataSet]
     *
     */
    fun removeGoals(goalNames: Set<String>) {
        val goalsToBeRemoved = definedGoals.filter { goalNames.contains(it.name) }
        if (goalsToBeRemoved.size < goalNames.size){
            Plog.warn(T_ANALYTICS, T_ANALYTICS_GOAL, "Could not remove some analytics goals since they could not be found")
        }
        val goalsData = definedGoalsDataSet.keys.filter { goalNames.contains(it.name) }
        for (goalData in goalsData){
            definedGoalsDataSet.remove(goalData)
        }
        val viewGoalsDataSet = definedViewGoalsDataSet.keys.filter { goalNames.contains(it.parentGoalName) }
        for (viewGoalData in viewGoalsDataSet){
            definedViewGoalsDataSet.remove(viewGoalData)
        }

        for (goal in goalsToBeRemoved){
            definedGoals.remove(goal)
        }
    }

    /**
     * Called on initialize (@link [initializeViewGoalsDataSet]) and when new goals are defined (@link [updateGoals])
     *
     * Builds a [ViewGoalData] object for each viewGoal in the given goals and adds it to [definedViewGoalsDataSet]
     */
    private fun extractViewGoalsDataSet(goals: List<Goal>) {
        var viewGoalFragmentObfuscatedName: String?
        var goalGoalFragmentInfo: GoalFragmentInfo?
        for (goal in goals){
            for (viewGoal in goal.viewGoals) {
                if (viewGoal.goalMessageFragmentInfo == null) {
                    goalGoalFragmentInfo = null
                } else {
                    viewGoalFragmentObfuscatedName = goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(viewGoal.goalMessageFragmentInfo)
                    goalGoalFragmentInfo = GoalFragmentInfo(
                        viewGoal.goalMessageFragmentInfo.actualName,
                        viewGoalFragmentObfuscatedName,
                        viewGoal.goalMessageFragmentInfo.fragmentId,
                        viewGoal.activityClassName
                    )
                }
                definedViewGoalsDataSet[ViewGoalData(
                        parentGoalName = goal.name,
                        targetValues = viewGoal.targetValues,
                        viewType = viewGoal.viewType,
                        viewID = viewGoal.viewID,
                        activityClassName = viewGoal.activityClassName,
                        goalFragmentInfo = goalGoalFragmentInfo)] = false
            }
        }
    }

    /**
     * Called on initialize (@link [initializeGoalsDataSet]) and when new goals are defined (@link [updateGoals])
     *
     * Builds a [GoalData] object for each Goal in the given list and adds it to [definedGoalsDataSet]
     */
    private fun extractGoalsDataSet(goals: List<Goal>) {
        for (goal in goals){
            when (goal.goalType) {
                GoalType.ACTIVITY_REACH -> {
                    definedGoalsDataSet[ActivityReachGoalData(
                        GoalType.ACTIVITY_REACH,
                        goal.name,
                        goal.activityClassName,
                        (goal as ActivityReachGoal).activityFunnel,
                        definedViewGoalsDataSet.keys.filter { it.parentGoalName == goal.name }
                    )] = false
                }
                GoalType.FRAGMENT_REACH -> {
                    definedGoalsDataSet[FragmentReachGoalData(
                        GoalType.FRAGMENT_REACH,
                        (goal as FragmentReachGoal).name,
                        goal.activityClassName,
                        GoalFragmentInfo(goal.goalMessageFragmentInfo.actualName,
                            goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(goal.goalMessageFragmentInfo),
                            goal.goalMessageFragmentInfo.fragmentId,
                            goal.activityClassName),
                        goal.fragmentFunnel,
                        definedViewGoalsDataSet.keys.filter { it.parentGoalName == goal.name }
                    )] = false
                }
                GoalType.BUTTON_CLICK -> {
                    val goalMessageFragmentInfo = (goal as ButtonClickGoal).goalMessageFragmentInfo
                    val goalGoalFragmentInfo =
                        if (goalMessageFragmentInfo == null) null
                        else GoalFragmentInfo(goalMessageFragmentInfo.actualName,
                            goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(goalMessageFragmentInfo),
                            goalMessageFragmentInfo.fragmentId,
                            goal.activityClassName)
                    definedGoalsDataSet[ButtonClickGoalData(
                        GoalType.BUTTON_CLICK,
                        goal.name,
                        goal.activityClassName,
                        goalGoalFragmentInfo,
                        goal.buttonID,
                        definedViewGoalsDataSet.keys.filter { it.parentGoalName == goal.name }
                    )] = false
                }
            }
        }
    }

    fun getActivityReachGoals(activityName: String): List<ActivityReachGoalData> {
        return definedGoalsDataSet.keys()
            .asSequence()
            .filter {
                it is ActivityReachGoalData &&
                        it.activityClassName == activityName
            }.map { it as ActivityReachGoalData }
            .toList()
    }

    fun getFragmentReachGoals(sessionFragmentInfo: SessionFragmentInfo): List<FragmentReachGoalData> {
        return definedGoalsDataSet.keys()
            .asSequence()
            .filter {
                it is FragmentReachGoalData &&
                        it.activityClassName == sessionFragmentInfo.activityName &&
                        (it.goalFragmentInfo.actualName == sessionFragmentInfo.fragmentName ||
                                it.goalFragmentInfo.obfuscatedName == sessionFragmentInfo.fragmentName) &&
                        it.goalFragmentInfo.fragmentId == sessionFragmentInfo.fragmentId
            }.map { it as FragmentReachGoalData }
            .toList()
    }

    fun getButtonClickGoals(activityName: String): List<ButtonClickGoalData> {
        return definedGoalsDataSet.keys()
            .asSequence()
            .filter {
                it is ButtonClickGoalData &&
                        it.activityClassName == activityName &&
                        it.goalFragmentInfo == null
            }.map { it as ButtonClickGoalData }
            .toList()
    }

    fun getButtonClickGoals(sessionFragmentInfo: SessionFragmentInfo): List<ButtonClickGoalData> {
        return definedGoalsDataSet.keys()
            .asSequence()
            .filter {
                it is ButtonClickGoalData &&
                        it.goalFragmentInfo != null &&
                        it.activityClassName == sessionFragmentInfo.activityName &&
                        it.goalFragmentInfo.fragmentId == sessionFragmentInfo.fragmentId &&
                        (it.goalFragmentInfo.actualName == sessionFragmentInfo.fragmentName ||
                                it.goalFragmentInfo.obfuscatedName == sessionFragmentInfo.fragmentName)
            }.map { it as ButtonClickGoalData }
            .toList()
    }

    fun viewGoalsByActivity(activityName: String): List<ViewGoalData> {
        return definedViewGoalsDataSet.keys.filter {
            it.goalFragmentInfo == null &&
                    it.activityClassName == activityName
        }
    }

    fun viewGoalsByFragment(sessionFragmentInfo: SessionFragmentInfo): List<ViewGoalData> {
        return definedViewGoalsDataSet.keys.filter {
            it.goalFragmentInfo != null &&
                    it.goalFragmentInfo.fragmentId == sessionFragmentInfo.fragmentId &&
                    (it.goalFragmentInfo.actualName == sessionFragmentInfo.fragmentName ||
                            it.goalFragmentInfo.obfuscatedName == sessionFragmentInfo.fragmentName)
        }
    }

    /**
     * Extracts the view of each [ViewGoalData] in the list given from the activity and updates
     * their [ViewGoalData.currentValue] with the value of the view
     */
    fun updateViewGoalValues(viewGoalDataSet: List<ViewGoalData>, activity: Activity) {
        var view: View?
        for (viewGoalData in viewGoalDataSet) {
            view = ViewExtractor.extractView(viewGoalData, activity)
            if (view != null) {
                updateViewGoalValue(view, viewGoalData)
            }
        }
    }

    /**
     * Extracts the view of each [ViewGoalData] in the list given from the fragment and updates
     * their [ViewGoalData.currentValue] with the value of the view
     */
    fun updateViewGoalValues(viewGoalDataSet: List<ViewGoalData>, fragment: Fragment) {
        var view: View?
        for (viewGoalData in viewGoalDataSet) {
            view = ViewExtractor.extractView(viewGoalData, fragment)
            if (view != null) {
                updateViewGoalValue(view, viewGoalData)
            }
        }
    }

    /**
     * updates [ViewGoalData.currentValue] of the given viewGoalData with the value of the view
     *
     * errors if the type of the [ViewGoalData] does not match the type of the view extracted
     */
    private fun updateViewGoalValue(view: View, viewGoalData: ViewGoalData) {
        var typeMisMatch = false
        when (viewGoalData.viewType) {
            ViewGoalType.TEXT_VIEW -> {
                if (view is TextView) {
                    viewGoalData.currentValue = view.text.toString()
                }else {
                    typeMisMatch = true
                }
            }
            ViewGoalType.SWITCH -> {
                if (view is Switch) {
                    viewGoalData.currentValue = view.isChecked.toString()
                } else {
                    typeMisMatch = true
                }
            }
            ViewGoalType.BUTTON -> {
                if (view is Button) {
                    viewGoalData.currentValue = view.text.toString()
                } else {
                    typeMisMatch = true
                }
            }
        }
        if (typeMisMatch) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL, "Type mismatch occurred while processing updated view goal data, the view goal will be ignored",
                "Goal Name" to viewGoalData.parentGoalName,
                "View Id" to viewGoalData.viewID,
                "Expected Type" to viewGoalData.viewType,
                "Actual Type" to view.javaClass.simpleName
            )
            viewGoalData.currentValue = Constants.ANALYTICS_ERROR_VIEW_GOAL
        }
    }
}


