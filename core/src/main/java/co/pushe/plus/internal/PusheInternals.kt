package co.pushe.plus.internal

import co.pushe.plus.Pushe

/**
 * A singleton object which contains the SDK's top-level global state (e.g, active components)
 *
 * Use this singleton for gaining access to the different components, for example:
 *
 * ```
 * val notifComponent = PusheInternals.getComponent(NotificationComponent::class.java)
 * ```
 */
object PusheInternals {
    val PUSHE_COMPONENTS = listOf(
        ComponentDescriptor("sentry", "co.pushe.plus.sentry.SentryInitializer"),
        ComponentDescriptor(Pushe.LOG_COLLECTION, "co.pushe.plus.logcollection.LogCollectionInitializer"),
        ComponentDescriptor(Pushe.CORE, "co.pushe.plus.CoreInitializer"),
        ComponentDescriptor(Pushe.NOTIFICATION, "co.pushe.plus.notification.NotificationInitializer", listOf(Pushe.CORE)),
        ComponentDescriptor(Pushe.DATALYTICS, "co.pushe.plus.datalytics.DatalyticsInitializer", listOf(Pushe.CORE)),
        ComponentDescriptor(Pushe.ANALYTICS, "co.pushe.plus.analytics.AnalyticsInitializer", listOf(Pushe.CORE))
    )

    internal val components = mutableMapOf<Class<out PusheComponent>, PusheComponent>()
    internal val componentsByName = mutableMapOf<String, PusheComponent>()
    internal val serviceApis = mutableMapOf<String, PusheServiceApi>()
    internal val serviceNames = mutableMapOf<Class<out PusheServiceApi>, String>()
    internal val debugCommandProviders = mutableListOf<DebugCommandProvider>()

    @Suppress("UNCHECKED_CAST")
    fun <T : PusheServiceApi> getService(serviceClass: Class<T>): T? {
        return getService(serviceNames[serviceClass]
                ?: "") as T?
    }

    fun getService(serviceName: String): PusheServiceApi? {
        return serviceApis[serviceName]
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : PusheComponent> getComponent(componentClass: Class<T>): T? {
        return components[componentClass] as? T
    }

    fun registerComponent(name: String, componentClass: Class<out PusheComponent>, component: PusheComponent) {
        components[componentClass] = component
        componentsByName[name] = component
    }

    fun registerApi(name: String, apiClass: Class<out PusheServiceApi>, api: PusheServiceApi) {
        serviceApis[name] = api
        serviceNames[apiClass] = name
    }

    fun registerDebugCommands(debugCommandProvider: DebugCommandProvider) {
        debugCommandProviders.add(debugCommandProvider)
    }
}

