
-keep class co.pushe.plus.Pushe { *; }
-keep class co.pushe.plus.Pushe$Callback { *; }

-keep class co.pushe.plus.messaging.fcm.TokenState { *; }

-keep class co.pushe.plus.internal.PusheInitializer { *; }
-keep class co.pushe.plus.internal.SchedulersKt { *; }
-keep interface co.pushe.plus.internal.PusheServiceApi
-keep class co.pushe.plus.internal.PusheConfig { *; }
-keep class co.pushe.plus.internal.PusheInternals { *; }
-keep class co.pushe.plus.internal.PusheException { *; }
-keep class co.pushe.plus.internal.ComponentNotAvailableException { *; }
-keep interface co.pushe.plus.internal.PusheComponent
-keep class co.pushe.plus.internal.PusheMoshi { *; }
-keep class co.pushe.plus.internal.task.** { *; }

-keep class co.pushe.plus.utils.** { *; }
-keep class co.pushe.plus.utils.moshi.** { *; }

-keep interface co.pushe.plus.dagger.CoreComponent { *; }

-keep class co.pushe.plus.PusheManifestException { *; }

-keep class co.pushe.plus.messaging.PostOffice { *; }
-keep class co.pushe.plus.PusheLifecycle { *; }
-keep class co.pushe.plus.RegistrationManager { void performRegistration(); } # Used in Admin
-keep class co.pushe.plus.UserCredentials { *; } # Used in sentry module

-keep class co.pushe.plus.messaging.DownstreamMessageParser { *; }
-keep class co.pushe.plus.messaging.TypedUpstreamMessage { *; }
-keep class co.pushe.plus.messaging.SendableUpstreamMessage
-keep class co.pushe.plus.messaging.MessageMixin { *; }
-keep class co.pushe.plus.messaging.SendPriority
-keep class co.pushe.plus.messaging.MessageStore { java.util.List getAllMessages(); } # Used in sentry module (SentryReportTask)
-keep class co.pushe.plus.Constants { *; }
-keep class co.pushe.plus.LogTag { *; } # Not sure if required
-keep class co.pushe.plus.messages.MessageType { *; } # Not sure if required

# Hadi: Not why this is needed, but caused this warning in final app build if it doesn't exist:
# Warning: co.pushe.plus.sentry.tasks.SentryReportTask: can't find referenced class co.pushe.plus.messaging.StoredUpstreamMessage
-keep class co.pushe.plus.messaging.StoredUpstreamMessage { *; }

-keep class co.pushe.plus.messages.mixin.LocationMixin { *; }
-keep class co.pushe.plus.messages.mixin.WifiInfoMixin { *; }
-keep class co.pushe.plus.messages.mixin.CellInfoMixin { *; }
-keep class co.pushe.plus.messages.mixin.NetworkInfoMixin { *; }
-keep class co.pushe.plus.messages.common.ApplicationDetail { *; }

# So that the FcmHandler class is available incase the developer is using his own FcmService
-keep class co.pushe.plus.messaging.fcm.FcmHandler { *; }

# Used in sentry module (only token and tokenState fields are used, can unkeep the rest)
-keep class co.pushe.plus.messaging.fcm.FcmTokenStore { *; }

# Used in logCollection module to get the appId
-keepnames class co.pushe.plus.AppManifest { *; }

# Needed in order to be able to get the FirebaseMessaging constructor with reflection (in `FirebaseServiceManager`)
-keep class com.google.firebase.messaging.FirebaseMessaging { *; }

# Debug command classes. Note, the actual debug command implementations will be removed from the
# release builds (except in admin app) using the 'assumenosideeffects' option in the common proguard configs.
-keep class co.pushe.plus.internal.PusheDebug { *; }
-keep class co.pushe.plus.internal.DebugCommandProvider { *; }
-keep class co.pushe.plus.internal.DebugInput { *; }