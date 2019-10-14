package co.pushe.plus

import android.content.Context
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.test.TestUtils.mockCpuThread
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PusheConfigTest{
    private val context: Context = mockk(relaxed = true)
    private val moshi = PusheMoshi()
    private lateinit var pusheConfig: PusheConfig
    private val sharedPreferences = MockSharedPreference()

    private val cpuThread = mockCpuThread()

    @Before
    fun init() {
        every { context.getSharedPreferences(PusheConfig.PUSHE_CONFIG_STORE, Context.MODE_PRIVATE) } returns sharedPreferences
        pusheConfig = PusheConfig(context, moshi)
    }

    @Test
    fun updatesAreAffectiveImmediatelyButPersistingUpdatesAreThrottled() {
        pusheConfig.updateConfig("key1", "value1")
        pusheConfig.updateConfig("key2", "value2")
        cpuThread.advanceTimeBy(10, TimeUnit.MILLISECONDS)
        assertEquals("value1", pusheConfig.getString("key1", "default"))
        assertEquals("default", sharedPreferences.getString("key1", "default"))
        assertEquals("value2", pusheConfig.getString("key2", "default"))
        assertEquals("default", sharedPreferences.getString("key2", "default"))
        pusheConfig.updateConfig("key3", "value3")
        assertEquals("value3", pusheConfig.getString("key3", "default"))
        assertEquals("default", sharedPreferences.getString("key3", "default"))
        cpuThread.advanceTimeBy(200, TimeUnit.MILLISECONDS)
        assertEquals("value1", pusheConfig.getString("key1", "default"))
        assertEquals("value1", pusheConfig.getString("key1", "default"))
        assertEquals("value2", pusheConfig.getString("key2", "default"))
        assertEquals("value2", pusheConfig.getString("key2", "default"))
        assertEquals("value3", pusheConfig.getString("key3", "default"))
        assertEquals("value3", pusheConfig.getString("key3", "default"))
    }

    @Test
    fun getConfig_ReturnsDefaultValueIfKeyIsNotFound() {
        assertEquals("default", pusheConfig.getString("key", "default"))
        assertEquals(100, pusheConfig.getInteger("key", 100))
        assertEquals(3.5F, pusheConfig.getFloat("key", 3.5F))
        assertEquals(true, pusheConfig.getBoolean("key", true))
        assertEquals(3000L, pusheConfig.getLong("key", 3000L))
        assertEquals(stringList, pusheConfig.getStringList("key", stringList))
        assertEquals(integerList, pusheConfig.getIntegerList("key", integerList))

        assertEquals(dummyObject, pusheConfig.getObject("key", dummyObject, moshi.adapter(DummyClass::class.java)))
        assertEquals(dummyObjectList, pusheConfig.getObjectList("key", DummyClass::class.java, dummyObjectList, moshi.adapter(DummyClass::class.java)))
        assertEquals(dummyObjectList, pusheConfig.getObjectList("key", DummyClass::class.java, dummyObjectList, DummyClassCustomJsonAdapter()))
    }

    @Test
    fun getConfig_ReturnsDefaultValueIfThereIsATypeError() {
        pusheConfig.updateConfig("intKey", "something")
        assertEquals(100, pusheConfig.getInteger("intKey", 100))

        pusheConfig.updateConfig("floatKey", "something")
        assertEquals(3.5F, pusheConfig.getFloat("floatKey", 3.5F))

        pusheConfig.updateConfig("booleanKey", "something")
        assertEquals(true, pusheConfig.getBoolean("booleanKey", true))

        pusheConfig.updateConfig("longKey", "something")
        assertEquals(3000L, pusheConfig.getLong("longKey", 3000L))

        pusheConfig.updateConfig("stringListKey", "something")
        assertEquals(stringList, pusheConfig.getStringList("stringListKey", stringList))

        pusheConfig.updateConfig("intListKey", "something")
        assertEquals(integerList, pusheConfig.getIntegerList("intListKey", integerList))

        pusheConfig.updateConfig("objectKey", "something")
        assertEquals(dummyObject, pusheConfig.getObject("objectKey", dummyObject, moshi.adapter(DummyClass::class.java)))

        pusheConfig.updateConfig("objectListKey", "something")
        assertEquals(dummyObjectList, pusheConfig.getObjectList("objectListKey", DummyClass::class.java, dummyObjectList, moshi.adapter(DummyClass::class.java)))
        pusheConfig.updateConfig("objectListKey", "something")
        assertEquals(dummyObjectList, pusheConfig.getObjectList("objectListKey", DummyClass::class.java, dummyObjectList, DummyClassCustomJsonAdapter()))
    }
}

@JsonClass(generateAdapter = true)
data class DummyClass(
    @Json(name = "key1") val key1: Int,
    @Json(name = "key2") val key2: Float,
    @Json(name = "key3") val key3: String
)

class DummyClassCustomJsonAdapter() {
    @ToJson
    fun toJson(dummyClass: DummyClass): String {
        return dummyClass.toString()
    }

    @FromJson
    fun fromJson(json: String): DummyClass {
        return DummyClass(20, 20.5F, "something")
    }
}

private val dummyObject = DummyClass(100, 10.5F, "something")
private val dummyObjectList = listOf(
    DummyClass(100, 10.5F, "something"),
    DummyClass(200, 20.5F, "something2"),
    DummyClass(300, 30.5F, "something3")
)
private val stringList = listOf("something", "something2", "something3")
private val integerList = listOf(10, 20, 30)
