package io.hengam.lib.tasks

import androidx.work.*
import io.hengam.lib.Hengam
import io.hengam.lib.RegistrationManager
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.messaging.fcm.FcmTokenStore
import io.hengam.lib.messaging.fcm.TokenState
import io.hengam.lib.registrationBackoffDelay
import io.hengam.lib.registrationBackoffPolicy
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.assertCpuThread
import io.reactivex.Single
import javax.inject.Inject

class RegistrationTask: HengamTask() {

    @Inject lateinit var fcmTokenStore: FcmTokenStore
    @Inject lateinit var registrationManager: RegistrationManager

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        assertCpuThread()

        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.CORE)

        core.inject(this)

        val registrationCause = inputData.getString(DATA_REGISTRATION_CAUSE) ?: ""
        return fcmTokenStore.revalidateTokenState()
            .map {
                if (it == TokenState.SYNCED) {
                    ListenableWorker.Result.success()
                } else {
                    registrationManager.performRegistration(registrationCause)
                    ListenableWorker.Result.retry()
                }
            }
    }

    class Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = RegistrationTask::class
        override fun taskId() = "hengam_registration"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun backoffPolicy(): BackoffPolicy? = hengamConfig.registrationBackoffPolicy
        override fun backoffDelay(): Time? = hengamConfig.registrationBackoffDelay
    }

    companion object {
        const val DATA_REGISTRATION_CAUSE = "cause"
    }
}


