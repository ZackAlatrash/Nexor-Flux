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

    @Query(
        "SELECT s.date AS date, st.reps AS reps, st.weightKg AS weightKg, st.rir AS rir " +
            "FROM session_sets st " +
            "JOIN session_exercises se ON st.sessionExerciseId = se.id " +
            "JOIN workout_sessions s ON se.sessionId = s.id " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND st.completed = 1 " +
            "ORDER BY s.date",
    )
    abstract suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow>
}
