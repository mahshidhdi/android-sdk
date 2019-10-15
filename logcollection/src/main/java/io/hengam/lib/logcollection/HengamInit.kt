package io.hengam.lib.logcollection

import android.content.Context
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.*
import io.hengam.lib.logcollection.dagger.DaggerLogCollectionComponent
import io.hengam.lib.logcollection.dagger.LogCollectionComponent
import io.hengam.lib.logcollection.dagger.LogCollectionModule
import io.hengam.lib.utils.rx.justDo

/**
 * Introduce LogCollection module to the sdk and start it's components.
 * @see LogCollectionComponent
 */
class LogCollectionInitializer : HengamComponentInitializer() {
    private lateinit var logCollectionComponent: LogCollectionComponent

    override fun preInitialize(context: Context) {
        logCollectionComponent = DaggerLogCollectionComponent.builder()
            .logCollectionModule(LogCollectionModule(context))
            .build()

        val hengamConfig = logCollectionComponent.hengamConfig()
        if (!hengamConfig.islogCollectionEnabled) {
            return
        }

        HengamInternals.registerComponent(Hengam.LOG_COLLECTION, LogCollectionComponent::class.java, logCollectionComponent)

        logCollectionComponent.logCollector().initialize()
    }

    override fun postInitialize(context: Context) {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?:  throw ComponentNotAvailableException(Hengam.CORE)

        core.hengamLifecycle().waitForWorkManagerInitialization().justDo {
            logCollectionComponent.logCollector().runDbCleaner()
        }
    }
}