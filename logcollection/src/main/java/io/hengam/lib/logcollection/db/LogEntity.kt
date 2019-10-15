package io.hengam.lib.logcollection.db

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import io.hengam.lib.logcollection.network.LogError

@Entity(tableName = "logs")
data class LogEntity (
        @PrimaryKey(autoGenerate = true) var id: Int = 0,
        var message: String? = "",
        var time: Long,
        var tags: List<String>? = emptyList(),
        var level: String,
        var error: LogError? = null,
        var logData: Map<String, String?>? = emptyMap(),
        var isSent: Boolean = false
)