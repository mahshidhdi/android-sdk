package co.pushe.plus.messages.mixin

import android.os.Build
import android.telephony.*
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.utils.test.TestUtils.mockAndroidSdkVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*

class CellInfoMixinTest {
    private val core: CoreComponent = mockk(relaxed = true)
    private val telephonyManager: TelephonyManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(PusheInternals)
        every { PusheInternals.getComponent(CoreComponent::class.java) } returns core
        every { core.telephonyManager() } returns telephonyManager
    }

    @Test
    fun cellComparator_CorrectlyOrdersCellInfo() {
        val c1 = createCell<CellInfoLte>("c1",true, false, false, 200)
        val c2 = createCell<CellInfoGsm>("c2",true, false, true, 100)
        val c3 = createCell<CellInfoCdma>("c3",false, true, false, 300)
        val c4 = createCell<CellInfoWcdma>("c4",false, true, false, 100)
        val c5 = createCell<CellInfoGsm>("c5",false, false, true, 200)
        val c6 = createCell<CellInfoLte>("c6",false, false, true, 100)
        val c7 = createCell<CellInfoWcdma>("c7",false, false, true, 100)
        val c8 = createCell<CellInfoCdma>("c8",false, false, false, 200)
        val c9 = createCell<CellInfoGsm>("c9",false, false, false, 100)
        val c10 = createCell<CellInfoLte>("c10",false, false, false, 50)

        mockAndroidSdkVersion(Build.VERSION_CODES.P)
        var sorted = listOf(c3, c6, c9, c1, c4, c7, c10, c2, c5, c8).sortedWith(CellInfoMixin.CellComparator)
        assertEquals(listOf(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10), sorted)

        mockAndroidSdkVersion(Build.VERSION_CODES.JELLY_BEAN_MR2)
        sorted = listOf(c3, c6, c9, c1, c4, c7, c10, c2, c5, c8).sortedWith(CellInfoMixin.CellComparator)
        assertEquals(listOf(c5, c6, c7, c2, c3, c1, c8, c9, c4, c10), sorted)
    }

    private inline fun <reified T : CellInfo> createCell(
            name: String, isPrimary: Boolean, isSecondary: Boolean,
            isRegistered: Boolean, signalStrength: Int): CellInfo {
        val cell = mockk<T>(relaxed = true)
        when {
            isPrimary -> every { cell.cellConnectionStatus } returns CellInfo.CONNECTION_PRIMARY_SERVING
            isSecondary -> every { cell.cellConnectionStatus } returns CellInfo.CONNECTION_SECONDARY_SERVING
            else -> every { cell.cellConnectionStatus } returns CellInfo.CONNECTION_NONE
        }
        every { cell.isRegistered } returns isRegistered
        when (cell) {
            is CellInfoLte -> every { cell.cellSignalStrength.dbm } returns signalStrength
            is CellInfoGsm -> every { cell.cellSignalStrength.dbm } returns signalStrength
            is CellInfoCdma -> every { cell.cellSignalStrength.dbm } returns signalStrength
            is CellInfoWcdma -> every { cell.cellSignalStrength.dbm } returns signalStrength
        }
        every { cell.toString() } returns name
        return cell
    }
}