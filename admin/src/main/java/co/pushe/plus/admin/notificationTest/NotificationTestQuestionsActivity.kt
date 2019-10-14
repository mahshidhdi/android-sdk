package co.pushe.plus.admin.notificationTest

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import co.pushe.plus.Pushe
import co.pushe.plus.admin.BuildConfig
import co.pushe.plus.admin.R
import co.pushe.plus.internal.ioThread
import co.pushe.plus.utils.IdGenerator
import com.squareup.moshi.Json
import io.reactivex.Completable
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.Event
import io.sentry.event.EventBuilder
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit


class NotificationTestQuestionsActivity: AppCompatActivity() {
    private var questionNumber = 0
    private val notificationTestsArray = mutableListOf<NotificationTest>()
    private val answerMap = mutableMapOf<Int, Boolean>()
    private lateinit var sentry: SentryClient
    private var name: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_test_questions)
        loadNotificationTestArray()
        val dsn = "http://$DSN_PUBLIC:$DSN_PRIVATE@cz.ronash.co/1574?stacktrace.app.packages=$packageName" +
                "&uncaught.handler.enabled=false"

        name = getSharedPreferences(NotificationTestMainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE).getString(NotificationTestMainActivity.NAME_FIELD, "")

        sentry = SentryClientFactory.sentryClient(dsn, AndroidSentryClientFactory(this))

        askQuestion(notificationTestsArray[questionNumber])

        findViewById<Button>(R.id.yesButton).setOnClickListener {
            submitAnswer(true)
        }
        findViewById<Button>(R.id.noButton).setOnClickListener {
            submitAnswer(false)
        }
        findViewById<Button>(R.id.retryButton).setOnClickListener {
            askQuestion(notificationTestsArray[questionNumber])
        }
    }

    private fun submitAnswer(wasOk: Boolean) {
        if (!wasOk) {
            sentry.sendEvent(
                    EventBuilder()
                            .withMessage("test #${questionNumber + 1} -> ${notificationTestsArray[questionNumber].title}")
                            .withLevel(Event.Level.ERROR)
                            .withTag("environment", if (BuildConfig.DEBUG) "debug" else "production")
                            .withTag("build type", BuildConfig.BUILD_TYPE)
                            .withTag("version name", BuildConfig.VERSION_NAME)
                            .withTag("version code", "${BuildConfig.VERSION_CODE}")
                            .withTag("os", android.os.Build.VERSION.RELEASE)
                            .withTag("device", android.os.Build.MODEL)
                            .withTag("brand", android.os.Build.BRAND)
                            .withTag("name", name)
                            .build()
            )
        }

        answerMap[questionNumber] = wasOk
        questionNumber++
        if (questionNumber < notificationTestsArray.size) {
            askQuestion(notificationTestsArray[questionNumber])
        } else {
            findViewById<RelativeLayout>(R.id.testLayout).visibility = View.GONE
            findViewById<TextView>(R.id.thanksView).visibility = View.VISIBLE
        }


    }

    @SuppressLint("CheckResult")
    private fun askQuestion(notificationTest: NotificationTest) {
        findViewById<TextView>(R.id.question).text = notificationTest.question
        findViewById<TextView>(R.id.title).text = notificationTest.title

        val client = OkHttpClient()
        var body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                notificationTest.notificationJson
        )
        var request = Request.Builder()
            .addHeader("authorization", "Token 40b0abd72a2dcba693df7f2d9060f846d327059c")
//                .addHeader("authorization", "Token 2746e8cf5ebe571670166ed84621a5c15b13bb2a")
                .url("https://api.pushe.co/v2/messaging/rapid/")
                .post(body)
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.e("fgd", "gdsf")
            }

        })
        if (notificationTest.notificationJson2 != null) {
            Completable.timer(notificationTest.delay, TimeUnit.SECONDS, ioThread())
                .subscribe {
                    body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        notificationTest.notificationJson2
                    )
                        request = Request.Builder()
                                .addHeader(
                                        "authorization",
                                        "Token 40b0abd72a2dcba693df7f2d9060f846d327059c"
                                )
                                .url("https://api.pushe.co/v2/messaging/rapid/")
                                .post(body)
                                .build()

                        client.newCall(request).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                e.printStackTrace()
                            }

                            override fun onResponse(call: Call, response: Response) {
                                Log.e("fgd", "gdsf")
                            }

                        })
                    }

        }


    }

    private fun loadJSONFromAsset(): String? {
        var json: String? = null
        try {
            val inputStream = assets.open("notification_tests.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            json = String(buffer, Charset.forName("UTF-8"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }

        return json
    }

    private fun loadNotificationTestArray() {
        val otk = IdGenerator.generateId()
        var json = loadJSONFromAsset()
        json = json?.replace("USER_AID", Pushe.getAndroidId())
        json = json?.replace("OTK", otk)
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            notificationTestsArray.add(
                    NotificationTest(
                            title = obj.getString("title"),
                            question = obj.getString("question"),
                            notificationJson = obj.getString("notificationJson"),
                            notificationJson2 = if (obj.has("notificationJson2")) obj.getString("notificationJson2") else null,
                            delay = if (obj.has("delay")) obj.getLong("delay") else 5
                    )
            )
        }
    }

    companion object {
        const val DSN_PUBLIC = "8a0021b9e51341fbbfebe63102a270e1"
        const val DSN_PRIVATE = "772292dfb34e4c49b6e39829cbe64a6c"
    }

}

class NotificationTest(
        @Json(name = "title") val title: String?,
        @Json(name = "question") val question: String?,
        @Json(name = "notificationJson") val notificationJson: String,
        @Json(name = "notificationJson2") val notificationJson2: String?,
        @Json(name = "delay") val delay: Long
) {
    companion object

}