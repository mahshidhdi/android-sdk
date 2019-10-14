package co.pushe.plus.datalytics.messages.downstream

import co.pushe.plus.messaging.DownstreamMessageParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Class to handle downstream message according to it's message type.
 * @param collectionMode with values: immediate, schedule
 * @param schedule The repeat interval for performing data collection in milliseconds. If set to any
 *                 value below zero, periodic data collection will be cancelled
 * @param sendImmediate is a boolean. True means collect and send immediately, False means check the [collectionMode].
 */
@JsonClass(generateAdapter = true)
class ScheduleCollectionMessage(
        @Json(name = "collection") val collectionMode: CollectionMode?,
        @Json(name = "schedule") val schedule: Long?,
        @Json(name = "send_immediate") val sendImmediate: Boolean?
) {
    class Parser(messageType: Int) : DownstreamMessageParser<ScheduleCollectionMessage> (
            messageType,
            { ScheduleCollectionMessage.jsonAdapter(it) }
    )

    companion object
}

enum class CollectionMode {
    @Json(name = "schedule") SCHEDULE,
    @Json(name = "immediate") IMMEDIATE
}
