package io.hengam.lib.datalytics.messages.downstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.DownstreamMessageParser
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
