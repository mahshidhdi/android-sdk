package io.hengam.lib.notification.ui

import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.R
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.upstream.UserInputDataMessage
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.Types
import javax.inject.Inject


class WebViewActivity : AppCompatActivity() {
    @Inject lateinit var moshi: HengamMoshi
    @Inject lateinit var postOffice: PostOffice
    private var originalMessageId: String? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val url = intent.getStringExtra(DATA_WEBVIEW_URL)
            originalMessageId = intent.getStringExtra(DATA_WEBVIEW_ORIGINAL_MSG_ID)
            setContentView(R.layout.hengam_webview_layout)
            val mWebView = findViewById<WebView>(R.id.hengam_webview)

            val notificationComponent =
                    HengamInternals.getComponent(NotificationComponent::class.java)
            notificationComponent?.inject(this)

            if (ACTION_SHOW_WEBVIEW == intent.action) {
                mWebView.settings.loadsImagesAutomatically = true
                mWebView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                mWebView.webViewClient = HengamWebViewClient()
                mWebView.settings.javaScriptEnabled = true
                mWebView.addJavascriptInterface(WebViewJavaScriptInterface(this), "app")
                mWebView.loadUrl(url)
            }
        } catch (e: Exception) {
            Plog.error(T_NOTIF, "Error in loading web view activity", e)
            finish()
        }
    }

    private inner class HengamWebViewClient : WebViewClient() {
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
        const val ACTION_SHOW_WEBVIEW = "io.hengam.lib.SHOW_WEBVIEW"
        const val DATA_WEBVIEW_URL = "webview_url"
        const val DATA_HTML_DATA = "html_data"
        const val DATA_WEBVIEW_ORIGINAL_MSG_ID = "original_msg_id"
    }
}
