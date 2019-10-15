package io.hengam.lib.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import io.hengam.lib.utils.PermissionChecker.ACCESS_COARSE_LOCATION
import io.hengam.lib.utils.PermissionChecker.ACCESS_FINE_LOCATION
import io.hengam.lib.utils.PermissionChecker.ACCESS_NETWORK_STATE
import io.hengam.lib.utils.PermissionChecker.ACCESS_WIFI_STATE
import io.hengam.lib.utils.PermissionChecker.hasPermission
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.reactivex.Observable
import javax.inject.Inject

class NetworkInfoHelper @Inject constructor(
        private val context: Context,
        private val telephonyManager: TelephonyManager?
) {
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager?
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager?
    }

    @SuppressLint("MissingPermission")
    fun getNetworkType(): NetworkType {
        try {
            if (!hasPermission(context, ACCESS_NETWORK_STATE)) {
                return NetworkType.Unknown
            }
            // TODO : For API >= 21 use connMgr.allNetworks
            val wifi = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            val mobile = connectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
            return when {
                wifi?.isConnectedOrConnecting == true -> NetworkType.Wifi(getWifiNetwork())
                mobile?.isConnectedOrConnecting == true -> getMobileNetwork()
                else -> NetworkType.None
            }
        } catch (e: Exception) {
            Plog.error("Failed to get network type in NetworkInfoHelper", e)
            return NetworkType.Unknown
        }
    }

    /***
     * Get mobile data network name such as 3G, 2G
     *
     * @return mobile network type
     */
    private fun getMobileNetwork(): NetworkType.Mobile {
        val networkType = telephonyManager?.networkType

        val dataNetworkName = when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "gprs"
            TelephonyManager.NETWORK_TYPE_EDGE -> "edge"
            TelephonyManager.NETWORK_TYPE_UMTS -> "umts"
            TelephonyManager.NETWORK_TYPE_CDMA -> "cdma"
            TelephonyManager.NETWORK_TYPE_EVDO_0 -> "evdo 0"
            TelephonyManager.NETWORK_TYPE_EVDO_A -> "evdo a"
            TelephonyManager.NETWORK_TYPE_1xRTT -> "1xrtt"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "hsdpa"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "hsupa"
            TelephonyManager.NETWORK_TYPE_HSPA -> "hspa"
            TelephonyManager.NETWORK_TYPE_IDEN -> "iden"
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "evdo b"
            TelephonyManager.NETWORK_TYPE_LTE -> "lte"
            TelephonyManager.NETWORK_TYPE_EHRPD -> "ehrpd"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "hspap"
            else -> "data"
        }

        return NetworkType.Mobile(dataNetworkName, telephonyManager?.networkOperatorName)
    }

    @SuppressLint("MissingPermission")
    fun getWifiNetwork(): WifiDetails? {
        return if (hasPermission(context, ACCESS_WIFI_STATE)) {
            wifiManager?.connectionInfo?.let { createWifiDetails(it) }
        } else {
            null
        }
    }


    /**
     * Scan for wifi networks and return an [Observable] which will emit [WifiDetails] objects for
     * each wifi network found.
     * If sufficient permissions are not available the Observable will not emit any items.
     *
     * The `ACCESS_WIFI_STATE` is needed to access wifi information. On Android version 23 and above
     * location permissions (either `ACCESS_COARSE_LOCATION` or `ACCESS_FINE_LOCATION`) are also
     * required
     */
    @SuppressLint("MissingPermission")
    fun scanWifiNetworks(): Observable<WifiDetails> {
        val isLocationPermitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            hasPermission(context, ACCESS_COARSE_LOCATION) || hasPermission(context, ACCESS_FINE_LOCATION)

        if (hasPermission(context, ACCESS_WIFI_STATE) && isLocationPermitted) {
            val emptyDetails = WifiDetails("empty", "empty", 0)
            return Observable.fromIterable(wifiManager?.scanResults ?: emptyList())
                    .map { createWifiDetails(it.SSID, it.BSSID, it.level) ?: emptyDetails }
                    .filter { it != emptyDetails }
        }
        return Observable.empty()
    }

    private fun createWifiDetails(ssid: String, bssid: String, rssi: Int): WifiDetails? {
        if (ssid.isBlank() || ssid == "<unknown ssid>") {
            return null
        }
        val normalizedName = if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid.substring(1, ssid.length - 1)
        } else {
            ssid
        }
        return WifiDetails(
                ssid = normalizedName,
                mac = bssid,
                signal = rssi
        )
    }

    private fun createWifiDetails(wifiInfo: WifiInfo): WifiDetails? {
        return createWifiDetails(wifiInfo.ssid ?: "", wifiInfo.bssid ?: "", wifiInfo.rssi)
    }
}

sealed class NetworkType(val name: String) {
    class Wifi(val info: WifiDetails?) : NetworkType("wifi")
    class Mobile(val dataNetwork: String, val operator: String?) : NetworkType("mobile")
    object None : NetworkType("none")
    object Unknown : NetworkType("unknown")

    class Adapter {
        @ToJson
        fun toJson(networkType: NetworkType) = networkType.name

        @FromJson
        fun fromJson(json: String): NetworkType = throw NotImplementedError("De-serializing NetworkType is not supported")
    }
}

class WifiDetails(
        val ssid: String,
        val mac: String,
        val signal: Int
)