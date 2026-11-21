package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TranscriptionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        @Volatile
        private var lastAppContext: Context? = null

        fun getInstance(context: Context): VaultDatabase {
            val appContext = context.applicationContext
            val cached = INSTANCE
            if (cached != null && lastAppContext === appContext) {
                return cached
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && lastAppContext === appContext) {
                    existing
                } else {
                    Room.databaseBuilder(
                        appContext,
                        VaultDatabase::class.java,
                        "vaultscribe_local.db",
                    ).build().also {
                        INSTANCE = it
                        lastAppContext = appContext
                    }
                }
            }
        }
    }
}
