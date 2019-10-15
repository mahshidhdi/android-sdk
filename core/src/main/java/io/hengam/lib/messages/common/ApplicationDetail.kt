package io.hengam.lib.messages.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


// TODO use this in analytics module as well
@JsonClass(generateAdapter = true)
class ApplicationDetail(
        @Json(name = "package_name") val packageName: String?,
        @Json(name = "app_version") val appVersion: String? = null,
        @Json(name = "src") val installer: String? = null,
        @Json(name = "fit") val installationTime: Long? = null,
        @Json(name = "lut") val lastUpdateTime: Long? = null,
        @Json(name = "app_name") val name: String? = null,
        @Json(name = "sign") val sign: List<String>? = null,
        @Json(name = "hidden_app") val isHidden: Boolean? = null
)