package co.pushe.plus.datalytics.messages

import co.pushe.plus.datalytics.Collectable
import co.pushe.plus.datalytics.CollectionController
import co.pushe.plus.datalytics.messages.downstream.GeofenceMessage
import co.pushe.plus.datalytics.messages.downstream.RemoveGeofenceMessage
import co.pushe.plus.datalytics.messages.downstream.ScheduleCollectionMessage
import co.pushe.plus.datalytics.geofence.GeofenceManager
import co.pushe.plus.messaging.PostOffice
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
