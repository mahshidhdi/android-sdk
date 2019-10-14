package co.pushe.plus.notification.actions

import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class DismissAction : Action {
    override fun execute(actionContext: ActionContext) {
        // Do Nothing
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing Dismiss Action")
    }

    companion object
}