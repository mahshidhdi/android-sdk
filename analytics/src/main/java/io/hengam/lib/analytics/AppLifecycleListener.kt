package io.hengam.lib.analytics

import android.app.Activity
import android.support.v4.app.Fragment
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.goal.Funnel
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.uiThread
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.BehaviorRelay
import io.reactivex.Observable
import javax.inject.Inject

/**
 * A class containing observables for resumed and paused activities and fragments
 *
 */
@AnalyticsScope
class AppLifecycleListener @Inject constructor() {

    private val activityResumeThrottler = BehaviorRelay.create<Activity>()
    private val fragmentResumeThrottler = BehaviorRelay.create<Fragment>()

    private val activityPauseThrottler = BehaviorRelay.create<Activity>()
    private val fragmentPauseThrottler = BehaviorRelay.create<Fragment>()

    private val emptySessionFragmentInfo by lazy {
        SessionFragmentInfo(
            "", "", ""
        )
    }

    fun activityResumed(activity: Activity) = activityResumeThrottler.accept(activity)

    fun activityPaused(activity: Activity) = activityPauseThrottler.accept(activity)

    fun fragmentResumed(fragment: Fragment) = fragmentResumeThrottler.accept(fragment)

    fun fragmentPaused(fragment: Fragment) = fragmentPauseThrottler.accept(fragment)

    /**
     * Repeated activities are ignored in this one
     */
    fun onNewActivity(): Observable<Activity> = activityResumeThrottler
        .filter { activity -> Funnel.activityFunnel.isEmpty() || !isSameActivityAsLast(activity) }

    fun onActivityResumed(): Observable<Activity> = activityResumeThrottler

    fun onActivityPaused(): Observable<Activity> = activityPauseThrottler

    fun onFragmentResumed(): Observable<Pair<SessionFragmentInfo, Fragment>> = fragmentResumeThrottler
        .map { getSessionFragmentInfo(it) to it }
        .filter { (sessionFragmentInfo, _) -> sessionFragmentInfo != emptySessionFragmentInfo }
        .onErrorResumeNext(Observable.empty())

    /**
     * Repeated fragments are ignored in this one
     */
    fun onNewFragment(): Observable<Pair<SessionFragmentInfo, Fragment>> = fragmentResumeThrottler
        .map { getSessionFragmentInfo(it) to it }
        .filter { (sessionFragmentInfo, _) -> sessionFragmentInfo != emptySessionFragmentInfo }
        .filter { (sessionFragmentInfo, _) ->
             Funnel.fragmentFunnel[sessionFragmentInfo.containerId].isNullOrEmpty() ||
                    Funnel.fragmentFunnel[sessionFragmentInfo.containerId]?.last() != sessionFragmentInfo.fragmentName
        }
        .onErrorResumeNext(Observable.empty())

    fun onFragmentPaused(): Observable<Pair<SessionFragmentInfo, Fragment>> = fragmentPauseThrottler
        .map { getSessionFragmentInfo(it) to it }
        .filter { (sessionFragmentInfo, _) -> sessionFragmentInfo != emptySessionFragmentInfo }
        .onErrorResumeNext(Observable.empty())

    /**
     * Builds the [SessionFragmentInfo] list for a given fragment's parents
     * The outer parent is the first element of the list
     */
    private fun getParentFragment(fragment: Fragment): SessionFragmentInfo? {
        val parentFragment = fragment.parentFragment ?: return null

        if (parentFragment.isInLayout) return getParentFragment(parentFragment)

        val parentFragmentInfo = getSessionFragmentInfo(parentFragment)

        return if (parentFragmentInfo == emptySessionFragmentInfo) null
        else parentFragmentInfo
    }

    /**
     * Builds the [SessionFragmentInfo] object for a given fragment
     */
    private fun getSessionFragmentInfo(f: Fragment): SessionFragmentInfo {
        if (f.id == 0) {
            return emptySessionFragmentInfo
        }
        val fragmentName = f.javaClass.canonicalName
        if (fragmentName == null){
            Plog.error(T_ANALYTICS,
                "Trying to retrieve sessionFragment's name. Canonical name is null, " +
                        "Ignoring the fragment"
            )
            return emptySessionFragmentInfo
        }
        return try {
            SessionFragmentInfo(
                fragmentName = fragmentName,
                fragmentId = f.activity?.resources?.getResourceEntryName(f.id) ?: "Unknown" ,
                activityName =  f.activity?.javaClass?.simpleName ?: "Unknown",
                parentFragment = getParentFragment(f)
            )
        } catch (e: Exception) {
            Plog.error(T_ANALYTICS, "Error trying to retrieve fragment's id name. Ignoring the fragment", e,
                "Fragment Name" to f.javaClass.canonicalName,
                "Fragment Id" to f.id
            )
            emptySessionFragmentInfo
        }
    }

    private fun isSameActivityAsLast(activity: Activity): Boolean {
        return Funnel.activityFunnel.last() == activity.javaClass.simpleName
    }
}