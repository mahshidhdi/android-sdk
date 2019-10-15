package io.hengam.lib

import android.os.Bundle
import android.util.Base64
import android.util.Log
import io.hengam.lib.utils.ApplicationInfoHelper
import io.mockk.every
import io.mockk.spyk
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class AppManifestTest {

    private val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
    private val bundle: Bundle = spyk(Bundle())
    private lateinit var appManifest: AppManifest

    @Before
    fun init() {
        every { applicationInfoHelper.getManifestMetaData() } returns bundle
        every { bundle.get(any()) } returns null
        every { bundle.getString(any(), any()) } returns "stub"
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        appManifest = AppManifest(applicationInfoHelper)
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId() {
        setToken("io.hengam.adminapp@292417903871")
        appManifest.extractManifestData()
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId_() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n6g@efjq@292417903871")
        appManifest.extractManifestData()
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId________() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n6g@fjq@292417903871")
        appManifest.extractManifestData()
    }

    @Test
    fun extractManifestData_ValidatesAppId__() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n6g@fjp@292417903871")
        appManifest.extractManifestData()
    }

    @Test
    fun extractManifestData_ValidatesAppId___() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n6@eis@292417903871")
        appManifest.extractManifestData()
    }

    @Test
    fun extractManifestData_ValidatesAppId____() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n@eiq@292417903871")
        appManifest.extractManifestData()
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId_____() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n@siq@292417903871")
        appManifest.extractManifestData()
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId______() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n@esq@292417903871")
        appManifest.extractManifestData()
    }

    @Test(expected = HengamManifestException::class)
    fun extractManifestData_ValidatesAppId_______() {
        setToken("s1o2m3t4h5i6n7g8s9o0m1e2t3h4i5n@eis@292417903871")
        appManifest.extractManifestData()
    }

    @Test
    fun extractManifestData_ValidatesAppId_________() {
        setToken("io.hengam.adminapp@rvr@292417903871")
        appManifest.extractManifestData()
    }


    /**
     * Mock Base64 to return the byteArray of the token. So `String(it)` will result the token string,
     * Token string is used as the main decoded token in AppManifest (the extracted value from Manifest).
     */
    private fun setToken(token: String) {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } returns token.toByteArray()
    }

    private fun calculateValidationLetter(chunk: String): Char {
        var letter = 0
        chunk.forEach {
            letter += it.toInt()
        }
        return ((letter % 26) + 97).toChar()
    }
}