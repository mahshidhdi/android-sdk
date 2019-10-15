package io.hengam.lib.analytics

import android.app.Application
import android.content.Context
import io.hengam.lib.LogTag.T_INIT
import io.hengam.lib.Hengam
import io.hengam.lib.analytics.dagger.AnalyticsComponent
import io.hengam.lib.analytics.dagger.DaggerAnalyticsComponent
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamComponentInitializer
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.utils.log.Plog


class AnalyticsInitializer : HengamComponentInitializer() {
    private lateinit var analyticsComponent: AnalyticsComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_INIT, "Initializing Hengam analytics component")

        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        analyticsComponent = DaggerAnalyticsComponent.builder()
            .coreComponent(core)
            .build()

        /* Extend Moshi */
        extendMoshi(analyticsComponent.moshi())

        /* Receive messages */
        analyticsComponent.messageDispatcher().listenForMessages()

        HengamInternals.registerComponent(Hengam.ANALYTICS, AnalyticsComponent::class.java, analyticsComponent)
        HengamInternals.registerApi(Hengam.ANALYTICS, HengamAnalytics::class.java, analyticsComponent.api())

        analyticsComponent.appLifeCycleListener().registerEndSessionListener()

        ((analyticsComponent.context().applicationContext) as Application)
            .registerActivityLifecycleCallbacks(analyticsComponent.appLifeCycleListener())

    }

    override fun postInitialize(context: Context) {
        analyticsComponent.goalProcessManager().initialize()
    }
}