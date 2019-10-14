package co.pushe.plus.messages.mixin

import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.messaging.MessageMixin
import io.reactivex.Single

class WifiInfoMixin(private val isNested: Boolean = false) : MessageMixin() {

    @SuppressWarnings("MissingPermission")
    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

        val wifiInfo = core.networkInfoHelper().getWifiNetwork()
        return if (wifiInfo != null) {
            val info = mapOf("mac" to wifiInfo.mac, "ssid" to wifiInfo.ssid)
            Single.just(if (isNested) mapOf("wifi" to info) else info)
        } else {
            Single.just(emptyMap())
        }
    }
}