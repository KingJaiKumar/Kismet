package net.kismetwireless.android.smarterwifimanager.Database

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(
        entities = arrayOf(
                Swm2LogEntry::class,
                SWM2Network::class,
                SWM2AccessPoint::class,
                SWM2CellTower::class
                ),
        version = 10,
        exportSchema = false)
abstract class SWM2Database : RoomDatabase() {
    abstract fun logDao(): Swm2LogDao
    abstract fun networkDao(): SWM2NetworkDao
    abstract fun apDao(): SWM2AccessPointDao
    abstract fun towerDao(): SWM2TowerDao
}