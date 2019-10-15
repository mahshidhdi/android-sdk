package io.hengam.lib.analytics.tasks

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_SESSION
import io.hengam.lib.analytics.dagger.AnalyticsComponent
import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.assertCpuThread
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject

class SessionEndDetectorTask(context: Context, workerParameters: WorkerParameters)
    : HengamTask("session_end_detector", context, workerParameters) {

    @Inject lateinit var sessionFlowManager: SessionFlowManager
    @Inject lateinit var hengamLifecycle: HengamLifecycle

    override fun perform(): Single<Result> {
        assertCpuThread()
        val analyticsComponent = HengamInternals.getComponent(AnalyticsComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.ANALYTICS)

        analyticsComponent.inject(this)

        if (!hengamLifecycle.isAppOpened) {
            sessionFlowManager.endSession()
        } else {
            Plog.warn(T_ANALYTICS, T_ANALYTICS_SESSION, "Session-end detector has been run while app is open, session will not be ended")
        }

        return Single.just(Result.success())
    }

    object Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = SessionEndDetectorTask::class
        override fun taskId() = "hengam_session_end_detector"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
    }
}


