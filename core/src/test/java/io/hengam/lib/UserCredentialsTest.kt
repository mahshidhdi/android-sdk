package io.hengam.lib

import android.content.SharedPreferences
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messages.upstream.UserIdUpdateMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.mockIoThread
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class UserCredentialsTest {
    private val ioThread = mockIoThread()
    private val cpuThread = mockCpuThread()

    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val userCredentials: UserCredentials = UserCredentials(HengamStorage(HengamMoshi(), sharedPreferences))

    private val coreComponent: CoreComponent = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkObject(HengamInternals)
        every { HengamInternals.getComponent(CoreComponent::class.java) } returns coreComponent
        every { coreComponent.postOffice() } returns postOffice
    }

    @Test
    fun customId_StoresAndRetrievesCustomId() {
        userCredentials.customId = "some_userId"
        assertNotNull(userCredentials.customId)
        assertEquals("some_userId", userCredentials.customId)
    }

    @Test
    fun customId_SendsMessageOnCustomIdChange() {
        userCredentials.customId = "some_userId"
        ioThread.triggerActions()
        cpuThread.triggerActions()
        val slot = CapturingSlot<UserIdUpdateMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot), SendPriority.SOON) }
        assertEquals("some_userId", slot.captured.customId)
    }

    @Test
    fun email_StoresAndRetrievesCustomId() {
        userCredentials.email = "some@email.com"
        assertNotNull(userCredentials.email)
        assertEquals("some@email.com", userCredentials.email)
    }

    @Test
    fun email_SendsMessageOnCustomIdChange() {
        userCredentials.email = "some@email.com"
        ioThread.triggerActions()
        cpuThread.triggerActions()
        val slot = CapturingSlot<UserIdUpdateMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot), SendPriority.SOON) }
        assertEquals("some@email.com", slot.captured.email)
    }

    @Test
    fun phoneNumber_StoresAndRetrievesCustomId() {
        userCredentials.phoneNumber = "12345678"
        assertNotNull(userCredentials.phoneNumber)
        assertEquals("12345678", userCredentials.phoneNumber)
    }

    @Test
    fun phoneNumber_SendsMessageOnCustomIdChange() {
        userCredentials.phoneNumber = "12345678"
        ioThread.triggerActions()
        cpuThread.triggerActions()
        val slot = CapturingSlot<UserIdUpdateMessage>()
        verify(exactly = 1) { postOffice.sendMessage(capture(slot), SendPriority.SOON) }
        assertEquals("12345678", slot.captured.phoneNumber)
    }

    @Test
    fun throttlesUpstreamMessagesOnRapidIdChanges() {
        userCredentials.customId = "some_id_0"
        ioThread.triggerActions()
        cpuThread.triggerActions()
        verify(exactly = 1) { postOffice.sendMessage(any(), any()) }

        1.until(10).onEach {
            when {
                it % 3 == 0 -> userCredentials.customId = "some_id_$it"
                it % 3 == 1 -> userCredentials.email = "$it@email.com"
                else -> userCredentials.phoneNumber = "$it"
            }

            ioThread.advanceTimeBy(100, TimeUnit.MILLISECONDS)
            cpuThread.triggerActions()
            verify(exactly = 1) { postOffice.sendMessage(any(), any()) }
        }

        ioThread.advanceTimeBy(100000, TimeUnit.MILLISECONDS)
        cpuThread.triggerActions()
        val slot = CapturingSlot<UserIdUpdateMessage>()
        verify(exactly = 2) { postOffice.sendMessage(capture(slot), any()) }
        println(slot.captured.customId)
        assertEquals("some_id_9", slot.captured.customId)
        assertEquals("7@email.com", slot.captured.email)
        assertEquals("8", slot.captured.phoneNumber)
    }

}