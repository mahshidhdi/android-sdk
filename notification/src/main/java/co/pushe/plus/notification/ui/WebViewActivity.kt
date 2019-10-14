package co.pushe.plus.notification.ui

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.R
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.upstream.UserInputDataMessage
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.Types
import javax.inject.Inject


class WebViewActivity : AppCompatActivity() {
    @Inject lateinit var moshi: PusheMoshi
    @Inject lateinit var postOffice: PostOffice
    private var originalMessageId: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val url = intent.getStringExtra(DATA_WEBVIEW_URL)
            originalMessageId = intent.getStringExtra(DATA_WEBVIEW_ORIGINAL_MSG_ID)
            setContentView(R.layout.pushe_webview_layout)
            val mWebView = findViewById<WebView>(R.id.pushe_webview)

            val notificationComponent =
                    PusheInternals.getComponent(NotificationComponent::class.java)
            notificationComponent?.inject(this)

            if (ACTION_SHOW_WEBVIEW == intent.action) {
                mWebView.settings.loadsImagesAutomatically = true
                mWebView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                mWebView.webViewClient = PusheWebViewClient()
                mWebView.settings.javaScriptEnabled = true
                mWebView.addJavascriptInterface(WebViewJavaScriptInterface(this), "app")
                mWebView.loadUrl(url)
            }
        } catch (e: Exception) {
            Plog.error(T_NOTIF, "Error in loading web view activity", e)
            finish()
        }
    }

    private inner class PusheWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            view.loadUrl(url)
            return true
        }
    }

    private inner class WebViewJavaScriptInterface(private val context: Context) {

        /*
         * @param formData A JSON string containing the form daa to be submitted
         */
        @JavascriptInterface
        fun sendResult(formData: String) {
            cpuThread {
                try {
                    val mapAdapter = moshi.adapter<Map<String, Any>>(Types.newParameterizedType(Map::class.java, String::class.java,  Any::class.java))
                    val map = mapAdapter.fromJson(formData)
                    originalMessageId?.let {
                        val reportMessage = UserInputDataMessage(
                            originalMessageId = it,
                            data = map
                        )
                        postOffice.sendMessage(reportMessage)
                    }
                    runOnUiThread {
                        finish() //close webview activity
                    }
                } catch (e: Exception) {
                    Plog.error(T_NOTIF, "Error in sending WebView form message", e)
                }
            }

        }
    }

    companion object {
        const val ACTION_SHOW_WEBVIEW = "co.pushe.plus.SHOW_WEBVIEW"
        const val DATA_WEBVIEW_URL = "webview_url"
        const val DATA_HTML_DATA = "html_data"
        const val DATA_WEBVIEW_ORIGINAL_MSG_ID = "original_msg_id"
    }
}
