package co.pushe.plus.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.pushe.plus.Pushe
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.ComponentNotAvailableException
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import javax.inject.Inject

class BootCompletedIntentReceiver @Inject constructor(): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        cpuThread {
            val coreComponent = PusheInternals.getComponent(CoreComponent::class.java)
                    ?: throw ComponentNotAvailableException(Pushe.CORE)
            coreComponent.pusheLifecycle().bootCompleted()
        }
    }
}