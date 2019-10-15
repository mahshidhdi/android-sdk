package io.hengam.lib.admin

import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatEditText
import android.view.View
import android.widget.Button
import android.widget.TextView
import io.hengam.lib.admin.analytics.activities.DuplicateFragmentActivity
import io.hengam.lib.admin.analytics.activities.NestedFragmentsActivity
import io.hengam.lib.analytics.Constants
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.ViewExtractor
import io.hengam.lib.analytics.goal.ViewGoalData
import io.hengam.lib.analytics.goal.ViewGoalTargetValue
import io.hengam.lib.analytics.goal.ViewGoalType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewExtractorTest {
    private lateinit var duplicateFragmentActivity: AppCompatActivity
    private lateinit var nestedFragmentsActivity: AppCompatActivity
    // the duplicated fragment directly inside the activity
    private lateinit var outerFragment: Fragment
    // the duplicated fragment inside another fragment
    private lateinit var innerFragment: Fragment

    private lateinit var parentFragment: Fragment


    private fun initializeDuplicateFragmentActivity() {
        duplicateFragmentActivity = Robolectric.setupActivity(DuplicateFragmentActivity::class.java)
        outerFragment = duplicateFragmentActivity.supportFragmentManager
                .findFragmentById(R.id.activityFragmentContainer)!!
        parentFragment = duplicateFragmentActivity.supportFragmentManager
                .findFragmentById(R.id.activityFragmentContainer2)!!

        val sampleTextViewInActivity = duplicateFragmentActivity.findViewById<TextView>(R.id.tvSample)
        sampleTextViewInActivity.text = "this is in activity"

        val sampleTextViewInOuterFragment = outerFragment.view!!.findViewById<TextView>(R.id.tvSample)
        sampleTextViewInOuterFragment.text = "this is in outer fragment"

        val button = parentFragment.view!!.findViewById<Button>(R.id.buttonFragmentInnerNext)
        button.performClick()

        innerFragment = parentFragment.childFragmentManager.findFragmentById(R.id.activityFragmentContainer)!!

        val sampleTextViewInInnerFragment = innerFragment.view!!.findViewById<TextView>(R.id.tvSample)
        sampleTextViewInInnerFragment.text = "this is in inner fragment"

    }

    private fun initializeNestedFragmentsActivity() {
        nestedFragmentsActivity = Robolectric.setupActivity(NestedFragmentsActivity::class.java)
        parentFragment = nestedFragmentsActivity.supportFragmentManager
                .findFragmentById(R.id.activityFragmentContainer2)!!

        val sampleTextViewInActivity = nestedFragmentsActivity.findViewById<TextView>(R.id.tvSample)
        sampleTextViewInActivity.text = "this is in activity"
    }

    @Test
    fun extractViews_ignoresViewGoalDataWithDifferentActivity() {
        initializeDuplicateFragmentActivity()

        ViewExtractor.extractView(viewGoal_simpleActivity_noFragment, duplicateFragmentActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }

        ViewExtractor.extractView(viewGoal_simpleActivity_noFragment, outerFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }

        ViewExtractor.extractView(viewGoal_simpleActivity_withFragment, outerFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }

        ViewExtractor.extractView(viewGoal_simpleActivity_withFragment, duplicateFragmentActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }
    }

    @Test
    fun extractViews_inAllCasesErrorsIfViewGoalIdIsWrong() {
        initializeDuplicateFragmentActivity()

        // viewGoalData without fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment_wrongId, duplicateFragmentActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue)

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongId, duplicateFragmentActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue)

        // viewGoalData without fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment_wrongId, outerFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue)

        // viewGoalData with fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongId, outerFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithoutFragment_getsTheViewFromActivity() {
        initializeDuplicateFragmentActivity()

        // viewGoalData without fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_noFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment, duplicateFragmentActivity)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in activity" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_noFragment.currentValue)

        // viewGoalData without fragment, calling with outerFragment
        viewGoalData_duplicateFragmentActivity_noFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment, outerFragment)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in activity" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_noFragment.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithFragment_errorsIfFragmentIdDoesNotExist() {
        initializeDuplicateFragmentActivity()

        // viewGoalData with wrong fragment id, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId, duplicateFragmentActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue)

        // viewGoalData with wrong fragment id, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId, outerFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithFragment_ignoresViewIfFragmentIsNotCurrentlyInLayout() {
        initializeNestedFragmentsActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, nestedFragmentsActivity)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, parentFragment)
            .test()
            .assertValue { it.javaClass == View::class.java }
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithFragment_findsViewFragmentIdInNestedFragments() {
        // testing when the fragment exists inside the activity
        initializeDuplicateFragmentActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, duplicateFragmentActivity)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in outer fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, parentFragment)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in outer fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)


        // testing when the fragment exists only inside another fragment
        initializeNestedFragmentsActivity()
        val button = parentFragment.view!!.findViewById<Button>(R.id.buttonFragmentInnerNext)
        button.performClick()

        innerFragment = parentFragment.childFragmentManager.findFragmentById(R.id.activityFragmentContainer)!!

        val sampleTextViewInInnerFragment = innerFragment.view!!.findViewById<TextView>(R.id.tvSample)
        sampleTextViewInInnerFragment.text = "this is in inner fragment"

        // viewGoalData with fragment, calling with activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, nestedFragmentsActivity)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in inner fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, parentFragment)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in inner fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)
    }

    @Test
    fun extractViewsByFragment_viewGoalWithFragment_givesTheFragmentPassedToItAPriority() {
        initializeDuplicateFragmentActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, duplicateFragmentActivity)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in outer fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with inner fragment
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, innerFragment)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in inner fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, outerFragment)
            .test()
            .assertValue {
                TextView::class.java.isAssignableFrom(it.javaClass) &&
                        "this is in outer fragment" == (it as TextView).text.toString()
            }
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)
    }
}

