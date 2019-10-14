package co.pushe.plus.datalytics.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.datalytics.dagger.DatalyticsComponent
import co.pushe.plus.datalytics.geofencePeriodicRegisterInterval
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.task.PeriodicTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.hours
import io.reactivex.Single

/**
 * A task which runs periodically and attempts to re-register all geofences stored in the [GeofenceManager].
 *
 * This is necessary in case a previous registration was unsuccessful or in case the registration
 * is removed by the [GeofencingClient] itself for any of the reasons defined in the link below
 *
 * https://developer.android.com/training/location/geofencing#re-register-geofences-only-when-required
 */
class GeofencePeriodicRegisterTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("geofence_periodic_register", context, workerParameters) {

    override fun perform(): Single<Result> {
        val datalyticsComponent = PusheInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.DATALYTICS)
        return datalyticsComponent.geofenceManager().ensureGeofencesAreRegistered().toSingleDefault(Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun repeatInterval(): Time = pusheConfig.geofencePeriodicRegisterInterval
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = GeofencePeriodicRegisterTask::class
        override fun taskId() = "pushe_geofence_periodic_register"
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun flexibilityTime() = hours(4)
    }
}
