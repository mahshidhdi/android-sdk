package co.pushe.plus.datalytics.collectors

import android.annotation.SuppressLint
import co.pushe.plus.datalytics.appListBlackListUrl
import co.pushe.plus.datalytics.messages.upstream.ApplicationDetailsMessage
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messaging.SendableUpstreamMessage
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.HttpUtils
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import javax.inject.Inject


class AppListCollector @Inject constructor(
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val httpUtils: HttpUtils,
        private val pusheMoshi: PusheMoshi,
        private val pusheConfig: PusheConfig
) : Collector() {

    override fun collect(): Observable<out SendableUpstreamMessage> = installedApplications


    val installedApplications: Observable<ApplicationDetailsMessage>
        @SuppressLint("CheckResult")
        get() {
            return httpUtils.request(pusheConfig.appListBlackListUrl)
                    .onErrorResumeNext { ex ->
                        if (ex is HttpUtils.HttpError || ex is IOException) {
                            if (isFinalAttempt) {
                                Single.just("[]")
                            } else {
                                Single.error(CollectionRetryRequiredError("Request for app list black list failed", ex))
                            }
                        } else {
                            Single.error(ex)
                        }
                    }
                    .map {
                        val adapter: JsonAdapter<List<String>> = pusheMoshi.moshi.adapter(Types.newParameterizedType(List::class.java, String::class.java))
                        adapter.fromJson(it.replace("\\", "\\\\"))
                    }
                    .map { packageList -> packageList.map { if (it.startsWith("^")) it.toRegex() else it } as List<Any> }
                    .flatMapObservable { data ->
                        val apps = applicationInfoHelper.getInstalledApplications()
                                .filter { app -> !data.any { b -> if (b is Regex) b.matches(app.packageName ?: "") else b == app.packageName } }
                                .map { app -> ApplicationDetailsMessage.fromApplicationDetail(app) }
                                .sortedWith(Comparator { o1, o2 ->
                                    if (o1.installationTime != null && o2.installationTime != null) {
                                        o1.installationTime.compareTo(o2.installationTime)
                                    } else {
                                        0
                                    }
                                })
                        Observable.fromIterable(apps)
                    }
        }
}
