package io.hengam.lib.notification.actions

import android.content.Intent
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.JsonClass

/**
 * Cafe Bazaar Rate Action; Opens Cafe Bazaar rating activity for the application.
 */
@JsonClass(generateAdapter = true)
class CafeBazaarRateAction : IntentAction() {
    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing CafeBazaarRate Action")
        execute(actionContext,
                action = action ?: Intent.ACTION_EDIT,
                packageName = packageName ?: "com.farsitel.bazaar",
                data="bazaar://details?id=" + actionContext.context.packageName)
    }

    companion object
}