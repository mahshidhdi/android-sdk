package co.pushe.plus.messages

import co.pushe.plus.DeliveryController
import co.pushe.plus.Pushe
import co.pushe.plus.RegistrationManager
import co.pushe.plus.TopicController
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.messages.downstream.RunDebugCommandMessage
import co.pushe.plus.messages.downstream.UpdateConfigMessage
import co.pushe.plus.messages.downstream.UpdateTopicSubscriptionMessage
import co.pushe.plus.messages.upstream.CheckHiddenAppUpstreamMessage
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.ResponseMessage
import co.pushe.plus.utils.ApplicationInfoHelper
import javax.inject.Inject

class MessageDispatcher @Inject constructor(
        private val postOffice: PostOffice,
        private val deliveryController: DeliveryController,
        private val registrationManager: RegistrationManager,
        private val topicController: TopicController,
        private val pusheConfig: PusheConfig,
        private val applicationInfoHelper: ApplicationInfoHelper

) {

    fun listenForMessages() {
        /* Send message deliveries if they have been requested */
        postOffice.mailBox { deliveryController.sendMessageDeliveryIfRequested(it) }

        /* Handle Registration Response Message */
        postOffice.mailBox(ResponseMessage.Parser(MessageType.Upstream.REGISTRATION)) {
            registrationManager.handleRegistrationResponseMessage(it)
        }

        /* Handle Topic Subscription Message */
        postOffice.mailBox(UpdateTopicSubscriptionMessage.Parser()) {
            topicController.handleUpdateTopicMessage(it)
        }

        /* Handle Config Message */
        postOffice.mailBox(UpdateConfigMessage.Parser()) {
            pusheConfig.updateConfig(it)
        }

        /* Handle HiddenApp Message */
        postOffice.mailBox(MessageType.Datalytics.IS_APP_HIDDEN) {
            postOffice.sendMessage(CheckHiddenAppUpstreamMessage(applicationInfoHelper.isAppHidden()))
        }

        /* Handle Reregistration message */
        postOffice.mailBox(MessageType.Downstream.REREGISTER) {
            registrationManager.performRegistration("t23_cmd")
        }

        /* Handle DebugCommand message */
        postOffice.mailBox(RunDebugCommandMessage.Parser()) {
            Pushe.debugApi().handleCommand(it)
        }
    }
}