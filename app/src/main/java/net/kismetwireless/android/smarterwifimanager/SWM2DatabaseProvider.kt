package net.kismetwireless.android.smarterwifimanager

import android.arch.persistence.room.Room
import android.content.Context
import net.kismetwireless.android.smarterwifimanager.Database.SWM2Database

fun provideSWM2Database(context : Context) : SWM2Database {
    return Room.databaseBuilder(
            context,
            SWM2Database::class.java,
            "swm2.db"
    ).fallbackToDestructiveMigration()
    .build()
}