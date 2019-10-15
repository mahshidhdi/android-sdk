package io.hengam.lib.utils

import android.content.SharedPreferences
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.test.TestUtils.advanceMockTimeBy
import io.hengam.lib.utils.test.TestUtils.mockCpuThread
import io.hengam.lib.utils.test.TestUtils.mockTime
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HengamStorageTest {
    private val cpuThread = mockCpuThread()

    @Before
    fun setUp() {
        mockTime(1000)
    }

    private fun advanceTimeBy(time: Time) {
        advanceMockTimeBy(time)
        cpuThread.advanceTimeBy(time.toMillis(), TimeUnit.MILLISECONDS)
    }

    @Test
    fun storedMap_PersistsUpdatesInStorage() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedMap = storageFactory.createStoredMap("test_map", Int::class.javaObjectType)
        storedMap["key1"] = 1000
        storedMap["key2"] = 2000
        advanceTimeBy(seconds(10))
        val restoredMap = storageFactory.createStoredMap("test_map", Int::class.javaObjectType)
        assertEquals(1000, restoredMap["key1"])
        assertEquals(2000, restoredMap["key2"])
        restoredMap.remove("key1")
        advanceTimeBy(seconds(10))
        val anotherRestoredMap = storageFactory.createStoredMap("test_map", Int::class.javaObjectType)
        assertNull(anotherRestoredMap["key1"])
        assertEquals(2000, anotherRestoredMap["key2"])
    }

    @Test
    fun storedMap_StoresObjects() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedMap = storageFactory.createStoredMap("test_map",
                TestObject::class.java, TestObjectAdapter)
        storedMap["key1"] = TestObject("guy", 25)
        storedMap["key2"] = TestObject("gal", 30)
        advanceTimeBy(seconds(10))
        val restoredMap = storageFactory.createStoredMap("test_map",
                TestObject::class.java, TestObjectAdapter)
        assertEquals(TestObject("guy", 25), restoredMap["key1"])
        assertEquals(TestObject("gal", 30), restoredMap["key2"])
    }

    @Test
    fun storedSet_PersistsUpdatesInStorage() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedSet = storageFactory.createStoredSet("test_set", Int::class.javaObjectType)
        storedSet.add(1000)
        storedSet.add(2000)
        advanceTimeBy(seconds(10))
        val restoredSet = storageFactory.createStoredSet("test_set", Int::class.javaObjectType)
        assertTrue(1000 in restoredSet)
        assertTrue(2000 in restoredSet)
        restoredSet.remove(1000)
        advanceTimeBy(seconds(10))
        val anotherRestoredSet = storageFactory.createStoredSet("test_set", Int::class.javaObjectType)
        assertTrue(1000 !in anotherRestoredSet)
        assertTrue(2000 in anotherRestoredSet)
    }

    @Test
    fun storedSet_StoresObjects() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedSet = storageFactory.createStoredSet("test_map",
                TestObject::class.java, TestObjectAdapter)
        storedSet.add(TestObject("guy", 25))
        storedSet.add(TestObject("gal", 30))
        advanceTimeBy(seconds(10))
        val restoredSet = storageFactory.createStoredSet("test_map",
                TestObject::class.java, TestObjectAdapter)
        assertTrue(TestObject("guy", 25) in restoredSet)
        assertTrue(TestObject("gal", 30) in restoredSet)
    }

    @Test
    fun storedMap_DoesNotExpiredValuesToSoon() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedMap = storageFactory.createStoredMap(
                "test_map", Int::class.javaObjectType, seconds(10))
        storedMap["key1"] = 1000
        storedMap["key2"] = 2000
        advanceTimeBy(seconds(5))
        val restoredMap = storageFactory.createStoredMap("test_map", Int::class.javaObjectType)
        assertEquals(1000, restoredMap["key1"])
        assertEquals(2000, restoredMap["key2"])
    }

    @Test
    fun storedMap_ExpiresValuesAfterExpireTime() {
        val storageFactory = HengamStorage(HengamMoshi(), MockSharedPreference())
        val storedMap = storageFactory.createStoredMap(
                "test_map", Int::class.javaObjectType, seconds(10))

        storedMap["key1"] = 1000
        advanceTimeBy(seconds(5))
        storedMap["key2"] = 2000
        advanceTimeBy(seconds(6))
        storedMap["key3"] = 3000
        advanceTimeBy(seconds(2))

        assertNull(storedMap["key1"])
        assertEquals(2000, storedMap["key2"])
        assertEquals(3000, storedMap["key3"])


        val restoredMap = storageFactory.createStoredMap(
                "test_map", Int::class.javaObjectType, seconds(10))
        assertNull(restoredMap["key1"])
        assertEquals(2000, restoredMap["key2"])
        assertEquals(3000, restoredMap["key3"])
        advanceTimeBy(seconds(5))
        assertNull(restoredMap["key1"])
        assertNull(restoredMap["key2"])
        assertEquals(3000, restoredMap["key3"])
    }

    @Test
    fun storedString_PersistsAndRestoresAndDeletesValue() {
        val sharedPreference = MockSharedPreference()
        var storedItem by HengamStorage(HengamMoshi(), sharedPreference).storedString("key1", "default")
        assertEquals("default", storedItem)
        storedItem = "value"
        assertEquals("value", storedItem)

        advanceTimeBy(seconds(2))
        val restoredItem = HengamStorage(HengamMoshi(), sharedPreference).storedString("key1", "default")
        assertEquals("value", restoredItem.get())
        restoredItem.delete()
        assertEquals("default", restoredItem.get())

        advanceTimeBy(seconds(2))
        val restoredItem2 by HengamStorage(HengamMoshi(), sharedPreference).storedString("key1", "default")
        assertEquals("default", restoredItem2)
    }

    @Test
    fun storedInt_PersistsAndRestoresAndDeletesValue() {
        val sharedPreference = MockSharedPreference()
        var storedItem by HengamStorage(HengamMoshi(), sharedPreference).storedInt("key1", 0)
        assertEquals(0, storedItem)
        storedItem = 1000
        assertEquals(1000, storedItem)

        advanceTimeBy(seconds(2))
        val restoredItem = HengamStorage(HengamMoshi(), sharedPreference).storedInt("key1", 0)
        assertEquals(1000, restoredItem.get())
        restoredItem.delete()
        assertEquals(0, restoredItem.get())

        advanceTimeBy(seconds(2))
        val restoredItem2 by HengamStorage(HengamMoshi(), sharedPreference).storedInt("key1", 0)
        assertEquals(0, restoredItem2)
    }

    @Test
    fun storedLong_PersistsAndRestoresAndDeletesValue() {
        val sharedPreference = MockSharedPreference()
        var storedItem by HengamStorage(HengamMoshi(), sharedPreference).storedLong("key1", 0L)
        assertEquals(0L, storedItem)
        storedItem = 1000L
        assertEquals(1000L, storedItem)

        advanceTimeBy(seconds(2))
        val restoredItem = HengamStorage(HengamMoshi(), sharedPreference).storedLong("key1", 0L)
        assertEquals(1000L, restoredItem.get())
        restoredItem.delete()
        assertEquals(0L, restoredItem.get())

        advanceTimeBy(seconds(2))
        val restoredItem2 by HengamStorage(HengamMoshi(), sharedPreference).storedLong("key1", 0L)
        assertEquals(0L, restoredItem2)
    }

    @Test
    fun storedFloat_PersistsAndRestoresAndDeletesValue() {
        val sharedPreference = MockSharedPreference()
        var storedItem by HengamStorage(HengamMoshi(), sharedPreference).storedFloat("key1", 0f)
        assertEquals(0f, storedItem)
        storedItem = 1000f
        assertEquals(1000f, storedItem)

        advanceTimeBy(seconds(2))
        val restoredItem = HengamStorage(HengamMoshi(), sharedPreference).storedFloat("key1", 0f)
        assertEquals(1000f, restoredItem.get())
        restoredItem.delete()
        assertEquals(0f, restoredItem.get())

        advanceTimeBy(seconds(2))
        val restoredItem2 by HengamStorage(HengamMoshi(), sharedPreference).storedFloat("key1", 0f)
        assertEquals(0f, restoredItem2)
    }

    @Test
    fun storedBoolean_PersistsAndRestoresAndDeletesValue() {
        val sharedPreference = MockSharedPreference()
        var storedItem by HengamStorage(HengamMoshi(), sharedPreference).storedBoolean("key1", false)
        assertEquals(false, storedItem)
        storedItem = true
        assertEquals(true, storedItem)

        advanceTimeBy(seconds(2))
        val restoredItem = HengamStorage(HengamMoshi(), sharedPreference).storedBoolean("key1", false)
        assertEquals(true, restoredItem.get())
        restoredItem.delete()
        assertEquals(false, restoredItem.get())

        advanceTimeBy(seconds(2))
        val restoredItem2 by HengamStorage(HengamMoshi(), sharedPreference).storedBoolean("key1", false)
        assertEquals(false, restoredItem2)
    }

    @Test
    fun rateLimitsPersistingOnFrequentUpdates() {
        val sharedPreferences: SharedPreferences = mockk(relaxed = true)
        val storageFactory = HengamStorage(HengamMoshi(), sharedPreferences)
        val storedMap = storageFactory.createStoredMap("test_map", Int::class.javaObjectType)
        val storedSet = storageFactory.createStoredSet("test_set", String::class.java)
        var storedInt by storageFactory.storedInt("test_int", 0)
        var storedString by storageFactory.storedString("test_string", "")
        storedMap["key1"] = 1000
        advanceTimeBy(millis(100))
        storedMap["key2"] = 2000
        advanceTimeBy(millis(100))
        storedSet.add("something1")
        advanceTimeBy(millis(50))
        storedInt = 100
        advanceTimeBy(millis(50))
        storedMap["key3"] = 3000
        advanceTimeBy(millis(100))
        verify(exactly = 0) { sharedPreferences.edit() }
        advanceTimeBy(seconds(10))
        verify(exactly = 1) { sharedPreferences.edit() }
        storedMap["key4"] = 3000
        advanceTimeBy(millis(100))
        verify(exactly = 1) { sharedPreferences.edit() }
        advanceTimeBy(seconds(10))
        verify(exactly = 2) { sharedPreferences.edit() }
        storedSet.add("something2")
        advanceTimeBy(millis(100))
        verify(exactly = 2) { sharedPreferences.edit() }
        advanceTimeBy(seconds(10))
        verify(exactly = 3) { sharedPreferences.edit() }
        storedString = "hello"
        advanceTimeBy(millis(10))
        storedInt = 200
        advanceTimeBy(millis(10))

        verify(exactly = 3) { sharedPreferences.edit() }
        advanceTimeBy(seconds(10))
        verify(exactly = 4) { sharedPreferences.edit() }
    }

}

data class TestObject(val name: String, val age: Int)

object TestObjectAdapter {
    @ToJson
    fun toJson(obj: TestObject): Map<String, String> = mapOf(
            "name" to obj.name,
            "age" to obj.age.toString()
    )

    @FromJson
    fun fromJson(json: Map<String, String>): TestObject = TestObject(
            json["name"] ?: "",
            json["age"]?.toIntOrNull() ?: 0
    )
}