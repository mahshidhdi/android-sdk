package co.pushe.plus.analytics

import android.app.Application
import android.content.Context
import co.pushe.plus.LogTag.T_INIT
import co.pushe.plus.Pushe
import co.pushe.plus.analytics.dagger.AnalyticsComponent
import co.pushe.plus.analytics.dagger.DaggerAnalyticsComponent
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheComponentInitializer
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.utils.log.Plog


class AnalyticsInitializer : PusheComponentInitializer() {
    private lateinit var analyticsComponent: AnalyticsComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_INIT, "Initializing Pushe analytics component")

        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

        analyticsComponent = DaggerAnalyticsComponent.builder()
            .coreComponent(core)
            .build()

        /* Extend Moshi */
        extendMoshi(analyticsComponent.moshi())

        /* Receive messages */
        analyticsComponent.messageDispatcher().listenForMessages()

        PusheInternals.registerComponent(Pushe.ANALYTICS, AnalyticsComponent::class.java, analyticsComponent)
        PusheInternals.registerApi(Pushe.ANALYTICS, PusheAnalytics::class.java, analyticsComponent.api())

        analyticsComponent.appLifeCycleListener().registerEndSessionListener()

        ((analyticsComponent.context().applicationContext) as Application)
            .registerActivityLifecycleCallbacks(analyticsComponent.appLifeCycleListener())

    }

    override fun postInitialize(context: Context) {
        analyticsComponent.goalProcessManager().initialize()
    }
}