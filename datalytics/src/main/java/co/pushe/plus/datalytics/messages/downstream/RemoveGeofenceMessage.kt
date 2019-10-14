package co.pushe.plus.datalytics.messages.downstream

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.DownstreamMessageParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class RemoveGeofenceMessage(
        @Json(name = "id") val id: String
) {
    class Parser : DownstreamMessageParser<RemoveGeofenceMessage>(
            MessageType.Datalytics.Downstream.GEOFENCE_REMOVE,
            { RemoveGeofenceMessageJsonAdapter(it) }
    )
}
