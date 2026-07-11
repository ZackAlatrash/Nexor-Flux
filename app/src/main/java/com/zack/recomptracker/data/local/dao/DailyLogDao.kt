package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // ── Partial single-metric writes (review P2-7) ──────────────────────────────────────────────
    // A single-column UPDATE never reads or rewrites the sibling columns, so it can't clobber a
    // concurrent whole-row save from the check-in sheet. [insertEmptyIfAbsent] guarantees a row to
    // update; [upsertMetric] wraps the two in a transaction so they can't interleave with a delete.

    /** Create an empty row for [date] if none exists, so a following single-column UPDATE lands. */
    @Query("INSERT OR IGNORE INTO daily_logs (date, trained, notes) VALUES (:date, 0, '')")
    suspend fun insertEmptyIfAbsent(date: String)

    @Query("UPDATE daily_logs SET bodyWeightKg = :v WHERE date = :date")
    suspend fun updateBodyWeightKg(date: String, v: Double)

    @Query("UPDATE daily_logs SET waistCm = :v WHERE date = :date")
    suspend fun updateWaistCm(date: String, v: Double)

    @Query("UPDATE daily_logs SET sleepHours = :v WHERE date = :date")
    suspend fun updateSleepHours(date: String, v: Double)

    @Query("UPDATE daily_logs SET energyScore = :v WHERE date = :date")
    suspend fun updateEnergyScore(date: String, v: Int)

    @Query("UPDATE daily_logs SET hungerScore = :v WHERE date = :date")
    suspend fun updateHungerScore(date: String, v: Int)

    @Query("UPDATE daily_logs SET sorenessScore = :v WHERE date = :date")
    suspend fun updateSorenessScore(date: String, v: Int)

    /**
     * Sets ONE daily metric for [date] with a partial column UPDATE (creating the row if absent), so
     * it cannot clobber the other columns a concurrent whole-row save wrote (review P2-7). [metric]
     * uses the coach `log_metric` vocabulary; an unknown name is a true no-op — no row is created.
     * Scores store the integer part. The row-insert lives inside each known branch so an unrecognised
     * metric can't leave a stray empty row behind.
     */
    @Transaction
    suspend fun upsertMetric(date: String, metric: String, value: Double) {
        when (metric) {
            "weight_kg" -> { insertEmptyIfAbsent(date); updateBodyWeightKg(date, value) }
            "waist_cm" -> { insertEmptyIfAbsent(date); updateWaistCm(date, value) }
            "sleep_hours" -> { insertEmptyIfAbsent(date); updateSleepHours(date, value) }
            "energy_score" -> { insertEmptyIfAbsent(date); updateEnergyScore(date, value.toInt()) }
            "hunger_score" -> { insertEmptyIfAbsent(date); updateHungerScore(date, value.toInt()) }
            "soreness_score" -> { insertEmptyIfAbsent(date); updateSorenessScore(date, value.toInt()) }
        }
    }
}
