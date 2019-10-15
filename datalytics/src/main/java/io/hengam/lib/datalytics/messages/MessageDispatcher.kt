package io.hengam.lib.datalytics.messages

import io.hengam.lib.datalytics.Collectable
import io.hengam.lib.datalytics.CollectionController
import io.hengam.lib.datalytics.messages.downstream.GeofenceMessage
import io.hengam.lib.datalytics.messages.downstream.RemoveGeofenceMessage
import io.hengam.lib.datalytics.messages.downstream.ScheduleCollectionMessage
import io.hengam.lib.datalytics.geofence.GeofenceManager
import io.hengam.lib.messaging.PostOffice
import javax.inject.Inject


class MessageDispatcher @Inject constructor(
        private val postOffice: PostOffice,
        private val collectionController: CollectionController,
        private val geofenceManager: GeofenceManager
) {

    fun listenForMessages() {
        Collectable.allCollectables.forEach { collectable ->
            postOffice.mailBox(ScheduleCollectionMessage.Parser(collectable.messageType)) { message ->
                collectionController.handleScheduleCollectionMessage(collectable, message)
            }
        }

        postOffice.mailBox(GeofenceMessage.Parser(), geofenceManager::addOrUpdateGeofence)
        postOffice.mailBox(RemoveGeofenceMessage.Parser(), geofenceManager::removeGeofence)
    }
}
