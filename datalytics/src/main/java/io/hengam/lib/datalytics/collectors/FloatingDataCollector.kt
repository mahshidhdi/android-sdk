package io.hengam.lib.datalytics.collectors

import android.location.Location
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.messages.upstream.FloatingDataMessage
import io.hengam.lib.datalytics.utils.NetworkUtils
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.messaging.SendableUpstreamMessage
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.reactivex.Observable
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class FloatingDataCollector @Inject constructor(
        private val networkInfoHelper: NetworkInfoHelper,
        private val networkUtils: NetworkUtils,
        private val geoUtils: GeoUtils,
        private val applicationInfoHelper: ApplicationInfoHelper
): Collector() {
    private val emptyLocation: Location = Location("")

    override fun collect(): Observable<out SendableUpstreamMessage> {
        val networkType = networkInfoHelper.getNetworkType()
        return Single.zip(listOf(geoUtils.getLocation(seconds(10)).toSingle(emptyLocation),
                networkUtils.getPublicIp()
                        .subscribeOn(ioThread())
                        .observeOn(cpuThread())
                        .timeout(10, TimeUnit.SECONDS, cpuThread())
                        .doOnError { Plog.warn(T_DATALYTICS, it) }
                        .onErrorComplete()
                        .toSingle(NetworkUtils.PublicIpInfo("")))

        )  { results ->
            val location = results[0] as? Location
            val publicIpInfo = results[1] as? NetworkUtils.PublicIpInfo
            FloatingDataMessage(
                    lat = location?.takeIf { it != emptyLocation }?.latitude?.toString(),
                    long = location?.takeIf { it != emptyLocation }?.longitude?.toString(),
                    ip = publicIpInfo?.ip.takeIf { it != "" },
                    networkType = networkType,
                    wifiNetworkSSID = (networkType as? NetworkType.Wifi)?.info?.ssid,
                    wifiNetworkSignal = (networkType as? NetworkType.Wifi)?.info?.signal,
                    wifiMac = (networkType as? NetworkType.Wifi)?.info?.mac,
                    mobileNetworkName = (networkType as? NetworkType.Mobile)?.dataNetwork,
                    appStandByBucket = applicationInfoHelper.getAppStandByBucket()
            )
        }.toObservable()
    }
}


