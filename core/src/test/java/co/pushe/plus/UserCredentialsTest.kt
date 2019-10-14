package co.pushe.plus

import android.content.SharedPreferences
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messages.upstream.UserIdUpdateMessage
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import co.pushe.plus.utils.test.TestUtils.mockIoThread
import co.pushe.plus.utils.test.mocks.MockSharedPreference
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
    private val userCredentials: UserCredentials = UserCredentials(PusheStorage(PusheMoshi(), sharedPreferences))

    private val coreComponent: CoreComponent = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkObject(PusheInternals)
        every { PusheInternals.getComponent(CoreComponent::class.java) } returns coreComponent
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