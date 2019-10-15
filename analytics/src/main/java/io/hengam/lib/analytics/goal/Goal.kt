package io.hengam.lib.analytics.goal

import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import io.hengam.lib.utils.moshi.RuntimeJsonAdapterFactory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import kotlin.reflect.KClass

enum class GoalType {
    @Json(name = "activity_reach") ACTIVITY_REACH,
    @Json(name = "fragment_reach") FRAGMENT_REACH,
    @Json(name = "button_click") BUTTON_CLICK;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}

abstract class Goal {
    abstract val goalType: GoalType
    abstract val name: String
    abstract val activityClassName: String
    abstract var viewGoals: Set<ViewGoal>
}

@JsonClass(generateAdapter = true)
data class ActivityReachGoal (
    @Json(name = "goal_type") override val goalType: GoalType = GoalType.ACTIVITY_REACH,
    @Json(name = "name") override val name: String,
    @Json(name = "activity") override val activityClassName: String,
    @Json(name = "funnel") val activityFunnel: List<String> = mutableListOf(),
    @Json (name = "view_goals") override var viewGoals: Set<ViewGoal> = setOf()
): Goal() {

    override fun equals(other: Any?): Boolean {
        return other is Goal &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}

/**
 * FragmentReachGoal funnel should contain target fragment-flow in the exact container of the goal fragment
 * before reaching the goal fragment itself
 */
@JsonClass(generateAdapter = true)
data class FragmentReachGoal (
    @Json(name = "goal_type") override val goalType: GoalType = GoalType.FRAGMENT_REACH,
    @Json(name = "name") override val name: String,
    @Json(name = "activity") override val activityClassName: String,
    @Json(name = "fragment_info") val goalMessageFragmentInfo: GoalMessageFragmentInfo,
    @Json(name = "funnel") val fragmentFunnel: List<String> = mutableListOf(),
    @Json(name = "view_goals") override var viewGoals: Set<ViewGoal> = setOf()
): Goal() {

    override fun equals(other: Any?): Boolean {
        return other is Goal &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}

@JsonClass(generateAdapter = true)
data class ButtonClickGoal (
    @Json(name = "goal_type") override val goalType: GoalType = GoalType.BUTTON_CLICK,
    @Json(name = "name") override val name: String,
    @Json(name = "activity") override val activityClassName: String,
    @Json(name = "fragment_info") val goalMessageFragmentInfo: GoalMessageFragmentInfo? = null,
    @Json(name = "id") val buttonID: String,
    @Json(name = "view_goals") override var viewGoals: Set<ViewGoal> = setOf()
): Goal() {
    override fun equals(other: Any?): Boolean {
        return other is Goal &&
               name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}

/**
 * This class is used to get fragmentInfo of fragments in goals and viewGoals received in Downstream messages
 *
 * Since fragment names are obfuscated by proguard, two groups of names are set for a fragmentInfo, one the
 * actual name and the other a map consisting of an obfuscated name for each app version
 * (for supporting different versions of same app)
 *
 */
@JsonClass(generateAdapter = true)
class GoalMessageFragmentInfo (
    @Json(name = "actual_name") val actualName: String,
    @Json(name = "obfuscated_names") val obfuscatedNames: Map<Long, String> = mutableMapOf(),
    @Json(name = "id") val fragmentId: String
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoalMessageFragmentInfo

        if (actualName != other.actualName) return false
        if (obfuscatedNames != other.obfuscatedNames) return false
        if (fragmentId != other.fragmentId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fragmentId.hashCode()
        result = 31 * result + actualName.hashCode()
        result = 31 * result + obfuscatedNames.hashCode()
        return result
    }

    override fun toString(): String {
        return "GoalFragmentInfo(fragmentName='$actualName', obfuscatedNames='$obfuscatedNames', fragmentId='$fragmentId')"
    }
}

enum class ViewGoalType(val type: KClass<out View>) {
    @Json(name = "text") TEXT_VIEW (TextView::class),
    @Json(name = "switch") SWITCH (Switch::class)
//    @Json(name = "button") BUTTON (Button::class)
//    @Json(name = "list") LIST
}

@JsonClass(generateAdapter = true)
data class ViewGoalTargetValue (
    @Json(name = "value") val targetValue: String,
    @Json(name = "ignore_case") val ignoreCase: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ViewGoal (
    @Json(name = "type") val viewType: ViewGoalType,
    @Json(name = "target_values") val targetValues: List<ViewGoalTargetValue> = listOf(),
    @Json(name = "id") val viewID: String,
    @Json(name = "activity") val activityClassName: String,
    @Json(name = "fragment_info") val goalMessageFragmentInfo: GoalMessageFragmentInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        return other is ViewGoal &&
                activityClassName == other.activityClassName &&
                viewID == other.viewID &&
                ((goalMessageFragmentInfo == null && other.goalMessageFragmentInfo == null) ||
                        goalMessageFragmentInfo == other.goalMessageFragmentInfo)
    }

    override fun hashCode(): Int {
        var result = viewID.hashCode()
        result = 31 * result + activityClassName.hashCode()
        result = 31 * result + (goalMessageFragmentInfo?.hashCode() ?: 0)
        return result
    }

    companion object {
        fun isValidView(view: View): Boolean{
            return ViewGoalType.values().any {
                it.type.javaObjectType.isAssignableFrom(view.javaClass)
            }
        }
    }
}

object GoalFactory {
    fun build(): JsonAdapter.Factory {
        val factory =
            RuntimeJsonAdapterFactory.of(Goal::class.java, "goal_type")

        factory.registerSubtype(GoalType.ACTIVITY_REACH.toString(), ActivityReachGoal::class.java) { ActivityReachGoal.jsonAdapter(it) }
        factory.registerSubtype(GoalType.FRAGMENT_REACH.toString(), FragmentReachGoal::class.java) { FragmentReachGoal.jsonAdapter(it) }
        factory.registerSubtype(GoalType.BUTTON_CLICK.toString(), ButtonClickGoal::class.java) { ButtonClickGoal.jsonAdapter(it) }

        return factory
    }
}