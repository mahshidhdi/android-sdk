package io.hengam.lib.datalytics.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.messages.upstream.AppInstallMessage
import io.hengam.lib.datalytics.messages.upstream.AppInstallMessageBuilder
import io.hengam.lib.datalytics.messages.upstream.AppUninstallMessage
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.log.Plog

class AppChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED &&
            intent.action != Intent.ACTION_PACKAGE_INSTALL &&
            intent.action != Intent.ACTION_PACKAGE_ADDED
        ) {
            return
        }

        cpuThread {
            val core = HengamInternals.getComponent(CoreComponent::class.java)
                    ?: throw ComponentNotAvailableException(Hengam.CORE)

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