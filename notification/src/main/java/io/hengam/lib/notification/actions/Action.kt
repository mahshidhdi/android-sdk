package io.hengam.lib.notification.actions

import android.content.Context
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.notification.dagger.NotificationScope
import io.hengam.lib.utils.moshi.RuntimeJsonAdapterFactory
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.dagger.NotificationComponent
import com.squareup.moshi.JsonAdapter
import io.reactivex.Completable
import javax.inject.Inject

interface Action {
    /**
     * This is **not** run on the cpuThread(). If the action needs to access any internal
     * data structures it must schedule to do so on the cpuThread.
     */
    fun execute(actionContext: ActionContext)

    fun executeAsCompletable(actionContext: ActionContext): Completable =
            Completable.fromCallable { execute(actionContext) }
}

object ActionFactory {
    fun build(): JsonAdapter.Factory {
        val factory =
                RuntimeJsonAdapterFactory.of(Action::class.java, "action_type")

        factory.registerSubtype("D", DismissAction::class.java) { DismissAction.jsonAdapter(it) }
        factory.registerSubtype("A", AppAction::class.java) { AppAction.jsonAdapter(it) }
        factory.registerSubtype("U", UrlAction::class.java) { UrlAction.jsonAdapter(it) }
        factory.registerSubtype("I", IntentAction::class.java) { IntentAction.jsonAdapter(it) }
        factory.registerSubtype("C", CafeBazaarRateAction::class.java) { CafeBazaarRateAction.jsonAdapter(it) }
        factory.registerSubtype("G", DialogAction::class.java) { DialogAction.jsonAdapter(it) }
        factory.registerSubtype("L", DownloadAppAction::class.java) { DownloadAppAction.jsonAdapter(it) }
        factory.registerSubtype("W", WebViewAction::class.java) { WebViewAction.jsonAdapter(it) }
        factory.registerSubtype("O", DownloadAndWebViewAction::class.java) { DownloadAndWebViewAction.jsonAdapter(it) }
        factory.registerSubtype("T", UserActivityAction::class.java) { UserActivityAction.jsonAdapter(it) }
        factory.registerDefault { FallbackAction.jsonAdapter(it) }
        factory.setFallbackValueOnError(FallbackAction())

        return factory
    }
}

class ActionContext constructor(
        val notification: NotificationMessage,
        val context: Context,
        val moshi: HengamMoshi
) {
    val notifComponent: NotificationComponent by lazy {
        HengamInternals.getComponent(NotificationComponent::class.java)
            ?: throw ActionException("Unable to obtain Notification Component in action")
    }
}

@NotificationScope
class ActionContextFactory @Inject constructor(
        val moshi: HengamMoshi,
        val context: Context
) {
    fun createActionContext(notification: NotificationMessage) =
            ActionContext(notification, context, moshi)
}

class ActionException(message: String, cause: Throwable? = null) : Exception(message, cause)