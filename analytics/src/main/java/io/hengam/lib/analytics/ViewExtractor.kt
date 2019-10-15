package io.hengam.lib.analytics

import android.app.Activity
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.View
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_GOAL
import io.hengam.lib.analytics.goal.ViewGoalData
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single

// TODO: needs better docs
/**
 * A singleton object to extract views of buttonClickGoals or ViewGoalDatas from the given layouts
 */
object ViewExtractor {

    fun extractView(viewGoalData: ViewGoalData, activity: Activity): Single<View> {
        return when {
            viewGoalData.activityClassName == activity.javaClass.simpleName -> {
                if (viewGoalData.goalFragmentInfo != null) {
                    getFragmentView(viewGoalData, activity)
                } else {
                    getActivityView(viewGoalData, activity)
                }
            }
            else -> Single.just(View(activity))
        }
    }

    /**
     * If the viewGoalData has a fragmentInfo that matches the possible fragment, the view is extracted from
     * the fragment. If not, the view is extracted from the fragment's activity
     */
    fun extractView(viewGoalData: ViewGoalData, possibleFragment: Fragment): Single<View> {
        return when {
            viewGoalData.activityClassName == possibleFragment.activity?.javaClass?.simpleName -> {
                when {
                    viewGoalData.goalFragmentInfo != null -> {
                        val possibleFragmentName = possibleFragment.javaClass.canonicalName
                        if (possibleFragmentName != null &&
                            (viewGoalData.goalFragmentInfo.actualName == possibleFragmentName ||
                                    viewGoalData.goalFragmentInfo.obfuscatedName == possibleFragmentName) &&
                            viewGoalData.goalFragmentInfo.fragmentId ==
                            possibleFragment.activity?.resources?.getResourceEntryName(possibleFragment.id)
                        ) {
                            getFragmentView(viewGoalData, possibleFragment)
                        } else {
                            if (possibleFragment.activity != null) {
                                getFragmentView(viewGoalData, possibleFragment.activity!!)
                            } else Single.just(View(possibleFragment.context))
                        }
                    }
                    else -> {
                        if (possibleFragment.activity != null) {
                            getActivityView(viewGoalData, possibleFragment.activity!!)
                        } else Single.just(View(possibleFragment.context))
                    }
                }
            }
            else -> Single.just(View(possibleFragment.context))
        }
    }

    /**
     * Extracts the view of given viewGoalData from the given activity
     *
     * errors if the view is not found
     */
    private fun getActivityView(viewGoalData: ViewGoalData, activity: Activity): Single<View> {
        val view = activity.findViewById<View> (
            activity.resources.getIdentifier(viewGoalData.viewID, "id", activity.packageName))
        if (view == null) {
            Plog.error(T_ANALYTICS, "Unable to extract view in activity, the id is possibly wrong. The viewGoal will be ignored.",
                "id" to viewGoalData.viewID,
                "activity" to viewGoalData.activityClassName
            )
            viewGoalData.currentValue = Constants.ANALYTICS_ERROR_VIEW_GOAL
            return Single.just(View(activity))
        }
        return Single.just(view)
    }

    /**
     * Extracts the view of given viewGoalData from the given fragment
     *
     * errors if the fragmentView is null or the view is not found
     */
    private fun getFragmentView(viewGoalData: ViewGoalData, fragment: Fragment): Single<View> {
        val fragmentView = fragment.view
        if (fragmentView == null){
            Plog.error(T_ANALYTICS, "Unable to extract view in fragment, the fragmentView has not been created. " +
                    "The viewGoal will be ignored.",
                "id" to viewGoalData.viewID,
                "activity" to viewGoalData.activityClassName,
                "fragmentInfo" to viewGoalData.goalFragmentInfo
            )
            return Single.just(View(fragment.context))
        }
        val view = fragmentView.findViewById<View> (
            fragment.resources.getIdentifier(viewGoalData.viewID, "id", fragment.activity?.packageName))
        if (view == null) {
            Plog.error(T_ANALYTICS, "Unable to extract view in fragment, the id is possibly wrong. The viewGoal will be ignored.",
                "id" to viewGoalData.viewID,
                "activity" to viewGoalData.activityClassName,
                "fragmentInfo" to viewGoalData.goalFragmentInfo
            )
            viewGoalData.currentValue = Constants.ANALYTICS_ERROR_VIEW_GOAL
            return Single.just(View(fragment.context))
        }
        return Single.just(view)
    }

    /**
     * Called when the viewGoalData has a fragmentInfo but the view is called to be extracted by the activity.
     *
     * Gets the current fragment inside the ViewGoalData's fragmentInfo's id. if the fragment is the
     * viewGoalData's fragment extracts the view from the fragment, if not returns null
     *
     */
    private fun getFragmentView(viewGoalData: ViewGoalData, activity: Activity): Single<View> {
        val currentFragment = getCurrentFragment(viewGoalData.goalFragmentInfo!!, activity)
        if (currentFragment == null) {
            viewGoalData.currentValue = Constants.ANALYTICS_ERROR_VIEW_GOAL
            Plog.error(T_ANALYTICS, "null value trying to get a viewGoal's fragment. The id is possibly wrong",
                "Activity Name" to activity.javaClass.simpleName,
                "Fragment Id" to viewGoalData.goalFragmentInfo.fragmentId
            )
            return Single.just(View(activity))
        }
        val currentFragmentName = currentFragment.javaClass.canonicalName
        if (currentFragmentName != null &&
            (viewGoalData.goalFragmentInfo.actualName == currentFragmentName ||
                    viewGoalData.goalFragmentInfo.obfuscatedName == currentFragmentName)){
            return getFragmentView(viewGoalData, currentFragment)
        }
        return Single.just(View(activity))
    }

    /**
     * The view-to-be-extracted's fragment can be nested inside other fragments.
     * When this view is called to be extracted by activity (extractView(viewGoalData: ViewGoalData, activity: Activity)),
     * all nested fragments should be searched to find the wanted fragment
     *
     */
    private fun getCurrentFragment(goalFragmentInfo: GoalFragmentInfo, activity: Activity): Fragment? {
        val activityFragmentManager = (activity as AppCompatActivity).supportFragmentManager
        if (activityFragmentManager.fragments.size == 0) {
            return null
        }
        return activityFragmentManager
            .findFragmentById(
                activity.resources.getIdentifier(
                    goalFragmentInfo.fragmentId,
                    "id",
                    activity.packageName
                )
            ) ?: getCurrentFragment(goalFragmentInfo, activityFragmentManager.fragments)
    }

    private fun getCurrentFragment(goalFragmentInfo: GoalFragmentInfo, fragments: List<Fragment>): Fragment? {
        if (fragments.isEmpty()) {
            return null
        }

        val childFragments = mutableListOf<Fragment>()
        var possibleFragment: Fragment?
        for (fragment in fragments) {
            possibleFragment =
                    fragment.childFragmentManager.findFragmentById(
                        fragment.resources.getIdentifier(
                            goalFragmentInfo.fragmentId,
                            "id",
                            fragment.activity?.packageName
                        )
                    )
            if (possibleFragment != null){
                return possibleFragment
            } else {
                childFragments.addAll(fragment.childFragmentManager.fragments)
            }
        }
        return getCurrentFragment(goalFragmentInfo, childFragments)
    }


}