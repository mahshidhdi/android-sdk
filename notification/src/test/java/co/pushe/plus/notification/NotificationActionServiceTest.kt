package co.pushe.plus.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.actions.*
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.NetworkInfoHelper
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import io.mockk.*
import org.junit.Before
import org.junit.Test

class NotificationActionServiceTest {
    private val cpuThread = mockCpuThread()

    private val context: Context = mockk(relaxed = true)
    private val moshi: PusheMoshi = PusheMoshi()
    private val mockedMoshi: PusheMoshi = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val networkInfo: NetworkInfoHelper = mockk(relaxed = true)
    private val actionContextFactory: ActionContextFactory = mockk(relaxed = true)
    private val notificationInteractionReporter: NotificationInteractionReporter =
        mockk(relaxed = true)

    private val notificationComponent: NotificationComponent = mockk(relaxed = true)

    private fun getNotificationActionService(mockMoshi: Boolean = false): NotificationActionService {
        val notificationActionService = NotificationActionService()
        notificationActionService.context = context
        notificationActionService.postOffice = postOffice
        notificationActionService.networkInfo = networkInfo
        notificationActionService.actionContextFactory = actionContextFactory
        notificationActionService.notificationInteractionReporter = notificationInteractionReporter
        if (mockMoshi) {
            notificationActionService.moshi = mockedMoshi
        } else {
            notificationActionService.moshi = moshi
        }

        return notificationActionService
    }

    @Before
    fun setUp() {
        co.pushe.plus.extendMoshi(moshi)
        co.pushe.plus.notification.extendMoshi(moshi)
        mockkObject(PusheInternals)
        every { PusheInternals.getComponent(NotificationComponent::class.java) } returns notificationComponent
        every { notificationComponent.inject(any() as NotificationActionService) } just Runs
    }

    @Test
    fun handelIntent_SendsNotificationClickIfClicked() {
        val messageJson = """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "I",
                    "uri": "Some Data",
                    "action":"Some Action",
                    "category":["Some Category"],
                    "market_package_name":"Some Package name",
                    "resolvers":["resolver","another resolver"]
                }
            }
        """

        val actionJson = """
            {
                "action_type": "I",
                "uri": "Some Data",
                "action":"Some Action",
                "category":["Some Category"],
                "market_package_name":"Some Package name",
                "resolvers":["resolver","another resolver"]

            }
        """

        val intent: Intent = mockk(relaxed = true)
        val data: Bundle = mockk(relaxed = true)
        every { intent.extras } returns data
        every { data.getString(NotificationActionService.INTENT_DATA_ACTION) } returns actionJson
        every { data.getString(NotificationActionService.INTENT_DATA_NOTIFICATION) } returns messageJson
        every { data.getString(NotificationActionService.INTENT_DATA_RESPONSE_ACTION) } returns NotificationActionService.RESPONSE_ACTION_CLICK

        val action :IntentAction = mockk(relaxed = true)
        val notificationMessage= NotificationMessage(
            messageId = "123",
            title = "Simple Message",
            content = "Simple Content",
            action = action
        )

        every { mockedMoshi.adapter(Action::class.java).fromJson(actionJson) } returns action
        every {
            mockedMoshi.adapter(NotificationMessage::class.java).fromJson(messageJson)
        } returns notificationMessage

        getNotificationActionService(true).handelIntent(intent)
        cpuThread.triggerActions()
        verify(exactly = 1) { notificationInteractionReporter.onNotificationClicked(notificationMessage, null) }
        verify(exactly = 0) { notificationInteractionReporter.onNotificationDismissed(notificationMessage) }
    }

    @Test
    fun handelIntent_SendsNotificationDismissIfDismissed() {
        val messageJson = """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                "action": {
                    "action_type": "D"
                }
            }
        """

        val actionJson = """
            {
                "action_type": "D"
            }
        """

        val intent: Intent = mockk(relaxed = true)
        val data: Bundle = mockk(relaxed = true)
        every { intent.extras } returns data
        every { data.getString(NotificationActionService.INTENT_DATA_ACTION) } returns actionJson
        every { data.getString(NotificationActionService.INTENT_DATA_NOTIFICATION) } returns messageJson
        every { data.getString(NotificationActionService.INTENT_DATA_RESPONSE_ACTION) } returns NotificationActionService.RESPONSE_ACTION_DISMISS

        val action :DismissAction = mockk(relaxed = true)
        val notificationMessage= NotificationMessage(
            messageId = "123",
            title = "Simple Message",
            content = "Simple Content",
            action = action
        )

        every { mockedMoshi.adapter(Action::class.java).fromJson(actionJson) } returns action
        every {
            mockedMoshi.adapter(NotificationMessage::class.java).fromJson(messageJson)
        } returns notificationMessage

        getNotificationActionService(true).handelIntent(intent)
        cpuThread.triggerActions()
        verify(exactly = 0) { notificationInteractionReporter.onNotificationClicked(notificationMessage, null) }
        verify(exactly = 1) { notificationInteractionReporter.onNotificationDismissed(notificationMessage) }
    }


    @Test
    fun handelIntent_ExecutesAction() {
        val messageJson = """
            {
                "message_id": "123",
                "title": "Simple Message",
                "content": "Simple Content",
                 "action": {
                    "action_type": "A"
                }
            }
        """

        val actionJson = """
            {
                 "action_type": "A"
            }
        """

        val intent: Intent = mockk(relaxed = true)
        val data: Bundle = mockk(relaxed = true)
        every { intent.extras } returns data
        every { data.getString(NotificationActionService.INTENT_DATA_ACTION) } returns actionJson
        every { data.getString(NotificationActionService.INTENT_DATA_NOTIFICATION) } returns messageJson
        every { data.getString(NotificationActionService.INTENT_DATA_RESPONSE_ACTION) } returns NotificationActionService.RESPONSE_ACTION_DISMISS



        val action :AppAction = mockk(relaxed = true)
        every { mockedMoshi.adapter(Action::class.java).fromJson(actionJson) } returns action
        every {
            mockedMoshi.adapter(NotificationMessage::class.java).fromJson(messageJson)
        } returns NotificationMessage(
            messageId = "123",
            title = "Simple Message",
            content = "Simple Content",
            action = action
        )

        getNotificationActionService(true).handelIntent(intent)

        verify(exactly = 1) { action.executeAsCompletable(any()) }
    }
}
