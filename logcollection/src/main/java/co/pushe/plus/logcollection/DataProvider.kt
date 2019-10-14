package co.pushe.plus.logcollection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.WorkManager
import co.pushe.plus.BuildConfig
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.utils.*
import co.pushe.plus.utils.TimeUtils.now
import javax.inject.Inject

class DataProvider @Inject constructor(
        private val context: Context,
        private val pusheConfig: PusheConfig
) {

    /**
     * This property provides base url from android manifest metadata tag.
     * If tag was not provided or it was empty the default value will be returned.
     */
    val logCollectionBaseUrl: String by lazy {
        val configUrl = pusheConfig.logCollectionBaseUrl
        if(configUrl.isNotEmpty()) {
            configUrl
        } else {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            val bundle = ai.metaData
            val url = bundle.getString(LOG_COLLECTION_META_KEY, null)
            if (!url.isNullOrEmpty()) url else LOG_COLLECTION_BASE_URL
        }
    }

    /**
     * If this method returned true, then log info must be added to message being sent, AND new time gets saved to logInfoLastSyncTime
     */
    private fun shouldSendLogInfo(logInfoLastSyncTime: Long) =
        now().toMillis() - logInfoLastSyncTime > pusheConfig.logCollectionSyncInfoPeriod.toMillis()

    /**
     * If this method returned true, then log info must be added to message being sent, AND new time gets saved to logStatLastSyncTime
     */
    private fun shouldSendStat(logStatLastSyncTime: Long)
            = now().toMillis() - logStatLastSyncTime > pusheConfig.logCollectionSyncStatsPeriod.toMillis()

    /**
     * Constant information of device and current app.
     */
    fun logInfo(): MutableMap<String, Any?>? {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?: return null
        val logInfoLastSyncTime = core.storage().getLong(LOG_INFO_SENT_TIME, 0)

        return if (shouldSendLogInfo(logInfoLastSyncTime)) {
            val applicationInfoHelper = core.applicationInfoHelper()

            core.storage().putLong(LOG_INFO_SENT_TIME, TimeUtils.nowMillis())

            return mutableMapOf(
                "os_version" to Build.VERSION.SDK_INT.toString(),
                "brand" to Build.BRAND,
                "model" to Build.MODEL,
                "pushe_version" to BuildConfig.VERSION_NAME,
                "pv_code" to BuildConfig.VERSION_CODE,
                "app_version" to applicationInfoHelper.getApplicationVersion(),
                "av_code" to applicationInfoHelper.getApplicationVersionCode(),
                "gplay_version" to getGooglePlayServicesVersionName(context),
                "install_date" to firstInstallTime()
            )
        } else null
    }

    /**
     * Status of messages currently stored.
     */
    fun getStats(): MutableMap<String, Any?>? {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?: return null
        val logStatLastSyncTime = core.storage().getLong(LOG_STAT_SENT_TIME, 0)

        return if (shouldSendStat(logStatLastSyncTime)) {
            core.storage().putLong(LOG_STAT_SENT_TIME, TimeUtils.nowMillis())

            val workStatuses = WorkManager.getInstance().getWorkInfosByTag(TaskScheduler.DEFAULT_WORK_TAG).get()
            val messageStore = core.messageStore()
            val pusheStorage = core.storage()
            val moshi = core.moshi()
            val messages = messageStore.allMessages
            val now = now()

            val collectionTimes = pusheStorage
                .createStoredMap("collection_last_run_times", Long::class.javaObjectType)
                .mapValues { it.value }

            val data = workStatuses?.map {
                mapOf(
                    "Id" to it.id.toString(),
                    "State" to it.state,
                    "Tags" to it.tags.map { tag -> tag.replace("co.pushe.plus", "") }
                )
            }

            val anyAdapter = moshi.adapter(Any::class.java)

            val storage =
                context.getSharedPreferences(PusheStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                    .all
                    .mapValues {
                        val stringValue = it.value.toString()
                        if (stringValue.startsWith("{") || stringValue.startsWith("[")) { anyAdapter.fromJson(stringValue)
                        }
                        else { stringValue }
                    }
            mutableMapOf(
                "messages" to messages.map { mapOf(
                    "type" to it.message.messageType,
                    "size" to it.messageSize,
                    "time" to timeAgo(now - it.message.time)
                ) },
                "msg_count" to messages.size,
                "total_msg_size" to messages.sumBy { it.messageSize },
                "collections" to collectionTimes,
                "storage" to storage,
                "workmanager_status" to data
            )
        } else null
    }

    fun resetStatsSyncTime() {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: return
        core.storage().remove(LOG_STAT_SENT_TIME)
    }

    fun resetInfoSyncTime() {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: return
        core.storage().remove(LOG_INFO_SENT_TIME)
    }

    //region Private methods
    private fun getGooglePlayServicesVersionName(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    private fun firstInstallTime() = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime

    private fun timeAgo(timeAgo: Time): String {
        return when {
            timeAgo < seconds(1) -> "$timeAgo millis"
            timeAgo < minutes(1) -> "${timeAgo.toSeconds()} seconds"
            timeAgo < hours(1) -> "${timeAgo.toMinutes()} minutes"
            timeAgo < days(1) -> "${timeAgo.toHours()} hours"
            else -> "${timeAgo.toDays()} days"
        }
    }
    //endregion

    companion object {
        private const val LOG_COLLECTION_META_KEY = "pushe_log_collection_url"
        private const val LOG_COLLECTION_BASE_URL = "https://sdklogs.ronash.co"
        private const val LOG_INFO_SENT_TIME = "log_info_sent_time"
        private const val LOG_STAT_SENT_TIME = "log_stat_sent_time"
    }
}