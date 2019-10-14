package co.pushe.plus.datalytics.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.messages.upstream.AppInstallMessage
import co.pushe.plus.datalytics.messages.upstream.AppInstallMessageBuilder
import co.pushe.plus.datalytics.messages.upstream.AppUninstallMessage
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.log.Plog

class AppChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED &&
            intent.action != Intent.ACTION_PACKAGE_INSTALL &&
            intent.action != Intent.ACTION_PACKAGE_ADDED
        ) {
            return
        }

        cpuThread {
            val core = PusheInternals.getComponent(CoreComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.CORE)

            val packageName = intent.data?.encodedSchemeSpecificPart ?: return@cpuThread
            if (intent.action == Intent.ACTION_PACKAGE_ADDED ||
                intent.action == Intent.ACTION_PACKAGE_INSTALL
            ) {
                Plog.debug(T_DATALYTICS, "Detected application install, reporting event to server")

                val appDetails = core.applicationInfoHelper().getApplicationDetails(packageName)
                if (appDetails == null){
                    Plog.warn(T_DATALYTICS, "Received null ApplicationDetail in appChange receiver")
                    return@cpuThread
                }
                core.postOffice().sendMessage(
                        AppInstallMessageBuilder.build(appDetails)
                )
            } else if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                Plog.debug(T_DATALYTICS, "Detected application uninstall, reporting event to server")
                core.postOffice().sendMessage(AppUninstallMessage(packageName))
            }
        }
    }
}