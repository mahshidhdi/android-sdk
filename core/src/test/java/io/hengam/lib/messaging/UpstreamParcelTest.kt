package io.hengam.lib.messaging


import io.hengam.lib.extendMoshi
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messages.upstream.RegistrationMessage
import io.hengam.lib.utils.packOf
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpstreamParcelTest {
    private val moshi = HengamMoshi()

    private val reynolds = MockPerson("Alastair", "Reynolds",40)
    private val jkRowling = MockPerson("JK", "Rowling",35)
    private val revelationSpaceBook = UpstreamMockMessageBook("Revelation Space", Genre.SCIFI, reynolds)
    private val harryPotterBook = UpstreamMockMessageBook("Harry Potter", Genre.FANTASY, jkRowling)
    private val mementoMovie = UpstreamMockMessageMovie("Memento", Genre.MYSTERY, 2000)
    private val matrixMovie = UpstreamMockMessageMovie("Matrix", Genre.SCIFI, 1998)
    private val terminatorMovie = UpstreamMockMessageMovie("Terminator", Genre.SCIFI, 1984)

    @Before
    fun setUp() {
        extendMoshi(moshi)
    }

    @Test
    fun jsonSerialization_CorrectlyConvertsParcelToJson() {
        val parcel = UpstreamParcel("", listOf(
                revelationSpaceBook,
                harryPotterBook,
                mementoMovie
        ))

        val expected = """
            {
                "types": ["t50", "t60"],
                "t50": [
                    {
                        "title": "Revelation Space",
                        "genre": "sci-fi",
                        "author": {
                          "first_name": "Alastair",
                          "last_name": "Reynolds",
                          "age": 40
                        },
                        "time": ${revelationSpaceBook.time}
                    },
                    {
                        "title": "Harry Potter",
                        "genre": "fantasy",
                        "author": {
                            "first_name": "JK",
                            "last_name": "Rowling",
                            "age": 35
                        },
                        "time": ${harryPotterBook.time}
                    }
                ],
                "t60": [
                    {
                        "title": "Memento",
                        "genre": "mystery",
                        "year": 2000,
                        "time": ${mementoMovie.time}
                    }
                ]
            }
        """.trimIndent()

        val parcelAdapter = moshi.adapter(UpstreamParcel::class.java)
        val packType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val anyAdapter = moshi.adapter<Map<String, Any>>(packType)
        assertEquals(anyAdapter.fromJson(expected), anyAdapter.fromJson(parcelAdapter.toJson(parcel)))
    }

    @Test
    fun jsonSerialization_CorrectlyAddsStampToParcelJson() {
        val parcel = UpstreamParcel("", listOf(
                revelationSpaceBook
        ))

        val stamp = packOf(
                "instance_id" to "12345",
                "android_id" to 54321
        )

        val stamped = UpstreamStampedParcel(parcel, stamp)

        val expected = """
            {
                "instance_id": "12345",
                "android_id": 54321,
                "t50": [
                    {
                        "title": "Revelation Space",
                        "genre": "sci-fi",
                        "author": {
                          "first_name": "Alastair",
                          "last_name": "Reynolds",
                          "age": 40
                        },
                        "time": ${revelationSpaceBook.time}
                    }
                ],
                "types": ["t50"]
            }
        """.trimIndent()

        val parcelAdapter = moshi.adapter(UpstreamParcel::class.java)
        val packType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val packAdapter = moshi.adapter<Map<String, Any>>(packType)
        assertEquals(packAdapter.fromJson(expected), packAdapter.fromJson(parcelAdapter.toJson(stamped)))
    }

    /**
     * @see [io.hengam.lib.ApiPatch.convertRegistrationMessageToSingleMessage]
     */
    @Test
    fun jsonSerialization_RegistrationMessageShouldBeSerializedAsSingleMessageNotList() {
        val registrationMessage = RegistrationMessage(
                "device_id",
                "device_brand",
                "device_model",
                "os_version",
                "fcm_token",
                "app_version",
                0L,
                "hengam_version",
                0,
                "cause",
                listOf("1D:AB:AE:1E:AB:AE:1E:AB:AE:1E:AB:AE:1E:AB:AE:1E:AB:AE:1E:2D"),
                "direct",
                0,
                0,
                true
        )

        val parcel = UpstreamParcel("", listOf(
                revelationSpaceBook,
                registrationMessage
        ))

        val parcelAdapter = moshi.adapter(UpstreamParcel::class.java)
        val packType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val packAdapter = moshi.adapter<Map<String, Any>>(packType)
        val deserialized = packAdapter.fromJson(parcelAdapter.toJson(parcel)) ?: emptyMap()
        assertTrue(deserialized["t50"] is List<*>)
        assertTrue(deserialized["t10"] is Map<*, *>)
    }
}

