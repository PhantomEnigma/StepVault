package com.stepvault.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface StepDao {
    @Query("SELECT * FROM daily_steps WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC")
    suspend fun getRange(from: Long, to: Long): List<DailyStepEntity>

    @Query("SELECT * FROM daily_steps ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): DailyStepEntity?

    @Upsert
    suspend fun upsertAll(rows: List<DailyStepEntity>)

    @Query("DELETE FROM daily_steps WHERE epochDay < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM hourly_steps WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay ASC, hour ASC")
    suspend fun getHourlyRange(from: Long, to: Long): List<HourlyStepEntity>

    @Upsert
    suspend fun upsertHourlyAll(rows: List<HourlyStepEntity>)

    @Query("DELETE FROM hourly_steps WHERE epochDay = :epochDay")
    suspend fun deleteHourlyForDay(epochDay: Long)

    @Query("DELETE FROM hourly_steps WHERE epochDay < :before")
    suspend fun deleteHourlyOlderThan(before: Long)
}
