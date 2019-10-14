package co.pushe.plus.logcollection.dagger

import android.content.Context
import co.pushe.plus.internal.PusheComponent
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.logcollection.LogCollector
import co.pushe.plus.logcollection.tasks.DbCleanerTask
import co.pushe.plus.logcollection.tasks.LogSyncerTask
import dagger.Component

@LogCollectionScope
@Component(modules = [LogCollectionModule::class])
interface LogCollectionComponent: PusheComponent {

    fun context() : Context
    fun logCollector(): LogCollector
    fun pusheConfig(): PusheConfig

    fun inject(logCollector: LogCollector)
    fun inject(logSyncerTask: LogSyncerTask)
    fun inject(logSyncerTask: DbCleanerTask)
}
