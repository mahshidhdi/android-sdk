package co.pushe.plus.logcollection.db

import android.arch.persistence.room.TypeConverter
import co.pushe.plus.logcollection.network.LogError
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types


class ListConverter {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    @TypeConverter
    fun fromList(value: List<String>?): String? {
        if (value.isNullOrEmpty()) return null
        return jsonAdapter.toJson(value)
    }

    @TypeConverter
    fun toList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        return jsonAdapter.fromJson(value)
    }
}

class MapConverter {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter<Map<String, String?>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))

    @TypeConverter
    fun fromMap(value: Map<String, String?>?): String? {
        if (value.isNullOrEmpty()) return null
        return jsonAdapter.toJson(value)
    }

    @TypeConverter
    fun toMap(value: String?): Map<String, String?>? {
        if (value.isNullOrEmpty()) return null
        return jsonAdapter.fromJson(value)
    }
}

class LogErrorConverter {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(LogError::class.java)

    @TypeConverter
    fun fromLogError(value: LogError?): String? {
        if (value == null) return null
        return jsonAdapter.toJson(value)
    }

    @TypeConverter
    fun toLogError(value: String?): LogError? {
        if (value.isNullOrEmpty()) return null
        return jsonAdapter.fromJson(value)
    }
}