package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs WHERE date = :date")
    fun observeByDate(date: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    suspend fun getByDate(date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs ORDER BY date")
    fun observeAll(): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs ORDER BY date")
    suspend fun getAll(): List<DailyLogEntity>

    @Query("SELECT * FROM daily_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeBetween(startDate: String, endDate: String): Flow<List<DailyLogEntity>>

    @Upsert
    suspend fun upsert(log: DailyLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<DailyLogEntity>)

    @Query("DELETE FROM daily_logs")
    suspend fun deleteAll()
}
