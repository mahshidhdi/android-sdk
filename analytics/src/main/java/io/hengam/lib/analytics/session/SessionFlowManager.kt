package io.hengam.lib.analytics.session

import android.support.annotation.VisibleForTesting
import io.hengam.lib.analytics.*
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_SESSION
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.goal.Funnel
import io.hengam.lib.analytics.messages.downstream.FragmentFlowInfo
import io.hengam.lib.analytics.messages.downstream.SessionFragmentFlowConfigMessage
import io.hengam.lib.analytics.messages.upstream.SessionInfoMessageBuilder
import io.hengam.lib.analytics.utils.CurrentTimeGenerator
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import javax.inject.Inject


@AnalyticsScope
class SessionFlowManager @Inject constructor (
        private val currentTimeGenerator: CurrentTimeGenerator,
        private val postOffice: PostOffice,
        private val hengamConfig: HengamConfig,
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
    val sessionFlow: PersistedList<SessionActivity> = hengamStorage.createStoredList(
            "user_session_flow",
            SessionActivity::class.java
    )

    var sessionId by hengamStorage.storedString("user_session_id", IdGenerator.generateId(
        SESSION_ID_LENGTH))
        private set

    fun endSession() {
        assertCpuThread()
        Plog.info(T_ANALYTICS, T_ANALYTICS_SESSION, "User session ended",
            "Id" to sessionId,
            "Flow" to sessionFlow
        )

        sendLastActivitySessionFlowItemMessage()

        sessionFlow.clear()
        sessionId = IdGenerator.generateId(SESSION_ID_LENGTH)

        Funnel.activityFunnel.clear()
    }

    /**
     * Called when an activity is paused (@link [AppLifecycleListener.onActivityPaused])
     */
    fun updateActivityDuration(activityName: String){
        assertCpuThread()

        if (sessionFlow.isEmpty()) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Updating activity's duration in session, sessionFlow is empty",
                "Activity" to activityName
            )
            return
        }

        if (sessionFlow.last().name != activityName) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Updating activity's duration in session, wrong value as last seen activity in sessionFlow",
                "Expected last seen activity" to activityName,
                "last activity in session" to sessionFlow.last().name
            )
        } else {
            sessionFlow.last().duration +=
                    currentTimeGenerator.getCurrentTime() - sessionFlow.last().startTime
            sessionFlow.save()
        }
    }

    /**
     * Called when a fragment is paused (@link [AppLifecycleListener.onFragmentPaused])
     * If the fragment is not supposed to be in the sessionFlow message (it is disabled), does not do anything
     */
    fun updateFragmentDuration(sessionFragmentInfo: SessionFragmentInfo, parentFragments: List<SessionFragmentInfo>){
        assertCpuThread()

        if (!shouldBeAddedToSession(sessionFragmentInfo, parentFragments.size)){
            return
        }

        val fragmentParent = getFragmentSessionParent(sessionFlow, parentFragments)

        if (fragmentParent != null) {
            val fragmentContainerFlow: MutableList<SessionFragment>? =
                fragmentParent.fragmentFlows[sessionFragmentInfo.fragmentId]

            if (fragmentContainerFlow == null || fragmentContainerFlow.isEmpty()) {
                Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Updating fragment's duration in session, null or empty fragmentFlow",
                    "Activity" to sessionFragmentInfo.activityName,
                    "Id" to sessionFragmentInfo.fragmentId
                )
                return
            }

            if (fragmentContainerFlow.last().name != sessionFragmentInfo.fragmentName) {
                Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Updating fragment's duration in session, wrong value as last seen fragment in fragmentFlow",
                    "Expected last seen fragment" to sessionFragmentInfo.fragmentName,
                    "Current" to fragmentContainerFlow.last().name
                )
                return
            }

            fragmentContainerFlow.last().duration += (currentTimeGenerator.getCurrentTime()
                            - (fragmentContainerFlow.last().startTime))
            sessionFlow.save()
        }
    }

    /**
     * Called when an activity is resumed (@link [AppLifecycleListener.onActivityResumed])
     *
     * If the activity is the same one as last activity in session (there has not been a layout change)
     * sets the startTime of the [SessionActivity] to be current time.
     *
     * If the activity is a new one, builds a new [SessionActivity] and adds it to [sessionFlow]
     */
    fun updateSessionFlow(activityName: String, notifMessageId: String? = null){
        assertCpuThread()

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
        }
    }

    /**
     * Called when a fragment is resumed (@link [AppLifecycleListener.onFragmentResumed])
     *
     * Note: When a layout with nested fragments is reached, the order of calling lifeCycle callbacks
     * for the fragments is from inner fragment to outer one. So in order to have a correct flow order
     * for the fragments, before adding a fragment to session, we need to add its parent fragments first.
     *
     * If the fragment is not a parent, first adds its enabled parent fragments to session. (@see [addParentFragments])
     * If the fragment is a parent, its parents have already been added to session when adding its child fragments
     *
     * If the fragment is not supposed to be in the sessionFlow message (it is disabled), just adds
     * its enabled parents to the session
     *
     * If the fragment is the same one as last fragment in its flow in session (there has not been a layout change)
     * sets the startTime of the [SessionFragment] to be current time.
     *
     * If the fragment is a new one, builds a new [SessionFragment] and adds it to the flow in session
     *
     */
    fun updateSessionFlow(sessionFragmentInfo: SessionFragmentInfo, parentFragments: List<SessionFragmentInfo>, isParent: Boolean) {
        val enabledParentFragments = getEnabledParentFragments(parentFragments)

        if (sessionFlow.last().name != sessionFragmentInfo.activityName) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Updating fragment sessionFlow failed due to invalid last activity",
                "Expected Activity" to sessionFragmentInfo.activityName,
                "last activity in session" to sessionFlow.last().name
            )
            return
        }

        if (!isParent) {
            sessionFlow.last().fragmentFlows =
                    addParentFragments(sessionFlow.last().fragmentFlows, enabledParentFragments)
            sessionFlow.save()
        }

        if (!shouldBeAddedToSession(sessionFragmentInfo, parentFragments.size)) {
            return
        }

        val fragmentParent = getFragmentSessionParent(sessionFlow, enabledParentFragments)

        if (fragmentParent != null) {
            val viewedFragment = SessionFragment(
                    sessionFragmentInfo.fragmentName,
                    currentTimeGenerator.getCurrentTime(),
                    currentTimeGenerator.getCurrentTime(),
                    0
            )

            val parentFragmentFlow = fragmentParent.fragmentFlows[sessionFragmentInfo.fragmentId]
            if (parentFragmentFlow == null) {
                fragmentParent.fragmentFlows[sessionFragmentInfo.fragmentId] = mutableListOf(viewedFragment)
            } else if (parentFragmentFlow.isEmpty() ||
                    parentFragmentFlow.last().name != sessionFragmentInfo.fragmentName) {
                parentFragmentFlow.add(viewedFragment)
            } else if (parentFragmentFlow.last().name == sessionFragmentInfo.fragmentName) {
                parentFragmentFlow.last().startTime = currentTimeGenerator.getCurrentTime()
            }
            sessionFlow.save()
        }
    }

    private fun getEnabledParentFragments(parentFragments: List<SessionFragmentInfo>): List<SessionFragmentInfo> {
        val enabledParentsFragments = mutableListOf<SessionFragmentInfo>()
        for (i in 0 until parentFragments.size) {
            if (shouldBeAddedToSession(parentFragments[i], i)) {
                enabledParentsFragments.add(parentFragments[i])
            }
        }
        return enabledParentsFragments
    }

    /**
     * Recursively adds the given list of parent fragments to fragmentFlows given.
     * If some parents already are in their flow as last fragments, the work is done!
     * (happens if some child&parent fragment is replaced with a non-parent child fragment)
     *
     * @param fragmentFlows The base fragmentFlows for parent fragments to be added to.
     * @param parentFragments The list of parent fragments to be added to the flow
     *
     * @return The updated version of the given fragmentFlows
     */
    private fun addParentFragments(
        fragmentFlows: MutableMap<String, MutableList<SessionFragment>>,
        parentFragments: List<SessionFragmentInfo>
    ): MutableMap<String, MutableList<SessionFragment>> {

        if (parentFragments.isEmpty()) return fragmentFlows

        val lastParentSessionFragment = SessionFragment(
            parentFragments[0].fragmentName,
            currentTimeGenerator.getCurrentTime(),
            currentTimeGenerator.getCurrentTime(),
            0
        )

        var lastParentFlow = fragmentFlows[parentFragments[0].fragmentId]
        if (lastParentFlow == null) {
            lastParentFlow = mutableListOf(lastParentSessionFragment)
            fragmentFlows[parentFragments[0].fragmentId] = lastParentFlow
        } else if (lastParentFlow.isEmpty() || lastParentFlow.last().name != parentFragments[0].fragmentName) {
            lastParentFlow.add(lastParentSessionFragment)
        }
        lastParentFlow.last().fragmentFlows =
                addParentFragments(lastParentFlow.last().fragmentFlows, parentFragments.drop(1))

        return fragmentFlows
    }

    /**
     * Retrieves the direct parent of a fragment in the given sessionFlow
     *
     * @param sessionFlow The sessionActivity list in which the parent is to be found
     * @param parentFragments list of fragment's parents infos
     *
     * @return An object of [SessionFragmentParent] which is either a SessionActivity (if there is
     * no parent fragments) or a SessionFragment
     */
    @VisibleForTesting
    fun getFragmentSessionParent (
        sessionFlow: MutableList<SessionActivity>, parentFragments: List<SessionFragmentInfo>
    ) : SessionFragmentParent? {

        if (sessionFlow.isEmpty()) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Getting fragment's sessionParent failed, the given sessionFlow is empty")
            return null
        }

        if (parentFragments.isEmpty()){
            return sessionFlow.last()
        }

        var fragmentContainerFlow: MutableList<SessionFragment>

        val firstFragmentContainerFlow = sessionFlow.last().fragmentFlows[parentFragments[0].fragmentId]

        if (firstFragmentContainerFlow == null) {
            Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Getting fragment's sessionParent failed, parent fragmentFlow is null",
                "Id" to parentFragments[0].fragmentId,
                "Flow Container" to sessionFlow.last().name
            )
            return null
        }

        fragmentContainerFlow = firstFragmentContainerFlow

        for (parentFragment in parentFragments.drop(1)) {
            val nextFragmentContainerFlow =
                    fragmentContainerFlow.last().fragmentFlows[parentFragment.fragmentId]
            if (nextFragmentContainerFlow == null) {
                Plog.error(T_ANALYTICS, T_ANALYTICS_SESSION, "Getting fragment's sessionParent failed, parent fragmentFlow is null",
                    "Id" to parentFragment.fragmentId,
                    "Flow Container" to fragmentContainerFlow.last().name
                )
                return null
            }
            fragmentContainerFlow = nextFragmentContainerFlow
        }

        return fragmentContainerFlow.last()
    }

    /**
     * Called when updating session fragmentFlows.
     * Checks whether the given fragment should be added to sessionFlow message according to the preDefined config
     * @see [sessionFragmentFlowEnabled]
     * @see [sessionFragmentFlowExceptionList]
     * @see [sessionFragmentFlowDepthLimit]
     */
    private fun shouldBeAddedToSession(sessionFragmentInfo: SessionFragmentInfo, numberOfParents: Int): Boolean{
        val fragmentFlowInfo = FragmentFlowInfo(sessionFragmentInfo.activityName, sessionFragmentInfo.fragmentId)
        return (hengamConfig.sessionFragmentFlowEnabled && numberOfParents < hengamConfig.sessionFragmentFlowDepthLimit && !hengamConfig.sessionFragmentFlowExceptionList.contains(fragmentFlowInfo)) ||
                (!hengamConfig.sessionFragmentFlowEnabled && hengamConfig.sessionFragmentFlowExceptionList.contains(fragmentFlowInfo))
    }

    fun changeSessionFragmentFlowConfig(message: SessionFragmentFlowConfigMessage) {
        hengamConfig.sessionFragmentFlowEnabled = message.isEnabled
        if (message.depthLimit != null) hengamConfig.sessionFragmentFlowDepthLimit = message.depthLimit
        hengamConfig.sessionFragmentFlowExceptionList = message.exceptionList
    }

    fun sendLastActivitySessionFlowItemMessage() {
        Plog.trace(T_ANALYTICS, T_ANALYTICS_SESSION,
            "Sending session activity message",
            "activityName" to sessionFlow.last().name
        )
        postOffice.sendMessage(
            message = SessionInfoMessageBuilder.build(sessionId, sessionFlow.last(), appVersionCode),
            sendPriority = SendPriority.LATE
        )
    }

    companion object {
        const val SESSION_ID_LENGTH = 16
    }
}
