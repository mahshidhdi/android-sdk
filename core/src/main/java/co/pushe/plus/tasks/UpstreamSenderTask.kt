package co.pushe.plus.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.LogTag.T_MESSAGE
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.UpstreamSender
import co.pushe.plus.upstreamSenderBackoffDelay
import co.pushe.plus.upstreamSenderBackoffPolicy
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.assertCpuThread
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject

class UpstreamSenderTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("upstream_sender", context, workerParameters) {

    @Inject lateinit var postOffice: PostOffice
    @Inject lateinit var upstreamSender: UpstreamSender

    override fun perform(): Single<Result> {
        assertCpuThread()

        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?: throw ComponentNotAvailableException(Pushe.CORE)

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
        override fun taskId() = "pushe_upstream_sender"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun backoffPolicy(): BackoffPolicy? = pusheConfig.upstreamSenderBackoffPolicy
        override fun backoffDelay(): Time? = pusheConfig.upstreamSenderBackoffDelay
    }
}


