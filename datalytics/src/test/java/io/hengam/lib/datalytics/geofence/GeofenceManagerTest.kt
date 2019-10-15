package io.hengam.lib.datalytics.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.datalytics.messages.downstream.GeofenceMessage
import io.hengam.lib.datalytics.tasks.GeofencePeriodicRegisterTask
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.minutes
import io.hengam.lib.utils.seconds
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.mockTime
import io.hengam.lib.utils.test.mocks.MockFcmTask
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class GeofenceManagerTest {
    private val cpuThread = mockCpuThread()
    private val context: Context = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val moshi: HengamMoshi = HengamMoshi()
    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val taskScheduler: TaskScheduler = mockk(relaxed = true)
    private val hengamStorage: HengamStorage = HengamStorage(moshi, sharedPreferences)
    private val geofencingClient: GeofencingClient = mockk(relaxed = true)
    private val geofenceManager =  GeofenceManager(context, postOffice, taskScheduler, hengamStorage, moshi)

    @Before
    fun setUp() {
        mockkStatic("com.google.android.gms.location.LocationServices")
        every { LocationServices.getGeofencingClient(context) } returns geofencingClient
    }

    private val testGeofence = GeofenceMessage(
            messageId = "message_id",
            id = "geo_id",
            lat = 35.7,
            long = 51.3,
            radius = 500f,
            trigger = GeofenceMessage.GEOFENCE_TRIGGER_ENTER,
            triggerOnInit = true,
            responsiveness = minutes(5),
            message = mapOf()
    )

    @Test
    fun addOrUpdateGeofence_SetsCorrectGeofenceParameters() {
        val (geofenceSlot, geofenceRequestSlot, _)
                = addGeofence(testGeofence)
        assertEquals("geo_id", geofenceSlot.requestId.captured)
        assertEquals(35.7, geofenceSlot.lat.captured, 0.01)
        assertEquals(51.3, geofenceSlot.long.captured, 0.01)
        assertEquals(500f, geofenceSlot.radius.captured, 0.01f)
        assertEquals(Geofence.NEVER_EXPIRE, geofenceSlot.expiration.captured)
        assertEquals(minutes(5).toMillis().toInt(), geofenceSlot.responsiveness.captured)

        assertEquals(1, geofenceRequestSlot.captured.geofences.size)
        assertEquals("geo_id", geofenceRequestSlot.captured.geofences[0].requestId)

        assertEquals(1, geofenceManager.geofences.size)
        assertEquals(testGeofence, geofenceManager.geofences["geo_id"])
    }

    @Test
    fun addOrUpdateGeofence_StartsPeriodicGeofenceRegisterTask() {
        val (_, _, _)
                = addGeofence(testGeofence)
        verify(exactly = 1) { taskScheduler.schedulePeriodicTask(any<GeofencePeriodicRegisterTask.Options>()) }
    }

    @Test
    fun addOrUpdateGeofence_SetsCorrectGeofenceExpirationDuration() {
        mockTime(1500000000000)
        val (geofenceSlot, _, _)
                = addGeofence(testGeofence.copy(expirationDate = Date(1500000010000)))
        assertEquals(10000, geofenceSlot.expiration.captured)
    }

    @Test
    fun addOrUpdateGeofence_DoesNotAddGeofenceIfExpirationDateHasPassed() {
        mockTime(1500000010000)
        val (geofenceSlot, geofenceRequestSlot, pendingIntentSlot)
                = addGeofence(testGeofence.copy(expirationDate = Date(1500000000000)))
        assertFalse(geofenceSlot.requestId.isCaptured)
        assertFalse(geofenceRequestSlot.isCaptured)
        assertFalse(pendingIntentSlot.requestCode.isCaptured)

        assertEquals(0, geofenceManager.geofences.size)
    }

    @Test
    fun addOrUpdateGeofence_UsesSamePendingIntentForAllGeofences() {
        val (_, _, p1) = addGeofence(testGeofence.copy(id = "g1"))
        val (_, _, p2) = addGeofence(testGeofence.copy(id = "g2"))
        assertTrue(p1.requestCode.captured == p2.requestCode.captured)
    }

    @Test
    fun addOrUpdateGeofence_SetsCorrectGeofenceTransitionForEnterTrigger() {
        val (g1, gr1, _)
                = addGeofence(testGeofence.copy(id = "g1", trigger = GeofenceMessage.GEOFENCE_TRIGGER_ENTER, triggerOnInit = false, dwellTime = null))
        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, g1.transitionType.captured)
        assertEquals(0, gr1.captured.initialTrigger)

        val (g2, gr2, _)
                = addGeofence(testGeofence.copy(id = "g2", trigger = GeofenceMessage.GEOFENCE_TRIGGER_ENTER, triggerOnInit = true, dwellTime = null))
        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, g2.transitionType.captured)
        assertEquals(GeofencingRequest.INITIAL_TRIGGER_ENTER, gr2.captured.initialTrigger)
    }

    @Test
    fun addOrUpdateGeofence_SetsCorrectGeofenceTransitionForExitTrigger() {
        val (g1, gr1, _)
                = addGeofence(testGeofence.copy(id = "g1", trigger = GeofenceMessage.GEOFENCE_TRIGGER_EXIT, triggerOnInit = false, dwellTime = null))
        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, g1.transitionType.captured)
        assertEquals(0, gr1.captured.initialTrigger)

        val (g2, gr2, _)
                = addGeofence(testGeofence.copy(id = "g2", trigger = GeofenceMessage.GEOFENCE_TRIGGER_EXIT, triggerOnInit = true, dwellTime = null))
        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, g2.transitionType.captured)
        assertEquals(GeofencingRequest.INITIAL_TRIGGER_EXIT, gr2.captured.initialTrigger)
    }

    @Test
    fun addOrUpdateGeofence_SetsCorrectGeofenceTransitionForDwellingGeofences() {
        val (g1, gr1, _)
                = addGeofence(testGeofence.copy(id = "g1", trigger = GeofenceMessage.GEOFENCE_TRIGGER_ENTER, triggerOnInit = false, dwellTime = seconds(2)))
        assertEquals(Geofence.GEOFENCE_TRANSITION_DWELL, g1.transitionType.captured)
        assertEquals(0, gr1.captured.initialTrigger)

        val (g2, gr2, _)
                = addGeofence(testGeofence.copy(id = "g2", trigger = GeofenceMessage.GEOFENCE_TRIGGER_ENTER, triggerOnInit = true, dwellTime = seconds(2)))
        assertEquals(Geofence.GEOFENCE_TRANSITION_DWELL, g2.transitionType.captured)
        assertEquals(GeofencingRequest.INITIAL_TRIGGER_DWELL or GeofencingRequest.INITIAL_TRIGGER_ENTER, gr2.captured.initialTrigger)
    }

    @Test
    fun removeGeofence_CorrectlyUnregistersAndRemovesGeofence() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1")
        geofenceManager.geofences["g2"] = testGeofence.copy(id = "g2")

        val removedIds = slot<List<String>>()
        every { geofencingClient.removeGeofences(any<List<String>>()) } returns MockFcmTask()

        geofenceManager.removeGeofence("g1")
        cpuThread.triggerActions()

        verify(exactly = 1) { geofencingClient.removeGeofences(capture(removedIds)) }
        assertEquals(listOf("g1"), removedIds.captured)
        assertEquals(1, geofenceManager.geofences.size)
        assertNull(geofenceManager.geofences["g1"])
    }

    @Test
    fun removeGeofence_CancelsPeriodicGeofenceRegisterTaskIfNoGeofencesRemain() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1")
        geofenceManager.geofences["g2"] = testGeofence.copy(id = "g2")
        geofenceManager.removeGeofence("g1")
        cpuThread.triggerActions()
        verify(exactly = 0) { taskScheduler.cancelTask(any<GeofencePeriodicRegisterTask.Options>()) }
        geofenceManager.removeGeofence("g2")
        cpuThread.triggerActions()
        verify(exactly = 1) { taskScheduler.cancelTask(any<GeofencePeriodicRegisterTask.Options>()) }
    }

    @Test
    fun onGeofenceTriggered_SendsCorrectMessageToPostOfficeAndIncreasesTriggerCount() {
        geofenceManager.geofences["g1"] = testGeofence.copy(
                messageId = "the_message_id",
                id = "g1",
                message = mapOf("key" to "value")
        )
        assertEquals(0, geofenceManager.geofenceTriggerCounts["g1"] ?: 0)
        geofenceManager.onGeofenceTriggered("g1")
        verify(exactly = 1) { postOffice.handleLocalParcel(mapOf("key" to "value"), "the_message_id") }
        assertEquals(1, geofenceManager.geofenceTriggerCounts["g1"])
    }

    @Test
    fun onGeofenceTriggered_UnregistersGeofenceIfItIsMissingFromStore() {
        assertNull(geofenceManager.geofences["g1"])
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 1) { geofencingClient.removeGeofences(listOf("g1")) }
        verify(exactly = 0) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(0, geofenceManager.geofenceTriggerCounts["g1"] ?: 0)
    }

    @Test
    fun onGeofenceTriggered_HandledContentIndefinitelyIfLimitNotGiven() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1", message = mapOf("key" to "value"))
        (1 until 5).forEach {
            geofenceManager.onGeofenceTriggered("g1")
            cpuThread.triggerActions()
            verify(exactly = it) { postOffice.handleLocalParcel(any(), any()) }
            assertEquals(it, geofenceManager.geofenceTriggerCounts["g1"] ?: 0)
        }
    }

    @Test
    fun onGeofenceTriggered_RemovesGeofenceIfItsLimitHasJustBeenReachedButHandlesContent() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1", limit = 2, message = mapOf("key" to "value"))
        geofenceManager.geofenceTriggerCounts["g1"] = 1
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 1) { geofencingClient.removeGeofences(listOf("g1")) }
        verify(exactly = 1) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(2, geofenceManager.geofenceTriggerCounts["g1"] ?: 0)
    }

    @Test
    fun onGeofenceTriggered_RemovesGeofenceIfItsLimitHasAlreadyBeenReachedAndDoesNotHandleContent() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1", limit = 2, message = mapOf("key" to "value"))
        geofenceManager.geofenceTriggerCounts["g1"] = 2
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 1) { geofencingClient.removeGeofences(listOf("g1")) }
        verify(exactly = 0) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(2, geofenceManager.geofenceTriggerCounts["g1"] ?: 0)
    }

    @Test
    fun onGeofenceTriggered_DoesNotHandleContentIfTriggeredSoonerThanRateLimit() {
        geofenceManager.geofences["g1"] = testGeofence.copy(id = "g1", rateLimit = seconds(10), message = mapOf("key" to "value"))
        mockTime(seconds(1))
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 1) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(seconds(1), geofenceManager.geofenceTriggerTimes["g1"])

        mockTime(seconds(5))
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 1) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(seconds(1), geofenceManager.geofenceTriggerTimes["g1"])

        mockTime(seconds(11))
        geofenceManager.onGeofenceTriggered("g1")
        cpuThread.triggerActions()
        verify(exactly = 2) { postOffice.handleLocalParcel(any(), any()) }
        assertEquals(seconds(11), geofenceManager.geofenceTriggerTimes["g1"])
    }

    private class PendingIntentSlot {
        val context = slot<Context>()
        val requestCode = slot<Int>()
        val intent = slot<Intent>()
        val flags = slot<Int>()
    }

    private class GeofenceSlot {
        val requestId = slot<String>()
        val lat = slot<Double>()
        val long = slot<Double>()
        val radius = slot<Float>()
        val expiration = slot<Long>()
        val responsiveness = slot<Int>()
        val transitionType = slot<Int>()
        val loiteringDelay = slot<Int>()
    }

    private fun addGeofence(geofenceMessage: GeofenceMessage): Triple<GeofenceSlot, CapturingSlot<GeofencingRequest>, PendingIntentSlot> {
        // Pending Intent
        mockkStatic(PendingIntent::class)
        val pendingIntentSlot = PendingIntentSlot()
        every { PendingIntent.getService(capture(pendingIntentSlot.context), capture(pendingIntentSlot.requestCode), capture(pendingIntentSlot.intent), capture(pendingIntentSlot.flags)) } returns mockk(relaxed = true)

        // Geofence
        val geofenceSlot = GeofenceSlot()
        mockkConstructor(Geofence.Builder::class)
        val mockCall = { it: Call -> it.invocation.originalCall();it.invocation.self as Geofence.Builder }
        every { anyConstructed<Geofence.Builder>().setRequestId(capture(geofenceSlot.requestId)) } answers { mockCall(it) }
        every { anyConstructed<Geofence.Builder>().setCircularRegion(capture(geofenceSlot.lat), capture(geofenceSlot.long), capture(geofenceSlot.radius)) } answers { mockCall(it) }
        every { anyConstructed<Geofence.Builder>().setExpirationDuration(capture(geofenceSlot.expiration)) } answers { mockCall(it) }
        every { anyConstructed<Geofence.Builder>().setNotificationResponsiveness(capture(geofenceSlot.responsiveness)) } answers { mockCall(it) }
        every { anyConstructed<Geofence.Builder>().setTransitionTypes(capture(geofenceSlot.transitionType)) } answers { mockCall(it) }
        every { anyConstructed<Geofence.Builder>().setLoiteringDelay(capture(geofenceSlot.loiteringDelay)) } answers { mockCall(it) }

        // Geofence Request
        val requestSlot = slot<GeofencingRequest>()
        val task = MockFcmTask<Void>()
        every { geofencingClient.addGeofences(capture(requestSlot), any()) } returns task

        geofenceManager.addOrUpdateGeofence(geofenceMessage)
        cpuThread.triggerActions()
        cpuThread.triggerActions()

        task.success(mockk())
        cpuThread.triggerActions()

        return Triple(geofenceSlot, requestSlot, pendingIntentSlot)
    }
}