package co.pushe.plus.logcollection.dagger

import android.content.Context
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import dagger.Module
import dagger.Provides
import javax.inject.Inject

@Module
class LogCollectionModule @Inject constructor(val context: Context) {
    @Provides @LogCollectionScope
    fun providesContext(): Context = context

    @Provides @LogCollectionScope
    fun providesPusheConfig(): PusheConfig = PusheConfig(context, PusheMoshi())
}