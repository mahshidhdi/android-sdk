package co.pushe.plus.notification.actions

import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.JsonClass

/**
 * Open Application Action
 */
@JsonClass(generateAdapter = true)
class AppAction : Action {
    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing App Action")

        val context = actionContext.context
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        context.startActivity(intent)
    }

    companion object
}