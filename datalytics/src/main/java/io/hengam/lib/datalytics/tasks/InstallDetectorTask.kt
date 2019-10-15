package io.hengam.lib.datalytics.tasks

import android.content.Context
import androidx.work.*
import androidx.work.NetworkType
import io.hengam.lib.Hengam
import io.hengam.lib.datalytics.InstallDetectorTaskInterval
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.datalytics.messages.upstream.AppInstallMessageBuilder
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.internal.task.PeriodicTaskOptions
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * A task which runs periodically and attempts to detect app installation on device.
 *
 */
class InstallDetectorTask
    : HengamTask() {

    @Inject
    lateinit var applicationInfoHelper: ApplicationInfoHelper
    @Inject
    lateinit var postOffice: PostOffice
    @Inject
    lateinit var hengamStorage: HengamStorage

    override fun perform(inputData:Data): Single<ListenableWorker.Result> {
        val datalyticsComponent = HengamInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.DATALYTICS)
        datalyticsComponent.inject(this)

        val lastCollectedAt = hengamStorage.getLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
        val apps = applicationInfoHelper.getInstalledApplications().filter { app -> app.installationTime != null && app.installationTime!! > lastCollectedAt }
        apps.map { app ->
            Plog.info(T_DATALYTICS, app.packageName + " installed in last " + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
            postOffice.sendMessage(AppInstallMessageBuilder.build(app))
        }
        if (apps.isEmpty()) Plog.info(T_DATALYTICS, "no app installed in last" + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
        hengamStorage.putLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
        return Single.just(ListenableWorker.Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun repeatInterval(): Time = hengamConfig.InstallDetectorTaskInterval
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = InstallDetectorTask::class
        override fun taskId() = "install_detector_task"
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun flexibilityTime() = hours(2)

    }
}
