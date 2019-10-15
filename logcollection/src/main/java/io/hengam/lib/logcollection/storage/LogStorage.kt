package io.hengam.lib.logcollection.storage

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

class LogStorage @Inject constructor(
        context: Context
) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("hengam_log_collection", Context.MODE_PRIVATE)

    fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean) = sharedPreferences.getBoolean(key, default)
}