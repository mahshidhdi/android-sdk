package co.pushe.plus.utils.test.mocks

import android.content.SharedPreferences

import java.util.HashMap

/**
 * Thanks to https://gist.github.com/amardeshbd/354173d00b988574ee5019c4ba0c8a0b
 *
 * Mock implementation of shared preference, which just saves data in memory using map.
 */
class MockSharedPreference : SharedPreferences {
    private val preferenceMap: HashMap<String, Any> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor =
            MockSharedPreferenceEditor(preferenceMap)

    override fun getAll(): Map<String, *> {
        return preferenceMap
    }

    override fun getString(key: String, default: String?): String? {
        return preferenceMap[key] as? String? ?: default
    }

    override fun getStringSet(key: String, default: Set<String>?): Set<String>? {
        return preferenceMap[key] as? Set<String>? ?: default
    }

    override fun getInt(key: String, default: Int): Int {
        return preferenceMap[key] as? Int ?: default
    }

    override fun getLong(key: String, default: Long): Long {
        return preferenceMap[key] as? Long ?: default
    }

    override fun getFloat(key: String, default: Float): Float {
        return preferenceMap[key] as? Float ?: default
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return preferenceMap[key] as? Boolean ?: default
    }

    override fun contains(key: String): Boolean {
        return preferenceMap.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return preferenceEditor
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {

    }

    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {

    }

    class MockSharedPreferenceEditor(private val preferenceMap: HashMap<String, Any>) : SharedPreferences.Editor {

        override fun putString(key: String, value: String): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun putStringSet(key: String, value: Set<String>): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            preferenceMap[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            preferenceMap.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            // Nothing to do, everything is saved in memory.
        }
    }

}