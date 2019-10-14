package co.pushe.plus.sentry

import android.content.Context
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.Environment
import co.pushe.plus.utils.days
import co.pushe.plus.utils.test.TestUtils
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PusheConfigKtTest {
    private val sharedPreferences = MockSharedPreference()
    private val context: Context = mockk(relaxed = true)
    private val moshi = PusheMoshi()

    private lateinit var pusheConfig: PusheConfig

    @Before
    fun setUp() {
        every { context.getSharedPreferences(PusheConfig.PUSHE_CONFIG_STORE, Context.MODE_PRIVATE) } returns sharedPreferences
        pusheConfig = PusheConfig(context, moshi)
        pusheConfig.isCacheEnabled = false
    }

    @Test
    fun getSentryReportInterval() {
        TestUtils.mockEnvironment(Environment.DEVELOPMENT)
        Assert.assertNull(pusheConfig.sentryReportInterval)

        TestUtils.mockEnvironment(Environment.STABLE)
        Assert.assertNull(pusheConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", (10 * 24 * 60 * 60 * 1000).toString()).apply()
        assertEquals(days(10), pusheConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "0").apply()
        Assert.assertNull(pusheConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", (1 * 24 * 60 * 60 * 1000).toString()).apply()
        assertEquals(days(1), pusheConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "-1").apply()
        Assert.assertNull(pusheConfig.sentryReportInterval)

        sharedPreferences.edit().putString("sentry_report_interval", "invalid").apply()
        Assert.assertNull(pusheConfig.sentryReportInterval)
    }
}