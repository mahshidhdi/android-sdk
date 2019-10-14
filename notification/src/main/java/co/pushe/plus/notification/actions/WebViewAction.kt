package co.pushe.plus.notification.actions

import android.content.Intent
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.ui.WebViewActivity
import co.pushe.plus.utils.isValidWebUrl
import co.pushe.plus.utils.log.Plog


@JsonClass(generateAdapter = true)
class WebViewAction(
    @Json(name = "url") val webUrl: String? = null
) : Action {

    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing WebView Action")


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
