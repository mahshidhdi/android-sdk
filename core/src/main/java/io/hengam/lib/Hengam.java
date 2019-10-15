package io.hengam.lib;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.hengam.lib.dagger.CoreComponent;
import io.hengam.lib.internal.HengamDebug;
import io.hengam.lib.internal.HengamException;
import io.hengam.lib.internal.HengamInternals;
import io.hengam.lib.internal.HengamServiceApi;
import io.hengam.lib.messaging.fcm.FcmHandler;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;

public class Hengam {
    public final static String CORE = "core";
    public final static String NOTIFICATION = "notification";
    public final static String ANALYTICS = "analytics";
    public final static String DATALYTICS = "datalytics";
    public final static String LOG_COLLECTION = "log_collection";

    private static Handler uiThreadHandler = null;

    /**
     * @deprecated This method is deprecated and will be removed in future versions of Hengam.
     * Hengam initialization is performed automatically once the Hengam library is added to the
     * project, calling Hengam.initialize() has no further effect.
     */
    @Deprecated
    public static void initialize() {
        // Doesn't do anything, included for backward compatibility
    }

    /**
     * Check if Hengam registration has successfully been performed
     *
     * @return True if the user has been registered with Hengam and false if registration
     * has not been performed yet
     */
    public static boolean isRegistered() {
        CoreComponent core = getCoreComponentOrFail("Checking Hengam registration failed");
        if (core == null) return false;
        return core.registrationManager().isRegistered();
    }

    /**
     * Check if Hengam initialization has successfully completed
     *
     * @return True if Hengam is initialization is complete, false otherwise
     */
    public static boolean isInitialized() {
        CoreComponent core = getCoreComponentOrFail(null);
        if (core == null) return false;
        return core.hengamLifecycle().isPostInitComplete();
    }

    public static void setRegistrationCompleteListener(final Callback callback) {
        CoreComponent core = getCoreComponentOrFail("Setting Hengam registration listener failed");
        if (core == null) return;

        Disposable disposable = core.hengamLifecycle().waitForRegistration()
                .subscribe(new Action() {
                    public void run() {
                        runOnUiThread(callback);
                    }
                });
    }

    public static void setInitializationCompleteListener(final Callback callback) {
        CoreComponent core = getCoreComponentOrFail(null);
        if (core == null) return;

        Disposable disposable = core.hengamLifecycle().waitForPostInit()
                .subscribe(new Action() {
                    public void run() {
                        runOnUiThread(callback);
                    }
                });
    }

    public static void subscribeToTopic(final String topic, final Callback callback) {
        final CoreComponent core = getCoreComponentOrFail("Subscribing to topic failed");
        if (core == null) return;

        Disposable disposable = core.hengamLifecycle().waitForPostInit()
                .subscribe(new Action() {
                    @Override
                    public void run() throws Exception {
                        TopicManager topicManager = core.topicManager();

                        Disposable innerDisposable = topicManager.subscribe(topic, true)
                                .subscribe(new Action() {
                                    public void run() {
                                        runOnUiThread(callback);
                                    }
                                });
                    }
                });
    }

    public static void subscribeToTopic(final String topic) {
        subscribeToTopic(topic, null);
    }

    public static void addTags(final Map<String, String> tags) {
        final CoreComponent core = getCoreComponentOrFail("Setting the tag failed");
        if (core == null) return;

        int sizeOfSubscribedTags = core.tagManager().getSubscribedTags().size();
        if (tags.size() + sizeOfSubscribedTags > 10) {
            Log.w("Hengam", "You can't subscribe a user to more that 10 tags.");
            return;
        }
        Disposable disposable = core.hengamLifecycle().waitForPostInit()
                .subscribe(new Action() {
                    @Override
                    public void run() throws Exception {
                        TagManager tagManager = core.tagManager();
                        Disposable innerDisposable = tagManager.addTags(tags).subscribe();
                    }
                });
    }

    public static void removeTags(final List<String> tags) {
        final CoreComponent core = getCoreComponentOrFail("Setting the tag failed");
        if (core == null) return;

        Disposable disposable = core.hengamLifecycle().waitForPostInit()
                .subscribe(new Action() {
                    @Override
                    public void run() throws Exception {
                        TagManager tagManager = core.tagManager();
                        Disposable innerDisposable = tagManager.removeTags(tags).subscribe();
                    }
                });
    }

