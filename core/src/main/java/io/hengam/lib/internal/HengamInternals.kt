package io.hengam.lib.internal

import io.hengam.lib.Hengam

/**
 * A singleton object which contains the SDK's top-level global state (e.g, active components)
 *
 * Use this singleton for gaining access to the different components, for example:
 *
 * ```
 * val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
 * ```
 */
object HengamInternals {
    val HENGAM_COMPONENTS = listOf(
        ComponentDescriptor("sentry", "io.hengam.lib.sentry.SentryInitializer"),
        ComponentDescriptor(Hengam.LOG_COLLECTION, "io.hengam.lib.logcollection.LogCollectionInitializer"),
        ComponentDescriptor(Hengam.CORE, "io.hengam.lib.CoreInitializer"),
        ComponentDescriptor(Hengam.NOTIFICATION, "io.hengam.lib.notification.NotificationInitializer", listOf(Hengam.CORE)),
        ComponentDescriptor(Hengam.DATALYTICS, "io.hengam.lib.datalytics.DatalyticsInitializer", listOf(Hengam.CORE)),
        ComponentDescriptor(Hengam.ANALYTICS, "io.hengam.lib.analytics.AnalyticsInitializer", listOf(Hengam.CORE))
    )

    internal val components = mutableMapOf<Class<out HengamComponent>, HengamComponent>()
    internal val componentsByName = mutableMapOf<String, HengamComponent>()
    internal val serviceApis = mutableMapOf<String, HengamServiceApi>()
    internal val serviceNames = mutableMapOf<Class<out HengamServiceApi>, String>()
    internal val debugCommandProviders = mutableListOf<DebugCommandProvider>()

    @Suppress("UNCHECKED_CAST")
    fun <T : HengamServiceApi> getService(serviceClass: Class<T>): T? {
        return getService(serviceNames[serviceClass]
                ?: "") as T?
    }

    fun getService(serviceName: String): HengamServiceApi? {
        return serviceApis[serviceName]
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : HengamComponent> getComponent(componentClass: Class<T>): T? {
        return components[componentClass] as? T
    }

    fun registerComponent(name: String, componentClass: Class<out HengamComponent>, component: HengamComponent) {
        components[componentClass] = component
        componentsByName[name] = component
    }

    fun registerApi(name: String, apiClass: Class<out HengamServiceApi>, api: HengamServiceApi) {
        serviceApis[name] = api
        serviceNames[apiClass] = name
    }

    fun registerDebugCommands(debugCommandProvider: DebugCommandProvider) {
        debugCommandProviders.add(debugCommandProvider)
    }
}

