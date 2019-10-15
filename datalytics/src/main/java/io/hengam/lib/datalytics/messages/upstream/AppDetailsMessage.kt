package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messages.common.ApplicationDetail
import io.hengam.lib.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ApplicationDetailsMessage(
        @Json(name = "package_name") val packageName: String?,
        @Json(name = "app_version") val appVersion: String?,
        @Json(name = "src") val installer: String?,
        @Json(name = "fit") val installationTime: Long?,
        @Json(name = "lut") val lastUpdateTime: Long?,
        @Json(name = "app_name") val name: String?,
        @Json(name = "sign") val sign: List<String>?,
        @Json(name = "hidden_app") val isHidden: Boolean?
) : TypedUpstreamMessage<ApplicationDetailsMessage>(
        MessageType.Datalytics.APP_LIST,
        { ApplicationDetailsMessageJsonAdapter(it) }
) {
    companion object {
        fun fromApplicationDetail(app: ApplicationDetail): ApplicationDetailsMessage {
            return ApplicationDetailsMessage(
                    packageName = app.packageName,
                    appVersion = app.appVersion,
                    installer = app.installer,
                    installationTime = app.installationTime,
                    lastUpdateTime = app.lastUpdateTime,
                    name = app.name,
                    sign = app.sign,
                    isHidden = app.isHidden
            )
        }
    }
}
