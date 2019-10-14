package co.pushe.plus.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.PusheServiceApi
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.notification.dagger.NotificationScope
import co.pushe.plus.notification.messages.upstream.UserNotificationMessage
import com.squareup.moshi.Types
import javax.inject.Inject

@Suppress("MemberVisibilityCanBePrivate", "unused")
@NotificationScope
class PusheNotification @Inject constructor(
    private val context: Context,
    private val notificationSettings: NotificationSettings,
    private val postOffice: PostOffice,
    private val moshi: PusheMoshi

) : PusheServiceApi {

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("WrongConstant")
    fun createNotificationChannel(
        channelId: String, channelName: String,
        description: String? = null,
        importance: Int = -1,
        enableLight: Boolean = false, enableVibration: Boolean = false,
        showBadge: Boolean = false,
        ledColor: Int = 0, vibrationPattern: LongArray? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notifImportance =
                if (importance < 0 || importance > 5) NotificationManager.IMPORTANCE_DEFAULT
                else importance

            val channel = NotificationChannel(channelId, channelName, notifImportance)
            description?.let { channel.description = description }
            channel.enableLights(enableLight)
            channel.lightColor = ledColor
            channel.setShowBadge(showBadge)
            channel.enableVibration(enableVibration)
            if (vibrationPattern != null && vibrationPattern.isNotEmpty()) {
                channel.vibrationPattern = vibrationPattern
            }
            createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(channelId: String, channelName: String, description: String?) =
        createNotificationChannel(channelId, channelName, description, -1)

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel(channel: NotificationChannel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun removeNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.deleteNotificationChannel(channelId)
        }
    }

    /**
     * Enable receiving notifications for the current user
     *
     * Notifications are enabled by default
     */
    fun enableNotifications() {
        notificationSettings.isNotificationEnabled = true
    }

    /**
     * Disable receiving notifications for the current user. Once disabled, the user will not
     * be shown notifications from Pushe unless the notification is forced.
     *
     * Notifications are enabled by default
     */
    fun disableNotifications() {
        notificationSettings.isNotificationEnabled = false
    }

    /**
     * Returns whether notifications are enabled for the current user
     *
     * Notifications can be enabled or disabled using the [enableNotifications] and
     * [disableNotifications] methods.
     */
    fun isNotificationEnable(): Boolean = notificationSettings.isNotificationEnabled


    /**
     * Enable receiving notifications with custom sound for the current user
     *
     * Notifications with custom sound are enabled by default
     */
    fun enableCustomSound() {
        notificationSettings.isCustomSoundEnabled = true
    }

    /**
     * Disable receiving notifications with custom sound for the current user.
     *
     * Notifications with custom sound are enabled by default
     */
    fun disableCustomSound() {
        notificationSettings.isCustomSoundEnabled = false
    }

    /**
     * Returns whether notifications with custom sound are enabled for the current user
     *
     * Notifications  with custom sound can be enabled or disabled using the [enableCustomSound] and
     * [disableCustomSound] methods.
     */
    fun isCustomSoundEnable(): Boolean = notificationSettings.isCustomSoundEnabled


    /**
     * A function to handle notification callback.
     * Instead of implementing Application class simply call this method
     */
    fun setNotificationListener(notificationListener: PusheNotificationListener) {
        notificationSettings.pusheNotificationListener = notificationListener
    }

    /**
     * Send a notification to another user
     *
     * To send a notification you must pass an instance of [UserNotification] to this class.
     * You can obtain an instance using one of these functions depending on how you want to identify
     * the user:
     * - `UserNotification.withAdvertisementId()`
     * - `UserNotification.withAndroidId()`
     * - `UserNotification.withCustomId()`
     *
     * Once you have an instance of [UserNotification] you can set the notification parameters using
     * the class methods.
     *
     * ```
     * UserNotification notification = UserNotification.withCustomId('user1');
     * notification
     *      .setTitle("Notification Title")
     *      .setContent("Notification Content")
     *      .setCustomContent("{\"key\": \"value\"});
     * ```
     *
     * If you want to set more advanced parameters for the notification which are not available
     * through the [UserNotification] class methods, you can do so by using the
     * `setAdvancedNotification()` method which will allow you to pass the notification parameters
     * as a JSON (using the same structure used for sending notification with the API, see the Pushe
     * API documentations for more information).
     *
     * ```
     * notification
     *      .setAdvancedNotification("{\"title\": \"Notification Title\", \"content\": \"Notification Content\"});
     * ```
     *
     * Note, if you use the `setAdvancedNotification()` method all other parameters passed through the
     * other methods will be ignored.
     */
    fun sendNotificationToUser(notification: UserNotification) {
        try {
            val customContent = notification.customContent?.let { customContent ->
                val anyAdapter = moshi.adapter<Map<String, Any>>(
                        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
                try {
                    anyAdapter.fromJson(customContent)
                } catch (ex: Exception) {
                    Log.w("Pushe", "Invalid json received for custom content, " +
                            "sending user notification without custom content", ex)
                    null
                }
            }

            val userMessage = if (notification.advancedJson == null) {
                notification.run {
                    listOfNotNull(
                            ("title" to title).takeIf {  it.second != null},
                            ("content" to content).takeIf { it.second != null },
                            ("big_title" to bigTitle).takeIf {  it.second != null },
                            ("big_content" to bigContent).takeIf {  it.second != null },
                            ("image" to imageUrl).takeIf {  it.second != null },
                            ("icon" to iconUrl).takeIf {  it.second != null },
                            ("notif_icon" to notifIcon).takeIf {  it.second != null },
                            ("custom_content" to customContent).takeIf { it.second != null }
                    ).toMap()
                }
            } else {
                val anyAdapter = moshi.adapter<Map<String, Any>>(
                        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
                try {
                    anyAdapter.fromJson(notification.advancedJson)
                } catch (ex: Exception) {
                    Log.e("Pushe", "Invalid json received for advanced notification, " +
                            "user notification will not be sent", ex)
                    return
                }
            } as Map<String, Any>

            val userNotification = when (notification.idType ?: UserNotification.IdType.CUSTOM_ID) {
                UserNotification.IdType.AD_ID -> UserNotificationMessage(userMessage, userAdvertisementId = notification.id)
                UserNotification.IdType.ANDROID_ID -> UserNotificationMessage(userMessage, userAndroidId = notification.id)
                UserNotification.IdType.CUSTOM_ID -> UserNotificationMessage(userMessage, userCustomId = notification.id)
            }

            postOffice.sendMessage(userNotification, SendPriority.IMMEDIATE)
        } catch (ex: Exception) {
            Log.e("Pushe", "Sending notification to user failed", ex)
        }
    }
}