-keepclassmembers enum co.pushe.plus.** { *; }
-keep class * extends co.pushe.plus.internal.PusheComponentInitializer {
    public void preInitialize(android.content.Context);
    public void postInitialize(android.content.Context);
}
-keep class co.pushe.plus.internal.PusheInitializer { *; }

-keep class co.pushe.plus.** extends co.pushe.plus.internal.task.PusheTask { *; }

# -- Moshi --
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *

-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepnames @com.squareup.moshi.JsonClass class *

-dontwarn org.codehaus.mojo.animal_sniffer.*

# Keep Exception names
-keepnames class co.pushe.plus.** extends java.lang.Exception