package co.pushe.plus.notification.dagger

import android.content.Context
import co.pushe.plus.receivers.BootCompletedIntentReceiver
import co.pushe.plus.PusheLifecycle
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheComponent
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.notification.*
import co.pushe.plus.notification.messages.MessageDispatcher
import co.pushe.plus.notification.NotificationStorage
import co.pushe.plus.notification.tasks.InstallationCheckTask
import co.pushe.plus.notification.tasks.NotificationBuildTask
import co.pushe.plus.notification.ui.PopupDialogActivity
import co.pushe.plus.notification.ui.WebViewActivity
import dagger.Component

@NotificationScope
@Component(dependencies = [(CoreComponent::class)], modules = [(NotificationModule::class)])
interface NotificationComponent : PusheComponent {
    fun context(): Context
    fun moshi(): PusheMoshi
    fun messageDispatcher(): MessageDispatcher
    fun notificationController(): NotificationController
    fun notificationAppInstaller(): NotificationAppInstaller
    fun api(): PusheNotification
    fun notificationExceptionHandler(): NotificationErrorHandler
    fun notificationStorage(): NotificationStorage
    fun pusheLifecycle(): PusheLifecycle
    fun debugCommands(): DebugCommands

    fun inject(notificationActionService: NotificationActionService)
    fun inject(actionService: PopupDialogActivity)
    fun inject(webViewActivity: WebViewActivity)
    fun inject(bootCompletedIntentReceiver: BootCompletedIntentReceiver)
    fun inject(installationCheckTask: InstallationCheckTask)
    fun inject(notificationBuildTask: NotificationBuildTask)

}
