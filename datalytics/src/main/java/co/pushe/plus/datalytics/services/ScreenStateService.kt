package co.pushe.plus.datalytics.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.isScreenStateServiceEnabled
import co.pushe.plus.datalytics.receivers.ScreenOnOffReceiver
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.utils.log.Plog

class ScreenStateService: Service() {
    private var screenOnOffReceiver: ScreenOnOffReceiver? = null

    override fun onCreate() {
        super.onCreate()

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        screenOnOffReceiver = ScreenOnOffReceiver()
        registerReceiver(screenOnOffReceiver, filter)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (screenOnOffReceiver != null)
            unregisterReceiver(screenOnOffReceiver)
        super.onDestroy()
    }
}

fun registerScreenReceiver(context: Context) {
    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val i = Intent(context, ScreenStateService::class.java)
            val core = PusheInternals.getComponent(CoreComponent::class.java)
            if (core == null) {
                Plog.error(T_DATALYTICS,"Core component was null when trying to start screen service")
                return
            }
            if (core.config().isScreenStateServiceEnabled) {
                context.startService(i)
            } else {
                context.stopService(i)
            }
        }
    } catch (ex: Throwable) {
        Plog.error(T_DATALYTICS, ex)
    }
}