package io.hengam.lib.notification

import androidx.work.BackoffPolicy
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.notification.NotificationBuildStep.*
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.seconds

val HengamConfig.isNotificationEnabled: Boolean get() = getBoolean("notif_enabled", true)

/**
 * **notif_build_max_attempts**
 *
 * Specifies the number of times the maximum number of times notification building should be
 * attempted for a notification (regardless of the reasons notification building fails in each
 * attempt)
 */
val HengamConfig.maxNotificationBuildAttempts: Int
        get() = getInteger("notif_build_max_attempts", 8)


/**
 * **notif_build_step_max_attempts_<step_name>**
 *
 * Specifies the number of times the notification builder should attempt a particular build
 * step before giving up and skipping that particular step in the notification build process.
 */
fun HengamConfig.maxNotificationBuildStepAttempts(step: NotificationBuildStep): Int {
    val stepName = moshi.adapter(NotificationBuildStep::class.java).toJson(step)
    return  getInteger("notif_build_step_max_attempts_$stepName", -1)
            .takeIf { it >= 0 }
            ?: when (step) {
                BACKGROUND_IMAGE -> 6
                IMAGE, ICON -> 5
                SMALL_ICON, DIALOG_ICON -> 4
                SOUND_DOWNLOAD -> 3
                UNKNOWN -> 3
                CONTENT -> 4
                else -> 2
            }
}


/**
 * **notif_build_step_timeout_<step_name>**
 * **notif_build_step_timeout**
 *
 * Specifies the timeout limit (in milliseconds) for each step in the notification build process.
 * The default value for all steps is 20 seconds.
 */
fun HengamConfig.notificationBuildStepTimeout(step: NotificationBuildStep): Time {
    val stepName = moshi.adapter(NotificationBuildStep::class.java).toJson(step)
    return  getLong("notif_build_step_timeout_$stepName", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) }
            ?: getLong("notif_build_step_timeout", -1).takeIf { it >= 0 }?.let { millis(it) }
            ?: when(step) {
                SOUND_DOWNLOAD -> seconds(35)
                else -> seconds(20)
            }
}

/**
 * **notif_max_sound_duration**
 *
 * The maximum allowed length for a custom notification sound
 */
val HengamConfig.notificationMaxSoundDuration: Time
    get() = getLong("notif_max_sound_duration", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: seconds(5)


/**
 * **notif_build_backoff_policy**
 */
val HengamConfig.notificationBuildBackOffPolicy: BackoffPolicy
    get() = getObject("notif_build_backoff_policy", BackoffPolicy::class.java, BackoffPolicy.LINEAR)


/**
 * **notif_build_backoff_delay**
 */
val HengamConfig.notificationBuildBackOffDelay: Time
    get() = getLong("notif_build_backoff_delay", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: seconds(20)
