package io.hengam.lib.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.UpstreamSender
import io.hengam.lib.upstreamSenderBackoffDelay
import io.hengam.lib.upstreamSenderBackoffPolicy
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.assertCpuThread
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject

class UpstreamSenderTask(context: Context, workerParameters: WorkerParameters)
    : HengamTask("upstream_sender", context, workerParameters) {

    @Inject lateinit var postOffice: PostOffice
    @Inject lateinit var upstreamSender: UpstreamSender

    override fun perform(): Single<Result> {
        assertCpuThread()

        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.CORE)

        core.inject(this)

        return postOffice.checkInFlightMessageTimeouts()
                .doOnError { Plog.error(T_MESSAGE, it) }.onErrorComplete()
                .andThen(postOffice.checkMessageExpirations())
                .doOnError { Plog.error(T_MESSAGE, it) }.onErrorComplete()
                .andThen(upstreamSender.collectAndSendParcels())
                .map {  if (it) Result.success() else Result.retry()  }
    }

    object Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.CONNECTED
        override fun task() = UpstreamSenderTask::class
        override fun taskId() = "hengam_upstream_sender"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun backoffPolicy(): BackoffPolicy? = hengamConfig.upstreamSenderBackoffPolicy
        override fun backoffDelay(): Time? = hengamConfig.upstreamSenderBackoffDelay
    }
}


