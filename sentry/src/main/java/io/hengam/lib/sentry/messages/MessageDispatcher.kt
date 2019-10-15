package io.hengam.lib.sentry.messages

import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.sentry.*
import io.hengam.lib.sentry.messages.downstream.SentryConfigMessage
import javax.inject.Inject

class MessageDispatcher @Inject constructor(
        private val postOffice: PostOffice,
        private val hengamConfig: HengamConfig
) {

    fun listenForMessages() {
        postOffice.mailBox(SentryConfigMessage.Parser()) { handleSentryConfigMessage(hengamConfig, it) }
    }
}