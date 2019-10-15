package io.hengam.lib.analytics.tasks

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import io.hengam.lib.Hengam
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.analytics.LogTag.T_ANALYTICS
import io.hengam.lib.analytics.LogTag.T_ANALYTICS_SESSION
import io.hengam.lib.analytics.dagger.AnalyticsComponent
import io.hengam.lib.analytics.session.SessionFlowManager
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.task.OneTimeTaskOptions
import io.hengam.lib.internal.task.HengamTask
import io.hengam.lib.utils.assertCpuThread
import io.hengam.lib.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject

class SessionEndDetectorTask: HengamTask() {

    @Inject lateinit var sessionFlowManager: SessionFlowManager
    @Inject lateinit var hengamLifecycle: HengamLifecycle

    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        assertCpuThread()

        val analyticsComponent = HengamInternals.getComponent(AnalyticsComponent::class.java)
            ?: throw ComponentNotAvailableException(Hengam.ANALYTICS)

        analyticsComponent.inject(this)

        return if (!hengamLifecycle.isAppOpened) {
            sessionFlowManager.endSession()
                .toSingleDefault(ListenableWorker.Result.success())
                .onErrorResumeNext { Single.just(ListenableWorker.Result.retry()) }
        } else {
            Plog.warn(T_ANALYTICS, T_ANALYTICS_SESSION, "Session-end detector has been run while app is open, session will not be ended")
            Single.just(ListenableWorker.Result.success())
        }
    }

    object Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = SessionEndDetectorTask::class
        override fun taskId() = "hengam_session_end_detector"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
    }
}