package io.hengam.lib.notification;

import android.support.annotation.NonNull;

import java.util.Map;

public interface HengamNotificationListener {
    void onNotification(@NonNull NotificationData notification);
    void onCustomContentNotification(@NonNull Map<String, Object> customContent);
    void onNotificationClick(@NonNull NotificationData notification);
    void onNotificationDismiss(@NonNull NotificationData notification);
    void onNotificationButtonClick(@NonNull NotificationButtonData button, @NonNull NotificationData notification);
}


