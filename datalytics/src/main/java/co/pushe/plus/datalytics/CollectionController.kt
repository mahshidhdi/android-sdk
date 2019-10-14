package co.pushe.plus.datalytics

import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.messages.downstream.CollectionMode
import co.pushe.plus.datalytics.messages.downstream.ScheduleCollectionMessage
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.millis
import co.pushe.plus.utils.rx.subscribeBy
import javax.inject.Inject

/**
 * Contains methods for handling downstream data collection messages
 */
class CollectionController @Inject constructor(
        private val collectorExecutor: CollectorExecutor,
        private val collectorScheduler: CollectorScheduler,
        private val pusheConfig: PusheConfig
) {
    /**
     * Handle [ScheduleCollectionMessage] messages received for a particular [Collectable]
     */
    fun handleScheduleCollectionMessage(collectable: Collectable, message: ScheduleCollectionMessage) {
        when {
            message.collectionMode == CollectionMode.IMMEDIATE -> immediateCollection(collectable, message)
            message.collectionMode == CollectionMode.SCHEDULE -> scheduledCollection(collectable, message)
        }
    }

    private fun immediateCollection(collectable: Collectable, message: ScheduleCollectionMessage) {
        collectorExecutor.collectAndSend(
                collectable,
                getCollectionSendPriority(message),
                finalAttempt = true
        ).subscribeBy(onError = {
            Plog.warn.message("Immediate data collection failed for ${collectable.id}").withTag(T_DATALYTICS).useLogCatLevel(LogLevel.DEBUG).log()
        })
    }

    private fun scheduledCollection(collectable: Collectable, message: ScheduleCollectionMessage) {
        val oldSettings = pusheConfig.getCollectableSettings(collectable)

        var scheduleTime = message.schedule

        if (scheduleTime == null) {
            Plog.error(T_DATALYTICS, "Schedule collection message with collection mode 'schedule' is missing `schedule` field")
            scheduleTime = oldSettings.repeatInterval.toMillis()
        } else if (scheduleTime < CollectorScheduler.MIN_REPEAT_INTERVAL_TIME.toMillis()) {
            Plog.error(T_DATALYTICS, "Schedule collection message has a `schedule` time smaller than" +
                    " the minimum allowed repeat interval, the schedule will not be set",
                "Schedule Time" to scheduleTime
            )
            return
        }

        val repeatInterval = millis(scheduleTime)
        val newSettings = CollectorSettings(
                repeatInterval,
                oldSettings.flexTime,
                message.sendImmediate?.let { getCollectionSendPriority(message) } ?: oldSettings.sendPriority,
                oldSettings.maxAttempts
        )
        pusheConfig.setCollectableSettings(collectable, newSettings)
        collectorScheduler.scheduleCollector(collectable)
    }

    private fun getCollectionSendPriority(message: ScheduleCollectionMessage): SendPriority {
        return if (message.sendImmediate == true) SendPriority.IMMEDIATE else SendPriority.WHENEVER
    }
}