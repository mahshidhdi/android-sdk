package io.hengam.lib.analytics.messages.downstream

import io.hengam.lib.messaging.DownstreamMessageParser
import io.hengam.lib.analytics.goal.ActivityReachGoal
import io.hengam.lib.analytics.goal.ButtonClickGoal
import io.hengam.lib.analytics.goal.FragmentReachGoal
import io.hengam.lib.messages.MessageType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NewGoalMessage (
    @Json(name = "activity") val activityReachGoals: List<ActivityReachGoal>,
    @Json(name = "fragment") val fragmentReachGoals: List<FragmentReachGoal>,
    @Json(name = "button") val buttonClickGoals: List<ButtonClickGoal>
) {
    class Parser : DownstreamMessageParser<NewGoalMessage>(
        MessageType.Analytics.Downstream.NEW_GOAL,
        { NewGoalMessageJsonAdapter(it) }
    )
}