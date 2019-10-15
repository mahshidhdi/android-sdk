package io.hengam.lib.analytics.goal

import io.hengam.lib.utils.ApplicationInfoHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalFragmentObfuscatedNameExtractorTest{
    @Test
    fun getFragmentName_getsObfuscatedNameOfTheAppVersion(){
        val applicationInfoHelper: ApplicationInfoHelper = mockk(relaxed = true)
        every { applicationInfoHelper.getApplicationVersionCode() } returns 1000
        val goalFragmentObfuscatedNameExtractor = GoalFragmentObfuscatedNameExtractor(applicationInfoHelper)

        // with obfuscated name
        var name = goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(
            GoalMessageFragmentInfo(
                actualName = "FragmentName",
                obfuscatedNames = mapOf(1000L to "aaa", 1002L to "bbb"),
                fragmentId = "fragmentId")
        )

        assertEquals("aaa", name)

        // without obfuscated name
        name = goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(
            GoalMessageFragmentInfo(
                actualName = "FragmentName",
                obfuscatedNames = mapOf(1002L to "bbb"),
                fragmentId = "fragmentId")
        )

        assertNull(name)

        // null app version
        every { applicationInfoHelper.getApplicationVersionCode() } returns null
        name = goalFragmentObfuscatedNameExtractor.getFragmentObfuscatedName(
            GoalMessageFragmentInfo(
                actualName = "FragmentName",
                obfuscatedNames = mapOf(1002L to "bbb"),
                fragmentId = "fragmentId")
        )

        assertNull(name)
    }
}