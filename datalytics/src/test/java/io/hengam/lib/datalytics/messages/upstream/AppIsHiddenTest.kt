package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.datalytics.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class AppIsHiddenTest {

    private val moshi = HengamMoshi()

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