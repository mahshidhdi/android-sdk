package io.hengam.lib.analytics.dagger

import android.content.Context
import io.hengam.lib.analytics.AppLifecycleListener
import io.hengam.lib.analytics.HengamAnalytics
import io.hengam.lib.analytics.goal.GoalProcessManager
import io.hengam.lib.analytics.messages.MessageDispatcher
import io.hengam.lib.analytics.tasks.SessionEndDetectorTask
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamComponent
import io.hengam.lib.internal.HengamMoshi
import dagger.Component

@AnalyticsScope
@Component(dependencies = [(CoreComponent::class)])
interface AnalyticsComponent : HengamComponent {
    fun context(): Context
    fun goalProcessManager(): GoalProcessManager
    fun appLifeCycleListener(): AppLifecycleListener
    fun messageDispatcher(): MessageDispatcher
    fun api(): HengamAnalytics
    fun moshi(): HengamMoshi

    fun inject(sessionEndDetectorTask: SessionEndDetectorTask)
}
