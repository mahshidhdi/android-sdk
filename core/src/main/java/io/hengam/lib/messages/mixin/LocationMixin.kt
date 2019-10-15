package io.hengam.lib.messages.mixin

import android.location.Location
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.messaging.MessageMixin
import io.hengam.lib.utils.seconds
import io.reactivex.Single

class LocationMixin(private val isNested: Boolean = false) : MessageMixin() {
    private val emptyLocation: Location = Location("")

    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        return core.geoUtils()
                .getLocation(seconds(10))
                .toSingle(emptyLocation)
                .map { location ->
                    if (location == emptyLocation) {
                        emptyMap()
                    } else {
                        val locationInfo = mapOf(
                                "lat" to location.latitude,
                                "long" to location.longitude
                        )
                        if (isNested) mapOf("location" to locationInfo) else locationInfo
                    }
                }

    }
}