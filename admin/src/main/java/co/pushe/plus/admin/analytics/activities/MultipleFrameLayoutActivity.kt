package co.pushe.plus.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import co.pushe.plus.admin.MainActivity
import co.pushe.plus.admin.R
import co.pushe.plus.admin.analytics.fragments.FragmentB
import co.pushe.plus.admin.analytics.fragments.FragmentWithLayouts

enum class FragmentIndex {
    FIRST,
    SECOND
}

class MultipleFrameLayoutActivity : AppCompatActivity() {
    var firstFragmentIndex = FragmentIndex.FIRST
    var secondFragmentIndex = FragmentIndex.SECOND

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var tvName: TextView
    private lateinit var homeButton: Button

    private lateinit var changeFragmentButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multiple_layout)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        changeFragmentButton = findViewById(R.id.buttonFragment)
        tvName = findViewById(R.id.tvName)
        homeButton = findViewById(R.id.buttonHome)

        tvName.text = "MultipleFrameLayoutActivity"

        nextButton.setOnClickListener {
            val intent = Intent(this, FragmentActivity::class.java)
            startActivity(intent)
        }

        previousButton.setOnClickListener {
            val intent = Intent(this, SimpleActivity2::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.flContainer, getFirstFragment())
                .addToBackStack(null)
                .commit()

//        supportFragmentManager
//            .beginTransaction()
//            .replace(R.id.flContainer2, getSecondFragment())
//            .addToBackStack(null)
//            .commit()

        changeFragmentButton.setOnClickListener {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.flContainer, getFirstFragment())
                    .addToBackStack(null)
                    .commit()
        }

//        changeSecondButton.setOnClickListener {
//            supportFragmentManager
//                .beginTransaction()
//                .replace(R.id.flContainer2, getSecondFragment())
//                .addToBackStack(null)
//                .commit()
//        }

    }

    private fun getFirstFragment(): Fragment {
        val fragment: Fragment
        when (firstFragmentIndex) {
            FragmentIndex.FIRST -> {
                fragment = FragmentWithLayouts()
                firstFragmentIndex =
                        FragmentIndex.SECOND
            }
            FragmentIndex.SECOND -> {
                fragment = FragmentB()
                firstFragmentIndex = FragmentIndex.FIRST
            }
        }
        return fragment
    }

    private fun getSecondFragment(): Fragment {
        val fragment: Fragment
        when (secondFragmentIndex) {
            FragmentIndex.FIRST -> {
                fragment = FragmentWithLayouts()
                secondFragmentIndex =
                        FragmentIndex.SECOND
            }
            FragmentIndex.SECOND -> {
                fragment = FragmentB()
                secondFragmentIndex =
                        FragmentIndex.FIRST
            }
        }
        return fragment
    }
}
