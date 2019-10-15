package io.hengam.lib.notification

import android.annotation.TargetApi
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamComponentInitializer
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.dagger.DaggerNotificationComponent
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.reactivex.Maybe
import java.util.concurrent.TimeUnit

class NotificationInitializer : HengamComponentInitializer() {
    private lateinit var notifComponent: NotificationComponent

    override fun preInitialize(context: Context) {
        Plog.trace(T_NOTIF, "Initializing Hengam notification component")

        val core = HengamInternals.getComponent(CoreComponent::class.java)
            ?:  throw ComponentNotAvailableException(Hengam.CORE)

        notifComponent = DaggerNotificationComponent.builder()
                .coreComponent(core)
                .build()

        /* Extend Moshi */
        extendMoshi(core.moshi())

        /* Start processing notification messages */
        notifComponent.messageDispatcher().listenForMessages()

        /* Register component and API */
        HengamInternals.registerComponent(Hengam.NOTIFICATION, NotificationComponent::class.java, notifComponent)
        HengamInternals.registerApi(Hengam.NOTIFICATION, HengamNotification::class.java, notifComponent.api())
        HengamInternals.registerDebugCommands(notifComponent.debugCommands())
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

        notifComponent.hengamLifecycle()
                .onAppOpened
                .take(1)
                .flatMapMaybe { Maybe.fromCallable { notificationStorage.updateNotification } }
                .filter { notificationStorage.shouldShowUpdatedNotification() }
                .doOnNext { Plog.info(T_NOTIF, "Publishing update notification", "Message Id" to it?.messageId) }
                .flatMapCompletable { message -> notificationController.showNotification(message ?: NotificationMessage("")) }
                .justDo(T_NOTIF) { notificationStorage.onUpdateNotificationShown() }

        notifComponent.hengamLifecycle()
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