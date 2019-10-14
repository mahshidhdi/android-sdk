package co.pushe.plus

import android.arch.lifecycle.ProcessLifecycleOwner
import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import co.pushe.plus.LogTag.T_INIT
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.dagger.CoreModule
import co.pushe.plus.dagger.DaggerCoreComponent
import co.pushe.plus.internal.PusheComponentInitializer
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.tasks.UpstreamFlushTask
import co.pushe.plus.utils.ExceptionCatcher
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.LogcatLogHandler
import co.pushe.plus.utils.log.Plog
import java.util.concurrent.TimeUnit

class CoreInitializer : PusheComponentInitializer() {
    private lateinit var core: CoreComponent

    override fun preInitialize(context: Context) {
        ExceptionCatcher.registerUnhandledPusheExceptionCatcher()

        core = DaggerCoreComponent.builder()
                .coreModule(CoreModule(context.applicationContext))
                .build()

        val appManifest = core.appManifest()
        core.appManifest().extractManifestData()

        initLogging(appManifest)

        Plog.trace(T_INIT, "Initializing Pushe core component")

        /* Extend Moshi */
        extendMoshi(core.moshi())

        /* Register message handlers */
        core.messageDispatcher().listenForMessages()

        /* Register component */
        PusheInternals.registerComponent(Pushe.CORE, CoreComponent::class.java, core)
        PusheInternals.registerDebugCommands(core.debugCommands())
    }

    override fun postInitialize(context: Context) {
        /* Register to Application lifeCycle */
        ProcessLifecycleOwner.get().lifecycle.addObserver(core.pusheLifecycle())

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
                core.pusheLifecycle().workManagerInitialized()
            } catch (ex: Exception){
                Log.e("Pushe", "WorkManager is needed by Pushe library but is not initialized. " +
                        "It's probably because you have disabled WorkManagerInitializer in your manifest " +
                        "but forgot to call WorkManager#initialize in your Application#onCreate or a ContentProvider")
            }
        }, WORK_MANAGER_INITIALIZATION_CHECK_DELAY, TimeUnit.MILLISECONDS)
    }

    private fun initLogging(appManifest: AppManifest) {
        Plog.aggregationScheduler = cpuThread()
        val logcatHandler = LogcatLogHandler(
                "Pushe",
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


