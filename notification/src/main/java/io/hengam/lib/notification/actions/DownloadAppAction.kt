package io.hengam.lib.notification.actions

import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.isValidWebUrl
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
class DownloadAppAction(
    @Json(name = "dl_url") val downloadUrl: String,
    @Json(name = "package_name") val packageName: String,
    @Json(name = "open_immediate") val openImmediate: Boolean = true,
    @Json(name = "notif_title") val fileTitle: String? = null,
    @Json(name = "time_to_install") val timeToInstall: Time? = null
) : Action {

    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION,"Executing Download App Action")

        if (isValidWebUrl(downloadUrl)) {
            actionContext.notifComponent.notificationAppInstaller().downloadAndInstallApp(
                    messageId = actionContext.notification.messageId,
                    packageName = packageName,
                    downloadUrl = downloadUrl,
                    openImmediate = openImmediate,
                    notifTitle = fileTitle,
                    timeToInstall = timeToInstall
            )
        }
    }

    companion object
}
