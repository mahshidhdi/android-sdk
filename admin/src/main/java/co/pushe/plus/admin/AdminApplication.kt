package co.pushe.plus.admin

import android.app.Application
import android.content.res.Configuration
import co.pushe.plus.Pushe
import co.pushe.plus.admin.LogTag.T_ADMIN
import co.pushe.plus.notification.NotificationButtonData
import co.pushe.plus.notification.NotificationData
import co.pushe.plus.notification.PusheNotification
import co.pushe.plus.notification.PusheNotificationListener
import co.pushe.plus.utils.log.Plog

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        log("onCreate()")

        Pushe.getPusheService(PusheNotification::class.java)?.run {
            setNotificationListener(object: PusheNotificationListener {
                override fun onNotification(notification: NotificationData) {
                    log("Notification -> onNotification")
                }

                override fun onCustomContentNotification(customContent: MutableMap<String, Any>) {
                    Plog.info(T_ADMIN, "Custom content received","Content" to customContent)
                }

                override fun onNotificationDismiss(
                        notification: NotificationData
                ) {
                    Plog.debug(T_ADMIN, "User callback function for notification dismiss called",
                        "Notification" to notification
                    )
                }

                override fun onNotificationButtonClick(
                        button: NotificationButtonData,
                        notification: NotificationData
                ) {
                    Plog.debug(T_ADMIN, "User callback function for notification button click called",
                        "Notification" to notification,
                        "Button Id" to button.id
                    )
                }

                override fun onNotificationClick(
                        notification: NotificationData
                ) {
                    Plog.debug(T_ADMIN, "User callback function for notification click called",
                        "Notification" to notification
                    )
                }
            })
        }

    }

    override fun onLowMemory() {
        super.onLowMemory()
        log("onLowMemory()")
    }

    override fun onTerminate() {
        super.onTerminate()
        log("onTerminate()")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        log("onTrimMemory()")
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        log("onConfigurationChanged()")
    }

    private fun log(message: String) {
//        Log.i("Pushe", "Application: $message")
//        Plog.trace("Application: $message")
    }
}