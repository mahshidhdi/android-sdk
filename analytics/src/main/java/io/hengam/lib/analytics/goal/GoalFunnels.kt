package io.hengam.lib.analytics.goal
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.messages.upstream.GoalReachedMessage

/**
 * a singleton object containing activity funnel in current session and fragment funnel for each
 * containerId in "current" activity (fragment funnel is cleared on start of a new activity)
 *
 * usage:
 * ------------
 * On resume of an activity/fragment, in order to prevent sending multiple messages for the same
 * activity/fragment reach goal, a check is made with the corresponding funnel for the resumed
 * activity/fragment not to be the same activity/fragment as last (and for the pause/resume to be
 * an actual layout change)
 * @see [AppLifecycleListener.onNewActivity]
 * @see [AppLifecycleListener.onNewFragment]
 *
 * ------------
 * With every activity/fragment reach goal, the corresponding funnel leading to the goal activity/fragment
 * is sent in the message
 * @see [GoalReachedMessage]
 *
 * ------------
 * A target activity/fragment funnel can be set by the user for each activity/fragment reach goal.
 * When handling reach goals, the target funnel of the goal is compared to actual corresponding funnel
 * before sending message.
 *
 * If the target funnel does not match the actual funnel, message won't be sent.
 * @see [ActivityReachHandler.onGoalReached]
 * @see [FragmentReachHandler.onGoalReached]
 *
 */

object Funnel{
    var activityFunnel: MutableList<String> = mutableListOf()
    var fragmentFunnel: MutableMap<String, MutableList<String>> = mutableMapOf()
}