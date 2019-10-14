package co.pushe.plus.notification

import android.annotation.TargetApi
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheComponentInitializer
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.dagger.DaggerNotificationComponent
import co.pushe.plus.notification.dagger.NotificationComponent
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.justDo
import io.reactivex.Maybe
import java.util.concurrent.TimeUnit

class NotificationInitializer : PusheComponentInitializer() {
    private lateinit var notifComponent: NotificationComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_NOTIF, "Initializing Pushe notification component")

        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?:  throw ComponentNotAvailableException(Pushe.CORE)

        notifComponent = DaggerNotificationComponent.builder()
                .coreComponent(core)
                .build()

        /* Extend Moshi */
        extendMoshi(core.moshi())

        /* Start processing notification messages */
        notifComponent.messageDispatcher().listenForMessages()

        /* Register component and API */
        PusheInternals.registerComponent(Pushe.NOTIFICATION, NotificationComponent::class.java, notifComponent)
        PusheInternals.registerApi(Pushe.NOTIFICATION, PusheNotification::class.java, notifComponent.api())
        PusheInternals.registerDebugCommands(notifComponent.debugCommands())
    }

    override fun postInitialize(context: Context) {
        createDefaultNotificationChannel(context)
        checkUpdateAndDelayedNotifications()
        context.registerReceiver(
                NotificationAppInstaller.DownloadCompleteReceiver(), IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        notifComponent.notificationController().rescheduleNotificationsOnBootComplete()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createDefaultNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE) as NotificationManager

            if (notificationManager.getNotificationChannel(Constants.DEFAULT_CHANNEL_ID) == null) {
                Plog.info(T_NOTIF, "Creating default notification channel")
                val channel = NotificationChannel(Constants.DEFAULT_CHANNEL_ID,
                        Constants.DEFAULT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                channel.enableLights(true)
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun checkUpdateAndDelayedNotifications() {
        val notificationStorage = notifComponent.notificationStorage()
        val notificationController = notifComponent.notificationController()

        notifComponent.pusheLifecycle()
                .onAppOpened
                .take(1)
                .flatMapMaybe { Maybe.fromCallable { notificationStorage.updateNotification } }
                .filter { notificationStorage.shouldShowUpdatedNotification() }
                .doOnNext { Plog.info(T_NOTIF, "Publishing update notification", "Message Id" to it?.messageId) }
                .flatMapCompletable { message -> notificationController.showNotification(message ?: NotificationMessage("")) }
                .justDo(T_NOTIF) { notificationStorage.onUpdateNotificationShown() }

        notifComponent.pusheLifecycle()
                .onAppOpened
                .flatMapMaybe { Maybe.fromCallable { notificationStorage.delayedNotification } }
                .doOnNext { Plog.debug(T_NOTIF, "Delayed notification exists and will be published in 15 seconds") }
                .delay(15, TimeUnit.SECONDS)
                .doOnNext {  Plog.info(T_NOTIF, "Publishing delayed notification", "Message Id" to it?.messageId) }
                .flatMapCompletable { message ->
                    notificationStorage.removeDelayedNotification()
                    notificationController.showNotification(message  ?: NotificationMessage(""))
                }
                .justDo(T_NOTIF)
    }
}