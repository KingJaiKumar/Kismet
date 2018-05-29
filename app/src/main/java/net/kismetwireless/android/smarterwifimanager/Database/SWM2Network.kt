package net.kismetwireless.android.smarterwifimanager.Database

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.OnConflictStrategy.REPLACE
import android.telephony.CellLocation
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation

// Learned networks are tracked by SSID; it may have multiple BSSIDs or towers
// associated with it, when in the presence of a learned bssid/tower, we
// turn on wifi.
@Entity(tableName="network",
        indices = [
            Index(value = ["ssid"], unique = true)]
)
data class SWM2Network(
        @ColumnInfo(name="lastTime") var lastTime: Long = System.currentTimeMillis(),
        @ColumnInfo(name="ssid") var ssid: String) {
    @ColumnInfo(name="id")
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

// BSSID table; last time used, bssid, and ssid
@Entity(tableName="accesspoint",
        indices = [
            Index(value = ["bssid", "networkId"], unique = true)],
        foreignKeys = [
            ForeignKey(entity=SWM2Network::class, parentColumns=["id"], childColumns=["networkId"], onDelete = CASCADE)
        ]
)
data class SWM2AccessPoint(
        @ColumnInfo(name="lastTime") var lastTime: Long = System.currentTimeMillis(),
        @ColumnInfo(name="bssid") var bssid: String,
        @ColumnInfo(name="networkId") var network_id : Long) {

    @ColumnInfo(name="id")
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}


// Slightly clumsy implementation of cell tower info, combining fields whenever
// possible because different cellinfo records have different numeric fields
@Entity(tableName="celltower",
        indices = [
            Index(value = ["bsidcid", "nidlac", "sid", "networkId"], unique = true)
        ],
        foreignKeys = [
            ForeignKey(entity = SWM2Network::class, parentColumns = ["id"], childColumns = ["networkId"], onDelete = CASCADE)
        ]
)
data class SWM2CellTower(
        @ColumnInfo(name="lastTime") var lastTime: Long = System.currentTimeMillis(),
        @ColumnInfo(name="bsidcid") var bsid_cid: Long,
        @ColumnInfo(name="nidlac") var nid_lac: Long,
        @ColumnInfo(name="sid") var sid : Long = 0,
        @ColumnInfo(name="networkId") var network_id : Long) {

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

class SWM2CommonTower(cellLocation: CellLocation?) {
    var bsidcid : Long = -1
    var nidlac : Long = -1
    var sid : Long = -1

    init {
        if (cellLocation != null) {
            if (cellLocation is GsmCellLocation) {
                val gsmTower = cellLocation as GsmCellLocation

                bsidcid = gsmTower.cid.toLong()
                nidlac = gsmTower.lac.toLong()
                sid = 0
            } else if (cellLocation is CdmaCellLocation) {
                val cdmaTower = cellLocation as CdmaCellLocation

                bsidcid = cdmaTower.baseStationId.toLong()
                nidlac = cdmaTower.networkId.toLong()
                sid = cdmaTower.systemId.toLong()
            }
        }
    }

    fun isValid() : Boolean =
            bsidcid != -1L && nidlac != -1L && sid != -1L

    override fun toString() : String {
        var str =
                bsidcid.toString() + "." + nidlac.toString() + "." + sid.toString()

        if (!isValid())
            str += "[INVALID]"

        return str
    }

    fun equals(tower : SWM2CommonTower) : Boolean =
            bsidcid == tower.bsidcid && nidlac == tower.nidlac && sid == tower.sid
}


@Dao
interface SWM2NetworkDao {
    @Query("SELECT * FROM network")
    fun getAllNetworks(): LiveData<List<SWM2Network>>

    @Query("SELECT * FROM network WHERE ssid=:ssid")
    fun findNetwork(ssid : String) : SWM2Network?

    @Insert(onConflict = REPLACE)
    fun insertNetwork(entry : SWM2Network) : Long

    @Update
    fun updateNetwork(entry : SWM2Network)

    @Delete
    fun deleteNetwork(entry : SWM2Network)

    @Query("SELECT * FROM accesspoint WHERE networkId=:id")
    fun findBssids(id : Long) : List<SWM2AccessPoint>

    @Query("SELECT COUNT(*) FROM accesspoint WHERE networkId=:id")
    fun countBssids(id : Long) : Long

    @Query("SELECT * FROM celltower WHERE networkId=:id")
    fun findTowers(id : Long) : List<SWM2CellTower>

    @Query("SELECT COUNT(*) FROM celltower WHERE networkId=:id")
    fun countTowers(id : Long) : Long

}

@Dao
interface SWM2AccessPointDao {
    @Query("select * FROM accesspoint WHERE (bssid=:bssid AND networkId=:networkId)")
    fun findAccessPoint(bssid : String, networkId : Long) : SWM2AccessPoint?

    @Query("select * FROM accesspoint")
    fun getAllAccessPoints(): LiveData<List<SWM2AccessPoint>>

    @Insert(onConflict = REPLACE)
    fun insertAccessPoint(entry : SWM2AccessPoint) : Long

    @Update
    fun updateAccessPoint(entry : SWM2AccessPoint)

    @Delete
    fun deleteAccessPoint(entry : SWM2AccessPoint)

    @Query("SELECT * FROM network WHERE id=:networkId")
    fun findNetwork(networkId : Long) : List<SWM2Network>
}

@Dao
interface SWM2TowerDao {
    @Query("SELECT * FROM celltower WHERE (bsidcid=:bsid_cid AND nidlac=:nid_lac AND sid=:sid AND networkId=:networkid)")
    fun findCellTowerAssociation(bsid_cid : Long, nid_lac : Long, sid : Long, networkid : Long) : SWM2CellTower?

    @Query("SELECT * FROM celltower WHERE (bsidcid=:bsid_cid AND nidlac=:nid_lac AND sid=:sid)")
    fun findCellTowers(bsid_cid : Long, nid_lac : Long, sid : Long) : List<SWM2CellTower>

    @Update
    fun updateTower(entry : SWM2CellTower)

    @Query("SELECT * FROM celltower")
    fun getAllTowers(): LiveData<List<SWM2CellTower>>

    @Insert(onConflict = REPLACE)
    fun insertTower(entry : SWM2CellTower) : Long
}

