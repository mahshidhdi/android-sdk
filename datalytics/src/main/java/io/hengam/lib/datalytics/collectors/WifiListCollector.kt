package io.hengam.lib.datalytics.collectors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.messages.upstream.WifiInfoMessage
import io.hengam.lib.messaging.SendableUpstreamMessage
import io.hengam.lib.utils.GeoUtils
import io.hengam.lib.utils.NetworkInfoHelper
import io.hengam.lib.utils.PermissionChecker.ACCESS_COARSE_LOCATION
import io.hengam.lib.utils.PermissionChecker.ACCESS_FINE_LOCATION
import io.hengam.lib.utils.PermissionChecker.ACCESS_WIFI_STATE
import io.hengam.lib.utils.PermissionChecker.hasPermission
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.reactivex.Observable
import io.reactivex.Single
import javax.inject.Inject

class WifiListCollector @Inject constructor(
        private val context: Context,
        private val geoUtils: GeoUtils,
        private val networkInfoHelper: NetworkInfoHelper
) : Collector() {
    private val emptyLocation = Location("")

    override fun collect(): Observable<out SendableUpstreamMessage> = getWifiList()

    /**
     * Get an observable that emits the wifi list when their location is provided.
     * @see GeoUtils
     */
    @SuppressLint("MissingPermission")
    fun getWifiList(): Observable<WifiInfoMessage> {
        if (!hasPermission(context, ACCESS_WIFI_STATE)) {
            Plog.warn.message("Not collecting wifi info due to lack of WifiState permissions").withTag(T_DATALYTICS).useLogCatLevel(LogLevel.DEBUG).log()
            return Observable.empty<WifiInfoMessage>()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !(hasPermission(context, ACCESS_COARSE_LOCATION) || hasPermission(context, ACCESS_FINE_LOCATION))) {
            Plog.warn.message("Wifi data cannot be collected due to lack of location permissions").withTag(T_DATALYTICS).useLogCatLevel(LogLevel.DEBUG).log()
        }

        return getLocation()
                .flatMapObservable { location ->
                    networkInfoHelper.scanWifiNetworks()
                            .map {
                                WifiInfoMessage(
                                        wifiSSID = it.ssid,
                                        wifiMac = it.mac,
                                        wifiSignal = it.signal,
                                        wifiLat = location.takeIf { loc -> loc != emptyLocation }?.latitude?.toString(),
                                        wifiLng = location.takeIf { loc -> loc != emptyLocation }?.longitude?.toString()
                                )
                            }
                }
    }

    private fun getLocation(): Single<Location> {
        return geoUtils.getLocation().toSingle(emptyLocation)
    }

}