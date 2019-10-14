package co.pushe.plus.datalytics.collectors

import co.pushe.plus.datalytics.messages.upstream.ConstantDataMessage
import co.pushe.plus.messaging.SendableUpstreamMessage
import co.pushe.plus.utils.DeviceInfoHelper
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