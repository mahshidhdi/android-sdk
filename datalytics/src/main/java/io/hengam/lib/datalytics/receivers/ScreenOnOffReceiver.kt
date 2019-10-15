package io.hengam.lib.datalytics.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.messages.upstream.ScreenOnOffMessage
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.TimeUtils
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog

class ScreenOnOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON &&
            intent.action != Intent.ACTION_SCREEN_OFF
        ) {
            return
        }

        cpuThread {
            val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)

            if (intent.action == Intent.ACTION_SCREEN_ON) {
                core.storage().putLong(SCREEN_ON_TIME, TimeUtils.nowMillis())
            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val onTime = core.storage().getLong(SCREEN_ON_TIME, -1)
                val offTime = TimeUtils.nowMillis()
                if (onTime == -1L) {
                    Plog.warn.message("Screen off event was detected but screen-on time " +
                                "was not found in the storage. The event will be ignored.")
                            .withTag(T_DATALYTICS)
                            .withData("off time", offTime)
                            .useLogCatLevel(LogLevel.DEBUG)
                            .log()
                    return@cpuThread
                }
                core.postOffice().sendMessage(
                    ScreenOnOffMessage(onTime.toString(), offTime.toString()),
                    sendPriority = SendPriority.BUFFER
                )
            }
        }
    }

    companion object {
        const val SCREEN_ON_TIME = "screen_on_time"
    }
}
