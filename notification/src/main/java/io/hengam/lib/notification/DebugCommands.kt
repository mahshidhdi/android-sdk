package io.hengam.lib.notification

import io.hengam.lib.LogTag.T_DEBUG
import io.hengam.lib.Hengam
import io.hengam.lib.internal.DebugCommandProvider
import io.hengam.lib.internal.DebugInput
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.actions.*
import io.hengam.lib.notification.messages.downstream.NotificationButton
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.utils.ImageDownloader
import io.hengam.lib.utils.IdGenerator
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.minutes
import io.reactivex.Completable
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DebugCommands @Inject constructor(
        private val notificationController: NotificationController,
        private val imageDownloader: ImageDownloader
) : DebugCommandProvider {
    private val sampleNotification get() = NotificationMessage(
            messageId = IdGenerator.generateId(),
            title = "Test Notification",
            content = "Test Notification"
    )


    override val commands: Map<String, Any>
        get() =
            mapOf(
                    "Notification" to mapOf(
                            "Notification with Action" to mapOf(
                                    "Download App Action" to "notif_action_download_app",
                                    "Url Action" to "notif_action_url",
                                    "WebView Action" to "notif_action_webview",
                                    "Download & WebView Action" to "notif_action_download_and_webview",
                                    "Dialog Action" to "notif_action_dialog",
                                    "User Activity Action" to "notif_action_activity"
                            ),
                            "Schedule Notification 5 min" to "notif_sched_5_min",
                            "Schedule Notification 5 sec" to "notif_sched_5_sec",
                            "Notification with Icon" to "notif_with_icon",
                            "Notification with Sound" to "notif_with_sound",
                            "Notification without Sound" to "notif_without_sound",
                            "Delayed Notification" to "notif_delayed",
                            "Update Notification" to "notif_update",
                            "Cancel Update Notification" to "notif_cancel_update",
                            "Notification badge" to mapOf(
                                    "Notification with custom badge" to "notif_with_badge",
                                    "Notification with increment badge" to "notif_badge_increment"
                            ),
                            "Enable Custom Sound" to "notif_enable_custom_sound",
                            "Disable Custom Sound" to "notif_disable_custom_sound",
                            "Clear Image Cache" to "notif_clear_img_cache"
                    )
            )

    override fun handleCommand(commandId: String, input: DebugInput): Boolean {
        when (commandId) {
            "notif_action_download_app" -> {
                notificationController.handleNotificationMessage(sampleNotification.copy(
                        action = DownloadAppAction("https://khandevaneh.arsh.co/media/app_release/khandevaneh-v1.10.0.apk", "co.arsh.khandevaneh", true, "khandevaneh", minutes(1))
                ))
            }
            "notif_action_url" -> {
                notificationController.handleNotificationMessage(sampleNotification.copy(
                        action = UrlAction("http://barkat.shop/static/apks/barkatV187.apk")
                ))
            }
            "notif_action_webview" -> {
                notificationController.handleNotificationMessage(sampleNotification.copy(
                        action = WebViewAction("http://hengam.me/android.html")
                ))
            }
            "notif_action_download_and_webview" -> {
                notificationController.handleNotificationMessage(sampleNotification.copy(
                        action = DownloadAndWebViewAction("http://hengam.me/android.html", "http://barkat.shop/static/apks/barkatV187.apk", "Barkat")
                ))
            }
            "notif_action_dialog" -> {
                val buttons = mutableListOf<NotificationButton>()
                buttons.add(NotificationButton("2", DismissAction(), "بیخیال!", null, 0))
                buttons.add(NotificationButton("1", DownloadAppAction("https://khandevaneh.arsh.co/media/app_release/khandevaneh-v1.10.0.apk", "co.arsh.khandevaneh", false, "khandevaneh", minutes(1)), "قطعا:))", null, 0))
                val message = sampleNotification.copy(action = DialogAction("سلام", "میخوای برکت رو دانلود کنی؟", "http://files.softicons.com/download/application-icons/message-types-icons-by-icontexto/png/256/icontexto-message-types-alert-orange.png", buttons))
                notificationController.handleNotificationMessage(message)
            }
            "notif_action_activity" -> {
                input.prompt("User Activity Action", "Enter Activity name", "")
                        .observeOn(cpuThread())
                        .justDo { activityName ->
                            val message = sampleNotification.copy(action = UserActivityAction(null, activityName))
                            notificationController.handleNotificationMessage(message)
                        }
            }
            "notif_sched_5_min" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MINUTE, 5)
                val message = sampleNotification.copy(scheduledTime = calendar.time)
                notificationController.handleNotificationMessage(message)
            }
            "notif_sched_5_sec" -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.SECOND, 5)
                notificationController.handleNotificationMessage(sampleNotification.copy(scheduledTime = calendar.time))
            }
            "notif_with_sound" -> {
                val message = sampleNotification.copy(soundUrl = "https://notificationsounds.com/soundfiles/ccb1d45fb76f7c5a0bf619f979c6cf36/file-sounds-1099-not-bad.mp3")
                notificationController.handleNotificationMessage(message)
            }
            "notif_without_sound" -> {
                notificationController.handleNotificationMessage(sampleNotification)
            }
            "notif_with_icon" -> {
                notificationController.handleNotificationMessage(sampleNotification.copy(
                        iconUrl = "https://api.hengam.me/static/public/20181118-c72e3bbb506b431da89f04ab85b1a3e3.png"
                ))
            }
            "notif_delayed" -> {
                Plog.debug(T_NOTIF, T_DEBUG,"A delayed notification will be sent in 5 seconds. If you close the app" +
                        " you should not see the notification until the next time you open the app.")
                Completable.complete()
                        .delay(5, TimeUnit.SECONDS, cpuThread())
                        .justDo(T_DEBUG) {
                            notificationController.handleNotificationMessage(sampleNotification.copy(
                                    title = "Delayed Notification",
                                    delayUntil = "open_app"
                            ))
                        }
            }
            "notif_update" -> {
                input.promptNumber("Update Notification", "Update Version", (BuildConfig.VERSION_CODE + 1).toLong())
                        .observeOn(cpuThread())
                        .map { it }
                        .doOnSuccess { Plog.debug(T_NOTIF, T_DEBUG, "Sending update notification with version code $it") }
                        .justDo { updateVersion ->
                            notificationController.handleNotificationMessage(sampleNotification.copy(
                                    title = "Update Notification",
                                    updateToAppVersion = updateVersion
                            ))
                        }
            }
            "notif_cancel_update" -> {
                Plog.debug(T_NOTIF, T_DEBUG, "Sending notification with cancel update flag")
                notificationController.handleNotificationMessage(sampleNotification.copy(cancelUpdate = "true"))
            }
            "notif_with_badge" -> {
                Plog.debug(T_NOTIF, T_DEBUG, "Showing a notification and adding 5 to badge count")
                notificationController.handleNotificationMessage(sampleNotification.copy(title = "Simple title", content = "Badge of this notification is 5", badgeState = 5))
            }
            "notif_badge_increment" -> {
                Plog.debug(T_NOTIF, T_DEBUG, "Showing a notification with badge = increment")
                notificationController.handleNotificationMessage(sampleNotification.copy(title = "Simple title", content = "Badge is increased by 1", badgeState = 1))
            }
            "notif_enable_custom_sound" -> {
                val notificationApi = Hengam.getHengamService(HengamNotification::class.java)
                notificationApi?.enableCustomSound()
            }
            "notif_disable_custom_sound" -> {
                val notificationApi = Hengam.getHengamService(HengamNotification::class.java)
                notificationApi?.disableCustomSound()
            }
            "notif_clear_img_cache" -> {
                imageDownloader.purgeOutdatedCache(millis(0))
            }
            else -> return false
        }
        return true
    }
}


