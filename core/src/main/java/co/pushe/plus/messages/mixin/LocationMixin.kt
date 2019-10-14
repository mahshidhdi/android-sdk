package co.pushe.plus.messages.mixin

import android.location.Location
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.messaging.MessageMixin
import co.pushe.plus.utils.seconds
import io.reactivex.Single

class LocationMixin(private val isNested: Boolean = false) : MessageMixin() {
    private val emptyLocation: Location = Location("")

    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

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