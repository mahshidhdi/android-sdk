package co.pushe.plus.analytics.messages

import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.analytics.dagger.AnalyticsScope
import co.pushe.plus.analytics.goal.GoalProcessManager
import co.pushe.plus.analytics.messages.downstream.NewGoalMessage
import co.pushe.plus.analytics.messages.downstream.RemoveGoalMessage
import co.pushe.plus.analytics.messages.downstream.SessionFragmentFlowConfigMessage
import co.pushe.plus.analytics.session.SessionFlowManager
import javax.inject.Inject

@AnalyticsScope
class MessageDispatcher @Inject constructor(
    private val postOffice: PostOffice,
    private val goalManager: GoalProcessManager,
    private val sessionFlowManager: SessionFlowManager
) {
    fun listenForMessages() {
        postOffice.mailBox(NewGoalMessage.Parser()) { goalManager.updateGoals(it) }
        postOffice.mailBox(RemoveGoalMessage.Parser()) { goalManager.removeGoals(it) }
        postOffice.mailBox(SessionFragmentFlowConfigMessage.Parser()) { sessionFlowManager.changeSessionFragmentFlowConfig(it) }
    }
}