package io.hengam.lib.datalytics.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.isScreenStateServiceEnabled
import io.hengam.lib.datalytics.receivers.ScreenOnOffReceiver
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.utils.log.Plog

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
            val core = HengamInternals.getComponent(CoreComponent::class.java)
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