package io.hengam.lib.analytics.session

import android.content.SharedPreferences
import io.hengam.lib.analytics.*
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.analytics.messages.downstream.FragmentFlowInfo
import io.hengam.lib.analytics.utils.CurrentTimeGenerator
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.test.TestUtils.turnOffThreadAssertions
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionFlowManagerTest {
    private lateinit var sessionFlowManager: SessionFlowManager

    private val sharedPreferences: SharedPreferences = MockSharedPreference()
    private val currentTimeGenerator: CurrentTimeGenerator = mockk(relaxed = true)
    private val postOffice: PostOffice = mockk(relaxed = true)
    private val hengamConfig: HengamConfig = mockk(relaxed = true)
    private val moshi = HengamMoshi()
    private val storageFactory = HengamStorage(moshi, sharedPreferences)

    @Before
    fun setUp() {
        turnOffThreadAssertions()

        io.hengam.lib.extendMoshi(moshi)
        io.hengam.lib.analytics.extendMoshi(moshi)

        sessionFlowManager = SessionFlowManager(currentTimeGenerator, postOffice, hengamConfig, mockk(relaxed = true), storageFactory)
        every { hengamConfig.sessionFragmentFlowDepthLimit } returns 5
        every { hengamConfig.sessionFragmentFlowEnabled } returns true
        every { hengamConfig.sessionFragmentFlowExceptionList } returns emptyList()
    }

    private fun setSessionFlow(sessionFlow: MutableList<SessionActivity>) {
        sessionFlowManager.sessionFlow.clear()
        sessionFlowManager.sessionFlow.addAll(sessionFlow)
    }

    @Test
    fun  updateSessionFlow_addsASessionActivityToSessionFlowInCaseOfNewActivity() {
        // empty SessionFlow
        sessionFlowManager.updateSessionFlow("firstActivity")
        assertEquals(1, sessionFlowManager.sessionFlow.size)

        sessionFlowManager.updateSessionFlow("secondActivity")
        assertEquals(2, sessionFlowManager.sessionFlow.size)

        // already existing activity
        sessionFlowManager.updateSessionFlow("firstActivity")
        assertEquals(3, sessionFlowManager.sessionFlow.size)

        // same activity as last, should not be added
        sessionFlowManager.updateSessionFlow("firstActivity")
        assertEquals(3, sessionFlowManager.sessionFlow.size)
    }

    @Test
    fun  updateSessionFlow_updatesStartTimeIfSameActivityAsLast() {
        // startTime is set
        every { currentTimeGenerator.getCurrentTime() } returns 400
        sessionFlowManager.updateSessionFlow("firstActivity")
        assertEquals(400, sessionFlowManager.sessionFlow.last().startTime)

        every { currentTimeGenerator.getCurrentTime() } returns 1000
        sessionFlowManager.updateSessionFlow("firstActivity")
        assertEquals(1000, sessionFlowManager.sessionFlow.last().startTime)

    }


    @Test
    fun getFragmentSessionParent_returnsNullIfSessionFlowIsEmpty(){
        val fragmentParent = sessionFlowManager.getFragmentSessionParent(
            mutableListOf(),
            listOf(
                SessionFragmentInfo(
                    "ParentFragment",
                    "parentFragmentId",
                    "ActivityName"
                )
            ))
        assertEquals(null, fragmentParent)
    }

    @Test
    fun getFragmentSessionParent_returnsLastSessionActivityIfParentFragmentsIsEmpty(){
        sessionFlowManager.updateSessionFlow("FirstActivity")
        val fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sessionFlowManager.sessionFlow,
            listOf())
        assert(fragmentParent is SessionActivity)
        assertEquals(sessionFlowManager.sessionFlow.last().name, (fragmentParent as SessionActivity).name)
    }

    @Test
    fun getFragmentSessionParent_returnsNullIfSessionFlowIsNotUpdatedWithParentFragments(){
        var fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithoutFragment,
            listOf(
                SessionFragmentInfo(
                    "ParentFragment",
                    "parentFragmentId",
                    "ActivityName"
                )
            ))
        assertEquals(null, fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithOneLevelFragment,
            listOf(
                SessionFragmentInfo(
                    "ParentFragment",
                    "parentFragmentId",
                    "ActivityName"
                )
            ))
        assertEquals(null, fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithOneLevelFragment,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity")
            ))
        assertEquals(null, fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithTwoLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity"),
                SessionFragmentInfo("ThirdParent", "id22", "SecondActivity")
            ))
        assertEquals(null, fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithThreeLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity"),
                SessionFragmentInfo("ThirdParent", "id22", "SecondActivity"),
                SessionFragmentInfo("FourthParent", "id32", "SecondActivity")
            ))
        assertEquals(null, fragmentParent)
    }

    @Test
    fun getFragmentSessionParent_returnsDirectParentOfFragmentInTheSessionFlow(){
        var fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithFourLevelFragments,
            listOf())
        assertEquals(sampleSessionFlowWithFourLevelFragments.last(), fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithFourLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity")
            ))
        assertEquals(sampleSessionFlowWithFourLevelFragments.last().fragmentFlows["id02"]!!.last(), fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithFourLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity")
            ))
        assertEquals(sampleSessionFlowWithFourLevelFragments.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last(), fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithFourLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity"),
                SessionFragmentInfo("ThirdParent", "id22", "SecondActivity")
            ))
        assertEquals(sampleSessionFlowWithFourLevelFragments.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last(), fragmentParent)

        fragmentParent = sessionFlowManager.getFragmentSessionParent(
            sampleSessionFlowWithFourLevelFragments,
            listOf(
                SessionFragmentInfo("FirstParent", "id02", "SecondActivity"),
                SessionFragmentInfo("SecondParent", "id12", "SecondActivity"),
                SessionFragmentInfo("ThirdParent", "id22", "SecondActivity"),
                SessionFragmentInfo("FourthParent", "id32", "SecondActivity")
            ))
        assertEquals(sampleSessionFlowWithFourLevelFragments.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id32"]!!.last(), fragmentParent)
    }

    @Test
    fun  updateSessionFlow_addsAFragmentWithAllParentsToSessionFlowIfNotParent() {
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)
        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.size)
    }

    @Test
    fun  updateSessionFlow_doesNotDuplicateAlreadyAddedParents() {
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)
        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )

        assertEquals(2, sessionFlowManager.sessionFlow.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows.size)

        assertEquals(2, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.size)

        assertEquals(1, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id32"]!!.size)
    }

    @Test
    fun  updateSessionFlow_updatesStartTimeIfFragmentAlreadyAdded() {
        // startTime is set
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)
        every { currentTimeGenerator.getCurrentTime() } returns 500
        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )
        assertEquals(500, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().startTime)



        every { currentTimeGenerator.getCurrentTime() } returns 1500
        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )
        assertEquals(1500, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last()
            .fragmentFlows["id22"]!!.last()
            .fragmentFlows["id31"]!!.last().startTime)
    }

    @Test
    fun updateSessionFlow_ifIsParentDoesNotAddFragment_updatesStartTime() {
        // startTime is set
        setSessionFlow(sampleSessionFlowWithFourLevelFragments)
        every { currentTimeGenerator.getCurrentTime() } returns 2500
        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity")
            ),
            true
        )
        assertEquals(2500, sessionFlowManager.sessionFlow.last()
            .fragmentFlows["id02"]!!.last()
            .fragmentFlows["id12"]!!.last().startTime)
    }

    @Test
    fun  updateActivityDuration_errorsIfNotSameActivityAsLastInSession() {
        // empty SessionFlow
        sessionFlowManager.updateActivityDuration("firstActivity")
        assertEquals(0, sessionFlowManager.sessionFlow.size)

        sessionFlowManager.updateSessionFlow("firstActivity")
        sessionFlowManager.updateActivityDuration("secondActivity")
        assertEquals(1, sessionFlowManager.sessionFlow.size)
    }

    @Test
    fun updateActivityDuration_updatesDurationIfSameActivityAsLast() {
        every { currentTimeGenerator.getCurrentTime() } returns 400
        sessionFlowManager.updateSessionFlow("firstActivity")
        every { currentTimeGenerator.getCurrentTime() } returns 1000
        sessionFlowManager.updateActivityDuration("firstActivity")

        assertEquals(600, sessionFlowManager.sessionFlow.last().duration)

        every { currentTimeGenerator.getCurrentTime() } returns 1500
        sessionFlowManager.updateSessionFlow("firstActivity")
        every { currentTimeGenerator.getCurrentTime() } returns 2000
        sessionFlowManager.updateActivityDuration("firstActivity")

        assertEquals(1100, sessionFlowManager.sessionFlow.last().duration)
    }

    @Test
    fun updateSessionFlow_checksForTheFragmentToBeEnabled_addsParentsIfTheyAreEnabled() {
        setSessionFlow(sampleSessionFlowWithoutFragment)
        // fragmentFlows disabled
        every { hengamConfig.sessionFragmentFlowEnabled } returns false

        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )
        assertEquals(0, sessionFlowManager.sessionFlow[1].fragmentFlows.size)

        // exceptions when disabled
        setSessionFlow(sampleSessionFlowWithoutFragment)
        every { hengamConfig.sessionFragmentFlowEnabled } returns false
        every { hengamConfig.sessionFragmentFlowExceptionList } returns listOf(FragmentFlowInfo("SecondActivity", "id12"))

        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity")
            ),
            false
        )
        assertEquals(1, sessionFlowManager.sessionFlow[1].fragmentFlows.size)
        assertEquals(1, sessionFlowManager.sessionFlow[1].fragmentFlows["id12"]!!.size)
        assertNull(sessionFlowManager.sessionFlow[1].fragmentFlows["id02"])

        // depth limit, with exceptions
        setSessionFlow(sampleSessionFlowWithoutFragment)
        every { hengamConfig.sessionFragmentFlowEnabled } returns true
        every { hengamConfig.sessionFragmentFlowDepthLimit } returns 2
        every { hengamConfig.sessionFragmentFlowExceptionList } returns listOf(FragmentFlowInfo("SecondActivity", "id12"))

        sessionFlowManager.updateSessionFlow(
            SessionFragmentInfo("Fragment312", "id31", "SecondActivity"),
            listOf(
                SessionFragmentInfo("Fragment022", "id02", "SecondActivity"),
                SessionFragmentInfo("Fragment122", "id12", "SecondActivity"),
                SessionFragmentInfo("Fragment222", "id22", "SecondActivity")
            ),
            false
        )

        val firstFlow = sessionFlowManager.sessionFlow[1].fragmentFlows["id02"]
        assertNotNull(firstFlow)
        assertEquals(1, firstFlow!!.size)
        assertEquals("Fragment022", firstFlow[0].name)

        // meets the depth limit but is in exceptions
        val secondFlow = firstFlow[0].fragmentFlows["id12"]
        assertNull(secondFlow)
        assertEquals(0, firstFlow[0].fragmentFlows.size)
    }

}

