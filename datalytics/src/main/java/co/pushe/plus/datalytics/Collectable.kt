package co.pushe.plus.datalytics

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.Time
import co.pushe.plus.utils.days
import co.pushe.plus.utils.hours
import com.squareup.moshi.JsonClass

sealed class Collectable(
        val id: String,
        val messageType: Int,
        val requiresNetwork: Boolean,
        val defaultSettings: CollectorSettings,
        val configKey: String = "collectable_$messageType"
) {

    object AppIsHidden : Collectable(
            id = "hidden_app",
            messageType = MessageType.Datalytics.IS_APP_HIDDEN,
            requiresNetwork = false,
            defaultSettings = CollectorSettings(days(2), hours(4), SendPriority.BUFFER, 1)
    )

    object AppList : Collectable(
            id = "app_list",
            messageType = MessageType.Datalytics.APP_LIST,
            requiresNetwork = true,
            defaultSettings = CollectorSettings(days(14), days(2), SendPriority.BUFFER, 5)
    )

    object CellInfo : Collectable(
            id = "cell_info",
            messageType = MessageType.Datalytics.CELLULAR_DATA,
            requiresNetwork = false,
            defaultSettings = CollectorSettings(hours(6), hours(2), SendPriority.BUFFER, 1)
    )

    object ConstantData : Collectable(
            id = "constant_data",
            messageType = MessageType.Datalytics.CONSTANT_DATA,
            requiresNetwork = false,
            defaultSettings = CollectorSettings(days(30), days(2), SendPriority.BUFFER, 1)
    )

    object FloatingData : Collectable(
            id = "floating_data",
            messageType = MessageType.Datalytics.FLOATING_DATA,
            requiresNetwork = false,
            defaultSettings = CollectorSettings(hours(6), hours(2), SendPriority.BUFFER, 1)
    )

    object VariableData : Collectable(
            id = "variable_data",
            messageType = MessageType.Datalytics.VARIABLE_DATA,
            requiresNetwork = false,
            defaultSettings = CollectorSettings(days(2), hours(4), SendPriority.BUFFER, 1)
    )

    object WifiList : Collectable(
            id = "wifi_list",
            messageType = MessageType.Datalytics.WIFI_LIST,
            requiresNetwork = true,
            defaultSettings = CollectorSettings(hours(6), hours(2), SendPriority.BUFFER, 1)
    )

    companion object {
        private val collectableTypeMap = mutableMapOf<Int, Collectable>()
        private val collectableIdMap = mutableMapOf<String, Collectable>()

        val allCollectables: Collection<Collectable> by lazy {
            listOf(AppIsHidden, AppList, CellInfo, ConstantData, FloatingData, VariableData, WifiList) // And AppList
        }

        init {
            allCollectables.forEach { collectable ->
                collectableTypeMap[collectable.messageType] = collectable
                collectableIdMap[collectable.id] = collectable
            }
        }

        fun getCollectableByMessageType(messageType: Int) = collectableTypeMap[messageType]
        fun getCollectableById(collectableId: String) = collectableIdMap[collectableId]
    }
}

/**
 * Holds data collection settings for a particular [Collectable]
 *
 * @param repeatInterval The repeat interval for performing collection. If null, periodic data
 *                       collection will be disabled
 * @param sendPriority Determines the [SendPriority] with which the message will be sent with once
 *                     the data is collected
 */
@JsonClass(generateAdapter = true)
class CollectorSettings(
        val repeatInterval: Time,
        val flexTime: Time,
        val sendPriority: SendPriority,
        val maxAttempts: Int
) {
    companion object
}
