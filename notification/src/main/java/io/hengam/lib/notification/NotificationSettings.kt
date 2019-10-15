package io.hengam.lib.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.dagger.NotificationScope
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.log.Plog
import javax.inject.Inject

/**
 * Manages settings related to showing notifications
 *
 * The following settings are available:
 *
 * - `isNotificationEnabled`: Indicates whether notifications are enabled by the hosting app. It is
 *    true by default.
 * - `usedOneTimeKeys`: A set of strings containing the one-time-keys which have been used.
 */
@NotificationScope
class NotificationSettings @Inject constructor(
        val context: Context,
        hengamStorage: HengamStorage
) {

    var isNotificationEnabled by hengamStorage.storedBoolean("notifications_enabled", true)

    val usedOneTimeKeys = hengamStorage.createStoredSet("used_one_time_keys", String::class.java)

    private var storedCustomSoundEnabled by hengamStorage.storedBoolean("custom_sound_enabled", true)

    var isCustomSoundEnabled: Boolean
        get() = storedCustomSoundEnabled
        set(value) {
            storedCustomSoundEnabled = value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (value) {
                    createSilentNotificationChannel()
                } else {
                    deleteSilentNotificationChannel()
                }
            }
        }

    var hengamNotificationListener: HengamNotificationListener? = null
        internal set

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createSilentNotificationChannel() {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID) == null) {
            Plog.info(T_NOTIF, "Creating default silent notification channel")
            val channel = NotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID,
                Constants.DEFAULT_SILENT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null,null)
            channel.enableLights(true)
            notificationManager.createNotificationChannel(channel)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun deleteSilentNotificationChannel() {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID) != null) {
            Plog.info(T_NOTIF, "Deleting default silent notification channel")
            notificationManager.deleteNotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID)
        }
    }



}