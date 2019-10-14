package co.pushe.plus.notification.utils

import android.content.Context
import android.os.PowerManager
import javax.inject.Inject

class ScreenWaker @Inject constructor(
       private val context: Context
) {
    private val WAKE_LOCK_TAG = "co.pushe.plus.WAKE_LOCK"

    /***
     * A helper method to get screen wakeLock and release it,
     * the screen will turn off after screen sleep timeout
     */
    fun wakeScreen() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE, WAKE_LOCK_TAG)
        wakeLock.acquire()
        wakeLock.release()
    }

}