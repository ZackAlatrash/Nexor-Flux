package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealEntryDao {
    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY id")
    fun observeForDate(date: String): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE date = :date ORDER BY id")
    suspend fun getForDate(date: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date, id")
    fun observeBetween(startDate: String, endDate: String): Flow<List<MealEntryEntity>>

    /** One-shot suspend read — for tool queries that don't need live updates. */
    @Query("SELECT * FROM meal_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date, id")
    suspend fun getBetween(startDate: String, endDate: String): List<MealEntryEntity>

    @Query("SELECT * FROM meal_entries ORDER BY date, id")
    fun observeAll(): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries WHERE mealType = 'FOOD_LIBRARY' ORDER BY id DESC")
    fun observeFoodLibraryEntries(): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entries ORDER BY date, id")
    suspend fun getAll(): List<MealEntryEntity>

    @Insert
    suspend fun insert(entry: MealEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<MealEntryEntity>)

    @Update
    suspend fun update(entry: MealEntryEntity)

    @Delete
    suspend fun delete(entry: MealEntryEntity)

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM meal_entries WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM meal_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM meal_entries WHERE slotId = :slotId")
    suspend fun deleteBySlotId(slotId: Long)

    @Query("UPDATE meal_entries SET slotId = NULL WHERE slotId = :slotId")
    suspend fun clearSlotId(slotId: Long)

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getById(id: Long): MealEntryEntity?

    /** Flip a single entry's planned flag (confirm a plan = set to 0). */
    @Query("UPDATE meal_entries SET planned = :planned WHERE id = :id")
    suspend fun setPlanned(id: Long, planned: Boolean)

    /** "Confirm all" — mark every planned entry on a day as eaten. */
    @Query("UPDATE meal_entries SET planned = 0 WHERE date = :date AND planned = 1")
    suspend fun confirmPlannedForDate(date: String)

    /** Move an entry to another day, recomputing its planned flag (caller decides). */
    @Query("UPDATE meal_entries SET date = :date, planned = :planned WHERE id = :id")
    suspend fun setDateAndPlanned(id: Long, date: String, planned: Boolean)

    /** Live count of unconfirmed plans strictly before [date] — drives the reconcile prompt. */
    @Query("SELECT COUNT(*) FROM meal_entries WHERE planned = 1 AND date < :date")
    fun observeStalePlannedCount(date: String): Flow<Int>
}
