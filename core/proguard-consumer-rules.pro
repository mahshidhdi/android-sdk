# Needed in order to be able to get the FirebaseMessaging constructor with reflection (in `FirebaseServiceManager`)
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }

-keep public class kotlin.reflect.jvm.internal.impl.** { public *; }