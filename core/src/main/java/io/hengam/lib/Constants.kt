package io.hengam.lib

object Constants {
    const val BROADCAST_TOPIC = "broadcast"
    const val STORAGE_NAME = "hengam_store"
    const val HENGAM_COURIER_VALUE = "pushe"

    object HengamInfo {
        const val PACKAGE_NAME = "io.hengam.lib"
        const val VERSION_NAME = BuildConfig.VERSION_NAME
        const val VERSION_CODE = BuildConfig.VERSION_CODE
    }
}

object MessageFields {
    const val MESSAGE_ID = "message_id"
    const val REQUEST_DELIVERY = "request_delivery"
    const val COURIER = "courier"
}

object LogTag {
    const val T_INIT = "Initialization"
    const val T_DEBUG = "Debug"
    const val T_MESSAGE = "Messaging"
    const val T_FCM = "FCM"
    const val T_LASH = "Lash"
    const val T_REGISTER = "Registration"
    const val T_TOPIC = "Topic"
    const val T_TAG = "Tag"
    const val T_CONFIG = "Config"
    const val T_TASK = "Task"
    const val T_LOCATION = "Location"
    const val T_UTILS = "Utils"
}
