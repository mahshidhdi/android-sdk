package co.pushe.plus.internal

import android.content.Context
import android.util.Log
import co.pushe.plus.LogTag.T_INIT
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.utils.InitProvider
import co.pushe.plus.utils.log.LogcatLogHandler
import co.pushe.plus.utils.log.Plog

class PusheInitializer : InitProvider() {
    private val preInitializedComponents = mutableMapOf<String, PusheComponentInitializer>()

    override fun initialize(context: Context) {
        try {
            Log.i("Pushe", "Starting Pushe initialization")
            preInitializeComponents(context)

            val core = PusheInternals.getComponent(CoreComponent::class.java)

            if (core == null) {
                Plog.warn(T_INIT, "Initialization will not proceed since the core component is not available")
                return
            }

            Plog.debug(T_INIT, "Pushe pre initialization complete",
                "Available Services" to PusheInternals.componentsByName.keys.joinToString()
            )

            core.pusheLifecycle().preInitComplete()

            Plog.trace(T_INIT, "Starting post initialization")

            cpuThread {
                postInitializeComponents(context)
                Plog.info(T_INIT, "Pushe initialization complete")
                core.pusheLifecycle().postInitComplete()
            }

        } catch (ex: Exception) {
            Plog.error(T_INIT, ex)
            // Make sure the error is reported to logcat if logcat handler isn't added
            if (Plog.logHandlers.find { it is LogcatLogHandler } == null) {
                Log.e("Pushe", "Initializing Pushe failed", ex)
            }
        } catch (ex: AssertionError) {
            Plog.error(T_INIT, ex)
            if (Plog.logHandlers.find { it is LogcatLogHandler } == null) {
                Log.e("Pushe", "Initializing Pushe failed", ex)
            }
        }
    }

    private fun preInitializeComponents(context: Context) {
        PusheInternals.PUSHE_COMPONENTS.forEach { descriptor ->
            val initializerClass = try {
                Class.forName(descriptor.initializerClass)
            } catch (ex: ClassNotFoundException) {
                null
            }

            if (initializerClass != null) {
                for (dependency in descriptor.dependencies) {
                    if (dependency !in preInitializedComponents) {
                        Plog.warn(T_INIT, "Pushe component ${descriptor.name} exists but cannot be initialized since it has $dependency as a dependency")
                        return@forEach
                    }
                }

                try {
                    val initializerInstance = initializerClass.newInstance() as PusheComponentInitializer
                    initializerInstance.preInitialize(context)
                    preInitializedComponents[descriptor.name] = initializerInstance
                } catch (ex: Exception) {
                    // If logging in logcat hasn't been initialized yet then manually log to logcat
                    // as well so that errors are not swallowed
                    Plog.error(T_INIT, ex)
                    if (!Plog.logHandlers.any { it is LogcatLogHandler }) {
                        Log.e("Pushe", "Could not initialize Pushe", ex)
                    }
                }
            } else if (descriptor.name == Pushe.CORE) {
                "Unable to find Pushe core component, this might be caused by incorrect proguard configurations".let { message ->
                    Plog.error(T_INIT, message)
                    if (!Plog.logHandlers.any { it is LogcatLogHandler }) {
                        Log.e("Pushe", message)
                    }
                }
            }
        }
    }

    private fun postInitializeComponents(context: Context) {
        PusheInternals.PUSHE_COMPONENTS.forEach { descriptor ->
            preInitializedComponents[descriptor.name]?.let { componentInitializer ->
                try {
                    componentInitializer.postInitialize(context)
                } catch (ex: Throwable) {
                    // Handle not-found errors (usually caused by proguard issues) here instead of
                    // letting it propagate to the exception catcher so that it won't interfere with
                    // the initialization of other modules
                    when (ex) {
                        is Exception, is NoSuchMethodError, is NoSuchFieldError, is NoClassDefFoundError
                            -> Plog.error(T_INIT, "Pushe ${descriptor.name} module could not initialize", ex)
                        else -> throw ex
                    }
                }
            }
        }
    }
}
