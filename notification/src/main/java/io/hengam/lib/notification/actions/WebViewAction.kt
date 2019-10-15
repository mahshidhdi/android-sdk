package io.hengam.lib.notification.actions

import android.content.Intent
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.ui.WebViewActivity
import io.hengam.lib.utils.isValidWebUrl
import io.hengam.lib.utils.log.Plog


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
