package io.hengam.lib.notification.actions

import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class DismissAction : Action {
    override fun execute(actionContext: ActionContext) {
        // Do Nothing
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing Dismiss Action")
    }

    companion object
}