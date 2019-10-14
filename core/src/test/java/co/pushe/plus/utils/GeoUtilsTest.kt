package co.pushe.plus.utils

import android.content.Context
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import co.pushe.plus.utils.test.mocks.MockFcmTask
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import co.pushe.plus.utils.test.TestUtils.mockTime
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeoUtilsTest {
    private val context: Context = mockk(relaxed = true)
    private val fusedLocationProviderClient: FusedLocationProviderClient = mockk(relaxed = true)

    private val geoUtils = GeoUtils(context, fusedLocationProviderClient)

    private val cpuThread = mockCpuThread()

    private val locationAvailabilityTask = MockFcmTask<LocationAvailability>()
    private val lastKnownLocationTask = MockFcmTask<Location?>()

    private val mockedTime = 1000L
    private val timeoutDuration = Time(5, TimeUnit.SECONDS)

    @Before
    fun setUp() {
        every { fusedLocationProviderClient.locationAvailability } returns locationAvailabilityTask
        every { fusedLocationProviderClient.lastLocation } returns lastKnownLocationTask

        mockTime(mockedTime)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns mockedTime
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns null
    }

    private fun setLastLocationAvailability(available: Boolean) {
        locationAvailabilityTask.success(mockk(relaxed = true) { every { isLocationAvailable } returns available })
    }

    private fun setLastKnownLocation(location: Location?) {
        lastKnownLocationTask.success(location)
    }

    private fun setHasLocationPermissions(hasPermissions: Boolean) {
        mockkObject(PermissionChecker)
        every { PermissionChecker.hasPermission(any(), any()) } returns hasPermissions
    }

    private fun createLocationResult(location: Location): LocationResult {
        return mockk(relaxed = true) { every { lastLocation } returns location }
    }

    @Test
    fun getLastKnownLocation_ReturnsEmptyIfLocationIsNull() {
        setHasLocationPermissions(true)
        val result = geoUtils.getLastKnownLocation().test()
        cpuThread.triggerActions()
        setLastKnownLocation(null)
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()
    }

    @Test
    fun getLastKnownLocation_ReturnsEmptyIfLocationPermissionsAreNotAvailable() {
        setHasLocationPermissions(false)
        val result = geoUtils.getLastKnownLocation().test()
        cpuThread.triggerActions()
        setLastKnownLocation(mockk(relaxed = true))
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()
    }

    @Test
    fun getLocation_ReturnsLastKnownLocationIfExists() {
        setHasLocationPermissions(true)
        val mockLocation: Location = mockk(relaxed = true)
        val result = geoUtils.getLocation().test()
        cpuThread.triggerActions()
        setLastLocationAvailability(true)
        setLastKnownLocation(mockLocation)
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertValue(mockLocation)
    }

    @Test
    fun getLocation_RequestsAndReceivesLocationIfLastKnownLocationDoesNotExist() {
        setHasLocationPermissions(true)
        val mockLocation: Location = mockk(relaxed = true)
        val result = geoUtils.getLocation().test()
        cpuThread.triggerActions()
        setLastLocationAvailability(false)
        cpuThread.triggerActions()
        geoUtils.onLocationResult(createLocationResult(mockLocation))
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertValue(mockLocation)
    }

    @Test
    fun getLocation_RequestsLocationWithCorrectParameters() {
        setHasLocationPermissions(true)
        geoUtils.getLocation(Time(5, TimeUnit.SECONDS)).test()
        cpuThread.triggerActions()
        setLastLocationAvailability(false)
        cpuThread.triggerActions()

        val slot = slot<LocationRequest>()
        verify { fusedLocationProviderClient.requestLocationUpdates(capture(slot), any(), any())}

        val locationRequest = slot.captured
        assertEquals(1, locationRequest.numUpdates)
        assertEquals(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, locationRequest.priority)
        assertEquals(mockedTime + timeoutDuration.toMillis(), locationRequest.expirationTime)
    }

    @Test
    fun getLocation_ReturnsEmptyIfNoLocationReceivedAfterTimeout() {
        setHasLocationPermissions(true)
        val result = geoUtils.getLocation(timeoutDuration).test()
        cpuThread.triggerActions()
        setLastLocationAvailability(false)
        cpuThread.advanceTimeBy(timeoutDuration.toMillis() + 500, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()

        geoUtils.onLocationResult(createLocationResult(mockk(relaxed = true)))
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()
    }

    @Test
    fun getLocation_ReturnsEmptyIfErrorOccurs() {
        setHasLocationPermissions(true)
        val result = geoUtils.getLocation().test()
        cpuThread.triggerActions()
        setLastLocationAvailability(true)
        lastKnownLocationTask.fail(Exception("Test Exception"))
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()
    }

    @Test
    fun getLocation_ReturnsEmptyIfLocationPermissionsAreNotAvailable() {
        setHasLocationPermissions(false)
        val result = geoUtils.getLocation().test()
        cpuThread.triggerActions()
        setLastLocationAvailability(true)
        setLastKnownLocation(mockk(relaxed = true))
        cpuThread.triggerActions()
        result.assertComplete()
        result.assertNoValues()
    }
}


