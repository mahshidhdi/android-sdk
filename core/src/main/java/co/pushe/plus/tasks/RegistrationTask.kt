package co.pushe.plus.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.*
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.messaging.fcm.FcmTokenStore
import co.pushe.plus.messaging.fcm.TokenState
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.assertCpuThread
import io.reactivex.Single
import javax.inject.Inject

class RegistrationTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("registration", context, workerParameters) {

    @Inject lateinit var fcmTokenStore: FcmTokenStore
    @Inject lateinit var registrationManager: RegistrationManager

    override fun perform(): Single<Result> {
        assertCpuThread()

        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

        core.inject(this)

        val registrationCause = inputData.getString(DATA_REGISTRATION_CAUSE) ?: ""
        return fcmTokenStore.revalidateTokenState()
            .map {
                if (it == TokenState.SYNCED) {
                    Result.success()
                } else {
                    registrationManager.performRegistration(registrationCause)
                    Result.retry()
                }
            }
    }

    companion object {
        const val DATA_REGISTRATION_CAUSE = "cause"
    }

    class Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = RegistrationTask::class
        override fun taskId() = "pushe_registration"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun backoffPolicy(): BackoffPolicy? = pusheConfig.registrationBackoffPolicy
        override fun backoffDelay(): Time? = pusheConfig.registrationBackoffDelay
    }
}


