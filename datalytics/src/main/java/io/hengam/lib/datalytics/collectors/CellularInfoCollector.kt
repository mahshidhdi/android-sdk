package io.hengam.lib.datalytics.collectors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.messages.upstream.*
import io.hengam.lib.messaging.SendableUpstreamMessage
import io.hengam.lib.utils.PermissionChecker.ACCESS_COARSE_LOCATION
import io.hengam.lib.utils.PermissionChecker.hasPermission
import io.hengam.lib.utils.log.LogLevel
import io.hengam.lib.utils.log.Plog
import io.reactivex.Observable
import javax.inject.Inject

/**
 * This class provides cellular data from device.
 * There are 2 types of data:
 *  1. We know what it is and it's [CellInfoMessage] is castable to subclasses that telephony provides for additional information.
 *  2. We can't say what it's type is (e.g. 5G which is not in sdk). For this case try to manually parse results
 *     of calling `toString()` on the `CellInfo`.
 */
class CellularInfoCollector @Inject constructor(
        private val context: Context,
        private val telephonyManager: TelephonyManager?
) : Collector() {

    /**
     * Collect cell info
     *
     * Note: We are sending a maximum of 5 cells with each message to avoid exceeding the maximum
     * TODO: Change this to check message size instead of count
     */
    override fun collect(): Observable<SendableUpstreamMessage> {
        return Observable.fromIterable(getCellsKnown())
                .buffer(5)
                .map { CellInfoMessage(it) }
    }


    /**
     * For castable data we use this method.
     *
     * Convert cellInfo s to CellArray using map
     * @see getCellList
     * Then remove the nulls from it.
     * It returns nullable because the whole list can be potentially null.
     * @return a list of [CellArray] (see it) representing the data.
     * @see CellInfoMessage
     */
    fun getCellsKnown(): List<CellArray> {
        return getCellList()
                .mapNotNull(this::createCellArrayFromCellInfo)

    }


    // Permission checked but lint doesn't get it.
    @SuppressLint("MissingPermission")
    private fun getCellList(): List<android.telephony.CellInfo> {
        var list: List<android.telephony.CellInfo> = emptyList()
        if (hasPermission(context, ACCESS_COARSE_LOCATION)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    list = telephonyManager?.allCellInfo ?: emptyList()
                } else {
                    Plog.warn.message("Cell info not available in API <17.").withTag(T_DATALYTICS).useLogCatLevel(LogLevel.DEBUG).log()
                }
            } catch (e: Exception) {
                Plog.error(T_DATALYTICS, "Getting cell info threw exception", e)
            }
        } else {
            Plog.warn.message("Cellular info not collected due to insufficient permissions").withTag(T_DATALYTICS).useLogCatLevel(LogLevel.DEBUG).log()
        }

        return list
    }

    /**
     * Gets the cellInfo and cast in order to get more information if possible.
     */
    private fun createCellArrayFromCellInfo(cell: android.telephony.CellInfo): CellArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return null
        }
        
        val equalInvalidVal = "=" + Int.MAX_VALUE
        return when (cell) {
            is CellInfoLte -> {
                CellArrayLTE(
                        CellLTE(
                                ci = cell.cellIdentity.ci.takeIf { it != Int.MAX_VALUE },
                                mcc = cell.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE },
                                mnc = cell.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE },
                                pci = cell.cellIdentity.pci.takeIf { it != Int.MAX_VALUE },
                                tac = cell.cellIdentity.tac.takeIf { it != Int.MAX_VALUE }
                        ),
                        SSP(
                                level = cell.cellSignalStrength.level,
                                dbm = cell.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                                asuLevel = cell.cellSignalStrength.asuLevel.takeIf { it != ASU_LEVEL_UNKNOWN },
                                original = cell.cellSignalStrength.toString().replace(equalInvalidVal, "")
                        )
                ).also { it.registered = cell.isRegistered }
            }
            is CellInfoGsm -> {
                CellArrayGSM(
                        CellGSM(
                                cid = cell.cellIdentity.cid.takeIf { it != Int.MAX_VALUE },
                                mcc = cell.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE },
                                mnc = cell.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE },
                                lac = cell.cellIdentity.lac.takeIf { it != Int.MAX_VALUE }
                        ),
                        SSP(
                                level = cell.cellSignalStrength.level,
                                dbm = cell.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                                asuLevel = cell.cellSignalStrength.asuLevel.takeIf { it != ASU_LEVEL_UNKNOWN },
                                original = cell.cellSignalStrength.toString().replace(equalInvalidVal, "")
                        )
                ).also { it.registered = cell.isRegistered }
            }
            is CellInfoWcdma -> {
                CellArrayWCDMA(
                        CellWCDMA(
                                cid = cell.cellIdentity.cid.takeIf { it != Int.MAX_VALUE },
                                mcc = cell.cellIdentity.mcc.takeIf { it != Int.MAX_VALUE },
                                mnc = cell.cellIdentity.mnc.takeIf { it != Int.MAX_VALUE },
                                psc = cell.cellIdentity.psc.takeIf { it != Int.MAX_VALUE },
                                lac = cell.cellIdentity.lac.takeIf { it != Int.MAX_VALUE }
                        ),
                        SSP(
                                level = cell.cellSignalStrength.level,
                                dbm = cell.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                                asuLevel = cell.cellSignalStrength.asuLevel.takeIf { it != ASU_LEVEL_UNKNOWN },
                                original = cell.cellSignalStrength.toString().replace(equalInvalidVal, "")
                        )
                ).also { it.registered = cell.isRegistered }
            }
            is CellInfoCdma -> {
                CellArrayCDMA(
                        CellCDMA(
                                basestationId = cell.cellIdentity.basestationId.takeIf { it != Int.MAX_VALUE },
                                latitude = cell.cellIdentity.latitude.takeIf { it != Int.MAX_VALUE },
                                longitude = cell.cellIdentity.longitude.takeIf { it != Int.MAX_VALUE },
                                networkId = cell.cellIdentity.networkId.takeIf { it != Int.MAX_VALUE },
                                systemId = cell.cellIdentity.systemId.takeIf { it != Int.MAX_VALUE }
                        ),
                        SSP(
                                level = cell.cellSignalStrength.level,
                                dbm = cell.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE },
                                asuLevel = cell.cellSignalStrength.asuLevel.takeIf { it != ASU_LEVEL_UNKNOWN },
                                original = cell.cellSignalStrength.toString().replace(equalInvalidVal, "")
                        )
                ).also { it.registered = cell.isRegistered }
            }
            else -> CellArrayUnknown(
                    parseUnknownCell(cell.toString())
            )
        }

    }


    /**
     * If no object for current cellular operator was made
     * this function will make a map of the string returned from [CellInfo.toString]
     *
     * @param phrase is that toString text
     * @see CellInfo.toString
     */
    private fun parseUnknownCell(phrase: String): Map<String, String> {
        val finalMap = mutableMapOf<String, String>()

        // split by space
        val spaceSplitArray = phrase.replace(":{", " :{ ").replace("}", " }").split(" ")

        // filter to get type of cell
        spaceSplitArray.filter {
            it.contains("CellInfo")
        }.forEach {
            finalMap["type"] = it.replace("CellInfo", "")
        }

        // Filter to get key values
        spaceSplitArray.filter {
            it.contains("=")
        }.forEach {
            val pair = it.split("=")
            if (pair.size ==2) {
                if (pair[0].startsWith("m"))
                    finalMap[pair[0].replaceFirst("m", "").toLowerCase()] = pair[1]
                else
                    finalMap[pair[0]] = pair[1]
            }
        }
        return finalMap
    }

    companion object {
        const val ASU_LEVEL_UNKNOWN = 99
    }
}

