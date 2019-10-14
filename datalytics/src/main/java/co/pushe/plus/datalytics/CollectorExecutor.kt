package co.pushe.plus.datalytics

import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.collectors.*
import co.pushe.plus.datalytics.dagger.DatalyticsScope
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.TimeUtils
import co.pushe.plus.utils.log.Plog
import io.reactivex.Completable
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * This class relates the [Collector] to [CollectorScheduler]
 * The data provided by collector will be sent by scheduler. But they can't do this on their own... This class does the job.
 */
@DatalyticsScope
class CollectorExecutor @Inject constructor(
        private val postOffice: PostOffice,
        private val appListCollector: AppListCollector,
        private val appIsHiddenCollector: AppIsHiddenCollector,
        private val cellularInfoCollector: CellularInfoCollector,
        private val constantDataCollector: ConstantDataCollector,
        private val floatingDataCollector: FloatingDataCollector,
        private val variableDataCollector: VariableDataCollector,
        private val wifiListCollector: WifiListCollector,
        pusheStorage: PusheStorage
) {

    private val collectionLastRunTimes = pusheStorage.createStoredMap(
            "collection_last_run_times",
            Long::class.javaObjectType
    )

    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

    /**
     * Collects data by executing the [Collector] instance for the given [Collectable] and
     * sends the generated upstream messages using the [PostOffice]
     *
     * @param collectable The [Collectable] to perform data collection for
     * @param sendPriority The [SendPriority] to use when sending upstream messages
     * @return A [Completable] which completes when all the messages generated by the [Collector]
     *         have been passed to the [PostOffice] for sending
     */
    fun collectAndSend(collectable: Collectable, sendPriority: SendPriority, finalAttempt: Boolean = false): Completable {
        val collector = getCollector(collectable)
        collector.isFinalAttempt = finalAttempt

        try {
            val lastCollectedAt = collectionLastRunTimes[collectable.id]
            Plog.debug(T_DATALYTICS, "Executing datalytics collection for ${collectable.id}",
                "Prev Collection" to (lastCollectedAt?.run { dateFormatter.format(Date(this)) } ?: "Never")
            )
            collectionLastRunTimes[collectable.id] = TimeUtils.nowMillis()
        } catch (ex :Exception) {
            Plog.error(T_DATALYTICS, ex)
        }

        return collector.collect()
                .doOnNext { postOffice.sendMessage(it, sendPriority) }
                .toList()
                .doOnSuccess {
                    Plog.debug(T_DATALYTICS, "Data collected for ${collectable.id}","Data" to it)
                }
                .ignoreElement()
    }

    /**
     * Returns the [Collector] instance which performs data collection for the given [Collectable]
     */
    private fun getCollector(collectable: Collectable): Collector {
        return when(collectable) {
            is Collectable.AppList -> appListCollector
            is Collectable.AppIsHidden -> appIsHiddenCollector
            is Collectable.CellInfo -> cellularInfoCollector
            is Collectable.ConstantData -> constantDataCollector
            is Collectable.FloatingData -> floatingDataCollector
            is Collectable.VariableData -> variableDataCollector
            is Collectable.WifiList -> wifiListCollector
        }
    }
}