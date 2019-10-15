package io.hengam.lib

import androidx.work.BackoffPolicy
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.messages.MessageType
import io.hengam.lib.utils.*

val HengamConfig.fcmDisabled: Boolean get() = getBoolean("fcm_disabled", false)

/**
 * **upstream_message_timeout**
 *
 * Determines how long we should wait for upstream messages to be ACKed before sending them again
 */
val HengamConfig.upstreamMessageTimeout: Time
    get() = getLong("upstream_message_timeout", 0)
            .takeIf { it > 0 }?.let { millis(it) } ?: days(1)


/**
 * **upstream_message_expiration**
 *
 * Determines how long should pass before we consider an upstream message to be expired and give up
 * on trying to send it
 */
val HengamConfig.upstreamMessageExpirationTime: Time
    get() = getLong("upstream_message_expiration", 0)
            .takeIf { it > 0 }?.let { millis(it) } ?: days(7)


/**
 * **upstream_max_parcel_size**
 *
 * Determines the maximum parcel size before stamping the parcel. Upstream messages may be grouped
 * into parcels until the parcel reaches this maximum size.
 */
val HengamConfig.upstreamMaxParcelSize: Int
    get() = getInteger("upstream_max_parcel_size", 3500)


/**
 * **upstream_sender_backoff_policy**
 */
val HengamConfig.upstreamSenderBackoffPolicy: BackoffPolicy
    get() = getObject("upstream_sender_backoff_policy", BackoffPolicy::class.java, BackoffPolicy.EXPONENTIAL)

/**
 * **upstream_sender_backoff_delay**
 */
val HengamConfig.upstreamSenderBackoffDelay: Time
    get() = getLong("upstream_sender_backoff_delay", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: seconds(10)


/**
 * **upstream_flush_interval**
 *
 * The interval at which the Upstream Flush task is run, which causes
 * all stored upstream messages to be sent.
 */
val HengamConfig.upstreamFlushInterval: Time
    get() = getLong("upstream_flush_interval", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: days(1)


/**
 * **default_max_pending_upstream_messages_per_type**
 *
 * The default maximum number of allowed pending upstream messages of the same type. If this limit
 * is reached, then any messages of this type will be ignored until the pending messages are decreased.
 */
val HengamConfig.defaultMaxPendingUpstreamMessagesPerType: Int
    get() = getInteger("default_max_pending_upstream_messages_per_type", 50)


/**
 * **max_pending_upstream_messages_for_type_**
 *
 * The maximum number of allowed pending upstream messages of the given type. If this limit is reached,
 * then any messages of this type will be ignored until the pending messages are decreased. If a
 * specific value has not been defined for the given type then the default value (as defined by
 * [HengamConfig.defaultMaxPendingUpstreamMessagesPerType]) will be used.
 */
fun HengamConfig.maxPendingUpstreamMessagesForType(type: Int): Int =
        getInteger("max_pending_upstream_messages_for_type_$type", -1)
        .takeIf { it >= 0 } ?: when (type) {
            MessageType.Upstream.REGISTRATION -> 20
            MessageType.Datalytics.APP_LIST -> 2000
            MessageType.Datalytics.CONSTANT_DATA -> 5
            MessageType.Datalytics.VARIABLE_DATA -> 10
            MessageType.Datalytics.FLOATING_DATA -> 20
            MessageType.Datalytics.CELLULAR_DATA -> 20
            MessageType.Datalytics.WIFI_LIST -> 20
            MessageType.Datalytics.Upstream.SCREEN_ON_OFF -> 100
            else -> defaultMaxPendingUpstreamMessagesPerType
        }


/**
 * **registration_backoff_policy**
 *
 * The backoff policy to use for the registration task. Can be either 'linear' or 'exponential'
 */
val HengamConfig.registrationBackoffPolicy: BackoffPolicy
    get() = getObject("registration_backoff_policy", BackoffPolicy::class.java, BackoffPolicy.EXPONENTIAL)


/**
 * **registration_backoff_delay**
 *
 * The backoff policy to use for the registration task. Should be passed in as milliseconds.
 */
val HengamConfig.registrationBackoffDelay: Time
    get() = getLong("registration_backoff_delay", -1)
            .takeIf { it >= 0 }
            ?.let { millis(it) } ?: seconds(30)


