package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanVersionDao {
    @Query("SELECT * FROM plan_versions ORDER BY effectiveFrom ASC")
    fun observeAll(): Flow<List<PlanVersionEntity>>

    @Query("SELECT * FROM plan_versions ORDER BY effectiveFrom ASC")
    suspend fun getAll(): List<PlanVersionEntity>

    @Query("SELECT COUNT(*) FROM plan_versions")
    suspend fun count(): Int

    /** Upsert by PK (effectiveFrom): a second change on the same day replaces that day's row. */
    @Upsert
    suspend fun upsert(version: PlanVersionEntity)

    @Query("DELETE FROM plan_versions")
    suspend fun deleteAll()
}
