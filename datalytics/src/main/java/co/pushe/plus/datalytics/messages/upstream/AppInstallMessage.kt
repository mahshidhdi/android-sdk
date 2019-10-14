package co.pushe.plus.datalytics.messages.upstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.messaging.TypedUpstreamMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppInstallMessage(
        @Json(name = "package_name") val packageName: String?,
        @Json(name = "app_version") val appVersion: String?,
        @Json(name = "src") val appInstaller: String?,
        @Json(name = "fit") val firstInstallTime: String?,
        @Json(name = "lut") val lastUpdateTime: String?,
        @Json(name = "app_name") val appName: String?,
        @Json(name = "sign") val appSignature: List<String>?
) : TypedUpstreamMessage<AppInstallMessage>(
        MessageType.Datalytics.Upstream.APP_INSTALL,
        { AppInstallMessageJsonAdapter(it) }
)

object AppInstallMessageBuilder {
    fun build(appDetails: ApplicationDetail): AppInstallMessage {

        val appFirstInstallTime = appDetails.installationTime
        val appLastUpdateTime = appDetails.lastUpdateTime
        val appName = appDetails.name

        return AppInstallMessage(
                appDetails.packageName,
                appDetails.appVersion,
                appDetails.installer,
                if (appFirstInstallTime == 0L) null else appFirstInstallTime.toString(),
                if (appLastUpdateTime == 0L) null else appLastUpdateTime.toString(),
                if (appName.equals("null", true)) null else appName,
                appDetails.sign
        )
    }

}
