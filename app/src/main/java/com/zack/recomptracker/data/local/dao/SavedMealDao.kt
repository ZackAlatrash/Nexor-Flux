package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMealDao {
    @Query("SELECT * FROM saved_meals ORDER BY name")
    fun observeAll(): Flow<List<SavedMealEntity>>

    @Query("SELECT * FROM saved_meals ORDER BY name")
    suspend fun getAll(): List<SavedMealEntity>

    @Insert
    suspend fun insert(meal: SavedMealEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meals: List<SavedMealEntity>)

    @Update
    suspend fun update(meal: SavedMealEntity)

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_meals")
    suspend fun deleteAll()
}
