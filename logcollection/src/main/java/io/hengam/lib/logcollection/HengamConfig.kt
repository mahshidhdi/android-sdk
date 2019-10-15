package io.hengam.lib.logcollection

import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.days
import io.hengam.lib.utils.millis

var HengamConfig.islogCollectionEnabled: Boolean
    get() = getBoolean("log_collection_enabled", true)
    set(value) = updateConfig("log_collection_enabled", value)


var HengamConfig.logCollectionBaseUrl: String
    get() = getString("log_collection_base_url", "")
    set(value) = updateConfig("log_collection_base_url", value)


var HengamConfig.logCollectionSyncInfoPeriod: Time
    get() = getLong("log_collection_sync_info_interval", -1)
        .takeIf { it >= 0 }
        ?.let { millis(it) } ?: days(3)
    set(value) = updateConfig("log_collection_sync_info_interval", value.toMillis())


var HengamConfig.logCollectionSyncStatsPeriod: Time
    get() = getLong("log_collection_sync_stats_interval", -1)
        .takeIf { it >= 0 }
        ?.let { millis(it) } ?: days(1)
    set(value) = updateConfig("log_collection_sync_stats_interval", value.toMillis())