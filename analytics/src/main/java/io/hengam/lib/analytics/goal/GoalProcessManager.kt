package io.hengam.lib.analytics.goal

import android.app.Activity
import android.support.v4.app.Fragment
import android.widget.Button
import io.hengam.lib.analytics.*
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_GOAL
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.messages.downstream.NewGoalMessage
import io.hengam.lib.analytics.messages.downstream.RemoveGoalMessage
import io.hengam.lib.analytics.utils.ButtonOnClickListener
import io.hengam.lib.analytics.utils.getOnClickListener
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.uiThread
import io.hengam.lib.utils.assertNotCpuThread
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject

@AnalyticsScope
class GoalProcessManager @Inject constructor (
    private val appLifecycleListener: AppLifecycleListener,
    private val activityReachHandler: ActivityReachHandler,
    private val fragmentReachHandler: FragmentReachHandler,
    private val buttonClickHandler: ButtonClickHandler,
    private val store: GoalStore,
    val moshi: HengamMoshi
)
{
    fun initialize() {
        initStoredGoals()
        initListeners()
    }

    private fun initStoredGoals() {
        store.initializeViewGoalsDataSet()
        store.initializeGoalsDataSet()
    }

    /**
     * Note: Regarding fragments, actions that need either fragment's view or activity cannot
     * be scheduled to execute on CPUThread, because in the case of CPUThread being occupied with other works,
     * there is a possibility that fragment's lifeCycle reaches the state of destroying its view or activity
     * before the scheduled work gets done.
     */
    private fun initListeners() {
        appLifecycleListener.onNewActivity()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                manageActivityReachGoals(activity)
                    .doOnError {
                        Plog.error(
                            T_ANALYTICS_GOAL, "Error handling activityReachGoals on start of a new activity", it,
                            "Activity Name" to activity.javaClass.simpleName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onActivityResumed()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                manageButtonClickGoals(activity)
                    .doOnError {
                        Plog.error(
                            LogTag.T_ANALYTICS_SESSION, "Error trying to set clickListeners on goalButtons on activity resume", it,
                            "Activity Name" to activity.javaClass.simpleName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onActivityPaused()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                updateActivityViewGoals(activity)
                    .doOnError {
                        Plog.error(
                            LogTag.T_ANALYTICS_SESSION, "Error updating activity viewGoals on activity pause", it,
                            "Activity Name" to activity.javaClass.simpleName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onFragmentResumed()
            .observeOn(uiThread())
            .flatMapCompletable { (sessionFragmentInfo, fragment) ->
                manageButtonClickGoals(sessionFragmentInfo, fragment)
                    .doOnError {
                        Plog.error(
                            T_ANALYTICS_GOAL, "Error updating fragment viewGoals and goal buttons on start of a fragment", it,
                            "Fragment Name" to sessionFragmentInfo.fragmentName,
                            "Fragment Id" to sessionFragmentInfo.fragmentId,
                            "Activity Name" to sessionFragmentInfo.activityName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onNewFragment()
            .observeOn(uiThread())
            .flatMapCompletable { (sessionFragmentInfo, fragment) ->
                manageFragmentReachGoals(sessionFragmentInfo, fragment)
                    .doOnError {
                        Plog.error(
                            T_ANALYTICS_GOAL, "Error handling fragmentReachGoals on start of a new fragment", it,
                            "Fragment Name" to sessionFragmentInfo.fragmentName,
                            "Fragment Id" to sessionFragmentInfo.fragmentId,
                            "Activity Name" to sessionFragmentInfo.activityName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onFragmentPaused()
            .observeOn(uiThread())
            .flatMapCompletable { (sessionFragmentInfo, fragment) ->
                updateFragmentViewGoals(sessionFragmentInfo, fragment)
                    .doOnError {
                        Plog.error(
                            T_ANALYTICS_GOAL, "Error updating fragment viewGoals on fragment pause", it,
                            "Fragment Name" to sessionFragmentInfo.fragmentName,
                            "Fragment Id" to sessionFragmentInfo.fragmentId,
                            "Activity Name" to sessionFragmentInfo.activityName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()
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

        store.updateGoals(goals).justDo(T_ANALYTICS_GOAL)
    }

    /**
     * Called when a new activity is resumed
     * Called on CPUThread
     *
     * Gets all activityReachedGoals for the given activity from [GoalStore] and after updating their
     * viewGoals, calls [ActivityReachHandler] for sending the messages
     *
     */
    private fun manageActivityReachGoals(activity: Activity): Completable {
        return store.getActivityReachGoals(activity.javaClass.simpleName)
            .flatMapCompletable { goal ->
                store.updateViewGoalValues(goal.viewGoalDataList, activity)
                    .andThen(activityReachHandler.onGoalReached(goal))
            }
    }

    /**
     * Called when a new fragment is resumed
     * Called on uiThread (The actual goal handling takes place on CPUThread)
     * @see [FragmentReachHandler.onGoalReached]
     *
     * Gets all fragmentReachedGoals for the given sessionFragmentInfo from [GoalStore] and calls
     * [FragmentReachHandler] for sending the messages
     */
    private fun manageFragmentReachGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment): Completable {
        return store.getFragmentReachGoals(sessionFragmentInfo)
            .flatMapCompletable {
                store.updateViewGoalValues(it.viewGoalDataList, fragment)
                    .andThen(fragmentReachHandler.onGoalReached(it, sessionFragmentInfo.containerId))
            }

    }

    /**
     * Called when an activity is resumed
     * Called on CPUThread
     *
     * Gets all buttonClickedGoals for the given activity from the store and sets listeners for each
     * target button
     *
     * @see [setButtonClickListener]
     */
    private fun manageButtonClickGoals(activity: Activity): Completable {
        return store.getButtonClickGoals(activity.javaClass.simpleName)
            .flatMapCompletable {
                setButtonClickListener(it, activity)
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
    private fun manageButtonClickGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment): Completable {
        return store.getButtonClickGoals(sessionFragmentInfo)
            .flatMapCompletable {
                setButtonClickListener(it, fragment)
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
    private fun updateActivityViewGoals(activity: Activity): Completable {
        return store.viewGoalsByActivity(activity.javaClass.simpleName)
            .flatMapCompletable {
                store.updateViewGoalValues(listOf(it), activity)
            }
    }

    /**
     * Called when a fragment is paused
     * Called on mainThread
     *
     * Gets all viewGoals in the given sessionFragmentInfo from [GoalStore] and updates their values
     *
     */
    private fun updateFragmentViewGoals(sessionFragmentInfo: SessionFragmentInfo, fragment: Fragment): Completable {
        return store.viewGoalsByFragment(sessionFragmentInfo)
            .flatMapCompletable {
                store.updateViewGoalValues(listOf(it), fragment)
            }
    }

    fun removeGoals(goalsRemoveMessage: RemoveGoalMessage) {
        store.removeGoals(goalsRemoveMessage.GoalNames)
            .observeOn(cpuThread())
            .justDo(T_ANALYTICS_GOAL)
    }

    /**
     * For each buttonClickGoal given, extracts the target button view from the activity, retrieves
     * current listener set for that button and sets a listener for the button
     * first invoking the current listener actions
     */
    private fun setButtonClickListener(goal: ButtonClickGoalData, activity: Activity): Completable {
        return getButtonViewData(goal)
            .flatMap { ViewExtractor.extractView(it, activity) }
            .doOnSuccess {
                if (it !is Button)
                    throw AnalyticsException("Setting listener for button-click goal failed, no button was found with the given id",
                        "Id" to goal.buttonID
                    )

                val oldListener = getOnClickListener(it)
                if (oldListener !is ButtonOnClickListener){
                    it.setOnClickListener(ButtonOnClickListener {
                        oldListener?.onClick(it)
                        store.updateViewGoalValues(goal.viewGoalDataList, activity)
                            .andThen(buttonClickHandler.onGoalReached(goal))
                            .subscribeOn(cpuThread())
                            .justDo(T_ANALYTICS_GOAL)
                    })
                }
            }.ignoreElement()
    }

    private fun setButtonClickListener(goal: ButtonClickGoalData, fragment: Fragment): Completable {
        return getButtonViewData(goal)
            .flatMap { ViewExtractor.extractView(it, fragment) }
            .doOnSuccess {
                if (it !is Button)
                    throw AnalyticsException("Setting listener for button-click goal failed, no button was found with the given id",
                        "Id" to goal.buttonID
                    )

                val oldListener = getOnClickListener(it)
                if (oldListener !is ButtonOnClickListener){
                    it.setOnClickListener(ButtonOnClickListener {
                        oldListener?.onClick(it)
                        store.updateViewGoalValues(goal.viewGoalDataList, fragment)
                            .andThen(buttonClickHandler.onGoalReached(goal))
                            .subscribeOn(cpuThread())
                            .justDo(T_ANALYTICS_GOAL)
                    })
                }
            }.ignoreElement()
    }

    /**
     * Builds and returns a viewGoalData object for the button in given ButtonClickGoal
     */
    private fun getButtonViewData(goal: ButtonClickGoalData): Single<ViewGoalData> {
        return Single.just(
            ViewGoalData(
                    parentGoalName = "",
                    targetValues = listOf(),
                    viewType = ViewGoalType.TEXT_VIEW,
                    viewID = goal.buttonID,
                    activityClassName = goal.activityClassName,
                    goalFragmentInfo = goal.goalFragmentInfo
            )
        )
    }
}
