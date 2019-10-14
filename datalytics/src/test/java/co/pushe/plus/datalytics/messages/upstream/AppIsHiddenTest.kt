package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.datalytics.extendMoshi
import co.pushe.plus.internal.PusheMoshi
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class AppIsHiddenTest {

    private val moshi = PusheMoshi()

    @Before
    fun setup() {
        extendMoshi(moshi)
    }

    @Test
    fun appIsHiddenJsonSerialization() {
        val adapter = AppIsHiddenMessageJsonAdapter(moshi.moshi)
        val obj = adapter.fromJson(
                """
                    {
                        "hidden_app":true
                    }
                """.trimIndent()
        )
        assertNotNull(obj)
        assertTrue(obj!!.appIsHidden)

    }
}