private val sampleSessionFlowWithoutFragment = mutableListOf(
    SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
    SessionActivity(
        "SecondActivity",
        255,
        255,
        0,
        mutableMapOf()
    )
)

private val sampleSessionFlowWithOneLevelFragment = mutableListOf(
    SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
    SessionActivity(
        "SecondActivity",
        255,
        255,
        0,
        mutableMapOf(
            "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
            "id02" to mutableListOf(
                SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                SessionFragment("Fragment022", 500, 500, 0, mutableMapOf())
            )
        )
    )
)

private val sampleSessionFlowWithTwoLevelFragments = mutableListOf(
    SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
    SessionActivity(
        "SecondActivity",
        255,
        255,
        0,
        mutableMapOf(
            "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
            "id02" to mutableListOf(
                SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                SessionFragment("Fragment022", 500, 500, 0,
                    mutableMapOf(
                        "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                        "id12" to mutableListOf(
                            SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                            SessionFragment("Fragment122", 700, 700, 0, mutableMapOf())

                        )
                    )
                )
            )
        )
    )
)

private val sampleSessionFlowWithThreeLevelFragments = mutableListOf(
    SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
    SessionActivity(
        "SecondActivity",
        255,
        255,
        0,
        mutableMapOf(
            "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
            "id02" to mutableListOf(
                SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                SessionFragment("Fragment022", 500, 500, 0,
                    mutableMapOf(
                        "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                        "id12" to mutableListOf(
                            SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                            SessionFragment("Fragment122", 700, 700, 0,
                                mutableMapOf(
                                    "id21" to mutableListOf(SessionFragment("Fragment211", 700, 700, 0, mutableMapOf())),
                                    "id22" to mutableListOf(
                                        SessionFragment("Fragment221", 700, 700, 0, mutableMapOf()),
                                        SessionFragment("Fragment222", 900, 900, 0, mutableMapOf())
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
)

private val sampleSessionFlowWithFourLevelFragments = mutableListOf(
    SessionActivity("FirstActivity", 100, 100, 0, mutableMapOf()),
    SessionActivity(
        "SecondActivity",
        255,
        255,
        0,
        mutableMapOf(
            "id01" to mutableListOf(SessionFragment("Fragment011", 255, 255, 0, mutableMapOf())),
            "id02" to mutableListOf(
                SessionFragment("Fragment021", 255, 255, 0, mutableMapOf()),
                SessionFragment("Fragment022", 500, 500, 0,
                    mutableMapOf(
                        "id11" to mutableListOf(SessionFragment("Fragment111", 500, 500, 0, mutableMapOf())),
                        "id12" to mutableListOf(
                            SessionFragment("Fragment121", 500, 500, 0, mutableMapOf()),
                            SessionFragment("Fragment122", 700, 700, 0,
                                mutableMapOf(
                                    "id21" to mutableListOf(SessionFragment("Fragment211", 700, 700, 0, mutableMapOf())),
                                    "id22" to mutableListOf(
                                        SessionFragment("Fragment221", 700, 700, 0, mutableMapOf()),
                                        SessionFragment("Fragment222", 900, 900, 0,
                                            mutableMapOf(
                                                "id31" to mutableListOf(SessionFragment("Fragment311", 900, 900, 0, mutableMapOf())),
                                                "id32" to mutableListOf(SessionFragment("Fragment321", 900, 900, 0, mutableMapOf()))
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
)