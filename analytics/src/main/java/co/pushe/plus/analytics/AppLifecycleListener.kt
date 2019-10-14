package co.pushe.plus.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup
import co.pushe.plus.PusheLifecycle
import co.pushe.plus.analytics.LogTag.T_ANALYTICS
import co.pushe.plus.analytics.LogTag.T_ANALYTICS_GOAL
import co.pushe.plus.analytics.LogTag.T_ANALYTICS_LIFECYCLE_LISTENER
import co.pushe.plus.analytics.dagger.AnalyticsScope
import co.pushe.plus.analytics.goal.FragmentContainer
import co.pushe.plus.analytics.goal.Funnel
import co.pushe.plus.analytics.goal.GoalProcessManager
import co.pushe.plus.analytics.session.SessionFlowManager
import co.pushe.plus.analytics.tasks.SessionEndDetectorTask
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.justDo
import javax.inject.Inject

/**
 * A class registered on initialize to listen to activities lifeCycle (@link [AnalyticsInitializer.preInitialize])
 * which has a callback function for each of the activity life-states
 *
 * On create of each activity, this very class is registered as a LifeCycle listener for the activity's fragments
 * which gives it a callback function for each of the fragment life-states as well.
 *
 * Note: Regarding fragment callbacks, actions that needed either fragment's view or activity could not
 * be scheduled to execute on CPUThread, because in the case of CPUThread being occupied with other works,
 * there was a possibility that fragment's lifeCycle reaches the state of destroying its view or activity
 * before the scheduled work gets done.
 *
 */
