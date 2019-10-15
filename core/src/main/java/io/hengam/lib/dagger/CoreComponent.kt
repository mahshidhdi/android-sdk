package io.hengam.lib.dagger

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import io.hengam.lib.*
import io.hengam.lib.internal.HengamComponent
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messages.MessageDispatcher
import io.hengam.lib.messaging.CourierLounge
import io.hengam.lib.messaging.MessageStore
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.UpstreamSender
import io.hengam.lib.messaging.fcm.FcmHandlerImpl
import io.hengam.lib.messaging.fcm.FcmMessaging
import io.hengam.lib.messaging.fcm.FcmService
import io.hengam.lib.messaging.fcm.FcmServiceManager
import io.hengam.lib.messaging.fcm.FcmTokenStore
import io.hengam.lib.tasks.RegistrationTask
import io.hengam.lib.tasks.UpstreamSenderTask
import io.hengam.lib.utils.*
import dagger.Component

@CoreScope
@Component(modules = [CoreModule::class])
interface CoreComponent : HengamComponent {
    fun postOffice(): PostOffice
    fun context(): Context
    fun courierLounge(): CourierLounge
    fun registrationManager(): RegistrationManager
    fun sharedPreferences(): SharedPreferences
    fun deviceIdHelper(): DeviceIDHelper
    fun deviceInfoHelper(): DeviceInfoHelper
    fun taskScheduler(): TaskScheduler
    fun topicManager(): TopicManager
    fun tagManager(): TagManager
    fun moshi(): HengamMoshi
    fun messageDispatcher(): MessageDispatcher
    fun messageStore(): MessageStore
    fun fcmServiceManager(): FcmServiceManager
    fun appManifest(): AppManifest
    fun upstreamSender(): UpstreamSender
    fun hengamLifecycle(): HengamLifecycle
    fun fcmMessaging(): FcmMessaging
    fun geoUtils(): GeoUtils
    fun userCredentials(): UserCredentials
    fun httpUtils(): HttpUtils
    fun networkInfoHelper(): NetworkInfoHelper
    fun storage(): HengamStorage
    fun config(): HengamConfig
    fun telephonyManager(): TelephonyManager?
    fun debugCommands(): DebugCommands
    fun applicationInfoHelper(): ApplicationInfoHelper
    fun fcmHandler(): FcmHandlerImpl
    fun fcmTokenStore(): FcmTokenStore

    fun inject(service: FcmService)

    fun inject(upstreamSenderTask: UpstreamSenderTask)
    fun inject(registrationTask: RegistrationTask)
}
