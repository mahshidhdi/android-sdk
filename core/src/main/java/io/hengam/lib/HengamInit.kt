package io.hengam.lib

import android.arch.lifecycle.ProcessLifecycleOwner
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import io.hengam.lib.LogTag.T_INIT
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.dagger.CoreModule
import io.hengam.lib.dagger.DaggerCoreComponent
import io.hengam.lib.internal.HengamComponentInitializer
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.tasks.UpstreamFlushTask
import io.hengam.lib.utils.ExceptionCatcher
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.LogcatLogHandler
import io.hengam.lib.utils.log.Plog
import java.util.concurrent.TimeUnit

class CoreInitializer : HengamComponentInitializer() {
    private lateinit var core: CoreComponent

    override fun preInitialize(context: Context) {
        ExceptionCatcher.registerUnhandledHengamExceptionCatcher()

        core = DaggerCoreComponent.builder()
                .coreModule(CoreModule(context.applicationContext))
                .build()

        val appManifest = core.appManifest()
        core.appManifest().extractManifestData()

        initLogging(appManifest)

        Plog.trace(T_INIT, "Initializing Hengam core component")

        /* Extend Moshi */
        extendMoshi(core.moshi())

        /* Register message handlers */
        core.messageDispatcher().listenForMessages()

        /* Register component */
        HengamInternals.registerComponent(Hengam.CORE, CoreComponent::class.java, core)
        HengamInternals.registerDebugCommands(core.debugCommands())
    }

    override fun postInitialize(context: Context) {
        /* Register to Application lifeCycle */
        ProcessLifecycleOwner.get().lifecycle.addObserver(core.hengamLifecycle())

        /* Prefetch Advertisement Id so it will available to developer after initialization */
        core.deviceIdHelper().advertisementId

        /* Initialize Messaging Services */
        core.fcmServiceManager().initializeFirebase()

        /* Start couriers */
        core.courierLounge().initialize()

        /* Perform registration */
        core.registrationManager().checkRegistration()

        /* Flush upstream messages every 24h */
        core.taskScheduler().schedulePeriodicTask(UpstreamFlushTask.Options())

        cpuThread().scheduleDirect({
            try {
                val workManager = WorkManager.getInstance()
                core.hengamLifecycle().workManagerInitialized()
            } catch (ex: Exception){
                Log.e("Hengam", "WorkManager is needed by Hengam library but is not initialized. " +
                        "It's probably because you have disabled WorkManagerInitializer in your manifest " +
                        "but forgot to call WorkManager#initialize in your Application#onCreate or a ContentProvider")
            }
        }, WORK_MANAGER_INITIALIZATION_CHECK_DELAY, TimeUnit.MILLISECONDS)
    }

    private fun initLogging(appManifest: AppManifest) {
        Plog.aggregationScheduler = cpuThread()
        val logcatHandler = LogcatLogHandler(
                "Hengam",
                appManifest.logLevel ?: if (BuildConfig.DEBUG) LogLevel.TRACE else LogLevel.INFO,
                appManifest.logDataEnabled ?: BuildConfig.DEBUG,
                appManifest.logTagsEnabled ?: BuildConfig.DEBUG
        )
        Plog.addHandler(logcatHandler)
        Plog.levelFilter = LogLevel.TRACE
    }

    companion object {
        private const val WORK_MANAGER_INITIALIZATION_CHECK_DELAY = 10000L
    }
}


