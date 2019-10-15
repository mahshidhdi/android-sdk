package io.hengam.lib.notification.tasks

import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import io.hengam.lib.Hengam
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.notification.dagger.NotificationComponent
import io.reactivex.Single

class InstallationCheckTask: HengamTask() {

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        return Single.fromCallable {

            val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.NOTIFICATION)

            notifComponent.notificationAppInstaller()
                .checkIsAppInstalled(inputData.getLong(DOWNLOAD_ID, 0))
            ListenableWorker.Result.success()
        }
    }

    companion object {
        const val DOWNLOAD_ID = "download_id"
    }

    class Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = InstallationCheckTask::class
    }
}