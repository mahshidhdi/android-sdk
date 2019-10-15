package io.hengam.lib.admin

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_changelog.*

class ChangelogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)
        title = "ChangeLog"
        MenuDrawer().initDrawer(this, MenuDrawer.DRAWER_ITEM_LOGS)
        textChangelog.loadMarkdownFromAssets("CHANGELOG.md");
    }
}
