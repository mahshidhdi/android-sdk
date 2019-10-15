package io.hengam.lib.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import io.hengam.lib.admin.MainActivity
import io.hengam.lib.admin.R

class SimpleActivity2 : AppCompatActivity() {

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple2)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        homeButton = findViewById(R.id.buttonHome)

        nextButton.setOnClickListener {
            val intent = Intent(this, MultipleFrameLayoutActivity::class.java)
            startActivity(intent)
        }

        previousButton.setOnClickListener {
            val intent = Intent(this, SimpleActivity::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}
