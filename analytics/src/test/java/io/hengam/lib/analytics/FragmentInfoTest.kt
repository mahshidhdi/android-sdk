package io.hengam.lib.analytics

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GoalStoreTest {
    @Before
    fun setUp() {

    }

    @Test
    fun sessionFragmentInfo_ContainerIdContainsParentFragments() {
        assertEquals("SecondActivity_id02_", sessionFragmentInfoWithoutParents.containerId)
        assertEquals("SecondActivity_id12_id02_", sessionFragmentInfoWithOneParents.containerId)
        assertEquals("SecondActivity_id22_id12_id02_", sessionFragmentInfoWithTwoParents.containerId)
        assertEquals("SecondActivity_id31_id22_id12_id02_", sessionFragmentInfoWithThreeParents.containerId)
    }

    private val sessionFragmentInfoWithoutParents =
        SessionFragmentInfo(
            "Fragment022",
            "id02",
            "SecondActivity"
        )

    private val sessionFragmentInfoWithOneParents =
        SessionFragmentInfo(
            "Fragment122",
            "id12",
            "SecondActivity",
            sessionFragmentInfoWithoutParents
        )

    private val sessionFragmentInfoWithTwoParents =
        SessionFragmentInfo(
            "Fragment222",
            "id22",
            "SecondActivity",
            sessionFragmentInfoWithOneParents
        )

    private val sessionFragmentInfoWithThreeParents =
        SessionFragmentInfo(
            "Fragment312",
            "id31",
            "SecondActivity",
            sessionFragmentInfoWithTwoParents
        )
}