private val viewGoal_simpleActivity_noFragment =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "goalName",
                targetValues = listOf(),
                goalFragmentInfo = null
        )

private val viewGoal_simpleActivity_withFragment =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "editTextSample",
                activityClassName = "SimpleActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = GoalFragmentInfo(
                        "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                        "io.hengam.lib.admin.analytics.fragments.aa",
                        "activityFragmentContainer",
                        "DuplicateFragmentActivity"
                )
        )

private val viewGoalData_duplicateFragmentActivity_noFragment =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "DuplicateFragmentActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = null
        )

private val viewGoalData_duplicateFragmentActivity_withFragment =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "DuplicateFragmentActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = GoalFragmentInfo(
                        "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                        "io.hengam.lib.admin.analytics.fragments.aa",
                        "activityFragmentContainer",
                        "DuplicateFragmentActivity"
                )
        )

private val viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "DuplicateFragmentActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = GoalFragmentInfo(
                        "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                        "io.hengam.lib.admin.analytics.fragments.aa",
                        // wrong value
                        "activityfFragmentContainer",
                        "DuplicateFragmentActivity"
                )
        )


private val viewGoalData_nestedFragmentsActivity_withFragment =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSample",
                activityClassName = "NestedFragmentsActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = GoalFragmentInfo(
                        "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                        "io.hengam.lib.admin.analytics.fragments.aa",
                        "activityFragmentContainer",
                        "NestedFragmentsActivity"
                )
        )


private val viewGoalData_duplicateFragmentActivity_noFragment_wrongId =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSampple",
                activityClassName = "DuplicateFragmentActivity",
                parentGoalName = "goalName",
                targetValues = listOf(),
                goalFragmentInfo = null
        )

private val viewGoalData_duplicateFragmentActivity_withFragment_wrongId =
        ViewGoalData(
                viewType = ViewGoalType.TEXT_VIEW,
                viewID = "tvSampple",
                activityClassName = "DuplicateFragmentActivity",
                parentGoalName = "goalName",
                targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                goalFragmentInfo = GoalFragmentInfo(
                        "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                        "io.hengam.lib.admin.analytics.fragments.aa",
                        "activityFragmentContainer",
                        "DuplicateFragmentActivity"
                )
        )