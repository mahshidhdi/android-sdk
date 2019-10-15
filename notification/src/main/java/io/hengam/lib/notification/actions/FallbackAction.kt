package io.hengam.lib.notification.actions

import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.JsonClass

/**
 * This is the default fallback action if building the action fails for any reason.
 *
 * The action does nothing.
 */
@JsonClass(generateAdapter = true)
class FallbackAction : Action {
    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION,"Executing Fallback Action")
    }

    companion object
}