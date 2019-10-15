package io.hengam.lib.datalytics.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.hengam.lib.Hengam
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.datalytics.geofencePeriodicRegisterInterval
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.hours
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
    : HengamTask("geofence_periodic_register", context, workerParameters) {

    override fun perform(): Single<Result> {
        val datalyticsComponent = HengamInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.DATALYTICS)
        return datalyticsComponent.geofenceManager().ensureGeofencesAreRegistered().toSingleDefault(Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun repeatInterval(): Time = hengamConfig.geofencePeriodicRegisterInterval
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = GeofencePeriodicRegisterTask::class
        override fun taskId() = "hengam_geofence_periodic_register"
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun flexibilityTime() = hours(4)
    }
}
