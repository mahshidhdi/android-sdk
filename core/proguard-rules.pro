
-keep class io.hengam.lib.Hengam { *; }
-keep class io.hengam.lib.Hengam$Callback { *; }

-keep class io.hengam.lib.messaging.fcm.TokenState { *; }

-keep class io.hengam.lib.internal.HengamInitializer { *; }
-keep class io.hengam.lib.internal.SchedulersKt { *; }
-keep interface io.hengam.lib.internal.HengamServiceApi
-keep class io.hengam.lib.internal.HengamConfig { *; }
-keep class io.hengam.lib.internal.HengamInternals { *; }
-keep class io.hengam.lib.internal.HengamException { *; }
-keep class io.hengam.lib.internal.ComponentNotAvailableException { *; }
-keep interface io.hengam.lib.internal.HengamComponent
-keep class io.hengam.lib.internal.HengamMoshi { *; }
-keep class io.hengam.lib.internal.task.** { *; }

-keep class io.hengam.lib.utils.** { *; }
-keep class io.hengam.lib.utils.moshi.** { *; }

-keep interface io.hengam.lib.dagger.CoreComponent { *; }

-keep class io.hengam.lib.HengamManifestException { *; }

-keep class io.hengam.lib.messaging.PostOffice { *; }
-keep class io.hengam.lib.HengamLifecycle { *; }
-keep class io.hengam.lib.RegistrationManager { void performRegistration(); } # Used in Admin
-keep class io.hengam.lib.UserCredentials { *; } # Used in sentry module

-keep class io.hengam.lib.messaging.DownstreamMessageParser { *; }
-keep class io.hengam.lib.messaging.TypedUpstreamMessage { *; }
-keep class io.hengam.lib.messaging.SendableUpstreamMessage
-keep class io.hengam.lib.messaging.MessageMixin { *; }
-keep class io.hengam.lib.messaging.SendPriority
-keep class io.hengam.lib.messaging.MessageStore { java.util.List getAllMessages(); } # Used in sentry module (SentryReportTask)
-keep class io.hengam.lib.Constants { *; }
-keep class io.hengam.lib.LogTag { *; } # Not sure if required
-keep class io.hengam.lib.messages.MessageType { *; } # Not sure if required

# Hadi: Not why this is needed, but caused this warning in final app build if it doesn't exist:
# Warning: io.hengam.lib.sentry.tasks.SentryReportTask: can't find referenced class io.hengam.lib.messaging.StoredUpstreamMessage
-keep class io.hengam.lib.messaging.StoredUpstreamMessage { *; }

-keep class io.hengam.lib.messages.mixin.LocationMixin { *; }
-keep class io.hengam.lib.messages.mixin.WifiInfoMixin { *; }
-keep class io.hengam.lib.messages.mixin.CellInfoMixin { *; }
-keep class io.hengam.lib.messages.mixin.NetworkInfoMixin { *; }
-keep class io.hengam.lib.messages.common.ApplicationDetail { *; }

# So that the FcmHandler class is available incase the developer is using his own FcmService
-keep class io.hengam.lib.messaging.fcm.FcmHandler { *; }

# Used in sentry module (only token and tokenState fields are used, can unkeep the rest)
-keep class io.hengam.lib.messaging.fcm.FcmTokenStore { *; }

# Used in logCollection module to get the appId
-keepnames class io.hengam.lib.AppManifest { *; }

# Needed in order to be able to get the FirebaseMessaging constructor with reflection (in `FirebaseServiceManager`)
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }

# Debug command classes. Note, the actual debug command implementations will be removed from the
# release builds (except in admin app) using the 'assumenosideeffects' option in the common proguard configs.
-keep class io.hengam.lib.internal.HengamDebug { *; }
-keep class io.hengam.lib.internal.DebugCommandProvider { *; }
-keep class io.hengam.lib.internal.DebugInput { *; }