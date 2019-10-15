package io.hengam.lib.internal

import android.content.Context
import android.util.Log
import io.hengam.lib.LogTag.T_INIT
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.utils.InitProvider
import io.hengam.lib.utils.log.LogcatLogHandler
import io.hengam.lib.utils.log.Plog

class HengamInitializer : InitProvider() {
    private val preInitializedComponents = mutableMapOf<String, HengamComponentInitializer>()

    override fun initialize(context: Context) {
        try {
            Log.i("Hengam", "Starting Hengam initialization")
            preInitializeComponents(context)

            val core = HengamInternals.getComponent(CoreComponent::class.java)

            if (core == null) {
                Plog.warn(T_INIT, "Initialization will not proceed since the core component is not available")
                return
            }

            Plog.debug(T_INIT, "Hengam pre initialization complete",
                "Available Services" to HengamInternals.componentsByName.keys.joinToString()
            )

            core.hengamLifecycle().preInitComplete()

            Plog.trace(T_INIT, "Starting post initialization")

            cpuThread {
                postInitializeComponents(context)
                Plog.info(T_INIT, "Hengam initialization complete")
                core.hengamLifecycle().postInitComplete()
            }

        } catch (ex: Exception) {
            Plog.error(T_INIT, ex)
            // Make sure the error is reported to logcat if logcat handler isn't added
            if (Plog.logHandlers.find { it is LogcatLogHandler } == null) {
                Log.e("Hengam", "Initializing Hengam failed", ex)
            }
        } catch (ex: AssertionError) {
            Plog.error(T_INIT, ex)
            if (Plog.logHandlers.find { it is LogcatLogHandler } == null) {
                Log.e("Hengam", "Initializing Hengam failed", ex)
            }
        }
    }

    private fun preInitializeComponents(context: Context) {
        HengamInternals.HENGAM_COMPONENTS.forEach { descriptor ->
            val initializerClass = try {
                Class.forName(descriptor.initializerClass)
            } catch (ex: ClassNotFoundException) {
                null
            }

            if (initializerClass != null) {
                for (dependency in descriptor.dependencies) {
                    if (dependency !in preInitializedComponents) {
                        Plog.warn(T_INIT, "Hengam component ${descriptor.name} exists but cannot be initialized since it has $dependency as a dependency")
                        return@forEach
                    }
                }

                try {
                    val initializerInstance = initializerClass.newInstance() as HengamComponentInitializer
                    initializerInstance.preInitialize(context)
                    preInitializedComponents[descriptor.name] = initializerInstance
                } catch (ex: Exception) {
                    // If logging in logcat hasn't been initialized yet then manually log to logcat
                    // as well so that errors are not swallowed
                    Plog.error(T_INIT, ex)
                    if (!Plog.logHandlers.any { it is LogcatLogHandler }) {
                        Log.e("Hengam", "Could not initialize Hengam", ex)
                    }
                }
            } else if (descriptor.name == Hengam.CORE) {
                "Unable to find Hengam core component, this might be caused by incorrect proguard configurations".let { message ->
                    Plog.error(T_INIT, message)
                    if (!Plog.logHandlers.any { it is LogcatLogHandler }) {
                        Log.e("Hengam", message)
                    }
                }
            }
        }
    }

    private fun postInitializeComponents(context: Context) {
        HengamInternals.HENGAM_COMPONENTS.forEach { descriptor ->
            preInitializedComponents[descriptor.name]?.let { componentInitializer ->
                try {
                    componentInitializer.postInitialize(context)
                } catch (ex: Throwable) {
                    // Handle not-found errors (usually caused by proguard issues) here instead of
                    // letting it propagate to the exception catcher so that it won't interfere with
                    // the initialization of other modules
                    when (ex) {
                        is Exception, is NoSuchMethodError, is NoSuchFieldError, is NoClassDefFoundError
                            -> Plog.error(T_INIT, "Hengam ${descriptor.name} module could not initialize", ex)
                        else -> throw ex
                    }
                }
            }
        }
    }
}
