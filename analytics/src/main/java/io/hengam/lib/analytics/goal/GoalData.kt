package io.hengam.lib.analytics.goal

import android.view.View
import android.widget.Switch
import android.widget.TextView
import io.hengam.lib.analytics.Constants
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.LogTag
import io.hengam.lib.utils.log.Plog
import io.reactivex.Completable

class ViewGoalData (
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


    /**
     * updates [currentValue] with the value of the given view
     *
     */
    fun updateValue(view: View): Completable {
        return Completable.fromCallable {
            var typeMisMatch = false
            when (viewType) {
                ViewGoalType.TEXT_VIEW -> {
                    if (view is TextView) {
                        currentValue = view.text.toString()
                    } else {
                        typeMisMatch = true
                    }
                }
                ViewGoalType.SWITCH -> {
                    if (view is Switch) {
                        currentValue = view.isChecked.toString()
                    } else {
                        typeMisMatch = true
                    }
                }
//                ViewGoalType.BUTTON -> {
//                    if (view is Button) {
//                        viewGoalData.currentValue = view.text.toString()
//                    } else {
//                        typeMisMatch = true
//                    }
//                }
            }
            if (typeMisMatch) {
                Plog.error(
                    LogTag.T_ANALYTICS, LogTag.T_ANALYTICS_GOAL, "Type mismatch occurred while processing updated view goal data, the view goal will be ignored",
                    "Goal Name" to parentGoalName,
                    "View Id" to viewID,
                    "Expected Type" to viewType,
                    "Actual Type" to view.javaClass.simpleName
                )
                currentValue = Constants.ANALYTICS_ERROR_VIEW_GOAL
            }
        }
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


