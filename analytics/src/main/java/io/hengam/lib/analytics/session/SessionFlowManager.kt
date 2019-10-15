package io.hengam.lib.analytics.session

import android.support.annotation.VisibleForTesting
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.analytics.*
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_SESSION
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.goal.Funnel
import io.hengam.lib.analytics.messages.upstream.SessionInfoMessageBuilder
import io.hengam.lib.analytics.tasks.SessionEndDetectorTask
import io.hengam.lib.analytics.utils.CurrentTimeGenerator
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.PersistedList
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject


@AnalyticsScope
class SessionFlowManager @Inject constructor (
    private val currentTimeGenerator: CurrentTimeGenerator,
    private val postOffice: PostOffice,
    private val hengamConfig: HengamConfig,
    private val hengamLifecycle: HengamLifecycle,
    private val taskScheduler: TaskScheduler,
    private val appLifecycleListener: AppLifecycleListener,
    private val sessionIdProvider: SessionIdProvider,
    applicationInfoHelper: ApplicationInfoHelper,
    hengamStorage: HengamStorage
) {

    private val appVersionCode = applicationInfoHelper.getApplicationVersionCode() ?: 0

    /**
     * A sessionFlow consists of twe units: [SessionActivity] & [SessionFragment]
     *
     * It's basically a list of SessionActivities with an item for each activity seen.
     * Since there can be multiple fragment layouts inside an activity (or fragment), the fragmentFlows
     * are stored using a map with keys being fragment IDs and values being sessionFragment lists.
     *
     * Each sessionFragment itself has such a map named fragmentFlows to support nested fragments in the session
     *
     */
    @VisibleForTesting
    val sessionFlow: PersistedList<SessionActivity> = hengamStorage.createStoredList(
            "user_session_flow",
            SessionActivity::class.java
    )

    fun endSession(): Completable {
        return sendLastActivitySessionFlowItemMessage()
            .doOnSubscribe {
                Plog.info(T_ANALYTICS, T_ANALYTICS_SESSION, "User session ended",
                    "Id" to sessionIdProvider.sessionId,
                    "Flow" to sessionFlow
                )
            }.doOnComplete {
                sessionFlow.clear()
                sessionIdProvider.renewSessionId()
                Funnel.activityFunnel.clear()
            }
    }

    fun initializeSessionFlow() {
        appLifecycleListener.onNewActivity()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                sendLastActivitySessionFlowItemMessage()
                    .andThen(updateFunnel(activity.javaClass.simpleName))
                    .doOnComplete {
                        Plog.debug(T_ANALYTICS_SESSION, "Reached a new activity in session",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Activity" to Funnel.activityFunnel.last()
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update activity funnel on new activity resume", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()


        appLifecycleListener.onActivityResumed()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                updateSessionFlow(
                    activity.javaClass.simpleName,
                    activity.intent.getStringExtra(ACTIVITY_EXTRA_NOTIF_MESSAGE_ID)
                )
                    .doOnComplete {
                        Plog.trace(T_ANALYTICS_SESSION, "SessionFlow was updated due to activity resume",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Last Activity" to sessionFlow.last().name
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update session flow on activity resume", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()


        appLifecycleListener.onNewFragment()
            .observeOn(cpuThread())
            .flatMapCompletable { (sessionFragmentInfo, _) ->
                updateFunnel(sessionFragmentInfo)
                    .doOnComplete {
                        Plog.debug(T_ANALYTICS_SESSION, "Reached a new fragment in session",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update funnel on new fragment resume", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()


        appLifecycleListener.onFragmentResumed()
            .observeOn(cpuThread())
            .flatMapCompletable { (sessionFragmentInfo, _) ->
                updateSessionFlow(sessionFragmentInfo)
                    .doOnComplete {
                        Plog.trace(T_ANALYTICS_SESSION, "SessionFlow was updated due to fragment resume",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update session flow on fragment resume", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onActivityPaused()
            .observeOn(cpuThread())
            .flatMapCompletable { activity ->
                updateActivityDuration(activity.javaClass.simpleName)
                    .doOnComplete {
                        Plog.trace(T_ANALYTICS_SESSION, "Activity duration was updated in the sessionFlow",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Activity" to sessionFlow.last().name,
                            "Duration" to sessionFlow.last().duration
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update activity duration in sessionFlow", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()

        appLifecycleListener.onFragmentPaused()
            .observeOn(cpuThread())
            .flatMapCompletable { (sessionFragmentInfo, _) ->
                updateFragmentDuration(sessionFragmentInfo)
                    .doOnComplete {
                        Plog.trace(T_ANALYTICS_SESSION, "Fragment duration was updated in the sessionFlow",
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName
                        )
                    }
                    .doOnError {
                        Plog.error(T_ANALYTICS_SESSION, "Error trying to update fragment duration in sessionFlow", it,
                            "Session Id" to sessionIdProvider.sessionId,
                            "Fragment" to sessionFragmentInfo.fragmentName,
                            *((it as? AnalyticsException)?.data ?: emptyArray())
                        )
                    }
                    .onErrorComplete()
            }
            .justDo()
    }

    private fun updateFunnel(activityName: String): Completable {
        return Completable.fromCallable {
            Funnel.fragmentFunnel = mutableMapOf()
            Funnel.activityFunnel.add(activityName)
        }
    }

    private fun updateFunnel(fragmentInfo: SessionFragmentInfo): Completable {
        return Completable.fromCallable {
            Funnel.fragmentFunnel[fragmentInfo.containerId]?.add(fragmentInfo.fragmentName)
                ?: Funnel.fragmentFunnel.put(fragmentInfo.containerId, mutableListOf(fragmentInfo.fragmentName))
        }
    }

    /**
     * Called when an activity is resumed
     *
     * If the activity is the same one as last activity in session (there has not been a layout change)
     * sets the startTime of the [SessionActivity] to be current time.
     *
     * If the activity is a new one, builds a new [SessionActivity] and adds it to [sessionFlow]
     */
    private fun updateSessionFlow(activityName: String, notifMessageId: String? = null): Completable {
        return Completable.fromCallable {
            if (sessionFlow.isEmpty() || sessionFlow.last().name != activityName) {
                val sessionActivity = SessionActivity(
                    name = activityName,
                    startTime = currentTimeGenerator.getCurrentTime(),
                    originalStartTime = currentTimeGenerator.getCurrentTime(),
                    duration = 0,
                    sourceNotifMessageId = notifMessageId
                )
                sessionFlow.add(sessionActivity)
            } else if (sessionFlow.last().name == activityName) {
                sessionFlow.last().startTime = currentTimeGenerator.getCurrentTime()
                sessionFlow.save()
            } else Completable.complete()
        }
    }

    /**
     * Called when an activity is paused to update its duration in session flow
     */
    private fun updateActivityDuration(activityName: String): Completable {
        return when {
            sessionFlow.isEmpty() ->
                Completable.error(AnalyticsException("SessionFlow is empty",
                    "Activity Name" to activityName
                ))
            sessionFlow.last().name != activityName ->
                Completable.error(AnalyticsException("Wrong value as last seen activity in sessionFlow",
                    "Expected Last Seen Activity" to activityName,
                    "Last Activity In Session" to sessionFlow.last().name
                ))
            else ->
                Completable.fromCallable {
                    sessionFlow.last().duration +=
                        currentTimeGenerator.getCurrentTime() - sessionFlow.last().startTime
                    sessionFlow.save()
                }
        }
    }


    /**
     * Called when a fragment is resumed
     *
     * Note: When a layout with nested fragments is reached, the order of calling lifeCycle callbacks
     * for the fragments is from the inner fragment to the outer one. So in order to have a correct flow
     * for the fragments, before adding a fragment to session, we need to add its parent fragments.
     *
     * To do so the method is recursively called for the parent fragment first.
     *
     * If the fragment is the same one as last fragment in its flow in session (there has not been a layout change)
     * sets the startTime of the [SessionFragment] to be current time.
     *
     * If the fragment is a new one, builds a new [SessionFragment] and adds it to the flow in session
     *
     */
    private fun updateSessionFlow(sessionFragmentInfo: SessionFragmentInfo?): Completable {
        return when {
            sessionFragmentInfo == null -> Completable.complete()
            sessionFlow.last().name != sessionFragmentInfo.activityName ->
                Completable.error(
                    AnalyticsException("Invalid last activity",
                        "Expected Activity" to sessionFragmentInfo.activityName,
                        "Last Activity In Session" to sessionFlow.last().name
                    )
                )
            !sessionFragmentInfo.shouldBeAddedToSession -> {
                Plog.trace(T_ANALYTICS_SESSION, "Updating sessionFlow for fragment was skipped because it was disabled",
                    "Fragment Funnel" to Funnel.fragmentFunnel,
                    "Fragment Name" to sessionFragmentInfo.fragmentName
                )
                Completable.complete()
            }
            else ->
                updateSessionFlow(sessionFragmentInfo.parentFragment)
                    .andThen(
                        getFragmentSessionFlow(sessionFlow.last().fragmentFlows, sessionFragmentInfo)
                            .doOnSuccess {
                                val viewedFragment = SessionFragment(
                                    sessionFragmentInfo.fragmentName,
                                    currentTimeGenerator.getCurrentTime(),
                                    currentTimeGenerator.getCurrentTime(),
                                    0
                                )
                                val flow = it[sessionFragmentInfo.fragmentId]
                                if (flow == null) {
                                    it[sessionFragmentInfo.fragmentId] = mutableListOf(viewedFragment)
                                } else if (flow.isEmpty() || flow.last().name != sessionFragmentInfo.fragmentName) {
                                    flow.add(viewedFragment)
                                } else if (flow.last().name == sessionFragmentInfo.fragmentName) {
                                    flow.last().startTime = currentTimeGenerator.getCurrentTime()
                                }
                                sessionFlow.save()
                            }
                            .ignoreElement()
                    )
        }
    }


    /**
     * Called when a fragment is paused to update its duration in session
     * If the fragment is not supposed to be in the sessionFlow message (it is disabled), does not do anything
     */
    private fun updateFragmentDuration(sessionFragmentInfo: SessionFragmentInfo): Completable{
        return when {
            sessionFlow.last().name != sessionFragmentInfo.activityName ->
                Completable.error(
                    AnalyticsException("Invalid last activity",
                        "Expected Activity" to sessionFragmentInfo.activityName,
                        "Last Activity In Session" to sessionFlow.last().name
                    )
                )
            !sessionFragmentInfo.shouldBeAddedToSession -> Completable.complete()
            else ->
                getFragmentSessionFlow(sessionFlow.last().fragmentFlows, sessionFragmentInfo)
                    .map { map -> map[sessionFragmentInfo.fragmentId] }
                    .map { it }
                    .doOnSuccess { flow ->
                        when {
                            flow.isEmpty() ->
                                Completable.error(
                                    AnalyticsException("Empty fragmentFlow",
                                        "Activity" to sessionFragmentInfo.activityName,
                                        "Id" to sessionFragmentInfo.fragmentId
                                    )
                                )
                            flow.last().name != sessionFragmentInfo.fragmentName ->
                                Completable.error(
                                    AnalyticsException("Wrong value as last seen fragment in fragmentFlow",
                                        "Expected Last Seen Fragment" to sessionFragmentInfo.fragmentName,
                                        "Current" to flow.last().name
                                    )
                                )
                            else -> {
                                flow.last().duration += (currentTimeGenerator.getCurrentTime()
                                        - (flow.last().startTime))
                                sessionFlow.save()
                                Completable.complete()
                            }
                        }
                    }
                    .ignoreElement()
        }
    }

    /**
     * Retrieves the fragment flow in the session which should include the given sessionFragment Info
     *
     * @param fragmentFlow The fragmentFlow of the current activity in the session
     * @param sessionFragmentInfo The fragment whose flow is to be found
     *
     */
    private fun getFragmentSessionFlow (
        fragmentFlow: MutableMap<String, MutableList<SessionFragment>>,
        sessionFragmentInfo: SessionFragmentInfo
    ) : Single<MutableMap<String, MutableList<SessionFragment>>> {

        return if (sessionFragmentInfo.parentFragment == null) Single.just(fragmentFlow)
        else if (!sessionFragmentInfo.parentFragment.shouldBeAddedToSession) getFragmentSessionFlow(fragmentFlow, sessionFragmentInfo.parentFragment)
        else getFragmentSessionFlow(fragmentFlow, sessionFragmentInfo.parentFragment)
            .map { it[sessionFragmentInfo.parentFragment.fragmentId]?.last()?.fragmentFlows }
            .map { it }
    }

    private fun sendLastActivitySessionFlowItemMessage(): Completable {
        return if (sessionFlow.isEmpty()) Completable.complete()
        else Completable.fromCallable {
            postOffice.sendMessage(
                message = SessionInfoMessageBuilder.build(sessionIdProvider.sessionId, sessionFlow.last(), appVersionCode),
                sendPriority = SendPriority.LATE
            )
        }
    }

    fun registerEndSessionListener() {
        hengamLifecycle.onAppClosed
            .doOnNext { taskScheduler.scheduleTask(SessionEndDetectorTask.Options, initialDelay = hengamConfig.sessionEndThreshold) }
            .justDo()

        appLifecycleListener.onActivityResumed()
            .justDo {
                taskScheduler.cancelTask(SessionEndDetectorTask.Options)
            }
    }

    companion object {
        /** This constant should be the same as [UserActivityAction.ACTIVITY_EXTRA_NOTIF_MESSAGE_ID] **/
        const val ACTIVITY_EXTRA_NOTIF_MESSAGE_ID = "hengam_notif_message_id"
    }
}
