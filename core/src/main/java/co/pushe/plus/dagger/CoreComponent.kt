package co.pushe.plus.dagger

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import co.pushe.plus.*
import co.pushe.plus.internal.PusheComponent
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.messages.MessageDispatcher
import co.pushe.plus.messaging.CourierLounge
import co.pushe.plus.messaging.MessageStore
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.messaging.UpstreamSender
import co.pushe.plus.messaging.fcm.FcmHandlerImpl
import co.pushe.plus.messaging.fcm.FcmMessaging
import co.pushe.plus.messaging.fcm.FcmService
import co.pushe.plus.messaging.fcm.FcmServiceManager
import co.pushe.plus.messaging.fcm.FcmTokenStore
import co.pushe.plus.tasks.RegistrationTask
import co.pushe.plus.tasks.UpstreamSenderTask
import co.pushe.plus.utils.*
import dagger.Component

@CoreScope
@Component(modules = [CoreModule::class])
interface CoreComponent : PusheComponent {
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
    fun moshi(): PusheMoshi
    fun messageDispatcher(): MessageDispatcher
    fun messageStore(): MessageStore
    fun fcmServiceManager(): FcmServiceManager
    fun appManifest(): AppManifest
    fun upstreamSender(): UpstreamSender
    fun pusheLifecycle(): PusheLifecycle
    fun fcmMessaging(): FcmMessaging
    fun geoUtils(): GeoUtils
    fun userCredentials(): UserCredentials
    fun httpUtils(): HttpUtils
    fun networkInfoHelper(): NetworkInfoHelper
    fun storage(): PusheStorage
    fun config(): PusheConfig
    fun telephonyManager(): TelephonyManager?
    fun debugCommands(): DebugCommands
    fun applicationInfoHelper(): ApplicationInfoHelper
    fun fcmHandler(): FcmHandlerImpl
    fun fcmTokenStore(): FcmTokenStore

    fun inject(service: FcmService)
    fun inject(upstreamSenderTask: UpstreamSenderTask)
    fun inject(registrationTask: RegistrationTask)
}
