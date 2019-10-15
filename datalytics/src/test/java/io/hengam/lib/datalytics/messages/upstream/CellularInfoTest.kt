package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.datalytics.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class CellularInfoTest {

    val moshi = HengamMoshi()

    @Before
    fun setup() {
        extendMoshi(moshi)
    }

    @Test
    fun cellInfoTest() {

        val adapter = CellInfoMessageJsonAdapter(moshi.moshi)
        val cellLte = CellArrayLTE(
                CellLTE(10,10,10,10,10),
                SSP(10, 10, 10, "111")
        ).apply { registered = true }
        val json = adapter.toJson(CellInfoMessage(listOf(cellLte)))
        assertNotNull(json)
        println("JSON of Cell Info: $json")
        assertTrue(json.isNotEmpty())
    }
}