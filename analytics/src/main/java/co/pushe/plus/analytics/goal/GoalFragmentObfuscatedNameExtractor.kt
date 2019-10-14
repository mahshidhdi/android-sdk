package co.pushe.plus.analytics.goal

import co.pushe.plus.analytics.dagger.AnalyticsScope
import co.pushe.plus.utils.ApplicationInfoHelper
import javax.inject.Inject

/**
 * Since goal fragment names can be obfuscated and different depending on the app version,
 * this class provides a function to get the fragment's obfuscated name given the [GoalMessageFragmentInfo]
 *
 */
@AnalyticsScope
class GoalFragmentObfuscatedNameExtractor @Inject constructor(
        applicationInfoHelper: ApplicationInfoHelper
){
    private val appVersionCode: Long?  = applicationInfoHelper.getApplicationVersionCode()

    /**
     * @param goalMessageFragmentInfo the [GoalMessageFragmentInfo] of which the name is needed
     *
     * @return The goal-fragment's obfuscated name according to app version.
     * It cab be null if the app does not use proguard
     *
     */
    fun getFragmentObfuscatedName(goalMessageFragmentInfo: GoalMessageFragmentInfo): String? {
        return appVersionCode?.let { goalMessageFragmentInfo.obfuscatedNames[it] }
    }
}