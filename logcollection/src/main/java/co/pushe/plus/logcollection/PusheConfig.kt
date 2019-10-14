package co.pushe.plus.logcollection

import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.days
import co.pushe.plus.utils.millis

var PusheConfig.islogCollectionEnabled: Boolean
    get() = getBoolean("log_collection_enabled", true)
    set(value) = updateConfig("log_collection_enabled", value)


var PusheConfig.logCollectionBaseUrl: String
    get() = getString("log_collection_base_url", "")
    set(value) = updateConfig("log_collection_base_url", value)


var PusheConfig.logCollectionSyncInfoPeriod: Time
    get() = getLong("log_collection_sync_info_interval", -1)
        .takeIf { it >= 0 }
        ?.let { millis(it) } ?: days(3)
    set(value) = updateConfig("log_collection_sync_info_interval", value.toMillis())


var PusheConfig.logCollectionSyncStatsPeriod: Time
    get() = getLong("log_collection_sync_stats_interval", -1)
        .takeIf { it >= 0 }
        ?.let { millis(it) } ?: days(1)
    set(value) = updateConfig("log_collection_sync_stats_interval", value.toMillis())