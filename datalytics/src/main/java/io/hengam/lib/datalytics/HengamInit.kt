package io.hengam.lib.datalytics

import android.content.Context
import io.hengam.lib.LogTag.T_INIT
import io.hengam.lib.Hengam
import io.hengam.lib.Hengam.DATALYTICS
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.LogTags.T_GEOFENCE
import io.hengam.lib.datalytics.dagger.DaggerDatalyticsComponent
import io.hengam.lib.datalytics.dagger.DatalyticsComponent
import io.hengam.lib.datalytics.messages.upstream.BootCompletedMessage
import io.hengam.lib.datalytics.services.registerScreenReceiver
import io.hengam.lib.datalytics.tasks.scheduleLocationCollection
import io.hengam.lib.datalytics.tasks.InstallDetectorTask
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamComponentInitializer
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.log.Plog

/**
 * Introduce datalytics module to the sdk and start it's components.
 * @see DatalyticsComponent
 */
class DatalyticsInitializer : HengamComponentInitializer() {
    private lateinit var datalyticsComponent: DatalyticsComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_INIT, "Initializing Hengam datalytics component")

        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?:  throw ComponentNotAvailableException(Hengam.CORE)

        datalyticsComponent = DaggerDatalyticsComponent.builder()
                .coreComponent(core)
                .build()

        extendMoshi(datalyticsComponent.moshi())

        HengamInternals.registerComponent(DATALYTICS, DatalyticsComponent::class.java, datalyticsComponent)
        HengamInternals.registerDebugCommands(datalyticsComponent.debugCommands())
    }

    override fun postInitialize(context: Context) {
        datalyticsComponent.messageDispatcher().listenForMessages()

        // Start tasks with the saved (or Initial) time value.
        datalyticsComponent.hengamLifecycle().waitForRegistration()
                .justDo(T_DATALYTICS) {
                    datalyticsComponent.collectorScheduler().scheduleAllCollectablesWithInitialValue()
                }

        datalyticsComponent.hengamLifecycle().onBootCompleted
                .justDo(T_DATALYTICS) {
                    Plog.info(T_DATALYTICS, "Device boot detected, reporting event to server")
                    datalyticsComponent.postOffice().sendMessage(BootCompletedMessage())

                    // We need to re-register all geofences on device boot
                    // (https://developer.android.com/training/location/geofencing#re-register-geofences-only-when-required)
                    datalyticsComponent.geofenceManager().ensureGeofencesAreRegistered().justDo(T_DATALYTICS, T_GEOFENCE)
                }

        registerScreenReceiver(context)
        scheduleLocationCollection()
        datalyticsComponent.taskScheduler().schedulePeriodicTask(InstallDetectorTask.Options())

    }
}