package io.hengam.lib.notification.actions

import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.utils.log.Plog
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