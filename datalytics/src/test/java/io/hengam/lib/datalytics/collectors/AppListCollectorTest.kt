package io.hengam.lib.datalytics.collectors

import io.hengam.lib.datalytics.appListBlackListUrl
import io.hengam.lib.datalytics.messages.upstream.ApplicationDetailsMessage
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messages.common.ApplicationDetail
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.HttpUtils
import io.hengam.lib.utils.test.mocks.MockSharedPreference
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
    private val moshi = HengamMoshi()
    private val pref = MockSharedPreference()
    private val hengamConfig = HengamConfig(pref, moshi)
    private val applicationInfoHelper = mockk<ApplicationInfoHelper>()
    private val appListCollector = AppListCollector(
            applicationInfoHelper, httpUtils,
            moshi, hengamConfig
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
        every { httpUtils.request(hengamConfig.appListBlackListUrl) } returns Single.just("[]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 1, 2, 3), result.values())
    }

    @Test
    fun collect_ShouldFilterAppsUsingBlackList() {
        every { httpUtils.request(hengamConfig.appListBlackListUrl) } returns Single.just("[ \"com.example1.app2\", \"com.example2.app1\"]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 3), result.values())
    }

    @Test
    fun collect_ShouldFilterAppsWithRegexStrings() {
        every { httpUtils.request(hengamConfig.appListBlackListUrl) } returns Single.just("[ \"^com.example1.*\"]")
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(2, 3), result.values())
    }

    @Test
    fun collect_ShouldUseNoBlacklistIfFailedToGetBlacklist() {
        every { httpUtils.request(hengamConfig.appListBlackListUrl) } returns Single.error(IOException("Test Error"))
        val scheduler = TestScheduler()
        val result = appListCollector.installedApplications.subscribeOn(scheduler).test()
        scheduler.triggerActions()
        assertResult(listOf(0, 1, 2, 3), result.values())
    }
}