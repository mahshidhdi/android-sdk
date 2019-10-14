package co.pushe.plus.datalytics

import android.content.Context
import co.pushe.plus.LogTag.T_INIT
import co.pushe.plus.Pushe
import co.pushe.plus.Pushe.DATALYTICS
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.dagger.DaggerDatalyticsComponent
import co.pushe.plus.datalytics.dagger.DatalyticsComponent
import co.pushe.plus.datalytics.messages.upstream.BootCompletedMessage
import co.pushe.plus.datalytics.services.registerScreenReceiver
import co.pushe.plus.datalytics.tasks.InstallDetectorTask
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheComponentInitializer
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.utils.rx.justDo
import co.pushe.plus.utils.log.Plog

/**
 * Introduce datalytics module to the sdk and start it's components.
 * @see DatalyticsComponent
 */
class DatalyticsInitializer : PusheComponentInitializer() {
    private lateinit var datalyticsComponent: DatalyticsComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_INIT, "Initializing Pushe datalytics component")

        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?:  throw ComponentNotAvailableException(Pushe.CORE)

        datalyticsComponent = DaggerDatalyticsComponent.builder()
                .coreComponent(core)
                .build()

        extendMoshi(datalyticsComponent.moshi())

        PusheInternals.registerComponent(DATALYTICS, DatalyticsComponent::class.java, datalyticsComponent)
        PusheInternals.registerDebugCommands(datalyticsComponent.debugCommands())
    }

    override fun postInitialize(context: Context) {
        datalyticsComponent.messageDispatcher().listenForMessages()

        // Start tasks with the saved (or Initial) time value.
        datalyticsComponent.pusheLifecycle().waitForRegistration()
                .justDo(T_DATALYTICS) {
                    datalyticsComponent.collectorScheduler().scheduleAllCollectablesWithInitialValue()
                }

        datalyticsComponent.pusheLifecycle().onBootCompleted
                .justDo(T_DATALYTICS) {
                    Plog.info(T_DATALYTICS, "Device boot detected, reporting event to server")
                    datalyticsComponent.postOffice().sendMessage(BootCompletedMessage())
                }

        registerScreenReceiver(context)

        datalyticsComponent.taskScheduler().schedulePeriodicTask(InstallDetectorTask.Options())

    }
}