package co.pushe.plus.datalytics.collectors

import android.content.SharedPreferences
import co.pushe.plus.datalytics.messages.upstream.AppIsHiddenMessage
import co.pushe.plus.messaging.SendableUpstreamMessage
import co.pushe.plus.utils.ApplicationInfoHelper
import io.reactivex.Observable
import javax.inject.Inject

class AppIsHiddenCollector @Inject constructor(
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val sharedPreferences: SharedPreferences
) : Collector() {
    override fun collect(): Observable<SendableUpstreamMessage> {
        val sharedPreferenceKey = "is_app_hidden"
        val isAppHidden = applicationInfoHelper.isAppHidden()
        if (!sharedPreferences.contains(sharedPreferenceKey) ||
                isAppHidden != sharedPreferences.getBoolean(sharedPreferenceKey, false)) {
            sharedPreferences.edit().putBoolean(sharedPreferenceKey, isAppHidden).apply()
            return Observable.just(AppIsHiddenMessage(isAppHidden))
        }
        return Observable.empty()
    }
}