package co.pushe.plus

import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.ResponseMessage
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class RegistrationManagerTest {
    private val registrationManager = RegistrationManager(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            PusheStorage(PusheMoshi(), MockSharedPreference())
    )

    private fun performSuccessfulRegistration() {
        registrationManager.handleRegistrationResponseMessage(
                ResponseMessage(status = ResponseMessage.Status.SUCCESS))
    }

    @Test
    fun isRegistered() {
        assertFalse(registrationManager.isRegistered)
        performSuccessfulRegistration()
        assertTrue(registrationManager.isRegistered)
    }

}