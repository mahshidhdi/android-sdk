package co.pushe.plus.datalytics.utils

import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.publicIPApis
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.HttpUtils
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Types
import io.reactivex.Maybe
import io.reactivex.Observable
import java.io.IOException
import javax.inject.Inject

class NetworkUtils @Inject constructor(
        private val moshi: PusheMoshi,
        private val httpUtils: HttpUtils,
        private val pusheConfig: PusheConfig
) {
    /**
     * Get the public IP of the android device.
     * The public IP is found by making HTTP requests to public ip APIs. A list of public APIs will
     * be tried until one of them succeeds.
     *
     * This method is not run on any particular thread. It should be subscribed on the ioThread when
     * being called.
     *
     * @return A [Maybe] object which will emit a [PublicIpInfo] object or will complete with no
     *         result it is not able to retrieve the public IP from any of the APIs.
     */
    fun getPublicIp(): Maybe<PublicIpInfo> {
        return Observable.fromIterable(pusheConfig.publicIPApis)
                .flatMap { apiUrl ->
                    httpUtils.request(apiUrl)
                            .map { response -> parseIpApiResponse(response.trim()) }
                            .toObservable()
                            .doOnError { e ->
                                Plog.warn.message("Getting public ip info failed")
                                        .withTag(T_DATALYTICS)
                                        .withError(e)
                                        .withData("URL", apiUrl)
                                        .useLogCatLevel(LogLevel.DEBUG)
                                        .log()
                            }
                            .onErrorResumeNext(Observable.empty<PublicIpInfo>())
                }
                .firstElement()
    }

    private fun parseIpApiResponse(response: String): PublicIpInfo {
        var ip = ""
        var isp: String? = null
        if (response.startsWith("{")) {
            val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
            val json = mapAdapter.fromJson(response)
                    ?: throw JsonDataException("Invalid Json")

            if (json.containsKey("ip") && json["ip"] != null) {
                ip = json["ip"] as String? ?: throw IOException("No 'ip' key available in response")
                if (!Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").matches(ip)) {
                    throw IOException("Invalid IP received from IP API: $ip")
                }
            }

            // ORG provides isp
            if (json.containsKey("org") && json["org"] != null) {
                isp = json["org"] as String?
            }
        } else if (Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").matches(response)) {
            ip = response
        } else {
            throw  IOException("Unknown response received from IP API: $response")
        }
        return PublicIpInfo(ip, isp)
    }

    data class PublicIpInfo(
            val ip: String,
            val isp: String? = null
    )
}
