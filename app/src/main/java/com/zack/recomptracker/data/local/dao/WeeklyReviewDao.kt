package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {
    @Query("SELECT * FROM weekly_reviews ORDER BY weekStart DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>

    @Query("SELECT * FROM weekly_reviews ORDER BY weekStart DESC")
    suspend fun getAll(): List<WeeklyReviewEntity>

    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart LIMIT 1")
    suspend fun getByWeekStart(weekStart: String): WeeklyReviewEntity?

    @Upsert
    suspend fun upsert(review: WeeklyReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<WeeklyReviewEntity>)

    @Query("DELETE FROM weekly_reviews")
    suspend fun deleteAll()
}
