package io.hengam.lib.analytics

import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.seconds

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
 */
val HengamConfig.sessionFragmentFlowEnabled: Boolean
    get() = getBoolean(
        "session_fragment_flow_enabled",
        true
    )

/**
 * Determines the number of inner-fragments (fragments inside fragments) to be included in the
 * user session message
 */
val HengamConfig.sessionFragmentFlowDepthLimit: Int
    get() = getInteger(
        "session_fragment_flow_depth_limit",
        2
    )

/**
 * Whether or not the sending fragmentFlow in session is enabled, there is an exception list
 * containing fragment containerIds to send if not enabled or not sent if enabled
 * @see [SessionFragmentInfo.shouldBeAddedToSession]
 *
 */
val HengamConfig.sessionFragmentFlowExceptionList: List<String>
    get() = getStringList("session_fragment_flow_exception_list")

