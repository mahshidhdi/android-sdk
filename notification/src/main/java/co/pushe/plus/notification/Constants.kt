package co.pushe.plus.notification

object Constants {
    const val DEFAULT_CHANNEL_ID = "__pushe_notif_channel_id"
    const val DEFAULT_CHANNEL_NAME = "Default Channel"

    /**
     * The `DEFAULT_SILENT_CHANNEL_ID` is used for notifications which have custom sounds.
     * For these notifications we need to use a channel which has no system sound enabled.
     */
    const val DEFAULT_SILENT_CHANNEL_ID = "__pushe_notif_silent_channel_id"
    const val DEFAULT_SILENT_CHANNEL_NAME = "Alternative Channel"
}

object LogTag {
    const val T_NOTIF = "Notification"
    const val T_NOTIF_ACTION = "Notification Action"
}
