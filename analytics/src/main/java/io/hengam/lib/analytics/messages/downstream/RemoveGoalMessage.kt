package io.hengam.lib.analytics.messages.downstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class RemoveGoalMessage (
    @Json(name = "goals") val GoalNames: Set<String>
) {
    class Parser : DownstreamMessageParser<RemoveGoalMessage>(
        MessageType.Analytics.Downstream.REMOVE_GOAL,
        { RemoveGoalMessageJsonAdapter(it) }
    )
}