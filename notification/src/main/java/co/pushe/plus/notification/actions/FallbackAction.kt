package co.pushe.plus.notification.actions

import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.utils.log.Plog
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