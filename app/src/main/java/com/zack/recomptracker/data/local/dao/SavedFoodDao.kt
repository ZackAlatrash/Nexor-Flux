package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFoodDao {
    @Query("SELECT * FROM saved_foods ORDER BY name")
    fun observeAll(): Flow<List<SavedFoodEntity>>

    @Query("SELECT * FROM saved_foods ORDER BY name")
    suspend fun getAll(): List<SavedFoodEntity>

    @Insert
    suspend fun insert(food: SavedFoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<SavedFoodEntity>)

    @Update
    suspend fun update(food: SavedFoodEntity)

    @Query("DELETE FROM saved_foods WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_foods")
    suspend fun deleteAll()
}
