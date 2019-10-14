package co.pushe.plus.messages.mixin

import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.messaging.MessageMixin
import co.pushe.plus.utils.NetworkType
import io.reactivex.Single

class NetworkInfoMixin(private val isNested: Boolean = false) : MessageMixin() {
    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

        val data = when (val networkType = core.networkInfoHelper().getNetworkType()) {
            is NetworkType.Wifi -> mapOf(
                    "type" to "wifi",
                    "name" to networkType.info?.ssid
            )
            is NetworkType.Mobile -> mapOf(
                    "type" to "mobile",
                    "name" to networkType.dataNetwork,
                    "operator" to networkType.operator
            )
            else -> emptyMap()
        }

        return Single.just(if (isNested) mapOf ("network" to data) else data)

    }
}