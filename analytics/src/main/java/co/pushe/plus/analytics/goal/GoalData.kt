package co.pushe.plus.analytics.goal

import co.pushe.plus.analytics.GoalFragmentInfo

data class ViewGoalData (
    var parentGoalName: String,
    val targetValues: List<ViewGoalTargetValue> = listOf(),
    var currentValue: String? = null,
    val viewType: ViewGoalType,
    val viewID: String,
    val activityClassName: String,
    val goalFragmentInfo: GoalFragmentInfo? = null
){
    override fun equals(other: Any?): Boolean {
        return (other is ViewGoalData) &&
                viewID == other.viewID &&
                activityClassName == other.activityClassName &&
                parentGoalName == other.parentGoalName &&
                ((goalFragmentInfo == null && other.goalFragmentInfo== null) ||
                        (goalFragmentInfo == other.goalFragmentInfo))
    }

    override fun hashCode(): Int {
        var result = viewID.hashCode()
        result = 31 * result + activityClassName.hashCode()
        if (goalFragmentInfo != null) result = 31 * result + goalFragmentInfo.hashCode()
        result = 31 * result + parentGoalName.hashCode()
        return result
    }
}

abstract class GoalData {
    abstract val goalType: GoalType
    abstract val name: String
    abstract val activityClassName: String
    abstract val viewGoalDataList: List<ViewGoalData>
}

data class ActivityReachGoalData (
    override val goalType: GoalType = GoalType.ACTIVITY_REACH,
    override val name: String,
    override val activityClassName: String,
    val activityFunnel: List<String> = mutableListOf(),
    override var viewGoalDataList: List<ViewGoalData> = listOf()
): GoalData() {

    override fun equals(other: Any?): Boolean {
        return other is GoalData &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}

data class FragmentReachGoalData (
    override val goalType: GoalType = GoalType.FRAGMENT_REACH,
    override val name: String,
    override val activityClassName: String,
    val goalFragmentInfo: GoalFragmentInfo,
    val fragmentFunnel: List<String> = mutableListOf(),
    override var viewGoalDataList: List<ViewGoalData> = listOf()
): GoalData() {

    override fun equals(other: Any?): Boolean {
        return other is GoalData &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}

data class ButtonClickGoalData (
    override val goalType: GoalType = GoalType.BUTTON_CLICK,
    override val name: String,
    override val activityClassName: String,
    val goalFragmentInfo: GoalFragmentInfo? = null,
    val buttonID: String,
    override var viewGoalDataList: List<ViewGoalData> = listOf()
): GoalData() {
    override fun equals(other: Any?): Boolean {
        return other is GoalData &&
                name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object
}


