package io.hengam.lib.notification.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
open class IntentAction(
        @Json(name="uri") val data: String? = null,
        @Json(name="action") val action: String? = null,
        @Json(name="category") val categories: List<String>? = null,
        @Json(name="market_package_name") val packageName: String? = null,
        @Json(name="resolvers") val resolvers: List<String>? = null
) : Action {

    fun execute(actionContext: ActionContext,
                data: String? = this.data,
                action: String? = this.action,
                categories: List<String>? = this.categories,
                packageName: String? = this.packageName,
                resolvers: List<String>? = this.resolvers
                    ) {
        val context = actionContext.context
        val intent = Intent()

        if (action != null && !action.isBlank()) {
            intent.action = action
        }

        if (categories != null && categories.isNotEmpty()) {
            for (category in categories) {
                intent.addCategory(category)
            }
        }

        if (data != null && !data.isBlank()) {
            intent.data = Uri.parse(data)
        }

        if (packageName != null && !packageName.isBlank()) {
            if (isPackageInstalledOnDevice(packageName, context)) {
                // Set package name if app with this package name is installed on device
                intent.setPackage(packageName)
            }
        }

        setIntentResolver(context, intent, resolvers)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Plog.warn(T_NOTIF, T_NOTIF_ACTION,"Intent action could not be resolved",
                    action?.let { "Action" to it },
                    data?.let { "Data" to it },
                    categories?.let { "Categories" to it.toString() }
            )
        }
    }

    override fun execute(actionContext: ActionContext) {
        Plog.info(T_NOTIF, T_NOTIF_ACTION, "Executing Intent Action")
        execute(actionContext, data, action, categories, packageName, resolvers)
    }

    private fun isPackageInstalledOnDevice(packageName: String, context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setIntentResolver(context: Context, intent: Intent, resolvers: List<String>?) {
        if (resolvers == null || resolvers.isEmpty()) {
            return
        }

        for (resolver in resolvers) {
            val otherApps = context.packageManager.queryIntentActivities(intent, 0)
            for (otherApp in otherApps) {
                if (otherApp.activityInfo.applicationInfo.packageName == resolver) {
                    val otherAppActivity = otherApp.activityInfo
                    val componentName = ComponentName(
                            otherAppActivity.applicationInfo.packageName,
                            otherAppActivity.name
                    )
                    intent.component = componentName
                    return
                }
            }
        }
    }

    companion object
}