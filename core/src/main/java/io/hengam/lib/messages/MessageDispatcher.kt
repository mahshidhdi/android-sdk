package io.hengam.lib.messages

import io.hengam.lib.DeliveryController
import io.hengam.lib.Hengam
import io.hengam.lib.RegistrationManager
import io.hengam.lib.TopicController
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.messages.downstream.RunDebugCommandMessage
import io.hengam.lib.messages.downstream.UpdateConfigMessage
import io.hengam.lib.messages.downstream.UpdateTopicSubscriptionMessage
import io.hengam.lib.messages.upstream.CheckHiddenAppUpstreamMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.ResponseMessage
import io.hengam.lib.utils.ApplicationInfoHelper
import javax.inject.Inject

class MessageDispatcher @Inject constructor(
        private val postOffice: PostOffice,
        private val deliveryController: DeliveryController,
        private val registrationManager: RegistrationManager,
        private val topicController: TopicController,
        private val hengamConfig: HengamConfig,
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
            hengamConfig.updateConfig(it)
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
            Hengam.debugApi().handleCommand(it)
        }
    }
}