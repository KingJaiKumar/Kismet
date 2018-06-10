package net.kismetwireless.android.smarterwifimanager.Database

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.ForeignKey.CASCADE
import android.arch.persistence.room.OnConflictStrategy.REPLACE

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
            Index(value = ["towerString", "networkId"], unique = true)
        ],
        foreignKeys = [
            ForeignKey(entity = SWM2Network::class, parentColumns = ["id"], childColumns = ["networkId"], onDelete = CASCADE)
        ]
)
data class SWM2CellTower(
        @ColumnInfo(name="lastTime") var lastTime: Long = System.currentTimeMillis(),
        @ColumnInfo(name="towerString") var towerString: String,
        @ColumnInfo(name="networkId") var network_id : Long) {

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
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
    @Query("SELECT * FROM celltower WHERE (towerString=:towerString AND networkId=:networkId)")
    fun findCellTowerAssociation(towerString : String, networkId : Long) : SWM2CellTower?

    @Query("SELECT * FROM celltower WHERE (towerString=:towerString)")
    fun findCellTowers(towerString : String) : List<SWM2CellTower>

    @Update
    fun updateTower(entry : SWM2CellTower)

    @Query("SELECT * FROM celltower")
    fun getAllTowers(): LiveData<List<SWM2CellTower>>

    @Insert(onConflict = REPLACE)
    fun insertTower(entry : SWM2CellTower) : Long
}

