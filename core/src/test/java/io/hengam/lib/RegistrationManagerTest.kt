package io.hengam.lib

import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.ResponseMessage
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.mocks.MockSharedPreference
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
            HengamStorage(HengamMoshi(), MockSharedPreference())
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