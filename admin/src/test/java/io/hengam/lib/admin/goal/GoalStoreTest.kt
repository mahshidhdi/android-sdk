package io.hengam.lib.admin.goal

import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import io.hengam.lib.admin.R
import io.hengam.lib.admin.analytics.activities.DuplicateFragmentActivity
import io.hengam.lib.admin.analytics.activities.SimpleActivity
import io.hengam.lib.analytics.Constants
import io.hengam.lib.analytics.GoalFragmentInfo
import io.hengam.lib.analytics.ViewExtractor
import io.hengam.lib.analytics.goal.*
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GoalStoreTest {
    private val moshi = HengamMoshi()
    private val context = RuntimeEnvironment.application
    private val goalFragmentNameExtractor = GoalFragmentObfuscatedNameExtractor(mockk(relaxed = true))
    private lateinit var simpleActivity: AppCompatActivity
    private lateinit var goalStore: GoalStore

    private val storage = HengamStorage(moshi, MockSharedPreference())

    private lateinit var duplicateFragmentActivity: AppCompatActivity
    // the duplicated fragment directly inside the activity
    private lateinit var outerFragment: Fragment
    // the duplicated fragment inside another fragment
    private lateinit var innerFragment: Fragment

    private lateinit var parentFragment: Fragment

    @Before
    fun init() {

        mockkObject(ViewExtractor)

        moshi.enhance { it.add(GoalFactory.build()) }
        goalStore = spyk(GoalStore(context, moshi, goalFragmentNameExtractor, storage))
    }

    private fun initializeSimpleActivity() {
        simpleActivity = Robolectric.setupActivity(SimpleActivity::class.java)
        val sampleTextView = simpleActivity.findViewById<TextView>(R.id.tvSample)
        sampleTextView.text = "some text"

        val sampleEditText = simpleActivity.findViewById<EditText>(R.id.editTextSample)
        sampleEditText.setText("some other text")

        val sampleSwitch = simpleActivity.findViewById<Switch>(R.id.switchSample)
        sampleSwitch.isChecked = true
    }

    private fun initializeDuplicateFragmentActivity() {
        duplicateFragmentActivity = Robolectric.setupActivity(DuplicateFragmentActivity::class.java)
        outerFragment = (duplicateFragmentActivity as AppCompatActivity).supportFragmentManager
                .findFragmentById(R.id.activityFragmentContainer)!!
        parentFragment = (duplicateFragmentActivity as AppCompatActivity).supportFragmentManager
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

    @Test
    fun updateViewGoalsByActivity_errorsIfViewGoalTypeIsWrong() {
        initializeSimpleActivity()

        every { ViewExtractor.extractView(viewGoalDataList_simpleActivity_wrongType[0], simpleActivity) } returns simpleActivity.findViewById(R.id.tvSample)
        every { ViewExtractor.extractView(viewGoalDataList_simpleActivity_wrongType[1], simpleActivity) } returns simpleActivity.findViewById(R.id.editTextSample)
        every { ViewExtractor.extractView(viewGoalDataList_simpleActivity_wrongType[2], simpleActivity) } returns simpleActivity.findViewById(R.id.switchSample)

        goalStore.updateViewGoalValues(viewGoalDataList_simpleActivity_wrongType, simpleActivity)

        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalDataList_simpleActivity_wrongType[0].currentValue)
        assertEquals("some other text", viewGoalDataList_simpleActivity_wrongType[1].currentValue)
        assertEquals("true", viewGoalDataList_simpleActivity_wrongType[2].currentValue)
    }

    @Test
    fun updateViewGoalsByFragment_errorsIfViewGoalTypeIsWrong() {
        initializeDuplicateFragmentActivity()

        every { ViewExtractor.extractView(viewGoalDataList_duplicateFragmentActivity_wrongType[0], outerFragment) } returns outerFragment.view!!.findViewById(R.id.tvSample)
        every { ViewExtractor.extractView(viewGoalDataList_duplicateFragmentActivity_wrongType[1], outerFragment) } returns outerFragment.view!!.findViewById(R.id.tvFragment)
        every { ViewExtractor.extractView(viewGoalDataList_duplicateFragmentActivity_wrongType[2], outerFragment) } returns duplicateFragmentActivity.findViewById(R.id.tvActivity)

        goalStore.updateViewGoalValues(viewGoalDataList_duplicateFragmentActivity_wrongType, outerFragment)

        assertEquals(Constants.ANALYTICS_ERROR_VIEW_GOAL, viewGoalDataList_duplicateFragmentActivity_wrongType[0].currentValue)
        assertEquals("(Duplicate Fragment)", viewGoalDataList_duplicateFragmentActivity_wrongType[1].currentValue)
        assertEquals("DUPLICATE FRAGMENT ACTIVITY", viewGoalDataList_duplicateFragmentActivity_wrongType[2].currentValue)
    }
}

private val viewGoalDataList_simpleActivity_wrongType =
        listOf(
                ViewGoalData(
                        viewType = ViewGoalType.SWITCH,
                        viewID = "tvSample",
                        activityClassName = "SimpleActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(),
                        goalFragmentInfo = null
                ),
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "editTextSample",
                        activityClassName = "SimpleActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                        goalFragmentInfo = null
                ),
                ViewGoalData(
                        viewType = ViewGoalType.SWITCH,
                        viewID = "switchSample",
                        activityClassName = "SimpleActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(ViewGoalTargetValue("targetValue", false)),
                        goalFragmentInfo = null
                )

        )

private val viewGoalDataList_duplicateFragmentActivity_wrongType =
        listOf(
                ViewGoalData(
                        viewType = ViewGoalType.SWITCH,
                        viewID = "tvSample",
                        activityClassName = "DuplicateFragmentActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(),
                        goalFragmentInfo = GoalFragmentInfo(
                                "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                                "io.hengam.lib.admin.analytics.fragments.c",
                                "activityFragmentContainer",
                                "DuplicateFragmentActivity"
                        )
                ),
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "tvFragment",
                        activityClassName = "DuplicateFragmentActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(),
                        goalFragmentInfo = GoalFragmentInfo(
                                "io.hengam.lib.admin.analytics.fragments.DuplicateFragment",
                                "io.hengam.lib.admin.analytics.fragments.c",
                                "activityFragmentContainer",
                                "DuplicateFragmentActivity"
                        )
                ),
                ViewGoalData(
                        viewType = ViewGoalType.TEXT_VIEW,
                        viewID = "tvActivity",
                        activityClassName = "DuplicateFragmentActivity",
                        parentGoalName = "goalName",
                        targetValues = listOf(),
                        goalFragmentInfo = null
                )

        )

