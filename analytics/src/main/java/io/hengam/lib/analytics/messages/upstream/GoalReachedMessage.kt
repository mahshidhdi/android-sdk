package io.hengam.lib.analytics.messages.upstream

import io.hengam.lib.messaging.TypedUpstreamMessage
import io.hengam.lib.analytics.goal.GoalType
import io.hengam.lib.analytics.goal.ViewGoal
import io.hengam.lib.messages.MessageType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class GoalReachedMessage(
        @Json(name = "session_id") val sessionId: String,
        @Json(name = "type") val goalType: GoalType,
        @Json(name = "name") val name: String,
        @Json(name = "view_goals") val viewGoals: Map<String, String?>,
        @Json(name = "view_goals_with_error") val viewGoalsWithError: List<ViewGoal>,
        @Json(name = "activity_funnel") val activityFunnel: List<String>,
        @Json(name = "fragment_funnel") val fragmentFunnel: List<String>
) : TypedUpstreamMessage<GoalReachedMessage>(
    MessageType.Analytics.Upstream.GOAL_REACHED,
    { GoalReachedMessageJsonAdapter(it) }){
    override fun toString(): String {
        return "GoalReachedMessage(" +
                "sessionId=$sessionId, " +
                "goalType=$goalType, " +
                "name='$name', " +
                "viewGoals=$viewGoals, " +
                "activityFunnel=$activityFunnel, " +
                "fragmentFunnel=$fragmentFunnel)"
    }
}