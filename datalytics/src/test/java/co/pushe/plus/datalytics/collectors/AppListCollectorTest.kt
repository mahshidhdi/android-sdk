package co.pushe.plus.datalytics.collectors

import co.pushe.plus.datalytics.appListBlackListUrl
import co.pushe.plus.datalytics.messages.upstream.ApplicationDetailsMessage
import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.HttpUtils
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AppListCollectorTest {

    private val httpUtils: HttpUtils = mockk()
    private val moshi = PusheMoshi()
    private val pref = MockSharedPreference()
    private val pusheConfig = PusheConfig(pref, moshi)
    private val applicationInfoHelper = mockk<ApplicationInfoHelper>()
    private val appListCollector = AppListCollector(
            applicationInfoHelper, httpUtils,
            moshi, pusheConfig
    )

    private val applications = listOf(
            ApplicationDetail("com.example1.app1", "1.0.0", "direct", 1000, 1000, "Ex1App1", emptyList(), false),
            ApplicationDetail("com.example1.app2", "1.0.0", "direct", 1000, 1000, "Ex1App2", emptyList(), false),
            ApplicationDetail("com.example2.app1", "1.0.0", "direct", 1000, 1000, "Ex2App1", emptyList(), false),
            ApplicationDetail("com.example2.app2", "1.0.0", "direct", 1000, 1000, "Ex2App2", emptyList(), false)
    )

    @Before
    fun setup() {
        every { applicationInfoHelper.getInstalledApplications() } returns applications
    }

    private fun assertResult(expectedIndices: List<Int>, result: List<ApplicationDetailsMessage>) {
        assertEquals(expectedIndices.size, result.size)
        assertEquals(
                applications.filterIndexed { index, _ -> index in expectedIndices }
                        .map { it.packageName }
                        .toSet(),
                result.map { it.packageName }.toSet()
        )
    }

    @Test
    fun collect_ShouldReturnAllAppsIfNoBlackListGiven() {
        every { httpUtils.request(pusheConfig.appListBlackListUrl) } returns Single.just("[]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 1, 2, 3), result.values())
    }

    @Test
    fun collect_ShouldFilterAppsUsingBlackList() {
        every { httpUtils.request(pusheConfig.appListBlackListUrl) } returns Single.just("[ \"com.example1.app2\", \"com.example2.app1\"]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 3), result.values())
    }

    @Test
    fun collect_ShouldFilterAppsWithRegexStrings() {
        every { httpUtils.request(pusheConfig.appListBlackListUrl) } returns Single.just("[ \"^com.example1.*\"]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(2, 3), result.values())
    }

    @Test
    fun collect_ShouldRetryIfFailedToGetBlacklistOnNonFinalAttempt() {
        appListCollector.isFinalAttempt = false
        every { httpUtils.request(pusheConfig.appListBlackListUrl) } returns Single.error(IOException("Test Error"))
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        result.assertError(CollectionRetryRequiredError::class.java)
    }

    @Test
    fun collect_ShouldUseNoBlacklistIfFailedToGetBlacklistOnFinalAttempt() {
        appListCollector.isFinalAttempt = true
        every { httpUtils.request(pusheConfig.appListBlackListUrl) } returns Single.error(IOException("Test Error"))
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 1, 2, 3), result.values())
    }
}