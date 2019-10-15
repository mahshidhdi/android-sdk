package io.hengam.lib

import android.os.Bundle
import android.util.Base64
import android.util.Log
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamException
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.log.LogLevel
import java.lang.StringBuilder
import javax.inject.Inject

/**
 * A singleton which holds values extracted from the AndroidManifest.xml file including the
 * Application Token and Fcm Sender Id.
 *
 * The [extractManifestData] method must be called for the values to become available.
 *
 * Note: This class must be singleton since it holds global state
 */
@CoreScope
class AppManifest @Inject constructor(
        private val applicationInfoHelper: ApplicationInfoHelper
) {
    lateinit var appId: String
    var fcmSenderId: String? = null
    var validator: String? = null
    var disableAdvertisementId: Boolean = false

    var logLevel: LogLevel? = null
    var logDataEnabled: Boolean? = null
    var logTagsEnabled: Boolean? = null



    fun extractManifestData() {
        val bundle = applicationInfoHelper.getManifestMetaData()
        val encodedToken = bundle?.getString(MANIFEST_KEY_HENGAM_TOKEN, null)
        if (encodedToken == null) {
            Log.w("Hengam", "Unable to find hengam_token in application manifest")
            throw HengamManifestException("Unable to find hengam_token in application manifest")
        } else if (encodedToken.isBlank()) {
            Log.w("Hengam", "Invalid hengam_token provided in application manifest")
            throw HengamManifestException("Invalid hengam_token provided in application manifest")
        }
        val hengamToken = String(Base64.decode(encodedToken, Base64.NO_WRAP))

        if (hengamToken.isBlank()) {
            Log.w("Hengam", "Invalid hengam_token provided in application manifest")
            throw HengamManifestException("Invalid hengam_token provided in application manifest")
        }

        val parts = hengamToken.split("#", "-", "@")
        println(parts)
        if (parts.size < 3 ||
                !parts[0].matches("^[a-zA-Z0-9.]+$".toRegex()) ||
                !parts[1].matches("^[a-z][a-z][a-z]$".toRegex()) ||
                !parts[2].matches("^[0-9]+$".toRegex())) {
            throw HengamManifestException("Invalid hengam_token provided in application manifest")
        }

        appId = parts[0]
        validator = parts[1]
        fcmSenderId = parts[2]

        validateHengamPlusAppId()

        disableAdvertisementId = readBooleanValue(bundle, MANIFEST_KEY_DISABLE_ADVERTISEMENT_ID) ?: false

        logLevel = when(bundle.getString(MANIFEST_KEY_LOG_LEVEL, "").toLowerCase()) {
            "trace" -> LogLevel.TRACE
            "debug" -> LogLevel.DEBUG
            "info" -> LogLevel.INFO
            "warn" -> LogLevel.WARN
            "error" -> LogLevel.ERROR
            "wtf" -> LogLevel.WTF
            else -> null
        }

        logDataEnabled = readBooleanValue(bundle, MANIFEST_KEY_LOG_DATA_ENABLED)
        logTagsEnabled = readBooleanValue(bundle, MANIFEST_KEY_LOG_TAGS_ENABLED)
    }

    private fun readBooleanValue(bundle: Bundle, key: String): Boolean? {
        val value = bundle.get(key)
        val error = HengamManifestException("Invalid value for key '$key' in manifest, should be either 'true' or 'false'")
        return when (value) {
            is Boolean -> value
            is String ->
                when (value.toLowerCase()) {
                    "true", "yes", "1" -> true
                    "false", "no", "0" -> false
                    else -> throw error
                }
            is Int ->
                when (value) {
                    1 -> true
                    0 -> false
                    else -> throw error
                }
            null -> null
            else -> throw error
        }
    }

    private fun validateHengamPlusAppId() {

        val validationCharCount: Int = (appId.length) / APP_ID_VALIDATORS_COUNT
        val appIdValidator = StringBuilder("")

        var chunkValidator: Int
        for (validationIndex in 0 until APP_ID_VALIDATORS_COUNT) {
            chunkValidator = 0
            val firstIndex = validationIndex * validationCharCount
            val lastIndex =
                if (validationIndex == APP_ID_VALIDATORS_COUNT - 1) appId.length
                else ((validationIndex + 1) * validationCharCount)

            appId.subSequence(firstIndex, lastIndex).forEach { char ->
                chunkValidator += char.toByte()
            }

            appIdValidator.append(((chunkValidator % ALPHABET_COUNT) + FIRST_LETTER_CODE).toChar())
        }

        if (appIdValidator.toString() != validator) {
            throw HengamManifestException("Provided token in the application manifest does not contain a valid appId")
        }
    }

    companion object {
        const val MANIFEST_KEY_HENGAM_TOKEN = "hengam_token"
        const val MANIFEST_KEY_DISABLE_ADVERTISEMENT_ID = "hengam_disable_advertisement_id"
        const val MANIFEST_KEY_LOG_LEVEL = "hengam_log_level"
        const val MANIFEST_KEY_LOG_DATA_ENABLED = "hengam_log_data_enabled"
        const val MANIFEST_KEY_LOG_TAGS_ENABLED = "hengam_log_tags_enabled"

        const val APP_ID_VALIDATORS_COUNT = 3
        const val ALPHABET_COUNT = 26
        const val FIRST_LETTER_CODE = 97
    }
}

class HengamManifestException(message: String): HengamException(message)