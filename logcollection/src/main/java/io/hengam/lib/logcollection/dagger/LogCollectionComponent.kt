package io.hengam.lib.logcollection.dagger

import android.content.Context
import io.hengam.lib.internal.HengamComponent
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.logcollection.LogCollector
import io.hengam.lib.logcollection.tasks.DbCleanerTask
import io.hengam.lib.logcollection.tasks.LogSyncerTask
import dagger.Component

@LogCollectionScope
@Component(modules = [LogCollectionModule::class])
interface LogCollectionComponent: HengamComponent {

    fun context() : Context
    fun logCollector(): LogCollector
    fun hengamConfig(): HengamConfig

    fun inject(logCollector: LogCollector)
    fun inject(logSyncerTask: LogSyncerTask)
    fun inject(logSyncerTask: DbCleanerTask)
}
