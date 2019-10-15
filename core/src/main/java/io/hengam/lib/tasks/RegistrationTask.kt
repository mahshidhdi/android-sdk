package io.hengam.lib.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.hengam.lib.*
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.messaging.fcm.FcmTokenStore
import io.hengam.lib.messaging.fcm.TokenState
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.assertCpuThread
import io.reactivex.Single
import javax.inject.Inject

class RegistrationTask(context: Context, workerParameters: WorkerParameters)
    : HengamTask("registration", context, workerParameters) {

    @Inject lateinit var fcmTokenStore: FcmTokenStore
    @Inject lateinit var registrationManager: RegistrationManager

    override fun perform(): Single<Result> {
        assertCpuThread()

        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

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
        override fun taskId() = "hengam_registration"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun backoffPolicy(): BackoffPolicy? = hengamConfig.registrationBackoffPolicy
        override fun backoffDelay(): Time? = hengamConfig.registrationBackoffDelay
    }
}


