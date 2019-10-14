package co.pushe.plus.datalytics.utils

import co.pushe.plus.internal.PusheConfig
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.utils.HttpUtils
import co.pushe.plus.utils.test.mocks.MockSharedPreference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler

import org.junit.Test
import java.io.IOException

class NetworkUtilsTest {
    private val httpUtils: HttpUtils = mockk(relaxed = true)
    private val moshi = PusheMoshi()
    private val pusheConfig = PusheConfig(MockSharedPreference(), moshi)
    private val networkUtils = NetworkUtils(moshi, httpUtils, pusheConfig)

    private fun setApiList(vararg apiList: String) {
        pusheConfig.updateConfig("public_ip_apis", List::class.java, apiList.toList())
    }

    @Test
    fun getPublicIp_ReturnsIpFromFirstApiThatSucceeds() {
        setApiList("http://api1", "http://api2", "http://api3")
        val testScheduler = TestScheduler()

        every { httpUtils.request("http://api1") } returns Single.error(IOException())
        every { httpUtils.request("http://api2") } returns Single.just("1.22.333.4")
        every { httpUtils.request("http://api3") } returns Single.just("5.6.7.8")

        val result = networkUtils.getPublicIp()
                .subscribeOn(testScheduler)
                .test()
        testScheduler.triggerActions()
        result.assertValue(NetworkUtils.PublicIpInfo("1.22.333.4"))

        verify(exactly = 1) { httpUtils.request("http://api1") }
        verify(exactly = 1) { httpUtils.request("http://api2") }
        verify(exactly = 0) { httpUtils.request("http://api3") }
    }

    @Test
    fun getPublicIp_ReturnsNothingIfNoneOfTheApisSucceed() {
        setApiList("http://api1", "http://api2", "http://api3")
        val testScheduler = TestScheduler()

        every { httpUtils.request("http://api1") } returns Single.error(IOException())
        every { httpUtils.request("http://api2") } returns Single.error(IOException())
        every { httpUtils.request("http://api3") } returns Single.error(IOException())

        val result = networkUtils.getPublicIp()
                .subscribeOn(testScheduler)
                .test()
        testScheduler.triggerActions()
        result.assertNoValues()
        result.assertComplete()

        verify(exactly = 1) { httpUtils.request("http://api1") }
        verify(exactly = 1) { httpUtils.request("http://api2") }
        verify(exactly = 1) { httpUtils.request("http://api3") }
    }

    @Test
    fun getPublicIp_ParsesJsonResponse() {
        setApiList("http://api1")
        val testScheduler = TestScheduler()

        every { httpUtils.request("http://api1") } returns Single.just("{\"ip\": \"1.2.3.4\"}")

        val result = networkUtils.getPublicIp()
                .subscribeOn(testScheduler)
                .test()
        testScheduler.triggerActions()
        result.assertValue(NetworkUtils.PublicIpInfo("1.2.3.4"))
    }

    @Test
    fun getPublicIp_ReadsIspFromJson() {
        setApiList("http://api1")
        val testScheduler = TestScheduler()

        every { httpUtils.request("http://api1") } returns
                Single.just("{\"ip\": \"1.2.3.4\", \"org\": \"SomeOrg\", \"sth\": \"else\"}")

        val result = networkUtils.getPublicIp()
                .subscribeOn(testScheduler)
                .test()
        testScheduler.triggerActions()
        result.assertValue(NetworkUtils.PublicIpInfo("1.2.3.4", "SomeOrg"))
    }

    @Test
    fun getPublicIp_HandlesInvalidResponseSilently() {
        setApiList("http://api1", "http://api2", "http://api3", "http://api4")
        val testScheduler = TestScheduler()

        every { httpUtils.request("http://api1") } returns Single.just("invalid response")
        every { httpUtils.request("http://api2") } returns Single.just("{\"ip\": \"1.23.4\"}")
        every { httpUtils.request("http://api3") } returns Single.just("{bad_json: \"1.23.4\"}")
        every { httpUtils.request("http://api4") } returns Single.just("1.2.3.4")

        val result = networkUtils.getPublicIp()
                .subscribeOn(testScheduler)
                .test()
        testScheduler.triggerActions()
        result.assertValue(NetworkUtils.PublicIpInfo("1.2.3.4"))
    }
}