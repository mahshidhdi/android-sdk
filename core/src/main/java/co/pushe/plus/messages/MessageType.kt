package co.pushe.plus.messages

object MessageType {
    object Upstream {
        const val DELIVERY = 1
        const val REGISTRATION = 10
        const val TOPIC_STATUS = 12
        const val CUSTOM_ID_UPDATE = 62
        const val TAG_SUBSCRIPTION = 64
    }

    object Downstream {
        const val UPDATE_TOPIC = 12
        const val REREGISTER = 23
        const val SENTRY_CONFIG = 25
        const val UPDATE_CONFIG = 61
        const val RUN_DEBUG_COMMAND = 63
    }

    /* 30 - 60 reserved for notification */
    object Notification {
        object Upstream {
            const val NOTIFICATION_ACTION = 1
            const val NOTIFICATION_REPORT = 8
            const val NOTIFICATION_USER_INPUT = 22
            const val SEND_NOTIF_TO_USER = 40
            const val APPLICATION_INSTALL = 45
            const val APPLICATION_DOWNLOAD = 46
        }

        object Downstream {
            const val NOTIFICATION = 1
            const val NOTIFICATION_ALTERNATE = 30
            const val NOTIFICATION_DIALOG_NOTIF = 31
            const val NOTIFICATION_WEBVIEW_NOTIF = 32
            const val NOTIFICATION_TURN_NOTIF_ON_OFF = 33
        }
    }


    object Datalytics {
        const val CONSTANT_DATA = 3
        const val VARIABLE_DATA = 4
        const val FLOATING_DATA = 5
        const val CELLULAR_DATA = 6
        const val APP_LIST = 14
        const val WIFI_LIST = 16
        const val IS_APP_HIDDEN = 29

        object Upstream {
            const val APP_UNINSTALL = 13
            const val APP_INSTALL = 15
            const val BOOT_COMPLETE = 21
            const val APP_USAGE = 18
            const val PUSH_NOTIF_RECEIVERS = 28
            const val SCREEN_ON_OFF = 24
            const val CONNECTIVITY_INFO = 26
        }

        object Downstream {
            const val IMEI_PERMISSION = 27
            const val GEOFENCE_ADD = 71
            const val GEOFENCE_REMOVE = 72
        }
    }

    /* 100 - 150 reserved for analytics */
    object Analytics {
        object Upstream {
            const val SESSION_INFO = 100
            const val GOAL_REACHED = 101
            const val EVENT = 102
            const val ECOMMERCE_EVENT = 103
            const val LEGACY_EVENT = 41
        }

        object Downstream {
            const val NEW_GOAL = 110
            const val REMOVE_GOAL = 111
            const val SESSION_CONFIG = 112
        }
    }


}