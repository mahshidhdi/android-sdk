package co.pushe.plus.datalytics.collectors

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import co.pushe.plus.BuildConfig
import co.pushe.plus.datalytics.LogTags.T_DATALYTICS
import co.pushe.plus.datalytics.messages.upstream.VariableDataMessage
import co.pushe.plus.messaging.SendableUpstreamMessage
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.DeviceInfoHelper
import co.pushe.plus.utils.log.Plog
import io.reactivex.Observable
import javax.inject.Inject

class VariableDataCollector @Inject constructor(
        private val context: Context,
        private val telephonyManager: TelephonyManager?,
        private val deviceInfoHelper: DeviceInfoHelper,
        private val applicationInfoHelper: ApplicationInfoHelper
) : Collector() {

    override fun collect(): Observable<SendableUpstreamMessage> = Observable.just(getVariableData())

    @SuppressLint("NewApi")
    fun getVariableData(): VariableDataMessage {
        return VariableDataMessage(
                osVersion = deviceInfoHelper.getOSVersion(),
                appVersion = applicationInfoHelper.getApplicationVersion() ?: "",
                appVersionCode = applicationInfoHelper.getApplicationVersionCode() ?: 0L,
                pusheVersion = BuildConfig.VERSION_NAME,
                pusheVersionCode = BuildConfig.VERSION_CODE.toString(),
                googlePlayVersion = getGooglePlayServicesVersionName(context),
                operator = getOperatorName(),
                operator2 = getSecondOperatorName(),
                installer = applicationInfoHelper.getInstallerPackageName()
        )
    }

    private fun getGooglePlayServicesVersionName(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo("com.google.android.gms", 0).versionName
        } catch (e: Exception) {
            Plog.warn(T_DATALYTICS, "Google play failed to be found.", e)
            null
        }
    }


    private fun getOperatorName() = telephonyManager?.simOperatorName

    @SuppressLint("MissingPermission")
    private fun getSecondOperatorName(): String? {
        val sm: SubscriptionManager?
        var operator2: String? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                sm = SubscriptionManager.from(context)
                if (sm!!.activeSubscriptionInfoCount == 2) {
                    operator2 = sm.activeSubscriptionInfoList[1].carrierName.toString()
                }
            }
        } catch (e: Exception) {
            if (e is SecurityException) {
                Plog.warn(T_DATALYTICS, "Could not detect second SIM information due to insufficient permissions")
            } else {
                Plog.warn(T_DATALYTICS, "Error detecting second SIM", e)
            }
        }
        return operator2
    }
}