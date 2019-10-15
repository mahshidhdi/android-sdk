package io.hengam.lib.logcollection

import android.content.Context
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.*
import io.hengam.lib.logcollection.dagger.DaggerLogCollectionComponent
import io.hengam.lib.logcollection.dagger.LogCollectionComponent
import io.hengam.lib.logcollection.dagger.LogCollectionModule
import io.hengam.lib.utils.log.Plog
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

        val firstTimeStarting = logCollectionComponent.logStorage().getBoolean(FIRST_INIT_KEY, true)

        val hengamConfig = logCollectionComponent.hengamConfig()

        if (!(hengamConfig.logCollectionForceEnabled || (hengamConfig.logCollectionLimitedEnabled && firstTimeStarting))) { return }

        HengamInternals.registerComponent(Hengam.LOG_COLLECTION, LogCollectionComponent::class.java, logCollectionComponent)

        logCollectionComponent.logCollector().initialize()

    }

    override fun postInitialize(context: Context) {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

        val firstTimeStarting = core.storage().getBoolean(FIRST_INIT_KEY, true)

        val hengamConfig = logCollectionComponent.hengamConfig()

        if (!(hengamConfig.logCollectionForceEnabled || (hengamConfig.logCollectionLimitedEnabled && firstTimeStarting))) {
            logCollectionComponent.logStorage().putBoolean(FIRST_INIT_KEY, false)
            return
        }

        core.hengamLifecycle().waitForWorkManagerInitialization().justDo {
            // If module was not enabled by force, run a task to stop it automatically after a while.
            if (!hengamConfig.logCollectionForceEnabled) {
                logCollectionComponent.logCollector().runAutoStopper()
            }
            logCollectionComponent.logStorage().putBoolean(FIRST_INIT_KEY, false)
            logCollectionComponent.logCollector().runDbCleaner()
        }
    }

    companion object {
        const val TASKS_TAG = "lagg_task"
        const val FIRST_INIT_KEY = "log_collection_first_run"
    }
}