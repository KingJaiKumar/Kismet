package net.kismetwireless.android.smarterwifimanager.Database

import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation

// Kluge a bunch of telephony options from android into a common string we can
// use in the database; we pick from a number of different incoming types of
// telephony data and select the unique attributes from each
class SWM2CommonTelephony {
    private var string : String = "invalid"
    private var valid : Boolean = false

    constructor() {
    }

    constructor(cellLocation: CellLocation?) {
        if (cellLocation != null) {
            if (cellLocation is GsmCellLocation) {
                val gsmTower = cellLocation as GsmCellLocation

                if (gsmTower.cid <= 0 && gsmTower.lac <= 0) {
                    valid = false
                    string = "GSM:invalid"
                    return
                }

                string = "GSM:" + gsmTower.cid.toString() + "." + gsmTower.lac.toString()
                valid = true
            } else if (cellLocation is CdmaCellLocation) {
                val cdmaTower = cellLocation as CdmaCellLocation

                if (cdmaTower.baseStationId <= 0 && cdmaTower.networkId <= 0 &&
                        cdmaTower.systemId <= 0) {
                    valid = false
                    string = "CDMA:invalid"
                    return
                }

                string = "CDMA:" + cdmaTower.baseStationId.toString() + "." +
                        cdmaTower.networkId.toString() + "." +
                        cdmaTower.systemId.toString()
                valid = true
            }
        } else {
            valid = false
            string = "invalid"
        }
    }

    constructor(cellInfo: CellInfo?) {
        if (cellInfo != null) {
            if (cellInfo is CellInfoGsm) {
                val id = (cellInfo as CellInfoGsm).cellIdentity

                if (id.cid <= 0 && id.lac <= 0) {
                    valid = false
                    string = "GSM:invalid"
                }

                string = "GSM:" + id.cid.toString() + "." + id.lac.toString()
                valid = true
            } else if (cellInfo is CellInfoCdma) {
                val id = (cellInfo as CellInfoCdma).cellIdentity

                if (id.basestationId <= 0 && id.networkId <= 0 && id.systemId <= 0) {
                    valid = false
                    string = "CDMA:invalid"
                    return
                }

                string = "CDMA:" + id.basestationId.toString() + "." +
                        id.networkId.toString() + "." +
                        id.systemId.toString()
                valid = true
            } else if (cellInfo is CellInfoLte) {
                val id = (cellInfo as CellInfoLte).cellIdentity

                if (id.tac == Int.MAX_VALUE || id.pci == Int.MAX_VALUE || id.ci == Int.MAX_VALUE) {
                    valid = false
                    string = "LTE:invalid"
                    return
                }

                string = "LTE:" + id.tac + "." + id.pci + "." + id.ci
                valid = true
            } else if (cellInfo is CellInfoWcdma) {
                val id = (cellInfo as CellInfoWcdma).cellIdentity

                if (id.cid == Int.MAX_VALUE || id.lac == Int.MAX_VALUE) {
                    valid = false
                    string = "WCDMA:invalid"
                    return
                }

                string = "WCDMA:" + id.cid + "." + id.lac
                valid = true
            }
        } else {
            valid = false
            string = "invalid"
        }
    }

    override fun toString() : String = string
    fun isValid() : Boolean = valid

    fun equals(other : SWM2CommonTelephony) =
            string.equals(other.toString())

}