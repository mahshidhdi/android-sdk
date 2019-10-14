package co.pushe.plus.notification.actions

import android.content.Context
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.notification.dagger.NotificationScope
import co.pushe.plus.utils.moshi.RuntimeJsonAdapterFactory
import co.pushe.plus.notification.messages.downstream.NotificationMessage
import co.pushe.plus.notification.dagger.NotificationComponent
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
        val moshi: PusheMoshi
) {
    val notifComponent: NotificationComponent by lazy {
        PusheInternals.getComponent(NotificationComponent::class.java)
            ?: throw ActionException("Unable to obtain Notification Component in action")
    }
}

@NotificationScope
class ActionContextFactory @Inject constructor(
        val moshi: PusheMoshi,
        val context: Context
) {
    fun createActionContext(notification: NotificationMessage) =
            ActionContext(notification, context, moshi)
}

class ActionException(message: String, cause: Throwable? = null) : Exception(message, cause)