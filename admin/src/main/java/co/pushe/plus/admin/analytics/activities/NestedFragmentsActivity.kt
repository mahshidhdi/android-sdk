package co.pushe.plus.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import co.pushe.plus.admin.MainActivity
import co.pushe.plus.admin.R
import co.pushe.plus.admin.analytics.fragments.DuplicateFragmentParent

class NestedFragmentsActivity : AppCompatActivity() {

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nested_fragments)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        homeButton = findViewById(R.id.buttonHome)

        previousButton.setOnClickListener {
            val intent = Intent(this, DuplicateFragmentActivity::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        nextButton.setOnClickListener {
            val intent = Intent(this, ListViewActivity::class.java)
            startActivity(intent)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.activityFragmentContainer2, DuplicateFragmentParent())
                .addToBackStack(null)
                .commit()
    }
}
