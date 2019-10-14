package co.pushe.plus.logcollection.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class LogRequestData(
        @field:Json(name = "logs") val logs: List<RequestLogItem>,
        @field:Json(name = "time")  val time: Long,
        @field:Json(name = "log_count")  val remainingLogCount: Int,
        @field:Json(name = "instance_id") val instanceId: String? = null,
        @field:Json(name = "gaid") val adId: String? = null,
        @field:Json(name = "android_id") val androidId: String? = null,
        @field:Json(name = "app_id") val appId: String?,
        @field:Json(name = "package_name") val packageName: String?,
        @field:Json(name = "token") val token: String? = null,
        @field:Json(name = "token_status") val tokenStatus: String?,
        @field:Json(name = "info") val extraInfo: MutableMap<String, Any?>? = null,
        @field:Json(name = "stats") val stats: MutableMap<String, Any?>? = null
)

data class RequestLogItem(
        @field:Json(name = "id") val id: Int,
        @field:Json(name = "message") val message: String? = null,
        @field:Json(name = "level") val level: String,
        @field:Json(name = "tags") val tags: List<String>?,
        @field:Json(name = "data") val data: Map<String, String?>? = null,
        @field:Json(name = "time") val time: Long,
        @field:Json(name = "error") val error: LogError?
)

@JsonClass(generateAdapter = true)
data class LogError(
        @field:Json(name = "message") val message: String?,
        @field:Json(name = "stacktrace") val stacktrace: String?
)