package co.pushe.plus.analytics.messages.downstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.DownstreamMessageParser
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