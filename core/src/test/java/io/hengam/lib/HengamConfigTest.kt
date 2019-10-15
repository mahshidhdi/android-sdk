package io.hengam.lib

import android.content.Context
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.mocks.MockSharedPreference
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

class HengamConfigTest{
    private val context: Context = mockk(relaxed = true)
    private val moshi = HengamMoshi()
    private lateinit var hengamConfig: HengamConfig
    private val sharedPreferences = MockSharedPreference()

    private val cpuThread = mockCpuThread()

    @Before
    fun init() {
        every { context.getSharedPreferences(HengamConfig.HENGAM_CONFIG_STORE, Context.MODE_PRIVATE) } returns sharedPreferences
        hengamConfig = HengamConfig(context, moshi)
    }

    @Test
    fun updatesAreAffectiveImmediatelyButPersistingUpdatesAreThrottled() {
        hengamConfig.updateConfig("key1", "value1")
        hengamConfig.updateConfig("key2", "value2")
        cpuThread.advanceTimeBy(10, TimeUnit.MILLISECONDS)
        assertEquals("value1", hengamConfig.getString("key1", "default"))
        assertEquals("default", sharedPreferences.getString("key1", "default"))
        assertEquals("value2", hengamConfig.getString("key2", "default"))
        assertEquals("default", sharedPreferences.getString("key2", "default"))
        hengamConfig.updateConfig("key3", "value3")
        assertEquals("value3", hengamConfig.getString("key3", "default"))
        assertEquals("default", sharedPreferences.getString("key3", "default"))
        cpuThread.advanceTimeBy(200, TimeUnit.MILLISECONDS)
        assertEquals("value1", hengamConfig.getString("key1", "default"))
        assertEquals("value1", hengamConfig.getString("key1", "default"))
        assertEquals("value2", hengamConfig.getString("key2", "default"))
        assertEquals("value2", hengamConfig.getString("key2", "default"))
        assertEquals("value3", hengamConfig.getString("key3", "default"))
        assertEquals("value3", hengamConfig.getString("key3", "default"))
    }

    @Test
    fun getConfig_ReturnsDefaultValueIfKeyIsNotFound() {
        assertEquals("default", hengamConfig.getString("key", "default"))
        assertEquals(100, hengamConfig.getInteger("key", 100))
        assertEquals(3.5F, hengamConfig.getFloat("key", 3.5F))
        assertEquals(true, hengamConfig.getBoolean("key", true))
        assertEquals(3000L, hengamConfig.getLong("key", 3000L))
        assertEquals(stringList, hengamConfig.getStringList("key", stringList))
        assertEquals(integerList, hengamConfig.getIntegerList("key", integerList))

        assertEquals(dummyObject, hengamConfig.getObject("key", dummyObject, moshi.adapter(DummyClass::class.java)))
        assertEquals(dummyObjectList, hengamConfig.getObjectList("key", DummyClass::class.java, dummyObjectList, moshi.adapter(DummyClass::class.java)))
        assertEquals(dummyObjectList, hengamConfig.getObjectList("key", DummyClass::class.java, dummyObjectList, DummyClassCustomJsonAdapter()))
    }

    @Test
    fun getConfig_ReturnsDefaultValueIfThereIsATypeError() {
        hengamConfig.updateConfig("intKey", "something")
        assertEquals(100, hengamConfig.getInteger("intKey", 100))

        hengamConfig.updateConfig("floatKey", "something")
        assertEquals(3.5F, hengamConfig.getFloat("floatKey", 3.5F))

        hengamConfig.updateConfig("booleanKey", "something")
        assertEquals(true, hengamConfig.getBoolean("booleanKey", true))

        hengamConfig.updateConfig("longKey", "something")
        assertEquals(3000L, hengamConfig.getLong("longKey", 3000L))

        hengamConfig.updateConfig("stringListKey", "something")
        assertEquals(stringList, hengamConfig.getStringList("stringListKey", stringList))

        hengamConfig.updateConfig("intListKey", "something")
        assertEquals(integerList, hengamConfig.getIntegerList("intListKey", integerList))

        hengamConfig.updateConfig("objectKey", "something")
        assertEquals(dummyObject, hengamConfig.getObject("objectKey", dummyObject, moshi.adapter(DummyClass::class.java)))

        hengamConfig.updateConfig("objectListKey", "something")
        assertEquals(dummyObjectList, hengamConfig.getObjectList("objectListKey", DummyClass::class.java, dummyObjectList, moshi.adapter(DummyClass::class.java)))
        hengamConfig.updateConfig("objectListKey", "something")
        assertEquals(dummyObjectList, hengamConfig.getObjectList("objectListKey", DummyClass::class.java, dummyObjectList, DummyClassCustomJsonAdapter()))
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
