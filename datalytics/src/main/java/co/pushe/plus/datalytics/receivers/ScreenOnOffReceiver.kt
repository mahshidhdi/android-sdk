package co.pushe.plus.datalytics.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.messages.upstream.ScreenOnOffMessage
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.TimeUtils
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog

class ScreenOnOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON &&
            intent.action != Intent.ACTION_SCREEN_OFF
        ) {
            return
        }

        cpuThread {
            val core = PusheInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Pushe.CORE)

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
