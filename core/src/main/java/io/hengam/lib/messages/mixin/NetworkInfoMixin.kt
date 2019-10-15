package io.hengam.lib.messages.mixin

import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.messaging.MessageMixin
import io.hengam.lib.utils.NetworkType
import io.reactivex.Single

class NetworkInfoMixin(private val isNested: Boolean = false) : MessageMixin() {
    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

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