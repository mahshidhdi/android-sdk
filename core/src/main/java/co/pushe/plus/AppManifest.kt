package co.pushe.plus

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheException
import co.pushe.plus.utils.log.LogLevel
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
        private val context: Context
) {
    lateinit var appId: String
    var fcmSenderId: String? = null
    var disableAdvertisementId: Boolean = false

    var logLevel: LogLevel? = null
    var logDataEnabled: Boolean? = null
    var logTagsEnabled: Boolean? = null

    fun extractManifestData() {
        val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val bundle = ai.metaData

        val pusheToken = bundle.getString(MANIFEST_KEY_PUSHE_TOKEN, null)

        if (pusheToken == null) {
            Log.w("Pushe", "Unable to find pushe_token in application manifest")
            throw PusheManifestException("Unable to find pushe_token in application manifest")
        } else if (pusheToken.isNullOrBlank()) {
            Log.w("Pushe", "Invalid pushe_token provided in application manifest")
            throw PusheManifestException("Invalid pushe_token provided in application manifest")
        }

        val parts = pusheToken.split("#", "-", "@")

        if (parts.size < 2 ||
                !parts[0].matches("^[a-zA-Z0-9.]+$".toRegex()) ||
                !parts[1].matches("^[0-9]+$".toRegex())) {
            throw PusheManifestException("Invalid pushe_token provided in application manifest")
        }

        appId = parts[0]
        fcmSenderId = parts[1]

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
        val error = PusheManifestException("Invalid value for key '$key' in manifest, should be either 'true' or 'false'")
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

    companion object {
        const val MANIFEST_KEY_PUSHE_TOKEN = "pushe_token"
        const val MANIFEST_KEY_DISABLE_ADVERTISEMENT_ID = "pushe_disable_advertisement_id"
        const val MANIFEST_KEY_LOG_LEVEL = "pushe_log_level"
        const val MANIFEST_KEY_LOG_DATA_ENABLED = "pushe_log_data_enabled"
        const val MANIFEST_KEY_LOG_TAGS_ENABLED = "pushe_log_tags_enabled"

    }
}

class PusheManifestException(message: String): PusheException(message)