package io.hengam.lib.analytics

/**
 * This data class is used to save info of fragments seen in session lifeCycle
 */
data class SessionFragmentInfo (
    val fragmentName: String,
    val fragmentId: String,
    val activityName: String
)

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
