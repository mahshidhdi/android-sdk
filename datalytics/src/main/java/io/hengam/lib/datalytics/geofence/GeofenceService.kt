package io.hengam.lib.datalytics.geofence

import android.app.IntentService
import android.content.Intent
import io.hengam.lib.Hengam
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.LogTags.T_GEOFENCE
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.log.Plog
import com.google.android.gms.location.GeofencingEvent


class GeofenceService : IntentService("GeofenceIntentService") {
    override fun onHandleIntent(intent: Intent?) {
        try {
            cpuThread {
                try {
                    val datalyticsComponent = HengamInternals.getComponent(DatalyticsComponent::class.java)
                            ?: throw ComponentNotAvailableException(Hengam.DATALYTICS)
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
