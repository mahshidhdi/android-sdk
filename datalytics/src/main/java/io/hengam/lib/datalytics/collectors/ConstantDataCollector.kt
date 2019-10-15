package io.hengam.lib.datalytics.collectors

import io.hengam.lib.datalytics.messages.upstream.ConstantDataMessage
import io.hengam.lib.messaging.SendableUpstreamMessage
import io.hengam.lib.utils.DeviceInfoHelper
import io.reactivex.Observable
import javax.inject.Inject

class ConstantDataCollector @Inject constructor(
        private val deviceInfoHelper: DeviceInfoHelper
): Collector(){

    override fun collect(): Observable<SendableUpstreamMessage> = Observable.just(getConstantData())

    fun getConstantData(): ConstantDataMessage {
        return ConstantDataMessage(
                deviceInfoHelper.getDeviceBrand(),
                deviceInfoHelper.getDeviceModel(),
                "${deviceInfoHelper.getScreenSize().x}x${deviceInfoHelper.getScreenSize().y}"
        )
    }
}