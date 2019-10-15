package io.hengam.lib.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.hengam.lib.Hengam
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import javax.inject.Inject

class BootCompletedIntentReceiver @Inject constructor(): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        cpuThread {
            val coreComponent = HengamInternals.getComponent(CoreComponent::class.java)
                    ?: throw ComponentNotAvailableException(Hengam.CORE)
            coreComponent.hengamLifecycle().bootCompleted()
        }
    }
}