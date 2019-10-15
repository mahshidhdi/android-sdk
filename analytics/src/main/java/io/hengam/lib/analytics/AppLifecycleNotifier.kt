package io.hengam.lib.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.ViewGroup
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_LIFECYCLE_NOTIFIER
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.utils.ExceptionCatcher
import io.hengam.lib.utils.log.Plog
import java.lang.Exception
import javax.inject.Inject

/**
 * A class registered on initialize to listen to activities lifeCycles (@link [AnalyticsInitializer.preInitialize])
 * which has a callback function for each of the activity life-states
 *
 * On create of each activity, this very class is registered as a LifeCycle listener for the activity's fragments
 * which gives it a callback function for each of the fragment life-states as well.
 *
 */
@AnalyticsScope
class AppLifecycleNotifier @Inject constructor (
    private val appLifecycleListener: AppLifecycleListener
): FragmentManager.FragmentLifecycleCallbacks(), Application.ActivityLifecycleCallbacks {

    /**
     * Registers this class as a fragment lifeCycle listener for the activity
     */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = ExceptionCatcher.catchAllUnhandledErrors("Main Thread") {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Activity ${activity.javaClass.simpleName} was created.")

        if (activity !is AppCompatActivity){
            Plog.warn(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Activity ${activity.javaClass.simpleName} is not an AppCompatActivity. " +
                    "Lifecycle of fragments in this activity will be ignored.")
            return
        }

        try {
            activity.supportFragmentManager
                .unregisterFragmentLifecycleCallbacks(this)
            activity.supportFragmentManager
                .registerFragmentLifecycleCallbacks(this, true)
        } catch (e: Exception) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Error trying to register fragment callbacks for activity",
                "Activity" to activity.javaClass.simpleName
            )
        }
    }

    override fun onActivityResumed(activity: Activity) = ExceptionCatcher.catchAllUnhandledErrors("Main Thread") {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Activity ${activity.javaClass.simpleName} was resumed.")
        appLifecycleListener.activityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) = ExceptionCatcher.catchAllUnhandledErrors("Main Thread") {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Activity ${activity.javaClass.simpleName} was paused.")
        appLifecycleListener.activityPaused(activity)
    }

    /*
    * in-layout fragments are ignored
    */
    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) = ExceptionCatcher.catchAllUnhandledErrors("Main Thread") {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Fragment ${f.javaClass.canonicalName} resumed.")

        if (isStatic(f)) return
        if ((f.view as ViewGroup).parent is ViewPager) {
            // TODO
            // setPagerListener(f)
            return
        } else {
            appLifecycleListener.fragmentResumed(f)
        }

    }

    override fun onFragmentPaused(fm: FragmentManager, f: Fragment) = ExceptionCatcher.catchAllUnhandledErrors("Main Thread") {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "Fragment ${f.javaClass.canonicalName} paused.")

        if (isStatic(f)) return
        if ((f.view as ViewGroup).parent is ViewPager) {
            // TODO
            return
        } else {
            appLifecycleListener.fragmentPaused(f)
        }


    }

    private fun isStatic(fragment: Fragment): Boolean {
        return fragment.isInLayout ||
                fragment.view == null ||
                fragment.view !is ViewGroup ||
                fragment.activity == null
    }

    private fun setPagerListener(fragment: Fragment) {

        // TODO: consider fragments inside viewPager fragments
        // TODO: How to update duration??
        val viewPager = (fragment.view as ViewGroup).parent as ViewPager
        viewPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener{
            override fun onPageScrollStateChanged(p0: Int) {}
            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) {
                Plog.debug(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "onPageScrolled called: p0: $p0, p1: $p1, p2: $p2")
            }

            override fun onPageSelected(position: Int) {
                val fragment: Fragment = viewPager.adapter?.instantiateItem(viewPager, position) as Fragment
                Plog.debug(T_ANALYTICS, T_ANALYTICS_LIFECYCLE_NOTIFIER, "current Fragment: ${fragment.javaClass.simpleName}")
            }
        })
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}