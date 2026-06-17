package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.ExerciseHistoryRow
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutSessionDao {

    @Insert
    abstract suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    abstract suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun deleteSessionById(id: Long)

    @Insert
    abstract suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long

    @Insert
    abstract suspend fun insertSet(set: SessionSetEntity): Long

    @Update
    abstract suspend fun updateSet(set: SessionSetEntity)

    @Query("DELETE FROM session_sets WHERE id = :id")
    abstract suspend fun deleteSetById(id: Long)

    @Query("SELECT COUNT(*) FROM session_sets WHERE sessionExerciseId = :sessionExerciseId")
    abstract suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int

    /**
     * Inserts a set, assigning the next sequential setNumber within a single transaction so
     * concurrent calls can't produce duplicate set numbers. The [set]'s own setNumber is ignored.
     * Returns the new row id.
     */
    @Transaction
    open suspend fun insertNextSet(set: SessionSetEntity): Long {
        val nextNumber = getSessionExerciseSetCount(set.sessionExerciseId) + 1
        return insertSet(set.copy(setNumber = nextNumber))
    }

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getSessionWithDetails(id: Long): WorkoutSessionWithDetailsDb?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActiveSession(): Flow<WorkoutSessionWithDetailsDb?>

    @Transaction
    @Query(
        "SELECT * FROM workout_sessions WHERE workoutId = :workoutId AND status = 'COMPLETED' " +
            "ORDER BY date DESC, completedAt DESC LIMIT 1",
    )
    abstract suspend fun getLastCompletedSession(workoutId: Long): WorkoutSessionWithDetailsDb?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY date DESC, completedAt DESC")
    abstract fun observeCompletedSessions(): Flow<List<WorkoutSessionWithDetailsDb>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM session_exercises WHERE sessionId = :sessionId")
    abstract suspend fun nextExerciseSortOrder(sessionId: Long): Int

    @Query("DELETE FROM session_exercises WHERE id = :id")
    abstract suspend fun deleteSessionExerciseById(id: Long)

    @Query("UPDATE session_exercises SET sortOrder = :sortOrder WHERE id = :id")
    abstract suspend fun updateSessionExerciseSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE session_exercises SET note = :note WHERE id = :id")
    abstract suspend fun updateSessionExerciseNote(id: Long, note: String?)

    @Query("UPDATE workout_sessions SET note = :note WHERE id = :id")
    abstract suspend fun updateSessionNote(id: Long, note: String?)

    @Query("UPDATE workout_sessions SET durationSeconds = :durationSeconds WHERE id = :id")
    abstract suspend fun updateDuration(id: Long, durationSeconds: Int?)

    @Query(
        "SELECT s.date AS date, st.reps AS reps, st.weightKg AS weightKg, st.rir AS rir " +
            "FROM session_sets st " +
            "JOIN session_exercises se ON st.sessionExerciseId = se.id " +
            "JOIN workout_sessions s ON se.sessionId = s.id " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND st.completed = 1 " +
            "ORDER BY s.date, s.startedAt",
    )
    abstract suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow>
}
