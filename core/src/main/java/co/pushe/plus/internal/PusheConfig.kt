package co.pushe.plus.internal

import android.content.Context
import android.content.SharedPreferences
import android.support.annotation.VisibleForTesting
import co.pushe.plus.LogTag.T_CONFIG
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheConfig.Companion.PUSHE_CONFIG_THROTTLE_MILLIS
import co.pushe.plus.messages.downstream.UpdateConfigMessage
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.PublishRelay
import co.pushe.plus.utils.rx.keepDoing
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Holds SDK configurations
 *
 * Use the [updateConfig] and [removeConfig] methods for modifying the configurations.
 * Any changes made to the config will be effective immediately, but persisting the changes to
 * storage is throttled any may take several at most [PUSHE_CONFIG_THROTTLE_MILLIS] to be performed.
 *
 * Note, the class caches configuration values so updates made directly to the SharedPreference
 * storage may not be visible through [PusheConfig] until a new instance is created.
 */
@CoreScope
class PusheConfig @VisibleForTesting constructor(
    private val configStore: SharedPreferences,
    val moshi: PusheMoshi
) {

    @Inject constructor(context: Context, moshi: PusheMoshi): this(
            context.getSharedPreferences(PUSHE_CONFIG_STORE, Context.MODE_PRIVATE),
            moshi
    )

    private val updateThrottler = PublishRelay.create<ConfigChange>()
    private val updateList = mutableListOf<ConfigChange>()
    private val cachedConfig = mutableMapOf<String, Any>()

    /**
     * This should only be turned off for testing
     */
    @VisibleForTesting
    var isCacheEnabled = true

    init {
        updateThrottler
                .observeOn(cpuThread())
                .doOnNext { updateList.add(it) }
                .throttleLast(PUSHE_CONFIG_THROTTLE_MILLIS, TimeUnit.MILLISECONDS, cpuThread())
                .keepDoing(T_CONFIG) {
                    val editor = configStore.edit()
                    updateList.forEach {
                        when (it.action) {
                            UPDATE_CONFIG -> editor.putString(it.key, it.value)
                            REMOVE_CONFIG -> editor.remove(it.key)
                        }
                    }
                    editor.apply()

                    Plog.trace(T_CONFIG, "Persisted ${updateList.size} config changes",
                        "Changes" to updateList.map {
                            when (it.action) {
                                UPDATE_CONFIG -> "UPDATE ${it.key} -> ${it.value}"
                                REMOVE_CONFIG -> "REMOVE ${it.key}"
                                else -> "UNKNOWN CHANGE"
                            }
                        }
                    )

                    updateList.clear()
                    cachedConfig.clear()
                }
    }

    val allConfig: Map<String, String>
        get() = configStore.all as Map<String, String>

    fun getString(key: String, defaultValue: String): String {
        cachedConfig[key]?.let { cachedValue ->
            if (cachedValue is ConfigChange) {
                if (cachedValue.action == UPDATE_CONFIG) {
                    return cachedValue.value ?: defaultValue
                } else if (cachedValue.action == REMOVE_CONFIG) {
                    return defaultValue
                }
            } else if (isCacheEnabled && cachedValue is String) {
                return cachedValue
            }
        }
        val value = configStore.getString(key, defaultValue)
        cachedConfig[key] = value
        return value ?: ""
    }

    private fun getString(key: String): String? {
        val value = getString(key, "")
        return if (value.isEmpty()) null else value
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val configString = getString(key) ?: return defaultValue

        return if (configString.equals("true", ignoreCase = true) || configString.equals("false", ignoreCase = true)) {
            configString.toBoolean()
        } else {
            Plog.warn(T_CONFIG, "There was an invalid boolean value in the config store",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun getInteger(key: String, defaultValue: Int): Int {
        val configString = getString(key) ?: return defaultValue

        return configString.toIntOrNull() ?: run {
            Plog.warn(T_CONFIG, "There was an invalid integer value in the config store",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val configString = getString(key) ?: return defaultValue

        return configString.toLongOrNull() ?: run {
            Plog.warn(T_CONFIG, "There was an invalid long value in the config store",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun getFloat(key: String, defaultValue: Float): Float {
        val configString = getString(key) ?: return defaultValue

        return configString.toFloatOrNull() ?: run {
            Plog.warn(T_CONFIG, "There was an invalid float value in the config store",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun <T> getObject(key: String, defaultValue: T, adapter: JsonAdapter<T>): T {
        val configString = getString(key) ?: return defaultValue

        return try {
            adapter.fromJson(configString) ?: defaultValue
        } catch (ex: Exception) {
            Plog.warn(T_CONFIG, "There was an invalid value in the config store for object",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun <T> getObject(key: String, valueType: Class<T>, defaultValue: T): T {
        return getObject(key, defaultValue, moshi.adapter(valueType))
    }

    fun <T> getObjectList(key: String, type: Class<T>, defaultValue: List<T> = emptyList()): List<T> {
        val configString = getString(key) ?: return defaultValue
        val listAdapter: JsonAdapter<List<T>> = moshi.adapter(Types.newParameterizedType(List::class.java, type))

        return try {
            listAdapter.fromJson(configString) ?: defaultValue
        } catch (ex: Exception) {
            Plog.warn(T_CONFIG, "There was an invalid value in the config store for list of object",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun <T> getObjectList(key: String, type: Class<T>, defaultValue: List<T> = emptyList(), adapter: JsonAdapter<T>?): List<T> {
        adapter?.let { _ -> moshi.enhance { it.add(type, adapter) } }
        return getObjectList(key, type, defaultValue)
    }

    fun <T> getObjectList(key: String, type: Class<T>, defaultValue: List<T> = emptyList(), adapter: Any?): List<T> {
        adapter?.let { _ -> moshi.enhance { it.add(adapter) } }
        return getObjectList(key, type, defaultValue)
    }

    fun getStringList(key: String, defaultValue: List<String> = emptyList()): List<String> {
        val configString = getString(key) ?: return defaultValue
        val listAdapter: JsonAdapter<List<String>> = moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))

        return try {
            listAdapter.fromJson(configString) ?: defaultValue
        } catch (ex: Exception) {
            Plog.warn(T_CONFIG, "There was an invalid value in the config store for list of strings",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun getIntegerList(key: String, defaultValue: List<Int> = emptyList()): List<Int> {
        val configString = getString(key) ?: return defaultValue
        val listAdapter: JsonAdapter<List<Int>> = moshi.adapter(Types.newParameterizedType(List::class.java, Integer::class.java))

        return try {
            listAdapter.fromJson(configString) ?: defaultValue
        } catch (ex: Exception) {
            Plog.warn(T_CONFIG, "There was an invalid value in the config store for list of integers",
                "key" to key,
                "value" to configString
            )
            removeConfig(key)
            defaultValue
        }
    }

    fun updateConfig(key: String, newValue: String) {
        val change = ConfigChange(UPDATE_CONFIG, key, newValue)
        cachedConfig[key] = change
        updateThrottler.accept(change)
    }

    fun updateConfig(key: String, newValue: Int) = updateConfig(key, newValue.toString())

    fun updateConfig(key: String, newValue: Long) = updateConfig(key, newValue.toString())

    fun updateConfig(key: String, newValue: Double) = updateConfig(key, newValue.toString())

    fun updateConfig(key: String, newValue: Boolean) = updateConfig(key, newValue.toString())

    fun <T> updateConfig(key: String, newValue: T, adapter: JsonAdapter<T>) =
            updateConfig(key, adapter.toJson(newValue))

    fun <T> updateConfig(key: String, valueType: Class<T>, newValue: T) =
            updateConfig(key, newValue, moshi.adapter(valueType))

    fun updateConfig(message: UpdateConfigMessage) {
        Plog.debug(T_CONFIG, "Handling config update message",
            "Updates" to message.updateValues.size,
            "Removes" to message.removeValues.size
        )
        message.updateValues.forEach { updateConfig(it.key, it.value) }
        message.removeValues.forEach { removeConfig(it) }
    }

    fun removeConfig(key: String) = updateThrottler.accept(ConfigChange(REMOVE_CONFIG, key))

    companion object {
        const val PUSHE_CONFIG_STORE = "pushe_config_store"
        const val PUSHE_CONFIG_THROTTLE_MILLIS = 50L

        private const val UPDATE_CONFIG = 0
        private const val REMOVE_CONFIG = 1
    }

    class ConfigChange(val action: Int, val key: String, val value: String? = null)
}