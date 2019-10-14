package co.pushe.plus.messaging.fcm;

import com.google.firebase.messaging.RemoteMessage;

public interface FcmHandler {
    boolean onMessageReceived(RemoteMessage remoteMessage);
    void onDeletedMessages();
    void onMessageSent(String messageId);
    void onSendError(String messageId, Exception exception);
    void onNewToken(String token);
}
