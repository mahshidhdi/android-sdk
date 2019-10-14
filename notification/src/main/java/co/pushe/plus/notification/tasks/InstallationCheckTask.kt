package co.pushe.plus.notification.tasks

import android.content.Context
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.notification.dagger.NotificationComponent
import io.reactivex.Single

class InstallationCheckTask(
    context: Context,
    workerParameters: WorkerParameters
) : PusheTask("installation_check", context, workerParameters) {

    override fun perform(): Single<Result> {
        return Single.fromCallable {
            val notifComponent = PusheInternals.getComponent(NotificationComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.NOTIFICATION)
            notifComponent.notificationAppInstaller()
                    .checkIsAppInstalled(inputData.getLong(DOWNLOAD_ID, 0))
            Result.success()
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