    /**
     * @return all tags that user has subscribed,
     *      Null, if anything went wrong.
     */
    public static Map<String, String> getSubscribedTags() {
        final CoreComponent core = getCoreComponentOrFail("Setting the tag failed");
        if (core == null) return null;
        try {
            return core.tagManager().getSubscribedTags();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @deprecated use {@link #subscribeToTopic(String)} instead
     */
    @Deprecated
    public static void subscribe(final String topic) {
        subscribeToTopic(topic);
    }

    public static void unsubscribeFromTopic(final String topic, final Callback callback) {
        final CoreComponent core = getCoreComponentOrFail("Unsubscribing from topic failed");
        if (core == null) return;

        Disposable disposable = core.hengamLifecycle().waitForPostInit()
                .subscribe(new Action() {
                    @Override
                    public void run() throws Exception {
                        TopicManager topicManager = core.topicManager();

                        Disposable innerDisposable = topicManager.unsubscribe(topic, true)
                                .subscribe(new Action() {
                                    public void run() {
                                        runOnUiThread(callback);
                                    }
                                });
                    }
                });
    }

    public static void unsubscribeFromTopic(final String topic) {
        unsubscribeFromTopic(topic, null);
    }

    /**
     * Get all the topics you have subscribed.
     * @return a list of string having all subscribed topics.
     */
    public static List<String> getSubscribedTopics() {
        CoreComponent core = getCoreComponentOrFail(null);
        if (core == null) return new ArrayList<>();
        Iterator<String> actual = core.topicManager().getSubscribedTopics().iterator();
        List<String> list = new ArrayList<>();
        while (actual.hasNext()) {
            String s = actual.next();
            if (s.endsWith("_" + core.appManifest().appId)) {
                list.add(s.replace("_" + core.appManifest().appId, ""));
            }
        }
        return list;
    }

    /**
     * @deprecated use {@link #unsubscribeFromTopic(String)} instead
     */
    @Deprecated
    public static void unsubscribe(final String topic) {
        unsubscribeFromTopic(topic);
    }

    /**
     * Get the Google Advertisement Id associated with the device.
     *
     * This method should only be called after Hengam initialization has completed.
     *
     * @return The Google Advertisement Id as a String or null if Hengam initialized has not been
     * completed
     */
    public static String getGoogleAdvertisingId() {
        final CoreComponent core = getCoreComponentOrFail("Getting Google Advertisement Id failed");
        if (core == null) return null;

        if (!Hengam.isInitialized()) {
            return null;
        } else {
            return core.deviceIdHelper().getAdvertisementId();
        }
    }

    public static String getAndroidId() {
        final CoreComponent core = getCoreComponentOrFail("Getting Android Id failed");
        if (core == null) return null;

        return core.deviceIdHelper().getAndroidId();
    }

    /**
     * Set a custom ID for user. The user id can be used for sending notifications to users.
     *
     * @param id The user ID to use for this user. If `null` or blank, the user id will be removed.
     */
    public static void setCustomId(@Nullable String id) {
        final CoreComponent core = getCoreComponentOrFail("Setting Custom Id failed");
        if (core == null) return;

        if (id == null || id.isEmpty()) {
            core.userCredentials().setCustomId("");
        } else {
            core.userCredentials().setCustomId(id);
        }
    }

    /**
     * Retrieve the user's user id
     *
     * @return The user id as a string or a blank string if no user id has been set for the user. If
     * Hengam initialization has failed a null value will be returned.
     */
    public static String getCustomId() {
        final CoreComponent core = getCoreComponentOrFail("Getting Custom Id failed");
        if (core == null) return null;
        return core.userCredentials().getCustomId();
    }

    /**
     * Set the user email address. The email address can be used for sending notifications to users.
     *
     * @param email The email to set for this user. If `null` or blank, the user id will be removed.
     * @return true if the email was successfully set or false if could not set email because Hengam
     *         has not been initialized or the email address is invalid.
     */
    public static boolean setUserEmail(@Nullable String email) {
        final CoreComponent core = getCoreComponentOrFail("Setting user email failed");
        if (core == null) return false;

        if (email == null || email.isEmpty()) {
            core.userCredentials().setEmail("");
            return true;
        } else {
            Pattern emailRegex = Pattern.compile(
                    "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = emailRegex.matcher(email.trim());
            if (!matcher.matches()) {
                Log.w("Hengam", "Attempted to set an invalid email address '" + email + "'. The email will not be set.");
                return false;
            }
            core.userCredentials().setEmail(email.trim());
            return true;
        }
    }

    /**
     * Retrieve the user's set email address
     *
     * @return The email address as a string or a blank string if no user email has been set for the
     * user. If Hengam initialization has failed a null value will be returned.
     */
    public static String getUserEmail() {
        final CoreComponent core = getCoreComponentOrFail("Getting user email failed");
        if (core == null) return null;
        return core.userCredentials().getEmail();
    }

    /**
     * Set the user phone number. The phone number can be used for sending notifications to users.
     *
     * @param phoneNumber The phone number to set for this user. If `null` or blank, the user id will be removed.
     * @return true if the phone number was successfully set or false if could not set email because
     *         Hengam has not been initialized or the phone number is invalid.
     */
    public static boolean setUserPhoneNumber(@Nullable String phoneNumber) {
        final CoreComponent core = getCoreComponentOrFail("Settings user phone number failed");
        if (core == null) return false;

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            core.userCredentials().setPhoneNumber("");
            return true;
        } else {
            Pattern phoneRegex = Pattern.compile(
                    "^[0-9۰-۹+() ]+$", Pattern.CASE_INSENSITIVE);
            Matcher matcher = phoneRegex.matcher(phoneNumber.trim());
            if (!matcher.matches()) {
                Log.w("Hengam", "Attempted to set an invalid phone number '" + phoneNumber + "'. The phone number will not be set.");
                return false;
            }
            core.userCredentials().setPhoneNumber(phoneNumber);
            return true;
        }
    }

    /**
     * Retrieve the user's set phone number
     *
     * @return The phone number as a string or a blank string if no user id has been set for the user.
     * If Hengam initialization has failed a null value will be returned.
     */
    public static String getUserPhoneNumber() {
        final CoreComponent core = getCoreComponentOrFail("Getting user phone number failed");
        if (core == null) return null;
        return core.userCredentials().getPhoneNumber();
    }


    /**
     * @deprecated The Hengam Id value is deprecated and will stop being supported in the near future.
     * Please use the Android Id, Google Advertisement Id or a Custom Id instead.
     */
    @Deprecated
    public static String getHengamId() {
        final CoreComponent core = getCoreComponentOrFail("Getting Hengam Id");
        if (core == null) return "";
        return core.deviceIdHelper().getHengamId();
    }

    @Nullable
    public static <T extends HengamServiceApi> T getHengamService(Class<T> serviceClass) {
        T serviceApi = HengamInternals.INSTANCE.getService(serviceClass);
        if (serviceApi == null) {
            Log.e("Hengam", "The Hengam service " + serviceClass.getSimpleName()
                    + " is not available. This either means you have provided an invalid Hengam Service name, " +
                    "Hengam was not initialized successfully or " +
                    "you have not added the Hengam service to your gradle dependencies.");
        }
        return serviceApi;
    }

    @Nullable
    public static HengamServiceApi getHengamService(String serviceName) {
        HengamServiceApi serviceApi = HengamInternals.INSTANCE.getService(serviceName);
        if (serviceApi == null) {
            Log.e("Hengam", "The Hengam service " + serviceName
                    + " is not available. This either means you have provided an invalid Hengam Service name, " +
                    "Hengam was not initialized successfully or " +
                    "you have not added the Hengam service to your gradle dependencies.");
        }
        return serviceApi;
    }

    public static HengamDebug debugApi() {
        return new HengamDebug();
    }

    public static FcmHandler getFcmHandler() {
        CoreComponent core = getCoreComponentOrFail("Getting FcmHandler failed");
        if (core == null) {
            return new FcmHandler() {
                public boolean onMessageReceived(RemoteMessage remoteMessage) {
                    String courier = remoteMessage.getData().get(MessageFields.COURIER);
                    return courier != null && courier.toLowerCase().equals(Constants.HENGAM_COURIER_VALUE);
                }
                public void onDeletedMessages() {}
                public void onMessageSent(String messageId) {}
                public void onSendError(String messageId, Exception exception) {}
                public void onNewToken(String token) {}
            };
        }
        return core.fcmHandler();
    }

    private static CoreComponent getCoreComponentOrFail(String failMessage) {
        CoreComponent core = HengamInternals.INSTANCE.getComponent(CoreComponent.class);

        if (core == null && failMessage != null && !failMessage.isEmpty()) {
            Log.e("Hengam", failMessage, new HengamException("Unable to obtain the Hengam core component." +
                    " This probably means Hengam initialization has failed."));
        }

        return core;
    }

    private static void runOnUiThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (uiThreadHandler == null) {
            uiThreadHandler = new Handler(Looper.getMainLooper());
        }
        uiThreadHandler.post(runnable);
    }

    private static void runOnUiThread(final Callback callback) {
        if (callback == null) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onComplete();
            }
        });
    }

    public interface Callback {
        void onComplete();
    }
}
