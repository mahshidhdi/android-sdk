package co.pushe.plus.admin

import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import co.pushe.plus.admin.analytics.activities.DuplicateFragmentActivity
import co.pushe.plus.admin.analytics.activities.NestedFragmentsActivity
import co.pushe.plus.analytics.Constants
import co.pushe.plus.analytics.GoalFragmentInfo
import co.pushe.plus.analytics.ViewExtractor
import co.pushe.plus.analytics.goal.ViewGoalData
import co.pushe.plus.analytics.goal.ViewGoalTargetValue
import co.pushe.plus.analytics.goal.ViewGoalType
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

        var extractedView = ViewExtractor.extractView(viewGoal_simpleActivity_noFragment, duplicateFragmentActivity)
        assertEquals(null, extractedView)

        extractedView = ViewExtractor.extractView(viewGoal_simpleActivity_noFragment, outerFragment)
        assertEquals(null, extractedView)

        extractedView = ViewExtractor.extractView(viewGoal_simpleActivity_withFragment, outerFragment)
        assertEquals(null, extractedView)

        extractedView = ViewExtractor.extractView(viewGoal_simpleActivity_withFragment, duplicateFragmentActivity)
        assertEquals(null, extractedView)
    }

    @Test
    fun extractViews_inAllCasesErrorsIfViewGoalIdIsWrong() {
        initializeDuplicateFragmentActivity()

        // viewGoalData without fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment_wrongId, duplicateFragmentActivity)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue)
        assertNull(extractedView)

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongId, duplicateFragmentActivity)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue)
        assertNull(extractedView)

        // viewGoalData without fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment_wrongId, outerFragment)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_noFragment_wrongId.currentValue)
        assertNull(extractedView)

        // viewGoalData with fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongId, outerFragment)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongId.currentValue)
        assertNull(extractedView)
    }

    @Test
    fun extractViews_viewGoalWithoutFragment_getsTheViewFromActivity() {
        initializeDuplicateFragmentActivity()

        // viewGoalData without fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_noFragment.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment, duplicateFragmentActivity)
        assertNull(viewGoalData_duplicateFragmentActivity_noFragment.currentValue)
        assertEquals("this is in activity", (extractedView as TextView).text)

        // viewGoalData without fragment, calling with outerFragment
        viewGoalData_duplicateFragmentActivity_noFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_noFragment, outerFragment)
        assertNull(viewGoalData_duplicateFragmentActivity_noFragment.currentValue)
        assertEquals("this is in activity", (extractedView as TextView).text)
    }

    @Test
    fun extractViews_viewGoalWithFragment_errorsIfFragmentIdDoesNotExist() {
        initializeDuplicateFragmentActivity()

        // viewGoalData with wrong fragment id, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId, duplicateFragmentActivity)
        assertNull(extractedView)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue)

        // viewGoalData with wrong fragment id, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId, outerFragment)
        assertNull(extractedView)
        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalData_duplicateFragmentActivity_withFragment_wrongFragmentId.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithFragment_ignoresViewIfFragmentIsNotCurrentlyInLayout() {
        initializeNestedFragmentsActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, nestedFragmentsActivity)
        assertNull(extractedView)
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, parentFragment)
        assertNull(extractedView)
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)
    }

    @Test
    fun extractViews_viewGoalWithFragment_findsViewFragmentIdInNestedFragments() {
        // testing when the fragment exists inside the activity
        initializeDuplicateFragmentActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, duplicateFragmentActivity)
        assertEquals("this is in outer fragment", (extractedView as TextView).text.toString())
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, parentFragment)
        assertEquals("this is in outer fragment", (extractedView as TextView).text.toString())
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
        extractedView = ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, nestedFragmentsActivity)
        assertEquals("this is in inner fragment", (extractedView as TextView).text.toString())
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with different fragment in same activity
        viewGoalData_nestedFragmentsActivity_withFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_nestedFragmentsActivity_withFragment, parentFragment)
        assertEquals("this is in inner fragment", (extractedView as TextView).text.toString())
        assertNull(viewGoalData_nestedFragmentsActivity_withFragment.currentValue)
    }

    @Test
    fun extractViewsByFragment_viewGoalWithFragment_givesTheFragmentPassedToItAPriority() {
        initializeDuplicateFragmentActivity()

        // viewGoalData with fragment, calling with activity
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        var extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, duplicateFragmentActivity)
        assertNotNull(extractedView)
        assertEquals("this is in outer fragment", (extractedView as TextView).text.toString())

        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)
        // viewGoalData with fragment, calling with inner fragment
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, innerFragment)
        assertEquals("this is in inner fragment", (extractedView as TextView).text.toString())
        assertNull(viewGoalData_duplicateFragmentActivity_withFragment.currentValue)

        // viewGoalData with fragment, calling with outer fragment
        viewGoalData_duplicateFragmentActivity_withFragment.currentValue = null
        extractedView = ViewExtractor.extractView(viewGoalData_duplicateFragmentActivity_withFragment, outerFragment)
        assertEquals("this is in outer fragment", (extractedView as TextView).text.toString())
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
                        "co.pushe.plus.admin.analytics.fragments.DuplicateFragment",
                        "co.pushe.plus.admin.analytics.fragments.aa",
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
                        "co.pushe.plus.admin.analytics.fragments.DuplicateFragment",
                        "co.pushe.plus.admin.analytics.fragments.aa",
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
                        "co.pushe.plus.admin.analytics.fragments.DuplicateFragment",
                        "co.pushe.plus.admin.analytics.fragments.aa",
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
                        "co.pushe.plus.admin.analytics.fragments.DuplicateFragment",
                        "co.pushe.plus.admin.analytics.fragments.aa",
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
                        "co.pushe.plus.admin.analytics.fragments.DuplicateFragment",
                        "co.pushe.plus.admin.analytics.fragments.aa",
                        "activityFragmentContainer",
                        "DuplicateFragmentActivity"
                )
        )