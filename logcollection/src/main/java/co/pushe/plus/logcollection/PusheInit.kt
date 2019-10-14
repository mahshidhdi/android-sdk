package co.pushe.plus.logcollection

import android.content.Context
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.*
import co.pushe.plus.logcollection.dagger.DaggerLogCollectionComponent
import co.pushe.plus.logcollection.dagger.LogCollectionComponent
import co.pushe.plus.logcollection.dagger.LogCollectionModule
import co.pushe.plus.utils.rx.justDo

/**
 * Introduce LogCollection module to the sdk and start it's components.
 * @see LogCollectionComponent
 */
class LogCollectionInitializer : PusheComponentInitializer() {
    private lateinit var logCollectionComponent: LogCollectionComponent

    override fun preInitialize(context: Context) {
        logCollectionComponent = DaggerLogCollectionComponent.builder()
            .logCollectionModule(LogCollectionModule(context))
            .build()

        val pusheConfig = logCollectionComponent.pusheConfig()
        if (!pusheConfig.islogCollectionEnabled) {
            return
        }

        PusheInternals.registerComponent(Pushe.LOG_COLLECTION, LogCollectionComponent::class.java, logCollectionComponent)

        logCollectionComponent.logCollector().initialize()
    }

    override fun postInitialize(context: Context) {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?:  throw ComponentNotAvailableException(Pushe.CORE)

        core.pusheLifecycle().waitForWorkManagerInitialization().justDo {
            logCollectionComponent.logCollector().runDbCleaner()
        }
    }
}