package co.pushe.plus.notification;

public class UserNotification {
    IdType idType;
    String id;

    String advancedJson;
    String title;
    String content;
    String bigTitle;
    String bigContent;
    String imageUrl;
    String iconUrl;
    String notifIcon;
    String customContent;

    private UserNotification(IdType idType, String id) {
        this.id = id;
        this.idType = idType;
    }

    public static UserNotification withAdvertisementId(String advertisementId) {
        return new UserNotification(IdType.AD_ID, advertisementId);
    }

    public static UserNotification withAndroidId(String androidId) {
        return new UserNotification(IdType.ANDROID_ID, androidId);
    }

    public static UserNotification withCustomId(String customId) {
        return new UserNotification(IdType.CUSTOM_ID, customId);
    }

    public UserNotification setTitle(String title) {
        this.title = title;
        return this;
    }

    public UserNotification setContent(String content) {
        this.content = content;
        return this;
    }

    public UserNotification setBigTitle(String bigTitle) {
        this.bigTitle = bigTitle;
        return this;
    }

    public UserNotification setBigContent(String bigContent) {
        this.bigContent = bigContent;
        return this;
    }

    public UserNotification setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public UserNotification setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    public UserNotification setNotifIcon(String notifIcon) {
        this.notifIcon = notifIcon;
        return this;
    }

    public UserNotification setCustomContent(String customContent) {
        this.customContent = customContent;
        return this;
    }

    public UserNotification setAdvancedNotification(String advancedNotificationJson) {
        this.advancedJson = advancedNotificationJson;
        return this;
    }

    public enum IdType {
        AD_ID,
        ANDROID_ID,
        CUSTOM_ID
    }
}


