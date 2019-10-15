package io.hengam.lib.notification

import android.content.Context
import android.content.SharedPreferences
import android.support.v4.app.NotificationManagerCompat
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.utils.ImageDownloader
import io.hengam.lib.notification.utils.ScreenWaker
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.TestUtils.mockUIThread
import io.hengam.lib.utils.test.TestUtils.turnOffThreadAssertions
import io.hengam.lib.utils.test.mocks.MockSharedPreference
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
    private val moshi: HengamMoshi = mockk(relaxed = true)
    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val hengamConfig = HengamConfig(sharedPreferences, moshi)
    private val hengamStorage: HengamStorage = HengamStorage(moshi, sharedPreferences)
    private val notificationSettings: NotificationSettings = spyk(NotificationSettings(context, hengamStorage))
    private val notificationStorage: NotificationStorage = mockk(relaxed = true)
    private val notificationManagerCompat: NotificationManagerCompat = mockk(relaxed = true)
    private val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
    private val hengamNotificationListener: HengamNotificationListener = mockk(relaxed = true)


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
                mockk<HengamLifecycle>(relaxed = true),
                mockk<ApplicationInfoHelper>(relaxed = true),
                mockk<ImageDownloader>(relaxed = true),
                hengamConfig,
                hengamStorage
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
    fun handleNotificationMessage_RespectsNotifDisabledByHengamConfig() {
        hengamConfig.updateConfig("notif_enabled", false)
        val message = NotificationMessage(messageId = "1", title = "t", content = "c", forcePublish = true)
        notificationControllerSpy.handleNotificationMessage(message = message)
        verify(exactly = 0) { notificationControllerSpy.runNotificationBuilder(any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, any()) }
        verify(exactly = 1) { notificationStatusReporter.reportStatus(message, NotificationStatus.HENGAM_DISABLED) }
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
        every { notificationSettings.hengamNotificationListener } returns hengamNotificationListener
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", title = "title", content = "content")
        )
        uiThread.triggerActions()
        verify(exactly = 1) { hengamNotificationListener.onNotification(any()) }
        verify(exactly = 0) { hengamNotificationListener.onCustomContentNotification(any()) }
    }

    @Test
    fun handleNotificationMessage_InvokesNotificationCallbacksJustCustomContent() {
        every { notificationSettings.hengamNotificationListener } returns hengamNotificationListener
        val customContent = mutableMapOf<String, Any>(Pair("foo", "bar"))
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", customContent = customContent)
        )
        uiThread.triggerActions()
        verify(exactly = 0) { hengamNotificationListener.onNotification(any()) }
        verify(exactly = 1) { hengamNotificationListener.onCustomContentNotification(customContent) }
    }

    @Test
    fun handleNotificationMessage_InvokesNotificationCallbacksNotificationAndCustomContent() {
        every { notificationSettings.hengamNotificationListener } returns hengamNotificationListener
        val customContent = mutableMapOf<String, Any>(Pair("foo", "bar"))
        notificationControllerSpy.handleNotificationMessage(
                NotificationMessage(messageId = "1", title = "t", content = "c", customContent = customContent)
        )
        uiThread.triggerActions()
        verify(exactly = 1) { hengamNotificationListener.onNotification(any()) }
        verify(exactly = 1) { hengamNotificationListener.onCustomContentNotification(customContent) }
    }

    @Test
    fun handleNotificationMessage_CallbacksAreInvokedIfSystemOrAppDisabledButNotIfHengamDisabled() {
        every { notificationSettings.hengamNotificationListener } returns hengamNotificationListener

        every { notificationSettings.isNotificationEnabled } returns false
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "1", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 1) { hengamNotificationListener.onNotification(any()) }

        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns false
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "2", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 2) { hengamNotificationListener.onNotification(any()) }

        every { notificationSettings.isNotificationEnabled } returns true
        every { notificationManagerCompat.areNotificationsEnabled() } returns true
        hengamConfig.updateConfig("notif_enabled", false)
        notificationController.handleNotificationMessage(NotificationMessage(messageId = "3", title = "t", content = "c"))
        uiThread.triggerActions()
        verify(exactly = 2) { hengamNotificationListener.onNotification(any()) }
    }
}
