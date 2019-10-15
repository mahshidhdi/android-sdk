package io.hengam.lib.analytics.goal

import android.app.Activity
import android.content.Context
import android.support.v4.app.Fragment
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_GOAL
import io.hengam.lib.analytics.SessionFragmentInfo
import io.hengam.lib.analytics.ViewExtractor
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import io.reactivex.Observable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AnalyticsScope
class GoalStore @Inject constructor(
        val context: Context,
        val moshi: HengamMoshi,
        private val goalFragmentObfuscatedNameExtractor: GoalFragmentObfuscatedNameExtractor,
        hengamStorage: HengamStorage
) {
    val definedGoals: MutableList<Goal> = hengamStorage.createStoredList("defined_goals", Goal::class.java)

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
            .observeOn(cpuThread())
            .justDo(T_ANALYTICS_GOAL)
    }
    /**
     * must be called on initialize, after [initializeViewGoalsDataSet]
     */
    fun initializeGoalsDataSet() {
        extractGoalsDataSet(definedGoals)
            .observeOn(cpuThread())
            .justDo(T_ANALYTICS_GOAL)
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
    fun updateGoals(goals: List<Goal>): Completable {
        return Observable.fromIterable(goals)
            .observeOn(cpuThread())
            .subscribeOn(cpuThread())
            .doOnNext { goal ->
                val currentGoals = definedGoals.filter { goal.name == it.name }
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
            .ignoreElements()
            .andThen(extractViewGoalsDataSet(goals))
            .andThen(extractGoalsDataSet(goals))
            .doOnComplete {
                Plog.info(T_ANALYTICS, T_ANALYTICS_GOAL, "Analytics goals have been updated",
                    "Number of Goals" to definedGoals.size,
                    "Goals" to definedGoals
                )
            }
    }

    /**
     * Called by [GoalProcessManager] when a removeGoalMessage in received (@link [GoalProcessManager.removeGoals])
     *
     * Removes the given goals from [definedGoals] list and [definedGoalsDataSet]
     * Removes the ViewGoalDatas of the given goals from [definedViewGoalsDataSet]
     *
     */
    fun removeGoals(goalNames: Set<String>): Completable {
        return Completable.fromCallable {
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
        }.subscribeOn(cpuThread())
    }

    /**
     * Called on initialize (@link [initializeViewGoalsDataSet]) and when new goals are defined (@link [updateGoals])
     *
     * Builds a [ViewGoalData] object for each viewGoal in the given goals and adds it to [definedViewGoalsDataSet]
     */
    private fun extractViewGoalsDataSet(goals: List<Goal>): Completable {
        var viewGoalFragmentObfuscatedName: String?
        var goalGoalFragmentInfo: GoalFragmentInfo?
        return Observable.fromIterable(goals)
            .doOnNext { goal ->
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
        }.ignoreElements()
    }

    /**
     * Called on initialize (@link [initializeGoalsDataSet]) and when new goals are defined (@link [updateGoals])
     *
     * Builds a [GoalData] object for each Goal in the given list and adds it to [definedGoalsDataSet]
     */
    private fun extractGoalsDataSet(goals: List<Goal>): Completable {
        return Observable.fromIterable(goals)
            .doOnNext { goal ->
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
            .ignoreElements()
    }

    fun getActivityReachGoals(activityName: String): Observable<ActivityReachGoalData> {
        return Observable.fromIterable(definedGoalsDataSet.keys)
            .filter {
                it is ActivityReachGoalData &&
                        it.activityClassName == activityName
            }.map { (it as ActivityReachGoalData) }
    }

    fun getFragmentReachGoals(sessionFragmentInfo: SessionFragmentInfo): Observable<FragmentReachGoalData> {
        return Observable.fromIterable(definedGoalsDataSet.keys)
            .filter {
                it is FragmentReachGoalData &&
                        it.activityClassName == sessionFragmentInfo.activityName &&
                        (it.goalFragmentInfo.actualName == sessionFragmentInfo.fragmentName ||
                                it.goalFragmentInfo.obfuscatedName == sessionFragmentInfo.fragmentName) &&
                        it.goalFragmentInfo.fragmentId == sessionFragmentInfo.fragmentId
            }.map { (it as FragmentReachGoalData) }
    }

    fun getButtonClickGoals(activityName: String): Observable<ButtonClickGoalData> {
        return Observable.fromIterable(definedGoalsDataSet.keys)
            .filter {
                it is ButtonClickGoalData &&
                        it.activityClassName == activityName &&
                        it.goalFragmentInfo == null
            }.map { (it as ButtonClickGoalData) }
    }

    fun getButtonClickGoals(sessionFragmentInfo: SessionFragmentInfo): Observable<ButtonClickGoalData> {
        return Observable.fromIterable(definedGoalsDataSet.keys)
            .filter {
                it is ButtonClickGoalData &&
                        it.goalFragmentInfo != null &&
                        it.activityClassName == sessionFragmentInfo.activityName &&
                        it.goalFragmentInfo.fragmentId == sessionFragmentInfo.fragmentId &&
                        (it.goalFragmentInfo.actualName == sessionFragmentInfo.fragmentName ||
                                it.goalFragmentInfo.obfuscatedName == sessionFragmentInfo.fragmentName)
            }.map { (it as ButtonClickGoalData) }
    }

    fun viewGoalsByActivity(activityName: String): Observable<ViewGoalData> {
        return Observable.fromIterable(definedViewGoalsDataSet.keys)
            .filter {
                it.goalFragmentInfo == null &&
                        it.activityClassName == activityName
            }
    }

    fun viewGoalsByFragment(sessionFragmentInfo: SessionFragmentInfo): Observable<ViewGoalData> {
        return Observable.fromIterable(definedViewGoalsDataSet.keys)
            .filter {
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
    fun updateViewGoalValues(viewGoalDataSet: List<ViewGoalData>, activity: Activity): Completable {
        return Observable.fromIterable(viewGoalDataSet)
            .flatMapCompletable { viewGoalData ->
                ViewExtractor.extractView(viewGoalData, activity)
                    .filter { ViewGoal.isValidView(it) }
                    .flatMapCompletable { viewGoalData.updateValue(it) }
            }
    }

    /**
     * Extracts the view of each [ViewGoalData] in the list given from the fragment and updates
     * their [ViewGoalData.currentValue] with the value of the view
     */
    fun updateViewGoalValues(viewGoalDataSet: List<ViewGoalData>, fragment: Fragment): Completable {
        return Observable.fromIterable(viewGoalDataSet)
            .flatMapCompletable { viewGoalData ->
                ViewExtractor.extractView(viewGoalData, fragment)
                    .filter { ViewGoal.isValidView(it) }
                    .flatMapCompletable { viewGoalData.updateValue(it) }
            }
    }
}