package co.pushe.plus.analytics.messages.upstream

import co.pushe.plus.messaging.TypedUpstreamMessage
import co.pushe.plus.analytics.session.SessionActivity
import co.pushe.plus.analytics.session.SessionFragment
import co.pushe.plus.messages.MessageType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SessionInfoMessage (
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "name") val name: String,
    @Json(name = "start_time") var startTime: Long,
    @Json(name = "duration") var duration: Long,
    @Json(name = "fragments") val fragmentFlows: MutableMap<String, MutableList<SessionFragmentMessageWrapper>> = mutableMapOf(),
    @Json(name = "src_notif") var sourceNotifMessageId: String? = null,
    @Json(name = "av_code") val appVersionCode: Long? = null
) : TypedUpstreamMessage<SessionInfoMessage>(
    MessageType.Analytics.Upstream.SESSION_INFO,
    { SessionInfoMessageJsonAdapter(it) })

/**
 * The SessionFlow unit, [SessionFragment], has a dynamic startTime field that
 * is updated on every start of a layout and should not be in the sessionInfoMessage.
 * So it can not be used in sessionInfoMessage and there is a need for this wrapper
 */
@JsonClass(generateAdapter = true)
class SessionFragmentMessageWrapper(
    @Json(name = "name") val name: String,
    @Json(name = "start_time") var startTime: Long,
    @Json(name = "duration") var duration: Long,
    @Json(name = "fragments") val fragmentFlows: MutableMap<String, MutableList<SessionFragmentMessageWrapper>> = mutableMapOf()
)

/**
 * A singleton object with the only functionality of building SessionFlow message wrappers and
 * sessionInfoMessage object from sessionFlow units [SessionActivity] & [SessionFragment]
 */
object SessionInfoMessageBuilder {
    fun build(sessionId: String, sessionActivity: SessionActivity, appVersionCode: Long?): SessionInfoMessage {

        val fragmentFlows = sessionActivity.fragmentFlows
        val messageFragmentFlows: MutableMap<String, MutableList<SessionFragmentMessageWrapper>> = mutableMapOf()
        if (fragmentFlows.isNotEmpty()){
            for (fragmentFlow in fragmentFlows){
                messageFragmentFlows[fragmentFlow.key] =
                        getSessionFragmentWrappers(fragmentFlow.value)
            }
        }

        return SessionInfoMessage(
            sessionId,
            sessionActivity.name,
            sessionActivity.originalStartTime,
            sessionActivity.duration,
            messageFragmentFlows,
            sessionActivity.sourceNotifMessageId,
            appVersionCode
        )
    }

    private fun getSessionFragmentWrappers(sessionFragments: MutableList<SessionFragment>): MutableList<SessionFragmentMessageWrapper> {
        val fragmentWrappers: MutableList<SessionFragmentMessageWrapper> = mutableListOf()
        var sessionFragmentWrapper: SessionFragmentMessageWrapper
        for (sessionFragment in sessionFragments) {
            sessionFragmentWrapper = SessionFragmentMessageWrapper(
                sessionFragment.name,
                sessionFragment.originalStartTime,
                sessionFragment.duration,
                mutableMapOf()
            )
            val fragmentFlows = sessionFragment.fragmentFlows
            if (fragmentFlows.isNotEmpty()){
                for (fragmentFlow in fragmentFlows){
                    sessionFragmentWrapper.fragmentFlows[fragmentFlow.key] =
                            getSessionFragmentWrappers(fragmentFlow.value)
                }
            }
            fragmentWrappers.add(sessionFragmentWrapper)
        }
        return fragmentWrappers
    }
}
