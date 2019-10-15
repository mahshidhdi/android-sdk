package io.hengam.lib.notification.actions

import android.content.Intent
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)

/**
 * An action for opening an activity from the hosting application.
 *
 * @param activityClassName The class name of the activity to open. The class name must either be a
 * full path class name (containing the whole package name) or be relative to the hosting
 * application's package name (i.e., the `applicationId` field in the application's gradle file).
 * Relative class names may optionally be prefixed with a '.'.
 * @param activityExtra An optional string which will passed to the activity through the Intent extra
 * data under the [ACTIVITY_EXTRA] key.
 */
class UserActivityAction(
        @Json(name = "hengam_activity_extra") val activityExtra: String?,
        @Json(name = "action_data") val activityClassName: String
) : Action {

    override fun execute(actionContext: ActionContext) {
        val packageName = actionContext.context.packageName

        val activityClass = try {
            if (activityClassName.startsWith(".")) {
                Class.forName("$packageName$activityClassName")
            } else if (!activityClassName.contains(".")) {
                Class.forName("$packageName.$activityClassName")
            } else {
                try {
                    Class.forName(activityClassName)
                } catch (ex: ClassNotFoundException) {
                    Class.forName("$packageName.$activityClassName")
                }
            }
        } catch (ex: ClassNotFoundException) {
            Plog.warn(T_NOTIF, T_NOTIF_ACTION, "Could not find activity class for user activity action", ex,
                "Message Id" to actionContext.notification.messageId
            )
            return
        }

        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing User Activity Action",
            "Activity Class" to activityClassName,
            "Resolved Activity Class" to activityClass.canonicalName,
            "Extra" to activityExtra
        )

        val intent = Intent(actionContext.context, activityClass)
        intent.putExtra(ACTIVITY_EXTRA, activityExtra)
        intent.putExtra(ACTIVITY_EXTRA_NOTIF_MESSAGE_ID, actionContext.notification.messageId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        actionContext.context.startActivity(intent)
    }

    companion object {
        const val ACTIVITY_EXTRA = "hengam_data"
        /** This constant should be the same as [AppLifecycleListener.ACTIVITY_EXTRA_NOTIF_MESSAGE_ID] **/
        const val ACTIVITY_EXTRA_NOTIF_MESSAGE_ID = "hengam_notif_message_id"
    }
}