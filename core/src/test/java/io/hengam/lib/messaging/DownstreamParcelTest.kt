package io.hengam.lib.messaging

import io.hengam.lib.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownstreamParcelTest {
    private val moshi = HengamMoshi()

    @Before
    fun setUp() {
        extendMoshi(moshi)
    }

    @Test
    fun downstreamParcel_ParseFromJson() {
        val parcelId = "parcelId"

        val adapter = moshi.adapter(DownstreamParcel::class.java)

        val json = """
            {
                "t25": {
                    "key1": "something",
                    "key2": {
                        "nested": "baby"
                    }
                },
                "message_id": "parcelId",
                "t35": [
                    {
                        "key3": "value",
                        "key4": "I'll take a walk"
                    },
                    {
                        "key3": "value",
                        "key4": "Goodbye"
                    }
                ]
            }
        """.trimIndent()

        val parcel = adapter.fromJson(json) ?: throw NullPointerException()
        assertEquals(parcelId, parcel.parcelId)
        assertEquals(3, parcel.messages.size)
        assertEquals(25, parcel.messages.toList()[0].messageType)
        assertEquals(35, parcel.messages.toList()[1].messageType)
        assertEquals(35, parcel.messages.toList()[2].messageType)
        assertEquals(parcelId, parcel.messages.toList()[0].messageId)
        assertEquals(parcelId, parcel.messages.toList()[1].messageId)
        assertEquals(parcelId, parcel.messages.toList()[2].messageId)
        assertEquals("something", (parcel.messages.toList()[0].rawData as Map<*, *>)["key1"])
        assertEquals(mapOf("nested" to "baby"), (parcel.messages.toList()[0].rawData as Map<*, *>)["key2"])
        assertEquals("value", (parcel.messages.toList()[1].rawData as Map<*, *>)["key3"])
        assertEquals("I'll take a walk", (parcel.messages.toList()[1].rawData as Map<*, *>)["key4"])
        assertEquals("value", (parcel.messages.toList()[2].rawData as Map<*, *>)["key3"])
        assertEquals("Goodbye", (parcel.messages.toList()[2].rawData as Map<*, *>)["key4"])
    }

    @Test
    fun downstreamParcel_ParseFromJsonAddsMessageIdToAllMessageValues() {
        val parcelId = "parcelId"

        val adapter = moshi.adapter(DownstreamParcel::class.java)

        val json = """
            {
                "message_id": "parcelId",
                "t25": {
                    "key1": "something",
                    "key2": {
                        "nested": "baby"
                    }
                },
                "t35": [
                    {
                        "key3": "value",
                        "key4": "I'll take a walk"
                    },
                    {
                        "key3": "value",
                        "key4": "Goodbye"
                    }
                ]
            }
        """.trimIndent()

        val parcel = adapter.fromJson(json) ?: throw NullPointerException()
        assertEquals(parcelId, (parcel.messages.toList()[0].rawData as Map<*, *>)["message_id"])
        assertEquals(parcelId, (parcel.messages.toList()[1].rawData as Map<*, *>)["message_id"])
        assertEquals(parcelId, (parcel.messages.toList()[2].rawData as Map<*, *>)["message_id"])
    }

    @Test(expected = ParcelParseException::class)
    fun downstreamParcel_ParseFromJsonThrowsParcelParseExceptionOnMissingMessageId() {
        val parcelId = "ParcelID"
        val adapter = moshi.adapter(DownstreamParcel::class.java)

        val json = """
             {
                "t25": {
                    "key1": "sth",
                    "key2": {
                        "nested": "baby"
                    }
                },
                "t35": {
                    "something": "ye",
                    "ooah": "I'll take a walk"
                }
            }
        """.trimIndent()
        adapter.fromJson(json)
    }

    @Test(expected = ParcelParseException::class)
    fun downstreamParcel_ParseFromJsonThrowsParcelParseExceptionOnBadJson() {
        val parcelId = "ParcelID"
        val adapter = moshi.adapter(DownstreamParcel::class.java)

        val json = """
            {
                "t25": {
                    "key1": "sth",
                    "key2": {
            }
        """.trimIndent()
        adapter.fromJson(json)
    }

    @Test(expected = ParcelParseException::class)
    fun downstreamParcel_ParseFromJsonThrowsParcelParseExceptionOnBadMessageType() {
        val parcelId = "ParcelID"
        val adapter = moshi.adapter(DownstreamParcel::class.java)

        val json = """
            {
                "h76": {
                    "key1": "sth",
                    "key2": {
            }
        """.trimIndent()
        adapter.fromJson(json)
    }
}