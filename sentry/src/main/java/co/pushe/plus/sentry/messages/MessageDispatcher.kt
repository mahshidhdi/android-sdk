package co.pushe.plus.sentry.messages

import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.sentry.*
import co.pushe.plus.sentry.messages.downstream.SentryConfigMessage
import javax.inject.Inject

class MessageDispatcher @Inject constructor(
        private val postOffice: PostOffice,
        private val pusheConfig: PusheConfig
) {

    fun listenForMessages() {
        postOffice.mailBox(SentryConfigMessage.Parser()) { handleSentryConfigMessage(pusheConfig, it) }
    }
}