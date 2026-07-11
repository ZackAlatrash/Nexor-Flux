package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /** One-shot read of every exercise (both bundled and custom) — for backup export. */
    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun search(query: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT sourceVersion FROM exercises WHERE source = :source LIMIT 1")
    suspend fun sourceVersion(source: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturningId(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("SELECT id FROM exercises WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun findIdBySourceAndExternalId(source: String, externalId: String): Long?

    /**
     * Inserts a custom exercise, or returns the id of the existing row with the same
     * (source, externalId). Never REPLACE-deletes a conflicting row — doing so crashed on the FK
     * (NO ACTION) from a routine/session already using it (P1-20). Transactional so two rapid adds of
     * the same-named exercise are race-safe (the second finds the first's row).
     */
    @Transaction
    suspend fun insertCustomOrGetExisting(entity: ExerciseEntity): Long =
        findIdBySourceAndExternalId(entity.source, entity.externalId) ?: insertReturningId(entity)

    @Query("UPDATE exercises SET sourceVersion = :version WHERE source = :source")
    suspend fun stampSourceVersion(source: String, version: String)

    /**
     * Id-preserving upsert of a bundled library keyed on (source, externalId). Existing rows are
     * UPDATEd in place — their `id` never changes — so FK references from workout_exercises /
     * session_exercises stay valid; a delete-then-insert re-seed instead hit those FK (NO ACTION)
     * constraints and crashed (P1-19). New rows are inserted; rows removed from the new library
     * linger harmlessly. Every row of [source] is stamped to [version] so the seed version-gate
     * reflects the completed re-seed. Runs in one transaction.
     */
    @Transaction
    suspend fun upsertLibrary(source: String, version: String, exercises: List<ExerciseEntity>) {
        for (e in exercises) {
            val existingId = findIdBySourceAndExternalId(e.source, e.externalId)
            if (existingId != null) update(e.copy(id = existingId)) else insertReturningId(e)
        }
        stampSourceVersion(source, version)
    }

    @Query("DELETE FROM exercises WHERE source = :source")
    suspend fun deleteBySource(source: String)
}
