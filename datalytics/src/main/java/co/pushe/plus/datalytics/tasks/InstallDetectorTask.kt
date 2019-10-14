package co.pushe.plus.datalytics.tasks

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.datalytics.InstallDetectorTaskInterval
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.dagger.DatalyticsComponent
import co.pushe.plus.datalytics.messages.upstream.AppInstallMessageBuilder
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.internal.task.PeriodicTaskOptions
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * A task which runs periodically and attempts to detect app installation on device.
 *
 */
class InstallDetectorTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("install_detector_task", context, workerParameters) {

    @Inject
    lateinit var applicationInfoHelper: ApplicationInfoHelper
    @Inject
    lateinit var postOffice: PostOffice
    @Inject
    lateinit var pusheStorage: PusheStorage

    override fun perform(): Single<Result> {
        val datalyticsComponent = PusheInternals.getComponent(DatalyticsComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.DATALYTICS)
        datalyticsComponent.inject(this)

        val lastCollectedAt = pusheStorage.getLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
        val apps = applicationInfoHelper.getInstalledApplications().filter { app -> app.installationTime != null && app.installationTime!! > lastCollectedAt }
        apps.map { app ->
            Plog.info(T_DATALYTICS, app.packageName + " installed in last " + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
            postOffice.sendMessage(AppInstallMessageBuilder.build(app))
        }
        if (apps.isEmpty()) Plog.info(T_DATALYTICS, "no app installed in last" + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
        pusheStorage.putLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
        return Single.just(Result.success())
    }

    class Options : PeriodicTaskOptions() {
        override fun repeatInterval(): Time = pusheConfig.InstallDetectorTaskInterval
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = InstallDetectorTask::class
        override fun taskId() = "install_detector_task"
        override fun existingWorkPolicy() = ExistingPeriodicWorkPolicy.KEEP
        override fun flexibilityTime() = hours(2)

    }
}
