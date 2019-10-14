package co.pushe.plus.analytics.messages.downstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.DownstreamMessageParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SessionFragmentFlowConfigMessage (
    @Json(name = "is_enabled") val isEnabled: Boolean,
    @Json(name = "depth_limit") val depthLimit: Int? = null,
    @Json(name = "exception_list") val exceptionList: List<FragmentFlowInfo> = listOf()
) {
    class Parser : DownstreamMessageParser<SessionFragmentFlowConfigMessage>(
        MessageType.Analytics.Downstream.SESSION_CONFIG,
        { SessionFragmentFlowConfigMessageJsonAdapter(it) }
    )
}

data class FragmentFlowInfo(
    @Json(name = "activity_name") val activityName: String,
    @Json(name = "fragment_id") val fragmentId: String
)