package io.hengam.lib.logcollection.network

import android.content.Context
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.ioThread
import io.hengam.lib.logcollection.DataProvider
import io.hengam.lib.logcollection.db.LogCollectionDatabaseImpl
import io.hengam.lib.utils.TimeUtils
import io.reactivex.Completable
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject

class NetworkManager @Inject constructor(
        private val context: Context,
        private val database: LogCollectionDatabaseImpl,
        private val dataProvider: DataProvider
) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(dataProvider.logCollectionBaseUrl)
        .addConverterFactory(MoshiConverterFactory.create())
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .build()

    fun synchronizeLogs(list: List<RequestLogItem>): Completable {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
        val deviceIDHelper = core?.deviceIdHelper()
        val fcmTokenStore = core?.fcmTokenStore()
        val appManifest = core?.appManifest()
        return database.getLogsCount()
            .flatMapCompletable {
                val logData = LogRequestData(
                        logs = list,
                        time = TimeUtils.nowMillis(),
                        instanceId = fcmTokenStore?.instanceId,
                        adId = deviceIDHelper?.advertisementId,
                        androidId = deviceIDHelper?.androidId,
                        token = fcmTokenStore?.token,
                        tokenStatus = fcmTokenStore?.tokenState.toString().toLowerCase(),
                        remainingLogCount = it,
                        extraInfo = dataProvider.logInfo(),
                        stats = dataProvider.getStats(),
                        appId = appManifest?.appId,
                        packageName = context.packageName
                )
                retrofit.create(LogCollectionNetworkApi::class.java)
                    .postLog(logData)
                        .doOnError {
                            if (logData.stats != null) {
                                dataProvider.resetStatsSyncTime()
                            }
                            if (logData.extraInfo != null) {
                                dataProvider.resetInfoSyncTime()
                            }
                        }
            }
                .subscribeOn(ioThread())
    }

    companion object
}