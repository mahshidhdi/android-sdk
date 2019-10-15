package io.hengam.lib.notification.actions

import android.content.Intent
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.messages.downstream.NotificationButton
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.messages.downstream.jsonAdapter
import io.hengam.lib.notification.ui.PopupDialogActivity
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class DialogAction(
        @Json(name="title") val title: String?,
        @Json(name="content") val content: String?,
        @Json(name="icon") val iconUrl: String? = null,
        @Json(name="buttons") val buttons: List<NotificationButton> = emptyList(),
        @Json(name="inputs") val inputs: List<String> = emptyList()
) : Action {

    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing Dialog Action")

        val intent = Intent(actionContext.context, PopupDialogActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        intent.action = PopupDialogActivity.ACTION_OPEN_DIALOG

        val actionAdapter = actionContext.moshi.adapter(DialogAction::class.java)
        val notificationAdapter = NotificationMessage.jsonAdapter(actionContext.moshi.moshi)
        intent.putExtra(PopupDialogActivity.DATA_ACTION, actionAdapter.toJson(this))
        intent.putExtra(PopupDialogActivity.DATA_NOTIFICATION, notificationAdapter.toJson(actionContext.notification))

        actionContext.context.startActivity(intent)
    }

    companion object
}