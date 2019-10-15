package io.hengam.lib.messages.mixin

import android.annotation.SuppressLint
import android.os.Build
import android.support.annotation.RequiresApi
import android.telephony.*
import android.telephony.CellInfo.CONNECTION_PRIMARY_SERVING
import android.telephony.CellInfo.CONNECTION_SECONDARY_SERVING
import io.hengam.lib.Hengam
import io.hengam.lib.internal.HengamException
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.ComponentNotAvailableException
import io.hengam.lib.messaging.MessageMixin
import io.reactivex.Single

class CellInfoMixin(private val isNested: Boolean = false) : MessageMixin() {
    @SuppressLint("MissingPermission")
    override fun collectMixinData(): Single<Map<String, Any?>> {
        val core = HengamInternals.getComponent(CoreComponent::class.java)
                ?: throw ComponentNotAvailableException(Hengam.CORE)
        val telephonyManager: TelephonyManager = core.telephonyManager()
                ?: throw HengamException("Could not obtain TelephonyManager")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val cellInfo = telephonyManager.allCellInfo
                if (cellInfo.isNullOrEmpty()) {
                    return Single.just(emptyMap())
                }
                val sortedCells = cellInfo.sortedWith(CellComparator)
                val cellDetails = getCellDetails(sortedCells[0])
                return Single.just(if (isNested) mapOf("cell" to cellDetails) else cellDetails)
            }
        } catch (ex: SecurityException) {
            return Single.just(emptyMap())
        }

        return Single.just(emptyMap())
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun getCellDetails(c: CellInfo): Map<String, Any> {
        val isAboveP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        return when {
            c is CellInfoLte -> listOfNotNull(
                    "type" to "lte",
                    "cid" to c.cellIdentity.ci.takeIf { it != Integer.MAX_VALUE },
                    "mnc" to if (isAboveP) c.cellIdentity.mncString else null,
                    "mcc" to if (isAboveP) c.cellIdentity.mccString else null,
                    "lac" to c.cellIdentity.tac.takeIf { it != Integer.MAX_VALUE }
            )
            c is CellInfoGsm -> listOfNotNull(
                    "type" to "gsm",
                    "cid" to c.cellIdentity.cid.takeIf { it != Integer.MAX_VALUE },
                    "mnc" to if (isAboveP) c.cellIdentity.mncString else null,
                    "mcc" to if (isAboveP) c.cellIdentity.mccString else null,
                    "lac" to c.cellIdentity.lac.takeIf { it != Integer.MAX_VALUE }
            )
            c is CellInfoCdma -> listOfNotNull(
                    "type" to "cdma",
                    "cid" to c.cellIdentity.basestationId,
                    "nid" to c.cellIdentity.networkId,
                    "bid" to c.cellIdentity.basestationId,
                    "lat" to c.cellIdentity.latitude,
                    "long" to c.cellIdentity.longitude
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && c is CellInfoWcdma -> listOfNotNull(
                    "type" to "wcdma",
                    "cid" to c.cellIdentity.cid.takeIf { it != Integer.MAX_VALUE },
                    "mnc" to if (isAboveP) c.cellIdentity.mncString else null,
                    "mcc" to if (isAboveP) c.cellIdentity.mccString else null,
                    "lac" to c.cellIdentity.lac.takeIf { it != Integer.MAX_VALUE }
            )
            else -> emptyList()
        }.toMap() as Map<String, Any>
    }

    object CellComparator : Comparator<CellInfo> {
        override fun compare(c1: CellInfo?, c2: CellInfo?): Int {
            if (c1 == null && c2 == null) {
                return 0
            } else if (c1 == null) {
                return 1
            } else if (c2 == null) {
                return -1
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val status1 = c1.cellConnectionStatus
                val status2 = c2.cellConnectionStatus

                if (status1 == CONNECTION_PRIMARY_SERVING && status2 == CONNECTION_PRIMARY_SERVING) {
                    return getSignalStrength(c2) - getSignalStrength(c1)
                } else if (status1 == CONNECTION_PRIMARY_SERVING) {
                    return -1
                } else if (status2 == CONNECTION_PRIMARY_SERVING) {
                    return 1
                }

                if (status1 == CONNECTION_SECONDARY_SERVING && status2 == CONNECTION_SECONDARY_SERVING) {
                    return getSignalStrength(c2) - getSignalStrength(c1)
                } else if (status1 == CONNECTION_SECONDARY_SERVING) {
                    return -1
                } else if (status2 == CONNECTION_SECONDARY_SERVING) {
                    return 1
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return when {
                    c1.isRegistered && !c2.isRegistered -> -1
                    !c1.isRegistered && c2.isRegistered -> 1
                    else -> getSignalStrength(c2) - getSignalStrength(c1)
                }
            }

            return 0
        }

        private fun getSignalStrength(c: CellInfo): Int {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) return 0

            return when {
                c is CellInfoLte -> c.cellSignalStrength.dbm
                c is CellInfoGsm -> c.cellSignalStrength.dbm
                c is CellInfoCdma -> c.cellSignalStrength.dbm
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                        c is CellInfoWcdma -> c.cellSignalStrength.dbm
                else -> 0
            }
        }
    }
}