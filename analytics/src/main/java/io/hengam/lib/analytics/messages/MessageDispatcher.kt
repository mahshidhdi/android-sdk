package io.hengam.lib.analytics.messages

import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.analytics.goal.GoalProcessManager
import io.hengam.lib.analytics.messages.downstream.NewGoalMessage
import io.hengam.lib.analytics.messages.downstream.RemoveGoalMessage
import javax.inject.Inject

@AnalyticsScope
class MessageDispatcher @Inject constructor(
    private val postOffice: PostOffice,
    private val goalManager: GoalProcessManager
) {
    fun listenForMessages() {
        postOffice.mailBox(NewGoalMessage.Parser()) { goalManager.updateGoals(it) }
        postOffice.mailBox(RemoveGoalMessage.Parser()) { goalManager.removeGoals(it) }
    }
}