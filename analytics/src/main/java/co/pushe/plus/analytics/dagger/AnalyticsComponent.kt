package co.pushe.plus.analytics.dagger

import android.content.Context
import co.pushe.plus.analytics.AppLifecycleListener
import co.pushe.plus.analytics.PusheAnalytics
import co.pushe.plus.analytics.goal.GoalProcessManager
import co.pushe.plus.analytics.messages.MessageDispatcher
import co.pushe.plus.analytics.tasks.SessionEndDetectorTask
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheComponent
import co.pushe.plus.internal.PusheMoshi
import dagger.Component

@AnalyticsScope
@Component(dependencies = [(CoreComponent::class)])
interface AnalyticsComponent : PusheComponent {
    fun context(): Context
    fun goalProcessManager(): GoalProcessManager
    fun appLifeCycleListener(): AppLifecycleListener
    fun messageDispatcher(): MessageDispatcher
    fun api(): PusheAnalytics
    fun moshi(): PusheMoshi

    fun inject(sessionEndDetectorTask: SessionEndDetectorTask)
}
