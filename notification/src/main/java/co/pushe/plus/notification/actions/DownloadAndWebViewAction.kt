package co.pushe.plus.notification.actions


import android.content.Intent
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.ui.WebViewActivity
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.isValidWebUrl
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
class DownloadAndWebViewAction(
    @Json(name = "url") val webUrl: String? = null,
    @Json(name = "dl_url") val downloadUrl: String,
    @Json(name = "package_name") val packageName: String,
    @Json(name = "open_immediate") val openImmediate: Boolean = false,
    @Json(name = "notif_title") val fileTitle: String? = null,
    @Json(name = "time_to_install") val timeToInstall: Time? = null

) : Action {

    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION,"Executing Download And WebView Action")

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

        if (isValidWebUrl(webUrl)) {
            val intent = Intent(actionContext.context, WebViewActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.putExtra(WebViewActivity.DATA_WEBVIEW_URL, webUrl)
            intent.putExtra(WebViewActivity.DATA_WEBVIEW_ORIGINAL_MSG_ID, actionContext.notification.messageId)
            intent.action = WebViewActivity.ACTION_SHOW_WEBVIEW

            actionContext.context.startActivity(intent)
        }
    }

    companion object


}