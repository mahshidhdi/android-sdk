-keepattributes SourceFile,LineNumberTable
-keepparameternames

-keepclassmembers enum co.pushe.plus.** { *; }

-keep class co.pushe.plus.internal.PusheComponentInitializer { *; } # This is needed so the one below works in consumer rules
-keep class * extends co.pushe.plus.internal.PusheComponentInitializer {
    public void preInitialize(android.content.Context);
    public void postInitialize(android.content.Context);
}

# Keep Pushe Tasks and it's members, this is needed for WorkManager to work
-keep class co.pushe.plus.** extends co.pushe.plus.internal.task.PusheTask { *; }

-keep,allowobfuscation @interface com.squareup.moshi.ToJson
-keep,allowobfuscation @interface com.squareup.moshi.FromJson
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
-keep class * extends com.squareup.moshi.JsonAdapter
# -keep class com.squareup.moshi.Moshi

# Keep Exception names
-keepnames class co.pushe.plus.** extends java.lang.Exception