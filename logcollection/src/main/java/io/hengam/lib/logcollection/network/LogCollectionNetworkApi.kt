package io.hengam.lib.logcollection.network

import io.reactivex.Completable
import retrofit2.http.Body
import retrofit2.http.POST

interface LogCollectionNetworkApi {

    @POST("logs/")
    fun postLog (
        @Body requestBody: LogRequestData
    ): Completable
}