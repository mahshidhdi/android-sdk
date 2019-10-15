package io.hengam.lib.analytics

import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals

/**
 * This data class is used to save info of fragments seen in session lifeCycle
 */
class SessionFragmentInfo (
    val fragmentName: String,
    val fragmentId: String,
    val activityName: String,
    val parentFragment: SessionFragmentInfo? = null
){

    /**
     * parentIds are included in the container id to be able to distinguish between two different containers
     * and have different funnel flows for them, in cases like "two fragments with the same id,
     * in the same activity, but one directly inside the activity and the other inside another fragment"
     *
     */
    val containerId: String = "${activityName}_${fragmentId}_${getParentsIds()}"

    private fun getParentsIds(): String {
        return if (parentFragment == null) ""
        else "${parentFragment.fragmentId}_${parentFragment.getParentsIds()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionFragmentInfo

        if (fragmentName != other.fragmentName) return false
        if (fragmentId != other.fragmentId) return false
        if (activityName != other.activityName) return false
        if (containerId != other.containerId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fragmentName.hashCode()
        result = 31 * result + fragmentId.hashCode()
        result = 31 * result + activityName.hashCode()
        result = 31 * result + containerId.hashCode()
        return result
    }

    val parentsCount: Int = (parentFragment?.parentsCount ?: -1) + 1

    /**
     * Called when updating session fragmentFlows.
     * Checks whether the given fragment should be added to sessionFlow message according to the preDefined config
     * @see [sessionFragmentFlowEnabled]
     * @see [sessionFragmentFlowExceptionList]
     * @see [sessionFragmentFlowDepthLimit]
     */
    val shouldBeAddedToSession: Boolean by lazy {
        val coreComponent = HengamInternals.getComponent(CoreComponent::class.java)
        val hengamConfig = coreComponent?.config()
        if (hengamConfig == null) true
        else (hengamConfig.sessionFragmentFlowEnabled && parentsCount < hengamConfig.sessionFragmentFlowDepthLimit && !hengamConfig.sessionFragmentFlowExceptionList.contains(containerId)) ||
                (!hengamConfig.sessionFragmentFlowEnabled && hengamConfig.sessionFragmentFlowExceptionList.contains(containerId))
    }


}

/**
 * This data class is used to save info of fragments in goals and viewGoals
 * Since fragment names are obfuscated by proguard, two names are set for a GoalFragmentInfo, one the
 * actual name and the other obfuscated name for the app version
 *
 */
data class GoalFragmentInfo (
    val actualName: String,
    val obfuscatedName: String?,
    val fragmentId: String,
    val activityName: String
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoalFragmentInfo

        if (actualName != other.actualName) return false
        if (fragmentId != other.fragmentId) return false
        if (activityName != other.activityName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = actualName.hashCode()
        result = 31 * result + fragmentId.hashCode()
        result = 31 * result + activityName.hashCode()
        return result
    }
}
