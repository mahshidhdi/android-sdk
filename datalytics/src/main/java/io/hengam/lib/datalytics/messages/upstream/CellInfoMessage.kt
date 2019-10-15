package io.hengam.lib.datalytics.messages.upstream

import io.hengam.lib.messages.MessageType
import io.hengam.lib.messaging.TypedUpstreamMessage
import io.hengam.lib.utils.moshi.RuntimeJsonAdapterFactory
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class CellInfoMessage(
        @Json(name = "cellsInfo") val cellInfo: List<CellArray>
) : TypedUpstreamMessage<CellInfoMessage>(MessageType.Datalytics.CELLULAR_DATA, { CellInfoMessageJsonAdapter(it) })

@JsonClass(generateAdapter = true)
class CellInfoDefaultMessage(
        @Json(name = "cellsInfo") val cellInfo: List<String>
) : TypedUpstreamMessage<CellInfoDefaultMessage>(MessageType.Datalytics.CELLULAR_DATA, { CellInfoDefaultMessageJsonAdapter(it) })
//Different types of Cell

//Cell Array -- Base class of cell types
@JsonClass(generateAdapter = true)
open class CellArray {
    @Json(name = "registered")
    var registered: Boolean? = null
}


//LTE
@JsonClass(generateAdapter = true)
class CellArrayLTE(
        @Json(name = "CellIdentityLte") val cellIdentityLte: CellLTE,
        @Json(name = "CellSignalStrengthLte") val cellSignalStrengthLte: SSP
) : CellArray()

@JsonClass(generateAdapter = true)
class CellLTE(
        @Json(name = "ci") val ci: Int?,
        @Json(name = "mcc") val mcc: Int?,
        @Json(name = "mnc") val mnc: Int?,
        @Json(name = "pci") val pci: Int?,
        @Json(name = "tac") val tac: Int?
)

@JsonClass(generateAdapter = true)
class CellArrayCDMA(
        @Json(name = "CellIdentityCdma") val cellIdentityLte: CellCDMA,
        @Json(name = "CellSignalStrengthCdma") val cellSignalStrengthLte: SSP
) : CellArray() {

    override fun toString(): String {
        return """
            Cell array : $cellIdentityLte
            signal strength : $cellSignalStrengthLte
        """.trimIndent()
    }
}

@JsonClass(generateAdapter = true)
class CellCDMA(
        @Json(name = "basestationId") val basestationId: Int?,
        @Json(name = "latitude") val latitude: Int?,
        @Json(name = "longitude") val longitude: Int?,
        @Json(name = "networkId") val networkId: Int?,
        @Json(name = "systemId") val systemId: Int?
)

//WCDMA
@JsonClass(generateAdapter = true)
class CellArrayWCDMA(
        @Json(name = "CellIdentityWcmda") val cellIdentityLte: CellWCDMA,
        @Json(name = "CellSignalStrengthWcmda") val cellSignalStrengthLte: SSP
) : CellArray() {
    override fun toString(): String {
        return """
            Cell array : $cellIdentityLte
            signal strength : $cellSignalStrengthLte
        """.trimIndent()
    }
}

@JsonClass(generateAdapter = true)
class CellWCDMA(
        @Json(name = "cid") val cid: Int?,
        @Json(name = "mcc") val mcc: Int?,
        @Json(name = "mnc") val mnc: Int?,
        @Json(name = "psc") val psc: Int?,
        @Json(name = "lac") val lac: Int?
)

@JsonClass(generateAdapter = true)
class CellArrayGSM(
        @Json(name = "CellIdentityGsm") val cellIdentityLte: CellGSM,
        @Json(name = "CellSignalStrengthGsm") val cellSignalStrengthLte: SSP
) : CellArray()

@JsonClass(generateAdapter = true)
class CellGSM(
        @Json(name = "cid") val cid: Int?,
        @Json(name = "mcc") val mcc: Int?,
        @Json(name = "mnc") val mnc: Int?,
        @Json(name = "lac") val lac: Int?
)

@JsonClass(generateAdapter = true)
class CellArrayUnknown(
        @Json(name = "CellInfo") val cellInfo: Map<String, Any>
) : CellArray()

@JsonClass(generateAdapter = true)
class SSP(
        @Json(name = "level") val level: Int?,
        @Json(name = "dbm") val dbm: Int?,
        @Json(name = "asuLevel") val asuLevel: Int?,
        @Json(name = "original") val original: String
)

object CellArrayFactory {
    fun build(): JsonAdapter.Factory {
        val factory = RuntimeJsonAdapterFactory.of(CellArray::class.java, "cell_array_type")
        factory.registerSubtype("lte", CellArrayLTE::class.java) { CellArrayLTEJsonAdapter(it) }
        factory.registerSubtype("wcdma", CellArrayWCDMA::class.java) { CellArrayWCDMAJsonAdapter(it) }
        factory.registerSubtype("cdma", CellArrayCDMA::class.java) { CellArrayCDMAJsonAdapter(it) }
        factory.registerSubtype("gsm", CellArrayGSM::class.java) { CellArrayGSMJsonAdapter(it) }
        return factory
    }
}
