package io.hengam.lib.analytics.goal

import io.hengam.lib.analytics.Constants.ANALYTICS_ERROR_VIEW_GOAL
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_GOAL
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.messages.upstream.GoalReachedMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.log.Plog
import javax.inject.Inject

@AnalyticsScope
class ActivityReachHandler @Inject constructor (
    private val postOffice: PostOffice
) {
    fun onGoalReached(goal: ActivityReachGoalData, sessionId: String) {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_GOAL, "Checking whether Activity goal has been reached")

        if (!areViewGoalsReached(goal.viewGoalDataList)) return

        if (!checkFunnels(goal.activityFunnel, Funnel.activityFunnel)) return

        val viewGoalValues = getViewGoalValues(goal.viewGoalDataList)
        val viewGoalsWithError = getViewGoalsWithError(goal.viewGoalDataList)

        val message = GoalReachedMessage(
            sessionId,
            GoalType.ACTIVITY_REACH,
            goal.name,
            viewGoalValues,
            viewGoalsWithError,
            Funnel.activityFunnel,
            listOf()
        )

        Plog.info(T_ANALYTICS, T_ANALYTICS_GOAL, "Activity goal has been reached", "Session Id" to sessionId)
        postOffice.sendMessage(message = message, sendPriority = SendPriority.SOON)
    }
}

@AnalyticsScope
class FragmentReachHandler @Inject constructor (
    private val postOffice: PostOffice
) {
    fun onGoalReached(goal: FragmentReachGoalData, fragmentContainer: FragmentContainer, sessionId: String) {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_GOAL, "Checking whether Fragment goal has been reached")

        if (!areViewGoalsReached(goal.viewGoalDataList)) return

        val seenFragmentFunnel = Funnel.fragmentFunnel[fragmentContainer]

        if (seenFragmentFunnel == null) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL, "Getting goal-fragment's funnel failed. The value is null","key" to fragmentContainer)
            return
        }
        if (!checkFunnels(goal.fragmentFunnel, seenFragmentFunnel)) return

        val viewGoalValues = getViewGoalValues(goal.viewGoalDataList)
        val viewGoalsWithError = getViewGoalsWithError(goal.viewGoalDataList)

        val message = GoalReachedMessage (
            sessionId,
            GoalType.FRAGMENT_REACH,
            goal.name,
            viewGoalValues,
            viewGoalsWithError,
            Funnel.activityFunnel,
            seenFragmentFunnel
        )

        Plog.info(T_ANALYTICS, T_ANALYTICS_GOAL, "Fragment goal has been reached", "Session Id" to sessionId)

        postOffice.sendMessage(message = message, sendPriority = SendPriority.SOON)
    }
}

@AnalyticsScope
class ButtonClickHandler @Inject constructor (
    private val postOffice: PostOffice
) {
    fun onGoalReached(goal: ButtonClickGoalData, sessionId: String) {

        Plog.trace(T_ANALYTICS, T_ANALYTICS_GOAL, "Checking whether button goal has been reached")

        if (!areViewGoalsReached(goal.viewGoalDataList)) return

        val viewGoalValues = getViewGoalValues(goal.viewGoalDataList)
        val viewGoalsWithError = getViewGoalsWithError(goal.viewGoalDataList)

        val message = GoalReachedMessage(
            sessionId,
            GoalType.BUTTON_CLICK,
            goal.name,
            viewGoalValues,
            viewGoalsWithError,
            listOf(),
            listOf()
        )

        Plog.info(T_ANALYTICS, T_ANALYTICS_GOAL, "Button goal has been reached","Session Id" to sessionId)
        postOffice.sendMessage(message = message, sendPriority = SendPriority.SOON)
    }
}

/**
 * Called before sending a goalReachedMessage
 *
 * Checks whether the current values of the goal's viewGoals match any of their target values
 */
