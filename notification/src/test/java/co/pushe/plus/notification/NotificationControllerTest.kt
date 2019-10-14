package co.pushe.plus.notification

import android.content.Context
import android.content.SharedPreferences
import android.support.v4.app.NotificationManagerCompat
import co.pushe.plus.PusheLifecycle
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.utils.ImageDownloader
import co.pushe.plus.notification.utils.ScreenWaker
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.test.TestUtils.mockUIThread
import co.pushe.plus.utils.test.TestUtils.turnOffThreadAssertions
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.*
import org.junit.Before
import org.junit.Test
import java.util.*

class NotificationControllerTest {
    private lateinit var notificationController: NotificationController
    private lateinit var notificationControllerSpy: NotificationController

    private val uiThread = mockUIThread()
    private val context: Context = mockk(relaxed = true)
    private val notificationStatusReporter: NotificationStatusReporter = mockk(relaxed = true)
    private val taskScheduler: TaskScheduler = mockk(relaxed = true)
    private val moshi: PusheMoshi = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val pusheConfig = PusheConfig(sharedPreferences, moshi)
    private val pusheStorage: PusheStorage = PusheStorage(moshi, sharedPreferences)
    private val notificationSettings: NotificationSettings = spyk(NotificationSettings(context, pusheStorage))
    private val notificationStorage: NotificationStorage = mockk(relaxed = true)
    private val notificationManagerCompat: NotificationManagerCompat = mockk(relaxed = true)
    private val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
    private val pusheNotificationListener: PusheNotificationListener = mockk(relaxed = true)


    @Before
    fun setUp() {
        notificationController = NotificationController(
                context,
                mockk<NotificationBuilderFactory>(relaxed = true),
                notificationStatusReporter,
                mockk<NotificationInteractionReporter>(relaxed = true),
                mockk<ScreenWaker>(relaxed = true),
                taskScheduler,
                moshi,
                notificationSettings,
                notificationStorage,
                mockk<NotificationErrorHandler>(relaxed = true),
                mockk<PusheLifecycle>(relaxed = true),
                mockk<ApplicationInfoHelper>(relaxed = true),
                mockk<ImageDownloader>(relaxed = true),
                pusheConfig,
                pusheStorage
        )

        notificationControllerSpy = spyk(notificationController)
        turnOffThreadAssertions()

        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns true

        every { notificationControllerSpy.scheduleNotification(any()) } just Runs
        every { notificationControllerSpy.runNotificationBuilder(any()) } just Runs


        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(context) } returns notificationManagerCompat

    }

    @Test
    fun handleNotificationMessage_RespectsOTK() {
        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns true


        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", title = "t", content = "c")
        )
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }

        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "2", title = "t", content = "c", oneTimeKey = "my OTK")
        )
        verify(exactly = 2) { notificationControllerSpy.runNotificationBuilder(any()) }

        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "3", title = "t", content = "c", oneTimeKey = "my OTK")
        )
        verify(exactly = 2) { notificationControllerSpy.runNotificationBuilder(any()) }

        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "4", title = "t", content = "c")
        )
        verify(exactly = 3) { notificationControllerSpy.runNotificationBuilder(any()) }
    }

    @Test
    fun handleNotificationMessage_RespectsIsNotificationEnabled() {
        every { notificationSettings.isNotificationEnabled } returns false
        val message = NotificationMessage(messageId = "1", title = "title", content = "content")
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 0) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, NotificationStatus.APP_DISABLED) }
    }

    @Test
    fun handleNotificationMessage_RespectsMessageForcePublish() {
        every { notificationSettings.isNotificationEnabled } returns false
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", forcePublish = true)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 0) { notificationStatusReporter.reportStatus(message, any()) }
    }

    @Test
    fun handleNotificationMessage_SendsSystemDisabledStatusIfNeeded() {
        every { notificationManagerCompat.areNotificationsEnabled() } returns false
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", forcePublish = true)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 0) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, NotificationStatus.SYSTEM_DISABLED) }
    }

    @Test
    fun handleNotificationMessage_RespectsNotifDisabledByPusheConfig() {
        pusheConfig.updateConfig("notif_enabled", false)
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", forcePublish = true)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 0) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, NotificationStatus.PUSHE_DISABLED) }
    }

    @Test
    fun handleNotificationMessage_RespectsMessageScheduling() {
        val scheduledTime = Calendar.getInstance()
        scheduledTime.add(Calendar.MINUTE, 5)
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", scheduledTime = scheduledTime.time)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 0) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 0) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 1) { notificationControllerSpy.scheduleNotification(message) }
        verify(exactly = 1) { notificationStorage.saveScheduledNotificationMessage(message) }
    }

    @Test
    fun handleNotificationMessage_DoesNotScheduleIfTimeIsInPast() {
        val scheduledTime = Calendar.getInstance()
        scheduledTime.add(Calendar.MINUTE, -5)
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", scheduledTime = scheduledTime.time)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 0) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 0) { notificationControllerSpy.scheduleNotification(message) }
        verify(exactly = 0) { notificationStorage.saveScheduledNotificationMessage(message) }

    }

    @Test
    fun handleNotificationMessage_IgnoresBecauseOfUpdateMessage() {
        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns true
        every { applicationInfoHelper.getApplicationVersionCode() } returns 10


        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "2", title = "t", content = "c", updateToAppVersion = 11)
        )
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }

        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "2", title = "t", content = "c", updateToAppVersion = 10)
        )
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }

        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "2", title = "t", content = "c", updateToAppVersion = 9)
        )
        verify(exactly = 1) { notificationControllerSpy.runNotificationBuilder(any()) }
    }

    @Test
    fun handleNotificationMessage_InvokesNotificationCallbacksJustNotification() {
        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", title = "title", content = "content")
        )
        uiThread.triggerActions()
        verify(exactly = 1) { pusheNotificationListener.onNotification(any()) }
        verify(exactly = 0) { pusheNotificationListener.onCustomContentNotification(any()) }
    }

    @Test
    fun handleNotificationMessage_InvokesNotificationCallbacksJustCustomContent() {
        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener
        val customContent = mutableMapOf<String, Any>(Pair("foo", "bar"))
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", customContent = customContent)
        )
        uiThread.triggerActions()
        verify(exactly = 0) { pusheNotificationListener.onNotification(any()) }
        verify(exactly = 1) { pusheNotificationListener.onCustomContentNotification(customContent) }
    }

    @Test
    fun handleNotificationMessage_InvokesNotificationCallbacksNotificationAndCustomContent() {
        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener
        val customContent = mutableMapOf<String, Any>(Pair("foo", "bar"))
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", title = "t", content = "c", customContent = customContent)
        )
        uiThread.triggerActions()
        verify(exactly = 1) { pusheNotificationListener.onNotification(any()) }
        verify(exactly = 1) { pusheNotificationListener.onCustomContentNotification(customContent) }
    }

    @Test
    fun handleNotificationMessage_CallbacksAreInvokedIfSystemOrAppDisabledButNotIfPusheDisabled() {
        every { notificationSettings.pusheNotificationListener } returns pusheNotificationListener

        every { notificationSettings.isNotificationEnabled } returns false
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "1", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 1) { pusheNotificationListener.onNotification(any()) }

        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns false
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "2", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 2) { pusheNotificationListener.onNotification(any()) }

        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns true
        pusheConfig.updateConfig("notif_enabled", false)
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "3", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 2) { pusheNotificationListener.onNotification(any()) }
    }
}
