package io.hengam.lib.messages.mixin

import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.messaging.MessageMixin
import io.reactivex.Single

class WifiInfoMixin(private val isNested: Boolean = false) : MessageMixin() {

    @SuppressWarnings("MissingPermission")
    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        val wifiInfo = core.networkInfoHelper().getWifiNetwork()
        return if (wifiInfo != null) {
            val info = mapOf("mac" to wifiInfo.mac, "ssid" to wifiInfo.ssid)
            Single.just(if (isNested) mapOf("wifi" to info) else info)
        } else {
            Single.just(emptyMap())
        }
    }
}