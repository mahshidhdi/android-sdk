package io.hengam.lib.datalytics.tasks

import android.content.Context
import android.location.Location
import androidx.work.*
import androidx.work.NetworkType
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.LogTags
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.datalytics.geofencePeriodicRegisterInterval
import io.hengam.lib.datalytics.isLocationCollectionEnabled
import io.hengam.lib.datalytics.locationCollectionInterval
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single

/**
 * A task which runs periodically and attempts to request for device location.
 *
 */
class LocationRequestTask: HengamTask() {

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        core.geoUtils().requestLocationUpdates(seconds(10))
        return Single.just(ListenableWorker.Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun repeatInterval(): Time = hengamConfig.locationCollectionInterval
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = LocationRequestTask::class
        override fun taskId() = "hengam_periodic_location_request"
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun flexibilityTime() = minutes(10)
    }
}

fun scheduleLocationCollection() {
    val core = HengamInternals.getComponent(CoreComponent::class.java)
        ?: throw ComponentNotAvailableException(Hengam.CORE)

    if (core.config().isLocationCollectionEnabled) {
        core.taskScheduler().schedulePeriodicTask(LocationRequestTask.Options())
    } else {
        core.taskScheduler().cancelTask(LocationRequestTask.Options())
    }
}
