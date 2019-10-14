package co.pushe.plus.datalytics.dagger

import android.content.Context
import co.pushe.plus.PusheLifecycle
import co.pushe.plus.TopicManager
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.datalytics.CollectorExecutor
import co.pushe.plus.datalytics.CollectorScheduler
import co.pushe.plus.datalytics.DebugCommands
import co.pushe.plus.datalytics.collectors.*
import co.pushe.plus.datalytics.messages.MessageDispatcher
import co.pushe.plus.datalytics.geofence.GeofenceManager
import co.pushe.plus.datalytics.tasks.DatalyticsCollectionTask
import co.pushe.plus.datalytics.tasks.InstallDetectorTask
import co.pushe.plus.internal.PusheComponent
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.messaging.PostOffice
import co.pushe.plus.utils.DeviceInfoHelper
import co.pushe.plus.utils.NetworkInfoHelper
import dagger.Component

@DatalyticsScope
@Component(modules = [DatalyticsModule::class], dependencies = [CoreComponent::class])
interface DatalyticsComponent : PusheComponent {

    fun context(): Context
    fun moshi(): PusheMoshi
    fun messageDispatcher(): MessageDispatcher
    fun postOffice(): PostOffice
    fun deviceInfoHelper(): DeviceInfoHelper
    fun topicManager(): TopicManager
    fun taskScheduler(): TaskScheduler
    fun networkInfoHelper(): NetworkInfoHelper
    fun pusheLifecycle(): PusheLifecycle
    fun debugCommands(): DebugCommands
    fun geofenceManager(): GeofenceManager

    // Scheduler
    fun collectorScheduler(): CollectorScheduler
    fun collectorExecutor(): CollectorExecutor

    //Collectors
    fun appIsHiddenCollector(): AppIsHiddenCollector
    fun appListCollector(): AppListCollector
    fun cellularInfoCollector(): CellularInfoCollector
    fun constantDataCollector(): ConstantDataCollector
    fun floatingDataCollector(): FloatingDataCollector
    fun variableDataCollector(): VariableDataCollector
    fun wifiListCollector(): WifiListCollector

    fun inject(appIsHiddenTask: DatalyticsCollectionTask)
    fun inject(installDetectorTask: InstallDetectorTask)

}