package co.pushe.plus.analytics.tasks

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import co.pushe.plus.Pushe
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.PusheLifecycle
import co.pushe.plus.analytics.LogTag.T_ANALYTICS
import co.pushe.plus.analytics.LogTag.T_ANALYTICS_SESSION
import co.pushe.plus.analytics.dagger.AnalyticsComponent
import co.pushe.plus.analytics.session.SessionFlowManager
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.utils.assertCpuThread
import co.pushe.plus.utils.log.Plog
import io.reactivex.Single
import javax.inject.Inject

class SessionEndDetectorTask(context: Context, workerParameters: WorkerParameters)
    : PusheTask("session_end_detector", context, workerParameters) {

    @Inject lateinit var sessionFlowManager: SessionFlowManager
    @Inject lateinit var pusheLifecycle: PusheLifecycle

    override fun perform(): Single<Result> {
        assertCpuThread()
        val analyticsComponent = PusheInternals.getComponent(AnalyticsComponent::class.java)
            ?: throw ComponentNotAvailableException(Pushe.ANALYTICS)

        analyticsComponent.inject(this)

        if (!pusheLifecycle.isAppOpened) {
            sessionFlowManager.endSession()
        } else {
            Plog.warn(T_ANALYTICS, T_ANALYTICS_SESSION, "Session-end detector has been run while app is open, session will not be ended")
        }

        return Single.just(Result.success())
    }

    object Options : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = SessionEndDetectorTask::class
        override fun taskId() = "pushe_session_end_detector"
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
    }
}


