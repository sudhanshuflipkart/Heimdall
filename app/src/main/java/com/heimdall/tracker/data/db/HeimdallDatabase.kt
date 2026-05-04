package com.heimdall.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RunEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HeimdallDatabase : RoomDatabase() {

    abstract fun runDao(): RunDao

    companion object {
        @Volatile
        private var INSTANCE: HeimdallDatabase? = null

        fun getInstance(context: Context): HeimdallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HeimdallDatabase::class.java,
                    "heimdall_runs.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
