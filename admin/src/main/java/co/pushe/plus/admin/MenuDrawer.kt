package co.pushe.plus.admin

import android.content.Intent
import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import co.pushe.plus.admin.notificationTest.NotificationTestMainActivity
import co.pushe.plus.admin.analytics.activities.SimpleActivity
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem

class MenuDrawer {
    fun initDrawer(activity: AppCompatActivity, activeItem: Long) {
        val mainColor = activity.resources.getColor(R.color.colorPrimaryDark)

        val logsItem = SecondaryDrawerItem().withIcon(R.drawable.ic_logs)
                .withIdentifier(DRAWER_ITEM_LOGS)
                .withSetSelected(activeItem == DRAWER_ITEM_LOGS)
                .withSelectedColor(mainColor)
                .withSelectedTextColor(Color.WHITE)
                .withTextColor(Color.WHITE)
                .withName("Logs")

        val configItem = SecondaryDrawerItem().withIcon(R.drawable.ic_config)
                .withIdentifier(DRAWER_ITEM_CONFIG)
                .withSetSelected(activeItem == DRAWER_ITEM_CONFIG)
                .withSelectedColor(mainColor)
                .withSelectedTextColor(Color.WHITE)
                .withTextColor(Color.WHITE)
                .withName("Config")

        val statsItem = SecondaryDrawerItem().withIcon(R.drawable.ic_analytics)
                .withIdentifier(DRAWER_ITEM_STATS)
                .withSelectable(false)
                .withTextColor(Color.WHITE)
                .withName("Analytics Test Activities")

        val changeLogItem = SecondaryDrawerItem().withIcon(R.drawable.ic_changelog)
                .withIdentifier(DRAWER_CHANGELOG)
                .withSetSelected(activeItem == DRAWER_CHANGELOG)
                .withTextColor(Color.WHITE)
                .withSelectedColor(mainColor)
                .withSelectedTextColor(Color.WHITE)
                .withName("ChangeLog")

        val notificationTestItem = SecondaryDrawerItem().withIcon(R.drawable.pushe_ic_check)
            .withIdentifier(DRAWER_NOTIFICATION_TEST)
            .withSelectable(false)
            .withTextColor(Color.WHITE)
            .withName("Notification Test")


        val headerResult = AccountHeaderBuilder()
                .withActivity(activity)

                .withTextColor(Color.WHITE)
                .addProfiles(
                        ProfileDrawerItem().withName("Pushe SDK Admin")
                                .withIcon(activity.resources.getDrawable(R.drawable.ic_launcher_foreground))
                )
                .build()

        DrawerBuilder()
                .withActivity(activity)
                .withToolbar(activity.findViewById(R.id.toolbar))
                .withAccountHeader(headerResult)
                .withTranslucentStatusBar(false)
                .withActionBarDrawerToggle(false)
                .withSliderBackgroundColor(mainColor)
                .addDrawerItems(
                        logsItem,
                        DividerDrawerItem(),
                        statsItem,
                        DividerDrawerItem(),
                        changeLogItem,
                        DividerDrawerItem(),
                        notificationTestItem
                )
                .withOnDrawerItemClickListener { view, position, drawerItem ->
                    when(drawerItem.identifier) {
                        DRAWER_ITEM_LOGS -> {
                            val intent = Intent(activity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            activity.startActivity(intent)
                        }
                        DRAWER_ITEM_CONFIG -> {

                        }
                        DRAWER_ITEM_STATS -> {
                            val intent = Intent(activity, SimpleActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            activity.startActivity(intent)
                        }
                        DRAWER_CHANGELOG -> {
                            val intent = Intent(activity, ChangelogActivity::class.java)
                            activity.startActivity(intent)
                        }
                        DRAWER_NOTIFICATION_TEST -> {
                            val intent = Intent(activity, NotificationTestMainActivity::class.java)
                            activity.startActivity(intent)
                        }
                    }
                    true
                }
                .build()
    }

    companion object {
        const val DRAWER_ITEM_LOGS = 1L
        const val DRAWER_ITEM_STATS = 2L
        const val DRAWER_CHANGELOG = 3L
        const val DRAWER_NOTIFICATION_TEST = 4L
        const val DRAWER_ITEM_CONFIG = 5L
    }
}