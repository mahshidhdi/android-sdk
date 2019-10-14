package co.pushe.plus.utils.test

import android.os.Build
import co.pushe.plus.internal.PusheSchedulers
import co.pushe.plus.utils.*
import io.mockk.*
import io.reactivex.schedulers.TestScheduler
import java.lang.reflect.Field
import java.lang.reflect.Modifier


object TestUtils {
    private var schedulersMocked: Boolean = false
    private var timeUtilsMocked: Boolean = false
    private var assertionsMocked: Boolean = false
    private var mockedTime: Long = 0

    fun mockTime(currentTime: Long) {
        if (!timeUtilsMocked) {
            mockkObject(TimeUtils)
        }
        mockedTime = currentTime
        every { TimeUtils.nowMillis() } returns currentTime
    }

    fun mockTime(time: Time) =  mockTime(time.toMillis())

    fun advanceMockTimeBy(time: Time) = mockTime(mockedTime + time.toMillis())

    fun mockCpuThread(): TestScheduler {
        val cpuThread = TestScheduler()

        if (!schedulersMocked) {
            mockkObject(PusheSchedulers)
            schedulersMocked = true
        }

        every { PusheSchedulers.cpu } returns cpuThread
        return cpuThread
    }

    fun mockIoThread(): TestScheduler {
        val ioThread = TestScheduler()

        if (!schedulersMocked) {
            mockkObject(PusheSchedulers)
            schedulersMocked = true
        }

        every { PusheSchedulers.io } returns ioThread
        return ioThread
    }

    fun mockUIThread(): TestScheduler {
        val uiThread = TestScheduler()

        if (!schedulersMocked) {
            mockkObject(PusheSchedulers)
            schedulersMocked = true
        }

        every { PusheSchedulers.ui } returns uiThread
        return uiThread
    }

    fun turnOffThreadAssertions() {
        if (!assertionsMocked) {
            mockkStatic("co.pushe.plus.utils.PusheAssertsKt")
            assertionsMocked = true
        }

        every { assertCpuThread() } just runs
    }

    fun mockAndroidSdkVersion(sdkVersion: Int) {
        val field = Build.VERSION::class.java.getField("SDK_INT")
        field.isAccessible = true
        val modifiersField = Field::class.java!!.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.getModifiers() and Modifier.FINAL.inv())
        field.set(null, sdkVersion)
    }

    fun mockEnvironment(env: Environment) {
        mockkStatic("co.pushe.plus.utils.EnvironmentKt")
        every { environment() } returns env
    }
}