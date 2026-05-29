package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealSlotDao {
    @Query("SELECT * FROM meal_slots ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<MealSlotEntity>>

    @Query("SELECT * FROM meal_slots ORDER BY sort_order ASC")
    suspend fun getAll(): List<MealSlotEntity>

    @Insert
    suspend fun insert(slot: MealSlotEntity): Long

    @Update
    suspend fun update(slot: MealSlotEntity)

    @Query("DELETE FROM meal_slots WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE meal_slots SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
