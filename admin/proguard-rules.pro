# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Debug Commands
-keep class co.pushe.plus.internal.PusheDebug { *; }
-keep class co.pushe.plus.internal.DebugInput { *; }
-keep class co.pushe.plus.internal.DebugCommandProvider { *; }
-keep class * extends co.pushe.plus.internal.DebugCommandProvider { *; }


# -- Okio --
-dontwarn okio.**
# -- End Okio --

# -- OkHttp --
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
# -- End OkHttp --

# -- Retrofit --
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep public class * extends android.support.v4.
-keep public class * extends android.support.v4.app.Fragment

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**
# -- End Retrofit --

-keep class android.support.v7.widget.SearchView { *; }

# Not sure why these are needed but proguard gives warnings for sentry even if the sentry module
# is removed. If this is somehow resolved we can remove these
-dontwarn javax.naming.**
-dontwarn javax.servlet.**