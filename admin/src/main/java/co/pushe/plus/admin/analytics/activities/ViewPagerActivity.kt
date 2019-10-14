package co.pushe.plus.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import co.pushe.plus.admin.MainActivity
import co.pushe.plus.admin.R
import co.pushe.plus.admin.analytics.TabsPagerAdapter

class ViewPagerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager
    private lateinit var myAdapter: TabsPagerAdapter
    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewpager)
        viewPager = findViewById(R.id.viewpager_layout)
        homeButton = findViewById(R.id.buttonHome )
        nextButton = findViewById(R.id.buttonNext)
        previousButton = findViewById(R.id.buttonPrevious)
        myAdapter = TabsPagerAdapter(supportFragmentManager)
        viewPager.adapter = myAdapter

//        nextButton.setOnClickListener {
//            val intent = Intent(this, MultipleFrameLayoutActivity::class.java)
//            startActivity(intent)
//        }

        previousButton.setOnClickListener {
            val intent = Intent(this, ListViewActivity::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}
