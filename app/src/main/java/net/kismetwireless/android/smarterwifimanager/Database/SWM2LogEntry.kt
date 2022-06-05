package net.kismetwireless.android.smarterwifimanager.Database

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.OnConflictStrategy.REPLACE

@Entity(tableName = "log")
data class Swm2LogEntry(
        @ColumnInfo(name = "time") var time: Long = (System.currentTimeMillis() / 1000),
        @ColumnInfo(name = "msg") var msg: String) {
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true) var id : Long = 0
}

@Dao
interface Swm2LogDao {
    @Query("select * from log")
    fun getAllLogs(): LiveData<List<Swm2LogEntry>>

    @Query("select * from log order by time desc")
    fun getAllLogsInverse(): LiveData<List<Swm2LogEntry>>

    @Query("select * from log where time >= :time")
    fun getLogsSince(time : Long) : LiveData<List<Swm2LogEntry>>

    @Insert(onConflict = REPLACE)
    fun insertLog(entry : Swm2LogEntry) : Long

    @Query("delete from log where time < :time")
    fun deleteLogsBefore(time : Long)

}
