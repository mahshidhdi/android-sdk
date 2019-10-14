package co.pushe.plus.messaging

import co.pushe.plus.extendMoshi
import co.pushe.plus.internal.PusheMoshi
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownstreamMessageTest {
    private val moshi: PusheMoshi = PusheMoshi()

    @Before
    fun setUp() {
        extendMoshi(moshi)
    }

    @Test
    fun responseStateJsonSerialization() {
        val adapter = moshi.adapter(ResponseMessage.Status::class.java)
        assertEquals("0", adapter.toJson(ResponseMessage.Status.SUCCESS))
        assertEquals("1", adapter.toJson(ResponseMessage.Status.FAIL))
        assertEquals("-1", adapter.toJson(ResponseMessage.Status.NONE))
        assertEquals(ResponseMessage.Status.SUCCESS, adapter.fromJson("0"))
        assertEquals(ResponseMessage.Status.FAIL, adapter.fromJson("1"))
        assertEquals(ResponseMessage.Status.NONE, adapter.fromJson("-1"))
        assertEquals(ResponseMessage.Status.NONE, adapter.fromJson("1232"))
    }
}