private fun areViewGoalsReached(viewGoalDataList: List<ViewGoalData>): Boolean {
    var areReached: Boolean
    for (viewGoalData in viewGoalDataList) {
        if (viewGoalData.currentValue == ANALYTICS_ERROR_VIEW_GOAL ||
                viewGoalData.targetValues.isEmpty()) continue

        // if view has not been seen
        if (viewGoalData.currentValue == null) return false

        areReached = false
        for (targetValue in viewGoalData.targetValues) {
            if (viewGoalData.currentValue.equals(targetValue.targetValue, ignoreCase = targetValue.ignoreCase)) {
                areReached = true
            }
        }
        if (!areReached) return false
    }
    return true
}

/**
 * Called before sending a goalReachedMessage to check whether the target funnel of the goal has been seen
 *
 * @param goalFunnel The target funnel set by the host-app developer for the goal being handled
 * @param seenFunnel The funnel seen bu the user until reaching the goal screen. It's either [Funnel.activityFunnel]
 * or [Funnel.fragmentFunnel]
 *
 * @return true if the target funnel matches the end of seen funnel and false otherwise
 */
private fun checkFunnels(goalFunnel: List<String>, seenFunnel: MutableList<String>): Boolean {
    var seenScreens = seenFunnel.dropLast(1)

    for (screenName in goalFunnel.reversed()) {
        if (seenScreens.last() != screenName) {
            return false
        }
        seenScreens = seenScreens.dropLast(1)
    }
    return true
}

/**
 * Every [GoalReachedMessage] has a field called viewGoals, a map containing the goal's [ViewGoal]s values.
 * This map only contains the viewGoals found without any error
 *
 * To be able to identify each viewGoal uniquely in the map, the key for each viewGoal
 * with fragment info is a string with the format:
 * "[ViewGoalData.viewID]_[ViewGoalData.activityClassName]_[ViewGoalData.GoalFragmentInfo.fragmentId]_[ViewGoalData.GoalFragmentInfo.actualName]"
 * and for each viewGoal without fragment info with the format:
 * "[ViewGoalData.viewID]_[ViewGoalData.activityClassName]_"
 *
 * This function builds and returns the map from the goal's [ViewGoalData] list
 */
private fun getViewGoalValues(viewGoalDataList: List<ViewGoalData>): Map<String, String?> {
    val viewGoalValues = mutableMapOf<String, String?>()
    var viewGoalKey: String

    for (viewGoalData in viewGoalDataList) {
        if (viewGoalData.currentValue != ANALYTICS_ERROR_VIEW_GOAL) {
            viewGoalKey = "${viewGoalData.viewID}_${viewGoalData.activityClassName}_"
            if (viewGoalData.goalFragmentInfo != null) {
                viewGoalKey += "${viewGoalData.goalFragmentInfo.fragmentId}_${viewGoalData.goalFragmentInfo.actualName}"
            }
            viewGoalValues[viewGoalKey] = viewGoalData.currentValue
        }
    }
    return viewGoalValues
}

/**
 * Every [GoalReachedMessage] has a field called viewGoalsWithError, a list containing the goal's [ViewGoal]s
 * which there has been an error retrieving them from their layouts
 *
 * This function builds and returns the list from the goal's [ViewGoalData] list
 */
private fun getViewGoalsWithError(viewGoalDataList: List<ViewGoalData>): List<ViewGoal> {
    val viewGoalsWithError = mutableListOf<ViewGoal>()
    for (viewGoalData in viewGoalDataList){
        if (viewGoalData.currentValue == ANALYTICS_ERROR_VIEW_GOAL){
            val goalFragmentInfo:GoalMessageFragmentInfo? =
                if (viewGoalData.goalFragmentInfo == null) null
                else GoalMessageFragmentInfo(
                        actualName = viewGoalData.goalFragmentInfo.actualName,
                        fragmentId = viewGoalData.goalFragmentInfo.fragmentId)
            viewGoalsWithError.add(
                ViewGoal(
                    viewGoalData.viewType,
                    viewGoalData.targetValues,
                    viewGoalData.viewID,
                    viewGoalData.activityClassName,
                    goalFragmentInfo
                )
            )
        }
    }
    return viewGoalsWithError
}
