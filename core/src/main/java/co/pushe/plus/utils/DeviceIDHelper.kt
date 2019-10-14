package co.pushe.plus.utils

import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import co.pushe.plus.AppManifest
import co.pushe.plus.LogTag.T_UTILS
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import java.io.IOException
import java.util.*
import javax.inject.Inject

@CoreScope
class DeviceIDHelper @Inject constructor(
        private val context: Context,
        private val appManifest: AppManifest
) {
    val advertisementId: String by lazy { retrieveAdvertisementId() }
    val androidId: String by lazy {
        try {
            Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID)
        } catch (ex: Exception) {
            Plog.error("Error obtaining Android Id", ex)
            "unknown"
        }
    }
    val pusheId: String by lazy { retrievePusheId() }

    private fun retrieveAdvertisementId(): String {
        if (appManifest.disableAdvertisementId) {
            return ""
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Plog.error(T_UTILS, "Attempted to retrieve Advertising Id in main thread")
            return ""
        }

        return try {
            AdvertisingIdClient.getAdvertisingIdInfo(context).id ?: ""
        } catch (e: Exception) {
            when (e) {
                is ClassNotFoundException ->
                    Plog.warn.message("Error trying to retrieve advertisement id. Probably missing " +
                            "\"com.google.android.gms:play-services-ads\" dependency in gradle file.")
                            .useLogCatLevel(LogLevel.ERROR)
                            .log()
                is IOException, is GooglePlayServicesNotAvailableException, is GooglePlayServicesRepairableException ->
                    Plog.warn.message("Error trying to retrieve advertisement id.")
                            .withError(e)
                            .useLogCatLevel(LogLevel.ERROR)
                            .log()
                else -> Plog.error("Unknown error occurred while retrieving advertising id", e)
            }
            ""
        }
    }

    @SuppressLint("MissingPermission")
    private fun retrievePusheId(): String {
        val sharedPreferences = context.getSharedPreferences("device_id.xml", Context.MODE_PRIVATE)
        val storedPusheId = sharedPreferences.getString("device_id", "")
        val uuid = try {
             if (storedPusheId != null && storedPusheId.isNotEmpty()) {
                UUID.fromString(storedPusheId)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val seed = advertisementId
                if (seed.isNotEmpty()) {
                    UUID.nameUUIDFromBytes(seed.toByteArray(charset("utf8")))
                } else {
                    UUID.randomUUID()
                }
            } else if (androidId != "9774d56d682e549c") {
                UUID.nameUUIDFromBytes(androidId.toByteArray(charset("utf8")));
            } else if (PermissionChecker.hasPermission(context, READ_PHONE_STATE)) {
                val deviceId = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).deviceId
                if (deviceId != null) {
                    UUID.nameUUIDFromBytes(deviceId.toByteArray(charset("utf8")))
                } else {
                    UUID.randomUUID();
                }
            } else {
                UUID.randomUUID();
            }
        } catch (ex: Exception) {
            UUID.randomUUID();
        }
        val uuidStr = uuid.toString()
        return if (uuidStr.length > 16) {
            "pid_" + uuidStr.substring(4, 16)
        } else {
            "pid_" + uuidStr.substring(0, 12)
        }
    }

    companion object {
        const val PUSHE_ID_PREFIX = "pid_"
        const val PUSHE_ID_SIZE = 10
    }
}

