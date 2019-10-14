package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.datalytics.extendMoshi
import co.pushe.plus.internal.PusheMoshi
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class CellularInfoTest {

    val moshi = PusheMoshi()

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