package co.pushe.plus.admin.notificationTest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import co.pushe.plus.admin.R
import io.sentry.SentryClient


class NotificationTestMainActivity : AppCompatActivity() {
    private var questionNumber = 0
    private val notificationTestsArray = mutableListOf<NotificationTest>()
    private val answerMap = mutableMapOf<Int, Boolean>()
    private lateinit var sentry: SentryClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_test_main)

        val nameEditText = findViewById<EditText>(R.id.editText)

        val prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null && prefs.contains(NAME_FIELD)) {
            nameEditText.setText(prefs.getString(NAME_FIELD, ""))
        }

        findViewById<Button>(R.id.startTest).setOnClickListener {
            if (nameEditText.text.toString().isEmpty()) {
                nameEditText.error = "please enter your name"
            } else {
                prefs.edit().putString(NAME_FIELD, nameEditText.text.toString()).apply()
                val intent = Intent(this, NotificationTestQuestionsActivity::class.java)
                startActivity(intent)
                finish()
            }

        }

    }

    companion object {
        const val SHARED_PREFS_NAME = "admin_notification_test_prefs"
        const val NAME_FIELD = "name"

    }


}