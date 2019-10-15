package io.hengam.lib.dagger

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import io.hengam.lib.Constants
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.keyval.KVStorage
import io.hengam.lib.utils.keyval.SharedPreferencesStorage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import javax.inject.Inject

@Module
class CoreModule @Inject constructor(val context: Context) {
    @Provides @CoreScope
    fun providesContext(): Context = context

    @Provides @CoreScope
    fun providesSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(Constants.STORAGE_NAME, Context.MODE_PRIVATE)

    @Provides @CoreScope
    fun providesKVStorage(sharedPreferences: SharedPreferences): KVStorage =
            SharedPreferencesStorage(sharedPreferences)

    @Provides @CoreScope
    fun provideMoshi(): HengamMoshi {
        return HengamMoshi()
    }

    @Provides
    fun providesFusedLocationProviderClient(context: Context): FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

    @Provides
    fun providesTelephonyManager(context: Context): TelephonyManager? =
            context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager?
}