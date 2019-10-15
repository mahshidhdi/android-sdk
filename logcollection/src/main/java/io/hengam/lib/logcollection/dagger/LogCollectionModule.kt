package io.hengam.lib.logcollection.dagger

import android.content.Context
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import dagger.Module
import dagger.Provides
import javax.inject.Inject

@Module
class LogCollectionModule @Inject constructor(val context: Context) {
    @Provides @LogCollectionScope
    fun providesContext(): Context = context

    @Provides @LogCollectionScope
    fun providesHengamConfig(): HengamConfig = HengamConfig(context, HengamMoshi())
}