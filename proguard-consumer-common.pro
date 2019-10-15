-keepclassmembers enum io.hengam.lib.** { *; }
-keep class * extends io.hengam.lib.internal.HengamComponentInitializer {
    public void preInitialize(android.content.Context);
    public void postInitialize(android.content.Context);
}
-keep class io.hengam.lib.internal.HengamInitializer { *; }

-keep class io.hengam.lib.** extends io.hengam.lib.internal.task.HengamTask { *; }

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
-keepnames class io.hengam.lib.** extends java.lang.Exception