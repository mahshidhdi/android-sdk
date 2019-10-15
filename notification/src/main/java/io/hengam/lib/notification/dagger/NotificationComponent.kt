package io.hengam.lib.notification.dagger

import android.content.Context
import io.hengam.lib.receivers.BootCompletedIntentReceiver
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamComponent
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.notification.*
import io.hengam.lib.notification.messages.MessageDispatcher
import io.hengam.lib.notification.NotificationStorage
import io.hengam.lib.notification.tasks.NotificationBuildTask
import io.hengam.lib.notification.ui.PopupDialogActivity
import io.hengam.lib.notification.ui.WebViewActivity
import dagger.Component

@NotificationScope
@Component(dependencies = [(CoreComponent::class)], modules = [(NotificationModule::class)])
interface NotificationComponent : HengamComponent {
    fun context(): Context
    fun moshi(): HengamMoshi
    fun messageDispatcher(): MessageDispatcher
    fun notificationController(): NotificationController
    fun notificationAppInstaller(): NotificationAppInstaller
    fun api(): HengamNotification
    fun notificationExceptionHandler(): NotificationErrorHandler
    fun notificationStorage(): NotificationStorage
    fun hengamLifecycle(): HengamLifecycle
    fun debugCommands(): DebugCommands

    fun inject(notificationActionService: NotificationActionService)
    fun inject(actionService: PopupDialogActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(bootCompletedIntentReceiver: BootCompletedIntentReceiver)

    fun inject(notificationBuildTask: NotificationBuildTask)

}
