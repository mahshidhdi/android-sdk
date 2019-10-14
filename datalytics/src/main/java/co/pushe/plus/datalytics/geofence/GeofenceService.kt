package co.pushe.plus.datalytics.geofence

import android.app.IntentService
import android.content.Intent
import co.pushe.plus.Pushe
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.LogTags.T_GEOFENCE
import co.pushe.plus.datalytics.dagger.DatalyticsComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.log.Plog
import com.google.android.gms.location.GeofencingEvent


class GeofenceService : IntentService("GeofenceIntentService") {
    override fun onHandleIntent(intent: Intent?) {
        try {
            cpuThread {
                try {
                    val datalyticsComponent = PusheInternals.getComponent(DatalyticsComponent::class.java)
                            ?: throw ComponentNotAvailableException(Pushe.DATALYTICS)
                    val geofencingEvent = GeofencingEvent.fromIntent(intent)
                    if (geofencingEvent.hasError()) {
                        Plog.warn(T_DATALYTICS, T_GEOFENCE, "Error received in geofence service: ${geofencingEvent.errorCode}")
                    } else {
                        geofencingEvent.triggeringGeofences.forEach { event ->
                            datalyticsComponent.geofenceManager().onGeofenceTriggered(event.requestId)
                        }
                    }
                } catch (ex: Exception) {
                    Plog.error(T_DATALYTICS, T_GEOFENCE, ex)
                }
            }
        } catch (ex: Throwable) {
            Plog.error(T_DATALYTICS, T_GEOFENCE, ex)
        }
    }
}
