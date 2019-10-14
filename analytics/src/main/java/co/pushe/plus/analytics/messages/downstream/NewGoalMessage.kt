package co.pushe.plus.analytics.messages.downstream

import co.pushe.plus.messaging.DownstreamMessageParser
import co.pushe.plus.analytics.goal.ActivityReachGoal
import co.pushe.plus.analytics.goal.ButtonClickGoal
import co.pushe.plus.analytics.goal.FragmentReachGoal
import co.pushe.plus.messages.MessageType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NewGoalMessage (
    @Json(name = "activity") val activityReachGoals: List<ActivityReachGoal>,
    @Json(name = "fragment") val fragmentReachGoals: List<FragmentReachGoal>,
    @Json(name = "button") val buttonClickGoals: List<ButtonClickGoal>
) {
    class Parser : DownstreamMessageParser<NewGoalMessage>(
//         TODO: add a message type for goal passing
        MessageType.Analytics.Downstream.NEW_GOAL,
        { NewGoalMessageJsonAdapter(it) }
    )
}