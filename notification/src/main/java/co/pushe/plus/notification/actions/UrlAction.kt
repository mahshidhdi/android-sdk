package co.pushe.plus.notification.actions

import android.content.Intent
import co.pushe.plus.notification.LogTag.T_NOTIF_ACTION
import co.pushe.plus.notification.LogTag.T_NOTIF
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class UrlAction(
        @Json(name="url") val url: String?
) : IntentAction() {
    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION,"Executing Url Action")
        execute(actionContext,
                action = action ?: Intent.ACTION_VIEW,
                data = data ?: url
        )
    }

    companion object
}