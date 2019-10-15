package io.hengam.lib.sentry

import android.content.Context
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.utils.Environment
import io.hengam.lib.utils.days
import io.hengam.lib.utils.test.TestUtils
import io.hengam.lib.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HengamConfigKtTest {
    private val sharedPreferences = MockSharedPreference()
    private val context: Context = mockk(relaxed = true)
    private val moshi = HengamMoshi()

    private lateinit var hengamConfig: HengamConfig

    @Before
    fun setUp() {
        every { context.getSharedPreferences(HengamConfig.HENGAM_CONFIG_STORE, Context.MODE_PRIVATE) } returns sharedPreferences
        hengamConfig = HengamConfig(context, moshi)
        hengamConfig.isCacheEnabled = false
    }

    @Test
    fun getSentryReportInterval() {
        TestUtils.mockEnvironment(Environment.DEVELOPMENT)
        Assert.assertNull(hengamConfig.sentryReportInterval)

        TestUtils.mockEnvironment(Environment.STABLE)
        Assert.assertNull(hengamConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", (10 * 24 * 60 * 60 * 1000).toString()).apply()
        assertEquals(days(10), hengamConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "0").apply()
        Assert.assertNull(hengamConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", (1 * 24 * 60 * 60 * 1000).toString()).apply()
        assertEquals(days(1), hengamConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "-1").apply()
        Assert.assertNull(hengamConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "invalid").apply()
        Assert.assertNull(hengamConfig.sentryReportInterval)
    }
}