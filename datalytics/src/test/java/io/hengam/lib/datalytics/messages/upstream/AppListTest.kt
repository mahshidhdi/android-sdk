package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.datalytics.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AppListTest {

    private val moshi = HengamMoshi()

    @Before
    fun setup() {
        extendMoshi(moshi)
    }

    @Test
    fun appListTest() {
        val adapter = ApplicationDetailsMessageJsonAdapter(moshi = moshi.moshi)
        val obj = adapter.fromJson(
                """
                    {
                        "package_name":"something",
                        "app_version":"app_version",
                        "src":"src",
                        "fit":12345,
                        "lut":54321,
                        "app_name":"app_name",
                        "hidden_app":false
                    }
                """.trimIndent()
        )
        assertNotNull(obj)
        assertFalse(obj!!.packageName.isNullOrBlank())
    }
}