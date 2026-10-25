package com.stepvault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyStepEntity::class, HourlyStepEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var instance: StepDatabase? = null

        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StepDatabase::class.java,
                "stepvault.db",
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hourly_steps` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`steps` INTEGER NOT NULL, " +
                        "`distanceMeters` REAL NOT NULL, " +
                        "`calories` REAL NOT NULL, " +
                        "`activeSeconds` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epochDay`, `hour`))",
                )
            }
        }
    }
}
