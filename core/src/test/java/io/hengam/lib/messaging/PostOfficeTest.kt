package io.hengam.lib.messaging

import io.hengam.lib.HengamLifecycle
import io.hengam.lib.extendMoshi
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.tasks.UpstreamSenderTask
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.mockIoThread
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.*
import io.reactivex.Completable
import io.reactivex.plugins.RxJavaPlugins
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class PostOfficeTest {
    private lateinit var postOffice: PostOffice

    private val messageStore: MessageStore = mockk(relaxed = true)
    private val parcelStamper: ParcelStamper = mockk(relaxed = true)
    private val taskScheduler: TaskScheduler = mockk(relaxed = true)
    private val hengamLifecycle = HengamLifecycle(mockk(relaxed = true))
    private val moshi = HengamMoshi()
    private val hengamConfig = HengamConfig(MockSharedPreference(), moshi)

    private val ioThread = mockIoThread()
    private val cpuThread = mockCpuThread()

    fun setUp() {
        extendMoshi(moshi)

        RxJavaPlugins.setComputationSchedulerHandler { ioThread }

        postOffice = PostOffice(
                taskScheduler,
                messageStore,
                parcelStamper,
                moshi,
                hengamConfig,
                hengamLifecycle
        )
    }

    private fun setUpPostRegistrationSendMessageTests() {
        setUp()
        hengamLifecycle.registrationComplete()
        cpuThread.triggerActions()
    }

    private fun mockUpstreamMessage(): SendableUpstreamMessage {
        val message = mockk<SendableUpstreamMessage>()
        every { message.prepare() } returns Completable.complete()
        return message
    }

    @Test
    fun sendMessage_MessageIsStored() {
        setUpPostRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.SOON)
        cpuThread.triggerActions()
        ioThread.triggerActions()
        verify { messageStore.storeMessage(message, SendPriority.SOON, true, true, null, any()) }
    }

    @Test
    fun sendMessage_SenderTaskIsStartedImmediatelyOnIMMEDIATESenderPriority() {
        setUpPostRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.IMMEDIATE)
        cpuThread.triggerActions()
        ioThread.triggerActions()
        verify(exactly = 1) { taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_SenderTaskIsStartedAfterDelayOnSOONSenderPriority() {
        setUpPostRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.SOON)
        cpuThread.triggerActions()

        ioThread.advanceTimeBy(PostOffice.BUFFER_TIME_SOON / 2, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        verify(exactly = 0){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }

        ioThread.advanceTimeBy(PostOffice.BUFFER_TIME_SOON / 2, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
   }

    /**
     * The sending delay should be started from the time the message is received.
     * If the delay for SendPriority.SOON is 2 seconds and a message arrives at t=1s, then
     * the message should be sent at t=3s not at t=2s
     */
    @Test
    fun sendMessage_SenderTaskIsNotStartedTooSoonWithSOONSenderPriority() {
        setUpPostRegistrationSendMessageTests()
        val message = mockUpstreamMessage()

        ioThread.advanceTimeBy(PostOffice.BUFFER_TIME_SOON / 2, TimeUnit.MILLISECONDS)

        // time=T/2  send message
        postOffice.sendMessage(message, SendPriority.SOON)
        cpuThread.triggerActions()
        verify(exactly = 0){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }

        // time=T  should not be sent yet
        ioThread.advanceTimeBy(PostOffice.BUFFER_TIME_SOON / 2, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        verify(exactly = 0){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }

        // time=3T/2  should be sent here
        ioThread.advanceTimeBy(PostOffice.BUFFER_TIME_SOON / 2, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_SenderTaskIsNotStartedOnWHENEVERSenderPriority() {
        setUpPostRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.WHENEVER)
        cpuThread.triggerActions()
        ioThread.advanceTimeBy(100, TimeUnit.DAYS)
        verify(exactly = 0){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    private fun setUpPreRegistrationSendMessageTests() {
        setUp()
    }

    @Test
    fun sendMessage_MessageIsSentIfNotRegisteredAndRegistrationNotRequired() {
        setUpPreRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.IMMEDIATE, persistAcrossRuns = false, requiresRegistration = false)
        cpuThread.triggerActions()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_MessageIsNotSentIfNotRegisteredAndRegistrationIsRequired() {
        setUpPreRegistrationSendMessageTests()
        val message = mockUpstreamMessage()
        postOffice.sendMessage(message, SendPriority.IMMEDIATE, persistAcrossRuns = false, requiresRegistration = true)
        cpuThread.triggerActions()
        verify(exactly = 0){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_SendTaskWillNotBeScheduledAfterRegistrationIfMessagesNotPending() {
        sendMessage_MessageIsSentIfNotRegisteredAndRegistrationNotRequired()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
        hengamLifecycle.registrationComplete()
        cpuThread.triggerActions()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_SendTaskWillBeScheduledAfterRegistrationIfMessagesPending() {
        sendMessage_MessageIsNotSentIfNotRegisteredAndRegistrationIsRequired()
        hengamLifecycle.registrationComplete()
        cpuThread.triggerActions()
        verify(exactly = 1){ taskScheduler.scheduleTask(UpstreamSenderTask.Options) }
    }

    @Test
    fun sendMessage_PreparesMessage() {
        setUpPostRegistrationSendMessageTests()
        val message = UpstreamMockMessageMovieWithMixin("Matrix", Genre.SCIFI, 1998)
        postOffice.sendMessage(message, SendPriority.WHENEVER)
        cpuThread.triggerActions()
        assertTrue(message.isPrepared)
    }

    private fun setUpReceiveMessageTests(vararg inboundCouriers: InboundCourier) {
        setUp()
    }

    @Test
    fun receiveMessages_OnlyReceiveMessagesWhichFilteredOn() {
        setUpReceiveMessageTests()
        val goodMessageType = 10
        val badMessageType = 20

        val subscription = postOffice.receiveMessages(goodMessageType).test()

        val message11 = RawDownstreamMessage("11", goodMessageType, "Some Message")
        val message12 = RawDownstreamMessage("12", badMessageType, "Some Message")
        val message13 = RawDownstreamMessage("13", goodMessageType, "Some Message")
        val message21 = RawDownstreamMessage("21", goodMessageType, "Some Message")
        val message22 = RawDownstreamMessage("22", badMessageType, "Some Message")

        postOffice.onInboundParcelReceived(createParcel(message11, message12))
        postOffice.onInboundParcelReceived(createParcel(message22, message21))
        postOffice.onInboundParcelReceived(createParcel(message13))

        ioThread.triggerActions()
        cpuThread.triggerActions()

        subscription.assertValuesOnly(message11, message21, message13)
    }

    @Test
    fun receiveMessages_AllowMultipleReceiversForMessageType() {
        setUpReceiveMessageTests()
        val messageType = 10

        val subscription1 = postOffice.receiveMessages(messageType).test()
        val subscription2 = postOffice.receiveMessages(messageType).test()

        val message = RawDownstreamMessage("11", messageType, "Some Message")
        postOffice.onInboundParcelReceived(createParcel(message))

        ioThread.triggerActions()
        cpuThread.triggerActions()

        subscription1.assertValuesOnly(message)
        subscription2.assertValuesOnly(message)
    }

    @Test
    fun receiveMessages_ContinueIfErrorHappensInSubscriber() {
        setUpReceiveMessageTests()
        val messageType = 10

        postOffice.receiveMessages(messageType)
                .subscribe(
                        { throw RuntimeException("Test Exception") },
                        {}
                )
        val subscription2 = postOffice.receiveMessages(messageType).test()

        val message1 = RawDownstreamMessage("11", messageType, "Some Message")
        val message2 = RawDownstreamMessage("12", messageType, "Some Message")
        val message3 = RawDownstreamMessage("21", messageType, "Some Message")
        val message4 = RawDownstreamMessage("22", messageType, "Some Message")
        postOffice.onInboundParcelReceived(createParcel(message1))
        postOffice.onInboundParcelReceived(createParcel(message2))
        postOffice.onInboundParcelReceived(createParcel(message3, message4))

        ioThread.triggerActions()
        cpuThread.triggerActions()

        subscription2.assertValuesOnly(message1, message2, message3, message4)
    }

    @Test
    fun receiveMessages_MessageParsersFilterAndParseMessages() {
        setUpReceiveMessageTests()

        val goodMessageType = 10
        val badMessageType = 20

        val message1 = RawDownstreamMessage("1", goodMessageType, "Some Message")
        val message2 = RawDownstreamMessage("2", badMessageType, "Some Message")
        val message3 = RawDownstreamMessage("3", goodMessageType, "Some Message")
        val message4 = RawDownstreamMessage("4", badMessageType, "Some Message")

        val parsedMessage1 = MockDownstreamMessage("1")
        val parsedMessage3 = MockDownstreamMessage("3")

        val messageParser = mockk<DownstreamMessageParser<MockDownstreamMessage>>()
        every { messageParser.messageType } returns goodMessageType
        every { messageParser.parseMessage(any(), message1) } returns parsedMessage1
        every { messageParser.parseMessage(any(), message3) } returns parsedMessage3

        val subscription = postOffice.receiveMessages(messageParser).test()

        postOffice.onInboundParcelReceived(createParcel(message1, message2, message3, message4))

        ioThread.triggerActions()
        cpuThread.triggerActions()

        verify(exactly = 0) { messageParser.parseMessage(any(), message2) }
        verify(exactly = 0) { messageParser.parseMessage(any(), message4) }
        subscription.assertValuesOnly(parsedMessage1, parsedMessage3)
    }

    @Test
    fun receiveMessages_MessageParserErrorsAreHandledAndDoesNotBreak() {
        setUpReceiveMessageTests()

        val messageType = 10

        val message1 = RawDownstreamMessage("1", messageType, "Some Message")
        val message2 = RawDownstreamMessage("2", messageType, "Some Message")
        val message3 = RawDownstreamMessage("3", messageType, "Some Message")
        val message4 = RawDownstreamMessage("4", messageType, "Some Message")

        val parsedMessage1 = MockDownstreamMessage("1")
        val parsedMessage3 = MockDownstreamMessage("3")
        val parsedMessage4 = MockDownstreamMessage("4")

        val messageParser = mockk<DownstreamMessageParser<MockDownstreamMessage>>()
        every { messageParser.messageType } returns messageType
        every { messageParser.parseMessage(any(), message1) } returns parsedMessage1
        every { messageParser.parseMessage(any(), message2) } throws IOException("Bad Json")
        every { messageParser.parseMessage(any(), message3) } returns parsedMessage3
        every { messageParser.parseMessage(any(), message4) } returns parsedMessage4

        val subscription = postOffice.receiveMessages(messageParser).test()

        postOffice.onInboundParcelReceived(createParcel(message1, message2, message3, message4))

        ioThread.triggerActions()
        cpuThread.triggerActions()

        subscription.assertValuesOnly(parsedMessage1, parsedMessage3, parsedMessage4)
    }
}

fun createParcel(vararg messages: RawDownstreamMessage): DownstreamParcel {
    return DownstreamParcel("parcel-id", messages.toList())
}

class MockDownstreamMessage(
        val messageId: String
)