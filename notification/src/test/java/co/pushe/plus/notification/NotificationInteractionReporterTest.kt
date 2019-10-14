package co.pushe.plus.notification

import android.content.Context
import android.content.SharedPreferences
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.messages.downstream.NotificationButton
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.messages.upstream.ApplicationDownloadMessage
import co.pushe.plus.notification.messages.upstream.ApplicationInstallMessage
import co.pushe.plus.notification.messages.upstream.NotificationActionMessage
import co.pushe.plus.utils.NetworkInfoHelper
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.test.TestUtils
import co.pushe.plus.utils.test.TestUtils.mockTime
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class NotificationInteractionReporterTest {
    private lateinit var notificationInteractionReporter: NotificationInteractionReporter

    private val context: Context = mockk(relaxed = true)
    private val moshi: PusheMoshi = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val pusheStorage: PusheStorage = PusheStorage(moshi, sharedPreferences)
    private val notificationSettings: NotificationSettings =
        spyk(NotificationSettings(context, pusheStorage))
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val networkInfo: NetworkInfoHelper = mockk(relaxed = true)
    private val pusheNotificationListener: PusheNotificationListener = mockk(relaxed = true)
    private val uiThread = TestUtils.mockUIThread()


    @Before
    fun setUp() {
        notificationInteractionReporter =
                NotificationInteractionReporter(
                    postOffice,
                    notificationSettings,
                    pusheStorage
                )

        mockTime(10000)
    }

    @Test
    fun onNotificationClicked_SendsClickEvent() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "message title",
            content = "message content"
        )
        notificationInteractionReporter.onNotificationPublished(notificationMessage.messageId)
        notificationInteractionReporter.onNotificationClicked(notificationMessage, null)

        val slot = CapturingSlot<NotificationActionMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot)) }
        val capturedMessage = slot.captured
        assertEquals("123", capturedMessage.originalMessageId)
        assertEquals(
            NotificationActionMessage.NotificationResponseAction.CLICKED,
            capturedMessage.status
        )
        assertEquals(Time(10, TimeUnit.SECONDS), capturedMessage.notificationPublishTime)
    }

    @Test
    fun onNotificationClicked_InvokesNotificationClickCallback() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "message title",
            content = "message content"
        )

        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener

        notificationInteractionReporter.onNotificationPublished(notificationMessage.messageId)
        notificationInteractionReporter.onNotificationClicked(notificationMessage, null)
        uiThread.triggerActions()


        verify(exactly = 1) {
            pusheNotificationListener.onNotificationClick(
                any()
            )
        }
        verify(exactly = 0) {
            pusheNotificationListener.onNotificationButtonClick(
                any(),
                any()
            )
        }
    }

    @Test
    fun onNotificationClicked_InvokesNotificationButtonClickCallback() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "message title",
            content = "message content",
            buttons = listOf(NotificationButton(id = "10", text = "btn", icon = "help"))
        )

        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener

        notificationInteractionReporter.onNotificationPublished(notificationMessage.messageId)
        notificationInteractionReporter.onNotificationClicked(notificationMessage, "10")
        uiThread.triggerActions()


        verify(exactly = 0) {
            pusheNotificationListener.onNotificationClick(
                any()
            )
        }
        val slot = CapturingSlot<NotificationButtonData>()
        verify(exactly = 1) {
            pusheNotificationListener.onNotificationButtonClick(
                capture(slot),
                any()
            )
        }
        assertEquals("10", slot.captured.id)
        assertEquals("btn", slot.captured.text)
        assertEquals("help", slot.captured.icon)
    }

    @Test
    fun onNotificationDismissed_SendsDismissEvent() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "message title",
            content = "message content"
        )

        notificationInteractionReporter.onNotificationPublished(notificationMessage.messageId)
        notificationInteractionReporter.onNotificationDismissed(notificationMessage)

        val slot = CapturingSlot<NotificationActionMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot)) }
        val capturedMessage = slot.captured
        assertEquals("123", capturedMessage.originalMessageId)
        assertEquals(
            NotificationActionMessage.NotificationResponseAction.DISMISSED,
            capturedMessage.status
        )
        assertEquals(Time(10, TimeUnit.SECONDS), capturedMessage.notificationPublishTime)
    }

    @Test
    fun onNotificationDismissed_InvokesNotificationDismissCallback() {
        val notificationMessage = NotificationMessage(
            messageId = "123",
            title = "message title",
            content = "message content"
        )

        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener

        notificationInteractionReporter.onNotificationPublished(notificationMessage.messageId)
        notificationInteractionReporter.onNotificationDismissed(notificationMessage)
        uiThread.triggerActions()

        verify(exactly = 1) {
            pusheNotificationListener.onNotificationDismiss(
                any()
            )
        }
    }

    @Test
    fun onApkDownloadSuccess_SendsDownloadEvent() {

        mockTime(10000)
        notificationInteractionReporter.onNotificationPublished("123")
        mockTime(20000)
        notificationInteractionReporter.onApkDownloadSuccess("123","some packageName")

        val slot = CapturingSlot<ApplicationDownloadMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot)) }
        val capturedMessage = slot.captured
        assertEquals("123", capturedMessage.originalMessageId)
        assertEquals("some packageName",capturedMessage.packageName)

        assertEquals(Time(10, TimeUnit.SECONDS), capturedMessage.publishedAt)
        assertEquals(Time(20, TimeUnit.SECONDS), capturedMessage.downloadedAt)
    }

    @Test
    fun onApkInstalled_SendsInstalledEvent() {
        val appInfo: ApplicationDetail= mockk(relaxed = true)

        mockTime(10000)
        notificationInteractionReporter.onNotificationPublished("123")
        mockTime(20000)
        notificationInteractionReporter.onApkDownloadSuccess("123","some packageName")
        mockTime(30000)
        notificationInteractionReporter.onApkInstalled("123",appInfo,"1.0.0")

        val slot = CapturingSlot<ApplicationInstallMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot)) }
        val capturedMessage = slot.captured
        assertEquals("123", capturedMessage.originalMessageId)
        assertEquals(ApplicationInstallMessage.InstallStatus.INSTALLED,capturedMessage.status)
        assertEquals("1.0.0",capturedMessage.previousVersion)
        assertEquals(appInfo,capturedMessage.appInfo)

        assertEquals(Time(10, TimeUnit.SECONDS), capturedMessage.publishedAt)
        assertEquals(Time(20, TimeUnit.SECONDS), capturedMessage.downloadedAt)
        assertEquals(Time(30, TimeUnit.SECONDS), capturedMessage.installCheckedAt)
    }


    @Test
    fun onApkNotInstalled_SendsNotInstalledEvent() {
        mockTime(10000)
        notificationInteractionReporter.onNotificationPublished("123")
        mockTime(20000)
        notificationInteractionReporter.onApkDownloadSuccess("123","some packageName")
        mockTime(30000)
        notificationInteractionReporter.onApkNotInstalled("123","1.0.0")

        val slot = CapturingSlot<ApplicationInstallMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot)) }
        val capturedMessage = slot.captured
        assertEquals("123", capturedMessage.originalMessageId)
        assertEquals(ApplicationInstallMessage.InstallStatus.NOT_INSTALLED,capturedMessage.status)
        assertEquals("1.0.0",capturedMessage.previousVersion)

        assertEquals(Time(10, TimeUnit.SECONDS), capturedMessage.publishedAt)
        assertEquals(Time(20, TimeUnit.SECONDS), capturedMessage.downloadedAt)
        assertEquals(Time(30, TimeUnit.SECONDS), capturedMessage.installCheckedAt)
    }
}
