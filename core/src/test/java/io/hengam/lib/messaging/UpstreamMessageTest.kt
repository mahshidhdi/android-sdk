package io.hengam.lib.messaging

import io.hengam.lib.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.test.TestUtils
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import com.squareup.moshi.Types
import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class UpstreamMessageTest {
    private val moshi = HengamMoshi()
    private val anyAdapter = moshi.adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    private val cpuThread = mockCpuThread()

    @Before
    fun setUp() {
        extendMoshi(moshi)

    }

    @Test
    fun sendPriority_SendPriorityOrderingIsCorrect() {
        assertTrue(SendPriority.IMMEDIATE > SendPriority.SOON)
        assertTrue(SendPriority.IMMEDIATE > SendPriority.WHENEVER)
        assertTrue(SendPriority.SOON > SendPriority.WHENEVER)
        assertTrue(SendPriority.SOON < SendPriority.IMMEDIATE)
        assertTrue(SendPriority.WHENEVER < SendPriority.IMMEDIATE)
        assertTrue(SendPriority.WHENEVER < SendPriority.SOON)
    }

    @Test
    fun upstreamMessage_CorrectlySerializesMessage() {
        TestUtils.mockTime(1000)
        val message = UpstreamMockMessageMovie("Matrix", Genre.SCIFI, 1998)
        val expected = """
            {
                "title": "Matrix",
                "genre": "sci-fi",
                "year": 1998,
                "time": 1000
            }
        """
        assertEquals(anyAdapter.fromJson(expected), anyAdapter.fromJson(message.toJson(moshi)))
    }

    @Test
    fun upstreamMessage_CorrectlySerializesMessageWithMixin() {
        TestUtils.mockTime(1000)
        val message = UpstreamMockMessageMovieWithMixin("Matrix", Genre.SCIFI, 1998)
        message.prepare().test()
        cpuThread.triggerActions()

        val expected = """
            {
                "title": "Matrix",
                "genre": "sci-fi",
                "year": 1998,
                "time": 1000,
                "mixinKey1": "mixinValue1",
                "mixinKey2": "mixinValue2"
            }
        """

        assertEquals(anyAdapter.fromJson(expected), anyAdapter.fromJson(message.toJson(moshi)))
    }

}