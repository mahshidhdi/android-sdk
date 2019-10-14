package co.pushe.plus.utils

import android.content.Context
import android.content.SharedPreferences
import co.pushe.plus.Constants
import co.pushe.plus.LogTag.T_UTILS
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.PublishRelay
import co.pushe.plus.utils.rx.keepDoing
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.reflect.KProperty

/**
 * Note: The class is not thread safe and should only be accessed on the cpu thread.
 */
@CoreScope
class PusheStorage constructor(
        private val moshi: PusheMoshi,
        private val sharedPreferences: SharedPreferences
) {

    @Inject constructor(moshi: PusheMoshi, context: Context): this(
            moshi,
            context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
    )

    private val stores = mutableMapOf<String, PersistableCollection>()
    private val saveDebouncer = PublishRelay.create<Boolean>()
    private val storeTimeMapAdapter by lazy {
        moshi.adapter<Map<String, Long>>(Types.newParameterizedType(Map::class.java, String::class.java, Long::class.javaObjectType))
    }
    val dirtyValues = mutableMapOf<String, Any>()
    val removedValues = mutableSetOf<String>()

    init {
        saveDebouncer
                .debounce(STORE_WRITE_RATE_LIMIT, TimeUnit.MILLISECONDS, cpuThread())
                .observeOn(cpuThread())
                .keepDoing {
                    val editor = sharedPreferences.edit()
                    stores.values.forEach { store -> store.performSave(editor) }
                    dirtyValues.forEach { item ->
                        val value = item.value
                        when(value) {
                            is String -> editor.putString(item.key, value)
                            is Int -> editor.putInt(item.key, value)
                            is Long -> editor.putLong(item.key, value)
                            is Boolean -> editor.putBoolean(item.key, value)
                            is Float -> editor.putFloat(item.key, value)
                        }
                    }
                    removedValues.forEach { key -> editor.remove(key) }
                    editor.apply()
                    dirtyValues.clear()
                    removedValues.clear()
                }
    }

    interface PersistableCollection {
        fun performSave(editor: SharedPreferences.Editor)
    }

    fun <T> createStoredMap(preferenceKey: String, valueType: Class<T>,
                            jsonAdapter: JsonAdapter<T>, expirationTime: Time? = null): PersistedMap<T> {
        if (preferenceKey in stores) {
            return stores[preferenceKey] as PersistedMap<T>
        }

        moshi.enhance { it.add(valueType, jsonAdapter) }
        val store = StoredMap(preferenceKey, valueType, expirationTime)
        stores[preferenceKey] = store
        cpuThread { if (store.haveKeysExpired()) saveDebouncer.accept(true) }
        return store
    }

    fun <T> createStoredMap(preferenceKey: String, valueType: Class<T>,
                            jsonAdapter: Any? = null, expirationTime: Time? = null): PersistedMap<T> {
        val store = if (preferenceKey in stores) {
            stores[preferenceKey] as StoredMap<T>
        } else {
            jsonAdapter?.let { adapter -> moshi.enhance { it.add(adapter) } }
            val store = StoredMap(preferenceKey, valueType, expirationTime)
            stores[preferenceKey] = store
            store
        }
        cpuThread { if (store.haveKeysExpired()) saveDebouncer.accept(true) }
        return store
    }

    fun <T> createStoredMap(preferenceKey: String, valueType: Class<T>, expirationTime: Time? = null)
            : PersistedMap<T> = createStoredMap(preferenceKey, valueType, null as Any?, expirationTime)

    fun <T> createStoredList(preferenceKey: String, valueType: Class<T>,
                            jsonAdapter: Any? = null): PersistedList<T> {
        if (preferenceKey in stores) {
            return stores[preferenceKey] as PersistedList<T>
        }

        jsonAdapter?.let { adapter -> moshi.enhance { it.add(adapter) } }
        val store = StoredList(preferenceKey, valueType)
        stores[preferenceKey] = store
        return store
    }

    fun <T> createStoredSet(preferenceKey: String, valueType: Class<T>,
                            jsonAdapter: Any? = null): PersistedSet<T> {
        if (preferenceKey in stores) {
            return stores[preferenceKey] as PersistedSet<T>
        }

        jsonAdapter?.let { adapter -> moshi.enhance { it.add(adapter) } }
        val store = StoredSet(preferenceKey, valueType)
        stores[preferenceKey] = store
        return store
    }

    fun storedString(key: String, default: String): PersistedItem<String> = StoredString(key, default)
    fun storedInt(key: String, default: Int): PersistedItem<Int> = StoredInt(key, default)
    fun storedLong(key: String, default: Long): PersistedItem<Long> = StoredLong(key, default)
    fun storedFloat(key: String, default: Float): PersistedItem<Float> = StoredFloat(key, default)
    fun storedBoolean(key: String, default: Boolean): PersistedItem<Boolean> = StoredBoolean(key, default)
    fun <T> storedObject(key: String, default: T, jsonAdapter: JsonAdapter<T>): PersistedItem<T> = StoredObject(key, default, jsonAdapter, null)
    fun <T> storedObject(key: String, default: T, objectClass: Class<T>): PersistedItem<T> = StoredObject(key, default, null, objectClass)

    private fun put(key: String, value: Any) {
        dirtyValues[key] = value
        removedValues.remove(key)
        saveDebouncer.accept(true)
    }

    fun putString(key: String, value: String) = put(key, value)
    fun putInt(key: String, value: Int) = put(key, value)
    fun putLong(key: String, value: Long) = put(key, value)
    fun putFloat(key: String, value: Float) = put(key, value)
    fun putBoolean(key: String, value: Boolean) = put(key, value)

    fun getString(key: String, default: String): String = if (key in removedValues) default else dirtyValues[key] as? String ?: sharedPreferences.getString(key, default) ?: default
    fun getInt(key: String, default: Int): Int = if (key in removedValues) default else dirtyValues[key] as? Int ?: sharedPreferences.getInt(key, default) ?: default
    fun getLong(key: String, default: Long): Long = if (key in removedValues) default else dirtyValues[key] as? Long ?: sharedPreferences.getLong(key, default) ?: default
    fun getFloat(key: String, default: Float): Float = if (key in removedValues) default else dirtyValues[key] as? Float ?: sharedPreferences.getFloat(key, default) ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = if (key in removedValues) default else dirtyValues[key] as? Boolean ?: sharedPreferences.getBoolean(key, default) ?: default

    fun remove(key: String) {
        dirtyValues.remove(key)
        removedValues.add(key)
        saveDebouncer.accept(true)
    }

    companion object {
        const val SHARED_PREF_NAME = Constants.STORAGE_NAME
        const val STORE_WRITE_RATE_LIMIT = 500L
    }

    private inner class StoredString constructor(private val key: String, private val default: String): PersistedItem<String> {
        override fun get() = getString(key, default)
        override fun set(value: String) = put(key, value)
        override fun delete() = remove(key)
    }

    private inner class StoredInt constructor(private val key: String, private val default: Int): PersistedItem<Int>  {
        override fun get() = getInt(key, default)
        override fun set(value: Int) = put(key, value)
        override fun delete() = remove(key)
    }

    private inner class StoredLong constructor(private val key: String, private val default: Long): PersistedItem<Long>  {
        override fun get() = getLong(key, default)
        override fun set(value: Long) = put(key, value)
        override fun delete() = remove(key)
    }

    private inner class StoredFloat constructor(private val key: String, private val default: Float): PersistedItem<Float>  {
        override fun get(): Float = getFloat(key, default)
        override fun set(value: Float) = put(key, value)
        override fun delete() = remove(key)
    }

    private inner class StoredBoolean constructor(private val key: String, private val default: Boolean): PersistedItem<Boolean>  {
        override fun get(): Boolean = getBoolean(key, default)
        override fun set(value: Boolean) = put(key, value)
        override fun delete() = remove(key)
    }

    private inner class StoredObject<T> constructor(
            private val key: String,
            private val default: T,
            private val jsonAdapter: JsonAdapter<T>?,
            private val objectClass: Class<T>?
    ): PersistedItem<T> {
        override fun get(): T {
            return try {
                val json = dirtyValues[key] as? String ?: sharedPreferences.getString(key, null) ?: return default
                val adapter = jsonAdapter ?: moshi.adapter(objectClass ?: return default).lenient()
                adapter.fromJson(json) ?: default
            } catch (ex: Exception) {
                Plog.error(T_UTILS, ex)
                default
            }
        }

        override fun set(value: T) {
            try {
                val adapter = jsonAdapter ?: moshi.adapter(objectClass ?: return)
                val json = adapter.toJson(value)
                putString(key, json)
            } catch (ex: Exception) {
                Plog.error(T_UTILS, ex)
            }
        }

        override fun delete() = remove(key)
    }

    /**
     * Note: Should not be used for storing large amounts of data or very complex data structures
     *
     * @param preferenceKey The key to store the map with in the shared preferences
     * @param valueType The [Class] of the valueType which is to be stored
     */
    private inner class StoredMap<T> constructor(
            val preferenceKey: String,
            private val valueType: Class<T>,
            private val defaultExpirationTime: Time? = null
    ) : PersistedMap<T>, PersistableCollection  {
        private var isDirty: Boolean = false
        private val mapAdapter by lazy {
            moshi.adapter<Map<String, T>>(Types.newParameterizedType(Map::class.java, String::class.java, valueType))
        }

        private val storedMap: MutableMap<String, T> by lazy {
            val json = sharedPreferences.getString(preferenceKey, null)
            json?.let {
                try {
                    mapAdapter.fromJson(it)?.toMutableMap()
                } catch (ex: Exception) {
                    Plog.error(T_UTILS, ex)
                    mutableMapOf<String, T>()
                }
            } ?: mutableMapOf()
        }

        private val storeExpirationMap: MutableMap<String, Long> by lazy {
            val json = sharedPreferences.getString(preferenceKey + "_expire", null)
            json?.let {
                try {
                    storeTimeMapAdapter.fromJson(it)?.toMutableMap()
                } catch (ex: Exception) {
                    Plog.error(T_UTILS, ex)
                    mutableMapOf<String, Long>()
                }
            } ?: mutableMapOf()
        }

        override fun performSave(editor: SharedPreferences.Editor) {
            if (isDirty) {
                val now = TimeUtils.nowMillis()

                var expiredKeys: MutableSet<String>? = null

                storeExpirationMap.forEach {
                    if (now >= it.value) {
                        expiredKeys = expiredKeys ?: mutableSetOf()
                        expiredKeys?.add(it.key)
                    }
                }

                expiredKeys?.forEach {
                    storeExpirationMap.remove(it)
                    storedMap.remove(it)
                }

                editor.putString(preferenceKey, mapAdapter.toJson(storedMap))
                storeExpirationMap.let { editor.putString(preferenceKey + "_expire", storeTimeMapAdapter.toJson(it)) }
                isDirty = false
            }
        }

        fun haveKeysExpired(): Boolean {
            if (defaultExpirationTime == null) {
                return false
            }

            val now = TimeUtils.nowMillis()
            val keysExpired = storeExpirationMap?.any { now  >= it.value } ?: false
            isDirty = if (keysExpired) true else isDirty
            return keysExpired
        }

        override fun save() {
            isDirty = true
            saveDebouncer.accept(true)
        }

        override fun clear() {
            storedMap.clear()
            storeExpirationMap.clear()
            save()
        }

        override fun put(key: String, value: T): T? {
            val result = storedMap.put(key, value)
            if (defaultExpirationTime != null) {
                storeExpirationMap[key] = TimeUtils.nowMillis() + defaultExpirationTime.toMillis()
            }
            save()
            return result
        }

        override fun put(key: String, value: T, expirationTime: Time?): T? {
            val result = storedMap.put(key, value)
            if (expirationTime != null) {
                storeExpirationMap[key] = TimeUtils.nowMillis() + expirationTime.toMillis()
            }
            save()
            return result
        }

        override fun putAll(from: Map<out String, T>) {
            storedMap.putAll(from)
            val now = TimeUtils.nowMillis()
            if (defaultExpirationTime != null) {
                from.keys.forEach { key -> storeExpirationMap[key] = now + defaultExpirationTime.toMillis() }
            }
            save()
        }

        override fun remove(key: String): T? {
            val result = storedMap.remove(key)
            storeExpirationMap.remove(key)
            save()
            return result
        }

        override fun containsKey(key: String): Boolean = storedMap.containsKey(key)
        override fun containsValue(value: T): Boolean = storedMap.containsValue(value)
        override fun get(key: String): T? = storedMap.get(key)
        override fun isEmpty(): Boolean = storedMap.isEmpty()

        override val size: Int
            get() = storedMap.size
        override val entries: MutableSet<MutableMap.MutableEntry<String, T>>
            get() = storedMap.entries
        override val keys: MutableSet<String>
            get() = storedMap.keys
        override val values: MutableCollection<T>
            get() = storedMap.values

        override fun toString(): String {
            return entries.toString()
        }
    }

    private inner class StoredList<T> constructor(
            val preferenceKey: String,
            val valueType: Class<T>
    ) : PersistedList<T>, PersistableCollection  {
        private var isDirty: Boolean = false
        private val listAdapter by lazy { moshi.adapter<List<T>>(Types.newParameterizedType(List::class.java, valueType)) }
        private val storedList: MutableList<T> by lazy {
            val json = sharedPreferences.getString(preferenceKey, null)
            json?.let {
                try {
                    listAdapter.fromJson(it)?.toMutableList()
                } catch (ex: Exception) {
                    Plog.error(T_UTILS, ex)
                    mutableListOf<T>()
                }
            } ?: mutableListOf()
        }

        override fun performSave(editor: SharedPreferences.Editor) {
            if (isDirty) {
                editor.putString(preferenceKey, listAdapter.toJson(storedList.toList()))
                isDirty = false
            }
        }

        override fun save() {
            isDirty = true
            saveDebouncer.accept(true)
        }

        override fun add(element: T): Boolean {
            val result = storedList.add(element)
            save()
            return result
        }

        override fun add(index: Int, element: T) {
            val result = storedList.add(index, element)
            save()
            return result
        }

        override fun addAll(index: Int, elements: Collection<T>): Boolean {
            val result = storedList.addAll(index, elements)
            save()
            return result
        }

        override fun addAll(elements: Collection<T>): Boolean {
            val result = storedList.addAll(elements)
            save()
            return result
        }

        override fun clear() {
            storedList.clear()
            save()
        }

        override fun remove(element: T): Boolean {
            val result = storedList.remove(element)
            save()
            return result
        }

        override fun removeAll(elements: Collection<T>): Boolean {
            val result = storedList.removeAll(elements)
            save()
            return result
        }

        override fun removeAt(index: Int): T {
            val result = storedList.removeAt(index)
            save()
            return result
        }

        override fun retainAll(elements: Collection<T>): Boolean {
            val result = storedList.retainAll(elements)
            save()
            return result
        }

        override fun set(index: Int, element: T): T {
            val result = storedList.set(index, element)
            save()
            return result
        }

        override val size: Int get() = storedList.size
        override fun contains(element: T): Boolean = storedList.contains(element)
        override fun containsAll(elements: Collection<T>): Boolean = storedList.containsAll(elements)
        override fun get(index: Int): T = storedList.get(index)
        override fun indexOf(element: T): Int = storedList.indexOf(element)
        override fun isEmpty(): Boolean = storedList.isEmpty()
        override fun iterator(): MutableIterator<T> = storedList.iterator()
        override fun lastIndexOf(element: T): Int = storedList.lastIndexOf(element)
        override fun listIterator(): MutableListIterator<T> = storedList.listIterator()
        override fun listIterator(index: Int): MutableListIterator<T> = storedList.listIterator(index)
        override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = storedList.subList(fromIndex, toIndex)

        override fun toString(): String {
            return storedList.toString()
        }
    }

    /**
     * Should not be used for storing large amounts of data or very complex data structures
     */
    private inner class StoredSet<T> constructor(
            val preferenceKey: String,
            val valueType: Class<T>
    ) : PersistedSet<T>, PersistableCollection {
        private var isDirty: Boolean = false
        private val listAdapter by lazy { moshi.adapter<List<T>>(Types.newParameterizedType(List::class.java, valueType)) }
        private val storedSet: MutableSet<T> by lazy {
            val json = sharedPreferences.getString(preferenceKey, null)
            json?.let {
                try {
                    listAdapter.fromJson(it)?.toMutableSet()
                } catch (ex: Exception) {
                    Plog.error(T_UTILS, ex)
                    mutableSetOf<T>()
                }
            } ?: mutableSetOf()
        }

        override fun performSave(editor: SharedPreferences.Editor) {
            if (isDirty) {
                editor.putString(preferenceKey, listAdapter.toJson(storedSet.toList()))
                isDirty = false
            }
        }

        override fun save() {
            isDirty = true
            saveDebouncer.accept(true)
        }

        override fun add(element: T): Boolean {
            val result = storedSet.add(element)
            save()
            return result
        }

        override fun addAll(elements: Collection<T>): Boolean {
            val result = storedSet.addAll(elements)
            save()
            return result
        }

        override fun clear() {
            storedSet.clear()
            save()
        }

        override fun iterator(): MutableIterator<T> = storedSet.iterator()

        override fun remove(element: T): Boolean {
            val result = storedSet.remove(element)
            save()
            return result
        }

        override fun removeAll(elements: Collection<T>): Boolean {
            val result = storedSet.removeAll(elements)
            save()
            return result
        }

        override fun retainAll(elements: Collection<T>): Boolean {
            val result = storedSet.retainAll(elements)
            save()
            return result
        }

        override val size: Int
            get() = storedSet.size

        override fun contains(element: T): Boolean = storedSet.contains(element)
        override fun containsAll(elements: Collection<T>): Boolean = storedSet.containsAll(elements)
        override fun isEmpty(): Boolean = storedSet.isEmpty()

        override fun toString(): String {
            return storedSet.toString()
        }
    }
}

interface PersistedMap<T> : MutableMap<String, T> {
    fun save()
    fun put(key: String, value: T, expirationTime: Time?): T?
}

interface PersistedList<T> : MutableList<T> {
    fun save()
}

interface PersistedSet<T> : MutableSet<T> {
    fun save()
}

interface PersistedItem<T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
    fun get(): T
    fun set(value: T)
    fun delete()
}
