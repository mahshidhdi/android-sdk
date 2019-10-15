package io.hengam.lib.datalytics.dagger

import android.content.Context
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.TopicManager
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.datalytics.CollectorExecutor
import io.hengam.lib.datalytics.CollectorScheduler
import io.hengam.lib.datalytics.DebugCommands
import io.hengam.lib.datalytics.collectors.*
import io.hengam.lib.datalytics.messages.MessageDispatcher
import io.hengam.lib.datalytics.geofence.GeofenceManager
import io.hengam.lib.datalytics.tasks.DatalyticsCollectionTask
import io.hengam.lib.datalytics.tasks.InstallDetectorTask
import io.hengam.lib.internal.HengamComponent
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.utils.DeviceInfoHelper
import io.hengam.lib.utils.NetworkInfoHelper
import dagger.Component

@DatalyticsScope
@Component(modules = [DatalyticsModule::class], dependencies = [CoreComponent::class])
interface DatalyticsComponent : HengamComponent {

    fun context(): Context
    fun moshi(): HengamMoshi
    fun messageDispatcher(): MessageDispatcher
    fun postOffice(): PostOffice
    fun deviceInfoHelper(): DeviceInfoHelper
    fun topicManager(): TopicManager
    fun taskScheduler(): TaskScheduler
    fun networkInfoHelper(): NetworkInfoHelper
    fun hengamLifecycle(): HengamLifecycle
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