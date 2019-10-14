package co.pushe.plus.datalytics

import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.days
import co.pushe.plus.utils.hours
import co.pushe.plus.utils.millis

fun PusheConfig.getCollectableSettings(collectable: Collectable): CollectorSettings {
    val sendPriority =
            getObject("collectable_send_priority_${collectable.id}",
                    SendPriority::class.java, collectable.defaultSettings.sendPriority)
    val repeatInterval =
            getLong("collectable_interval_${collectable.id}",
                    collectable.defaultSettings.repeatInterval.toMillis())
    val flexibilityTime =
        getLong("collectable_flex_time_${collectable.id}",
            collectable.defaultSettings.flexTime.toMillis())
    val maxAttempts =
            getInteger("collectable_max_attempts_${collectable.id}",
                    collectable.defaultSettings.maxAttempts)
    return CollectorSettings(millis(repeatInterval), millis(flexibilityTime), sendPriority, maxAttempts)
}

fun PusheConfig.setCollectableSettings(collectable: Collectable, settings: CollectorSettings) {
    updateConfig("collectable_send_priority_${collectable.id}", SendPriority::class.java, settings.sendPriority)
    updateConfig("collectable_interval_${collectable.id}", settings.repeatInterval.toString())
}

/**
 * App list will need a black list to handle ignore cases.
 * Url of request will be achieved using this variable.
 *
 * ** app_collection_black_list_url **
 */
val PusheConfig.appListBlackListUrl: String
    get() = getString("app_collection_black_list_url",
            "https://static.pushe.co/d/black_list/system_apps_black_list.json")



/**
 * **geofence_periodic_register_interval**
 *
 * Determines the interval at which geofences should be re-registered to ensure that they are
 * available
 */
val PusheConfig.geofencePeriodicRegisterInterval: Time
    get() = getLong("geofence_periodic_register_interval", 0)
            .takeIf { it > 0 }?.let { millis(it) } ?: days(3)

/**
 * **screen_service_enabled**
 *
 * Determines whether the screen receiver service should be started on initialize.
 */
val PusheConfig.isScreenStateServiceEnabled: Boolean get() =
    getBoolean("screen_service_enabled", true)

/**
 * **public_ip_ips**
 *
 * A list of API addresses to use for obtaining the SDKs public IP.
 * The API must return a JSON containing the address in an 'ip' field.
 */
val PusheConfig.publicIPApis: List<String>
    get() = getStringList("public_ip_apis", listOf(
            "https://ip-alt.pushe.co/geoip",
            "https://api.ipify.org?format=json",
            "https://ip.seeip.org/jsonip?",
            "https://ipapi.co/json/",
            "https://ip.pushe.co/geoip"
    ))


/**
 * **install_detector_interval**
 *
 * Determines the interval at which install detector task should be run
 */
val PusheConfig.InstallDetectorTaskInterval: Time
    get() = getLong("install_detector_interval", 0)
            .takeIf { it > 0 }?.let { millis(it) } ?: hours(6)