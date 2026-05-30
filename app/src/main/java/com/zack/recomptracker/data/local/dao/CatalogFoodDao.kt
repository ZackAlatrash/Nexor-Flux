package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogFoodDao {
    @Query("SELECT * FROM catalog_foods ORDER BY name")
    fun observeAll(): Flow<List<CatalogFoodEntity>>

    @Query("SELECT * FROM catalog_foods ORDER BY name")
    suspend fun getAll(): List<CatalogFoodEntity>

    @Query("SELECT sourceVersion FROM catalog_foods WHERE source = :source LIMIT 1")
    fun observeSourceVersion(source: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(foods: List<CatalogFoodEntity>)

    @Query("DELETE FROM catalog_foods WHERE source = :source")
    suspend fun deleteBySource(source: String)
}
