-keepattributes SourceFile,LineNumberTable
-keepparameternames

-keepclassmembers enum io.hengam.lib.** { *; }
-keepclassmembers enum androidx.work.** { *; }

-keep class io.hengam.lib.internal.HengamComponentInitializer { *; } # This is needed so the one below works in consumer rules
-keep class * extends io.hengam.lib.internal.HengamComponentInitializer {
    public void preInitialize(android.content.Context);
    public void postInitialize(android.content.Context);
}

# Keep Hengam Tasks and it's members, this is needed for WorkManager to work
-keep class io.hengam.lib.** extends io.hengam.lib.internal.task.HengamTask { *; }

-keep,allowobfuscation @interface com.squareup.moshi.ToJson
-keep,allowobfuscation @interface com.squareup.moshi.FromJson
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep class * extends com.squareup.moshi.JsonAdapter
# -keep class com.squareup.moshi.Moshi

# Keep Exception names
-keepnames class io.hengam.lib.** extends java.lang.Exception