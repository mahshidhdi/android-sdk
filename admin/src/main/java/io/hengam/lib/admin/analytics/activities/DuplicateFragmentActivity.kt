package io.hengam.lib.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import io.hengam.lib.admin.MainActivity
import io.hengam.lib.admin.R
import io.hengam.lib.admin.analytics.fragments.DuplicateFragment
import io.hengam.lib.admin.analytics.fragments.DuplicateFragmentParent

class DuplicateFragmentActivity : AppCompatActivity() {

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_duplicate_fragment)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        homeButton = findViewById(R.id.buttonHome)

        nextButton.setOnClickListener {
            val intent = Intent(this, NestedFragmentsActivity::class.java)
            startActivity(intent)
        }

        previousButton.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.activityFragmentContainer, DuplicateFragment())
            .addToBackStack(null)
            .commit()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.activityFragmentContainer2, DuplicateFragmentParent())
            .addToBackStack(null)
            .commit()
    }
}
