package com.rescuemesh.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NodeIdentityEntity::class,
        MessageEntity::class,
        MessageReceiptEntity::class,
        NeighborEntity::class,
        ForwardAttemptEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rescuemesh.db",
                ).build().also { instance = it }
            }
        }
    }
}
