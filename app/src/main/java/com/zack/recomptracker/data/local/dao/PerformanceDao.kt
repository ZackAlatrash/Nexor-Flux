package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceDao {
    @Query("SELECT * FROM lift_performance ORDER BY date, liftName")
    fun observeAll(): Flow<List<LiftPerformanceEntity>>

    @Query("SELECT * FROM lift_performance ORDER BY date, liftName")
    suspend fun getAll(): List<LiftPerformanceEntity>

    @Insert
    suspend fun insert(performance: LiftPerformanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(performances: List<LiftPerformanceEntity>)

    @Query("DELETE FROM lift_performance WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM lift_performance")
    suspend fun deleteAll()
}
