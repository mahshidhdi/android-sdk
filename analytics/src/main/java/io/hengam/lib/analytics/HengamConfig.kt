package io.hengam.lib.analytics

import io.hengam.lib.analytics.messages.downstream.FragmentFlowInfo
import io.hengam.lib.analytics.messages.downstream.SessionFragmentFlowConfigMessage
import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.seconds
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

/**
 * The amount of time which should pass after the application has been closed (or moved to the
 * background) in order to consider the session ended.
 */
val HengamConfig.sessionEndThreshold: Time
    get() = getLong("session_end_threshold", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: seconds(8)


/**
 * Sending fragmentFlow in the sessionMessage can be disabled.
 * This field shows whether it is enabled or not
 * @see [SessionFlowManager.shouldBeAddedToSession]
 * @see [SessionFragmentFlowConfigMessage]
 */
var HengamConfig.sessionFragmentFlowEnabled: Boolean
    get() = getBoolean(
        "session_fragment_flow_enabled",
        true
    )
    set(value) = updateConfig("session_fragment_flow_enabled", value.toString())

/**
 * Determines the number of inner-fragments (fragments inside fragments) to be included in the
 * user session message
 */
var HengamConfig.sessionFragmentFlowDepthLimit: Int
    get() = getInteger(
        "session_fragment_flow_depth_limit",
        2
    )
    set(value) = updateConfig("session_fragment_flow_depth_limit", value.toString())

/**
 * Whether or not the sending fragmentFlow in session is enabled, there is an exception list
 * containing [FragmentFlowInfo]s to send if not enabled or not sent if enabled
 * @see [SessionFlowManager.shouldBeAddedToSession]
 * @see [SessionFragmentFlowConfigMessage]
 *
 */
var HengamConfig.sessionFragmentFlowExceptionList: List<FragmentFlowInfo>
    get() = getObjectList(
        key = "session_fragment_flow_exception_list",
        type = FragmentFlowInfo::class.java,
        adapter = moshi.adapter(FragmentFlowInfo::class.java)
    )
    set(value) {
        val sessionFragmentFlowExceptionListAdapter: JsonAdapter<List<FragmentFlowInfo>> =
            moshi.adapter(Types.newParameterizedType(List::class.java, FragmentFlowInfo::class.java))
        updateConfig(
            "session_fragment_flow_exception_list",
            sessionFragmentFlowExceptionListAdapter.toJson(value).toString()
        )
    }

