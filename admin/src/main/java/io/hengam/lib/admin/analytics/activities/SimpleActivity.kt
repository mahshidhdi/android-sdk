package io.hengam.lib.admin.analytics.activities

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import io.hengam.lib.admin.R
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.view.View


class SimpleActivity : AppCompatActivity() {

    private lateinit var nextButton: Button
    private lateinit var previousButton: Button
    private lateinit var homeButton: Button
    private lateinit var textView: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple)

        nextButton = findViewById(R.id.buttonSample)
        previousButton = findViewById(R.id.buttonSample2)
        homeButton = findViewById(R.id.buttonHome)
        textView = findViewById(R.id.tvSample)

        requestForPermission()

        nextButton.setOnClickListener {
            textView.setText("changed test")
            val intent = Intent(this, SimpleActivity2::class.java)
            startActivity(intent)

        }

        homeButton.setOnClickListener {
            val bitmap = screenShot(it)
            Log.i("testtest", "bitmap is $bitmap")
            val result = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "homeButton" , "theHomeButton")
            Log.i("testtest", "result is $result")
            val result2 = MediaStore.Images.Media.insertImage(contentResolver, screenShot(it.rootView), "parent" , "parent")

//            val intent = Intent(this, MainActivity::class.java)
//            startActivity(intent)
        }
    }

    private fun requestForPermission() {
        if (this.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }

    fun screenShot(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width,
            view.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    override fun onStart() {
        super.onStart()
    }
}