@AnalyticsScope
class AppLifecycleListener @Inject constructor (
    private val goalProcessManager: GoalProcessManager,
    private val sessionFlowManager: SessionFlowManager,
    private val pusheLifecycle: PusheLifecycle,
    private val taskScheduler: TaskScheduler,
    private val pusheConfig: PusheConfig
): FragmentManager.FragmentLifecycleCallbacks(), Application.ActivityLifecycleCallbacks {

    fun registerEndSessionListener() {
        pusheLifecycle.onAppClosed
            .doOnNext { taskScheduler.scheduleTask(SessionEndDetectorTask.Options, initialDelay = pusheConfig.sessionEndThreshold) }
            .justDo()
    }

    /**
     * Registers this class as a fragment lifeCycle listener for the activity
     */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Activity ${activity.javaClass.simpleName} was created.")

        if (activity !is AppCompatActivity){
            Plog.warn(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Activity ${activity.javaClass.simpleName} is not a AppCompatActivity. " +
                    "Lifecycle of fragments in this activity will be ignored.")
            return
        }
        (activity).supportFragmentManager
            .unregisterFragmentLifecycleCallbacks(this)
        (activity).supportFragmentManager
            .registerFragmentLifecycleCallbacks(this, true)
    }

    /**
     * In case of the activity being a new one, updates the [Funnel.activityFunnel] and calls
     * [GoalProcessManager.manageActivityReachGoals]
     *
     *
     * Whether or not it's a new activity, calls [GoalProcessManager.manageButtonClickGoals] to
     * set buttonClickListeners for the target buttons, because if the host-app-developer sets the
     * buttonClickListeners on resume of the activity, our listener will be overwritten.
     *
     * Whether or not it's a new activity, calls [SessionFlowManager.updateSessionFlow] to
     * update activity's startTime in session.
     *
     */
    override fun onActivityResumed(activity: Activity) {
        cpuThread {
            if (activity is AppCompatActivity) {
                Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Activity ${activity.javaClass.simpleName} was resumed.")

                taskScheduler.cancelTask(SessionEndDetectorTask.Options)

                if (Funnel.activityFunnel.isNotEmpty() && !isSameActivityAsLast(activity)) {
                    sessionFlowManager.sendLastActivitySessionFlowItemMessage()
                }

                if (Funnel.activityFunnel.isEmpty() || !isSameActivityAsLast(activity)) {
                    Funnel.activityFunnel.add(activity.javaClass.simpleName)
                    goalProcessManager.manageActivityReachGoals(activity, sessionFlowManager.sessionId)
                    Funnel.fragmentFunnel = mutableMapOf()
                }

                goalProcessManager.manageButtonClickGoals(activity, sessionFlowManager.sessionId)
                if (activity.intent.hasExtra(ACTIVITY_EXTRA_NOTIF_MESSAGE_ID)){
                    Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Activity ${activity.javaClass.simpleName} was resumed due to notif click.",
                            "Notif Id" to activity.intent.getStringExtra(ACTIVITY_EXTRA_NOTIF_MESSAGE_ID)
                    )
                }
                sessionFlowManager.updateSessionFlow(
                        activity.javaClass.simpleName,
                        activity.intent.getStringExtra(ACTIVITY_EXTRA_NOTIF_MESSAGE_ID)
                )
            }
        }
    }

    /**
     * Calls manager to update activity's viewGoals (@see [GoalProcessManager.updateActivityViewGoals])
     * Calls manager to update activity's duration in session (@see [SessionFlowManager.updateActivityDuration])
     */
    override fun onActivityPaused(activity: Activity) {
        cpuThread {
            Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Activity ${activity.javaClass.simpleName} was paused.")
                sessionFlowManager.updateActivityDuration(activity.javaClass.simpleName)
                goalProcessManager.updateActivityViewGoals(activity)

        }
    }

    /**
     * In case of the fragment being a new one, updates the [Funnel.fragmentFunnel] and calls
     * [GoalProcessManager.handleFragmentReachMessage]
     *
     * Whether or not it's a new fragment, calls [GoalProcessManager.manageButtonClickGoals] to
     * set buttonClickListeners for the target buttons, because if the host-app-developer sets the
     * buttonClickListeners on resume of the fragment, our listener will be overwritten.
     *
     * Whether or not it's a new fragment, calls [SessionFlowManager.updateSessionFlow] to
     * update fragment's startTime in session.
     *
     */

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Fragment ${f.javaClass.canonicalName} resumed.")
        if (isNotSupported(f)) return

        val sessionFragmentInfo = getSessionFragmentInfo(f)
        val parentFragments = getParentFragments(f)
        if (sessionFragmentInfo == null || parentFragments == null){
            return
        }
        val isParent = f.childFragmentManager.fragments.isNotEmpty()

        goalProcessManager.manageButtonClickGoals(sessionFragmentInfo, f, sessionFlowManager.sessionId)
        goalProcessManager.updateFragmentReachViewGoals(sessionFragmentInfo, f)

        cpuThread {
            val parentIds = mutableListOf<String>()
            parentFragments.forEach {
                parentIds.add(it.fragmentId)
            }

            parentIds.reverse()
            val fragmentContainer = FragmentContainer(sessionFragmentInfo.activityName, sessionFragmentInfo.fragmentId, parentIds)

            val funnel = Funnel.fragmentFunnel[fragmentContainer]
            var repeatedFragment = false
            if (funnel != null) {
                if (funnel.last() == sessionFragmentInfo.fragmentName){
                    repeatedFragment = true
                }
                if (!repeatedFragment){
                    funnel.add(sessionFragmentInfo.fragmentName)
                }
            } else {
                Funnel.fragmentFunnel[fragmentContainer] = mutableListOf(sessionFragmentInfo.fragmentName)
            }
            if (!repeatedFragment){
                goalProcessManager.handleFragmentReachMessage(sessionFragmentInfo, fragmentContainer, sessionFlowManager.sessionId)
            }

            sessionFlowManager.updateSessionFlow(sessionFragmentInfo, parentFragments, isParent)
        }
    }

    /**
     * Calls manager to update fragment's viewGoals (@see [GoalProcessManager.updateFragmentViewGoals])
     * Calls manager to update fragment's duration in session (@see [SessionFlowManager.updateFragmentDuration])
     */
    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_LISTENER, "Fragment ${f.javaClass.canonicalName} paused.")

        if (isNotSupported(f)) return

        val sessionFragmentInfo = getSessionFragmentInfo(f)
        val parentFragments = getParentFragments(f)
        if (sessionFragmentInfo == null || parentFragments == null) {
            return
        }
        goalProcessManager.updateFragmentViewGoals(sessionFragmentInfo, f)

        cpuThread {
            sessionFlowManager.updateFragmentDuration(sessionFragmentInfo, parentFragments)
        }
    }

    /**
     * Builds the [SessionFragmentInfo] list for a given fragment's parents
     * The outer parent is the first element of the list
     */
    private fun getParentFragments(f: Fragment): List<SessionFragmentInfo>? {
        val parentFragments: MutableList<SessionFragmentInfo> = mutableListOf()

        var fragment = f
        var parentFragment = fragment.parentFragment

        while (parentFragment != null) {
            if(!isNotSupported(parentFragment)){
                val parentFragmentInfo = getSessionFragmentInfo(parentFragment) ?: return null
                parentFragments.add(0, parentFragmentInfo)
            }
            fragment = parentFragment
            parentFragment = fragment.parentFragment
        }

        return parentFragments
    }

    /**
     * Builds the [SessionFragmentInfo] object for a given fragment
     */
    private fun getSessionFragmentInfo(f: Fragment): SessionFragmentInfo? {
        if (f.id == 0) {
            return null
        }
        val fragmentName = f.javaClass.canonicalName
        if (fragmentName == null){
            Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL,
                "Trying to retrieve sessionFragment's name. Canonical name is null, " +
                        "Ignoring the fragment"
            )
            return null
        }
        return try {
            SessionFragmentInfo(
                    fragmentName = fragmentName,
                    fragmentId = f.activity!!.resources.getResourceEntryName(f.id),
                    activityName =  f.activity!!.javaClass.simpleName
            )
        } catch (e: Exception) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_GOAL, "Error trying to retrieve fragment's id name. Ignoring the fragment", e,
                "Fragment Name" to f.javaClass.canonicalName,
                "Fragment Id" to f.id
            )
            null
        }
    }

    /*
    * in-layout fragments are ignored
    * ViewPager is not currently supported
    */
    private fun isNotSupported(fragment: Fragment): Boolean{
        return (fragment.isInLayout) || ((fragment.view as ViewGroup).parent is ViewPager)
    }

    private fun isSameActivityAsLast(activity: Activity): Boolean {
        return Funnel.activityFunnel.last() == activity.javaClass.simpleName
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    companion object {
        /** This constant should be the same as [UserActivityAction.ACTIVITY_EXTRA_NOTIF_MESSAGE_ID] **/
        const val ACTIVITY_EXTRA_NOTIF_MESSAGE_ID = "pushe_notif_message_id"
    }
}