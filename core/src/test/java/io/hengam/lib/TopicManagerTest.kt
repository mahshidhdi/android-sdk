package io.hengam.lib

import io.hengam.lib.internal.HengamSchedulers
import io.hengam.lib.messages.upstream.TopicStatusMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.fcm.FcmTopicSubscriber
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.mockIoThread
import io.hengam.lib.utils.test.mocks.MockPersistedSet
import io.mockk.*
import io.reactivex.Completable
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class TopicManagerTest {
    private val ioThread = mockIoThread()
    private val cpuThread = mockCpuThread()

    private val fcmTopicSubscriber: FcmTopicSubscriber = mockk(relaxed = true)
//    private val lashTopicSubscriber: LashTopicSubscriber = mockk(relaxed = true)

    private val appManifest: AppManifest = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val hengamStorage: HengamStorage = mockk(relaxed = true)
    private val topicStore = MockPersistedSet<String>()

    private val appToken = "apptoken"

    private lateinit var topicManager: TopicManager

    @Before
    fun setUp() {
        mockkObject(HengamSchedulers)
        every { HengamSchedulers.io } returns ioThread
        every { HengamSchedulers.cpu } returns cpuThread
        every { appManifest.appId } returns appToken
        every { hengamStorage.createStoredSet(any(), String::class.java) } returns topicStore

        topicManager = TopicManager(
                fcmTopicSubscriber = fcmTopicSubscriber,
//            lashTopicSubscriber = lashTopicSubscriber,
                appManifest = appManifest,
                postOffice = postOffice,
                hengamStorage = hengamStorage
        )
    }

    private fun setUpToSucceed() {
        every { fcmTopicSubscriber.subscribeToTopic(any()) } returns Completable.complete()
        every { fcmTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.complete()
//        every { lashTopicSubscriber.subscribeToTopic(any()) } returns Completable.complete()
//        every { lashTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.complete()
    }

    private fun setUpToFail() {
        every { fcmTopicSubscriber.subscribeToTopic(any()) } returns Completable.error(IOException("Test Exception"))
        every { fcmTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.error(IOException("Test Exception"))
//        every { lashTopicSubscriber.subscribeToTopic(any()) } returns Completable.error(IOException("Test Exception"))
//        every { lashTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.error(IOException("Test Exception"))
    }

    private fun setTopics(vararg topics: String) {
        topicStore.addAll(topics)
    }

    private fun verifyTopics(vararg topics: String) {
        assertEquals(setOf(*topics), topicManager.subscribedTopics.toSet())
        assertEquals(setOf(*topics), topicStore.toSet())
    }

    private fun verifyTopicMessageSent(topicName: String, status: Int) {
        val slot = CapturingSlot<TopicStatusMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot))}
        assertEquals("${topicName}_$appToken", slot.captured.topic)
        assertEquals(status, slot.captured.status)
    }

    @Test
    fun subscribe_OnSuccessWillStoreTopicAndSendMessage() {
        setUpToSucceed()
        setTopics("topic1", "topic2")
        val subscription = topicManager.subscribe("topic3").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertComplete()
        verifyTopics("topic1", "topic2", "topic3_$appToken")
        verifyTopicMessageSent("topic3", 0)
    }

    @Test
    fun subscribe_OnFailWillNotStoreTopicOrSendMessage() {
        setUpToFail()
        setTopics("topic1", "topic2")
        val subscription = topicManager.subscribe("topic3").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertError(IOException::class.java)
        verifyTopics("topic1", "topic2")
        verify(exactly = 0) { postOffice.sendMessage(any(), any())}
    }

    @Test
    fun subscribe_WillFailIfEvenOneCourierFails() {
        every { fcmTopicSubscriber.subscribeToTopic(any()) } returns Completable.error(IOException("Test Exception"))
//        every { lashTopicSubscriber.subscribeToTopic(any()) } returns Completable.error(IOException("Test Exception"))

        setTopics("topic1", "topic2")
        val subscription = topicManager.subscribe("topic3").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertError(IOException::class.java)
        verifyTopics("topic1", "topic2")
        verify(exactly = 0) { postOffice.sendMessage(any(), any())}
    }

    @Test
    fun unsubscribe_OnSuccessWillRemoveTopicAndSendMessage() {
        setUpToSucceed()
        setTopics("topic1", "topic2_$appToken", "topic3")
        val subscription = topicManager.unsubscribe("topic2").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertComplete()
        verifyTopics("topic1", "topic3")
        verifyTopicMessageSent("topic2", 1)
    }

    @Test
    fun unsubscribe_OnFailWillNotRemoveTopicOrSendMessage() {
        setUpToFail()
        setTopics("topic1", "topic2", "topic3")
        val subscription = topicManager.unsubscribe("topic2").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertError(IOException::class.java)
        verifyTopics("topic1", "topic2", "topic3")
        verify(exactly = 0) { postOffice.sendMessage(any(), any())}
    }

    @Test
    fun unsubscribe_WillFailIfEvenOneCourierFails() {
        every { fcmTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.error(IOException("Test Exception"))
//        every { lashTopicSubscriber.unsubscribeFromTopic(any()) } returns Completable.error(IOException("Test Exception"))

        setTopics("topic1", "topic2", "topic3")
        val subscription = topicManager.unsubscribe("topic2").test()
        ioThread.triggerActions()
        cpuThread.triggerActions()
        subscription.assertError(IOException::class.java)
        verifyTopics("topic1", "topic2", "topic3")
        verify(exactly = 0) { postOffice.sendMessage(any(), any())}
    }
}
