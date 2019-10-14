package co.pushe.plus.notification

import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.messages.downstream.jsonAdapter
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.days
import co.pushe.plus.utils.hours
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import co.pushe.plus.utils.test.TestUtils.mockTime
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NotificationStorageTest {
    private val cpuThread = mockCpuThread() // Needed for PusheStorage to work correctly here
    private val sharedPreferences = MockSharedPreference()
    private val moshi = PusheMoshi()
    private val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
    private val storage = PusheStorage(moshi, sharedPreferences)
    private val delayedNotif = NotificationMessage("delayed", "Delayed Notif", "Delayed Notif", delayUntil = "open_app")
    private val updateNotif = NotificationMessage("updateNotif", "Update Notif", "Update Notif", updateToAppVersion = 2000)
    private lateinit var delayedNotifJson: String
    private lateinit var updateNotifJson: String

    private val DELAYED_NOTIF_KEY = "delayed_notification"
    private val UPDATE_NOTIF_KEY = "update_notification"
    private val DELAYED_NOTIF_TIME_KEY = "delayed_notification_time"
    private val UPDATE_NOTIF_TIME_KEY = "update_notification_time"

    private lateinit var notificationStorage: NotificationStorage

    @Before
    fun setUp() {
        co.pushe.plus.extendMoshi(moshi)
        co.pushe.plus.notification.extendMoshi(moshi)
        delayedNotifJson = NotificationMessage.jsonAdapter(moshi.moshi).toJson(delayedNotif)
        updateNotifJson = NotificationMessage.jsonAdapter(moshi.moshi).toJson(updateNotif)

        every { applicationInfoHelper.getApplicationVersionCode(any()) } returns 1000

        notificationStorage = NotificationStorage(
                applicationInfoHelper,
                storage
        )
    }

    private fun assertMessageEquals(expected: NotificationMessage, actualMessage: NotificationMessage?) {
        assertEquals(expected.title, actualMessage?.title)
        assertEquals(expected.content, actualMessage?.content)
    }

    @Test
    fun delayedNotification_ReturnsNullIfNoNotificationExists() {
        assertNull(notificationStorage.delayedNotification)
    }

    @Test
    fun delayedNotification_ReturnsNotificationIfNotExpired() {
        sharedPreferences.edit()
                .putString(DELAYED_NOTIF_KEY, delayedNotifJson)
                .putLong(DELAYED_NOTIF_TIME_KEY, days(1).toMillis())
                .commit()
        mockTime(days(2).toMillis())
        assertMessageEquals(delayedNotif, notificationStorage.delayedNotification)
    }

    @Test
    fun delayedNotification_DoesNotReturnNotificationIfExpiredAndDeletesNotif() {
        storage.putString(DELAYED_NOTIF_KEY, delayedNotifJson)
        storage.putLong(DELAYED_NOTIF_TIME_KEY, days(1).toMillis())
        mockTime(days(14).toMillis())
        assertNull(notificationStorage.delayedNotification)
        assertEquals("empty", storage.getString(DELAYED_NOTIF_KEY, "empty"))
    }

    @Test
    fun updateNotification_ReturnsNullIfNoNotificationExists() {
        assertNull(notificationStorage.updateNotification)
    }

    @Test
    fun updateNotification_ReturnsNotificationIfNotExpired() {
        sharedPreferences.edit()
                .putString(UPDATE_NOTIF_KEY, updateNotifJson)
                .putLong(UPDATE_NOTIF_TIME_KEY, days(1).toMillis())
                .commit()
        mockTime(days(2).toMillis())
        assertMessageEquals(updateNotif, notificationStorage.updateNotification)
    }

    @Test
    fun updateNotification_DoesNotReturnNotificationIfExpiredAndDeletesNotif() {
        storage.putString(UPDATE_NOTIF_KEY, updateNotifJson)
        storage.putLong(UPDATE_NOTIF_TIME_KEY, days(1).toMillis())
        mockTime(days(14).toMillis())
        assertNull(notificationStorage.updateNotification)
        assertEquals(-1, sharedPreferences.getLong(UPDATE_NOTIF_TIME_KEY, -1))
    }

    @Test
    fun updateNotification_DoesNotReturnNotificationIfAppAlreadyUpdatedAndDeletesNotif() {
        storage.putString(UPDATE_NOTIF_KEY, updateNotifJson)
        storage.putLong(UPDATE_NOTIF_TIME_KEY, days(1).toMillis())
        every { applicationInfoHelper.getApplicationVersionCode(any()) } returns 3000
        assertNull(notificationStorage.updateNotification)
        assertEquals(-1, sharedPreferences.getLong(UPDATE_NOTIF_TIME_KEY, -1))
    }

    @Test
    fun canStoreDelayedAndUpdateNotificationsAndTheirTimes() {
        mockTime(days(1).toMillis())
        notificationStorage.delayedNotification = delayedNotif

        mockTime(days(2).toMillis())
        notificationStorage.updateNotification = updateNotif

        assertEquals(delayedNotifJson, storage.getString(DELAYED_NOTIF_KEY, ""))
        assertEquals(days(1).toMillis(), storage.getLong(DELAYED_NOTIF_TIME_KEY, 0))

        assertEquals(updateNotifJson, storage.getString(UPDATE_NOTIF_KEY, ""))
        assertEquals(days(2).toMillis(), storage.getLong(UPDATE_NOTIF_TIME_KEY, 0))

        notificationStorage.updateNotification = null
        assertEquals("", storage.getString(UPDATE_NOTIF_KEY, ""))
        assertNotEquals("", storage.getString(DELAYED_NOTIF_KEY, ""))

        notificationStorage.delayedNotification = null
        assertEquals("", storage.getString(DELAYED_NOTIF_KEY, ""))
    }

    @Test
    fun updateAndDelayedNotificationsExpireSeparately() {
        mockTime(days(1).toMillis())
        notificationStorage.delayedNotification = delayedNotif
        mockTime(days(4).toMillis())
        notificationStorage.updateNotification = updateNotif
        mockTime(days(10).toMillis())
        assertNotNull(notificationStorage.updateNotification)
        assertNull(notificationStorage.delayedNotification)
        assertNotNull(notificationStorage.updateNotification)

        mockTime(days(1).toMillis())
        notificationStorage.updateNotification = updateNotif
        mockTime(days(4).toMillis())
        notificationStorage.delayedNotification = delayedNotif
        mockTime(days(10).toMillis())
        assertNotNull(notificationStorage.delayedNotification)
        assertNull(notificationStorage.updateNotification)
        assertNotNull(notificationStorage.delayedNotification)
    }

    @Test
    fun updateNotification_ShowsNotificationAtMostOnceEvery24Hours() {
        mockTime(hours(1).toMillis())
        notificationStorage.updateNotification = updateNotif
        assertNotNull(notificationStorage.updateNotification)

        mockTime(hours(2).toMillis())
        assertTrue(notificationStorage.shouldShowUpdatedNotification())

        notificationStorage.onUpdateNotificationShown()
        assertFalse(notificationStorage.shouldShowUpdatedNotification())

        mockTime(hours(10).toMillis())
        assertFalse(notificationStorage.shouldShowUpdatedNotification())

        mockTime(hours(26).toMillis())
        assertTrue(notificationStorage.shouldShowUpdatedNotification())

        notificationStorage.onUpdateNotificationShown()

        mockTime(hours(30).toMillis())
        assertFalse(notificationStorage.shouldShowUpdatedNotification())
    }
}