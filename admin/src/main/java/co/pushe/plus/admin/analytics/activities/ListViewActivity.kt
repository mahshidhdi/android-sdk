package co.pushe.plus.admin.analytics.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.Button
import android.widget.ListView
import co.pushe.plus.admin.MainActivity
import co.pushe.plus.admin.R
import co.pushe.plus.admin.analytics.RecyclerViewAdapter

class ListViewActivity : AppCompatActivity() {

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var listView: ListView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_view)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        homeButton = findViewById(R.id.buttonHome)
        recyclerView = findViewById(R.id.recyclerView)

        previousButton.setOnClickListener {
            val intent = Intent(this, NestedFragmentsActivity::class.java)
            startActivity(intent)
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        nextButton.setOnClickListener {
            val intent = Intent(this, ViewPagerActivity::class.java)
            startActivity(intent)
        }

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        val input = mutableListOf<String>()
        for (i in 0..49) {
            input.add("Test$i")
        }
        recyclerView.adapter = RecyclerViewAdapter(input)
    }

    override fun onStart() {
        super.onStart()
    }
}
