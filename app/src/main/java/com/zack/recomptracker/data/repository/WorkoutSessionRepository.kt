package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.domain.workout.ExerciseHistoryPoint
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.ValidationResult
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutValidation
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class WorkoutSessionRepository(
    private val sessionDao: WorkoutSessionDao,
    private val now: () -> String = { Instant.now().toString() },
    private val today: () -> String = { LocalDate.now().toString() },
) {

    /** Creates an ACTIVE session snapshotting the template's name and exercises,
     *  pre-filling session_sets from planned sets (completed=false). */
    open suspend fun startSession(template: WorkoutTemplate): Long {
        val sessionId = sessionDao.insertSession(
            WorkoutSessionEntity(
                workoutId = template.id,
                workoutName = template.name,
                date = today(),
                startedAt = now(),
                completedAt = null,
                status = SessionStatus.ACTIVE.name,
                note = null,
                durationSeconds = null,
            ),
        )
        template.exercises.sortedBy { it.sortOrder }.forEachIndexed { index, line ->
            val seId = sessionDao.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = line.exercise.id,
                    exerciseName = line.exercise.name,
                    sortOrder = index,
                    note = line.note,
                ),
            )
            line.plannedSets.forEach { ps ->
                sessionDao.insertSet(
                    SessionSetEntity(
                        sessionExerciseId = seId,
                        setNumber = ps.setNumber,
                        reps = ps.targetReps ?: 0,
                        weightKg = ps.targetWeightKg,
                        rir = null,
                        completed = false,
                    ),
                )
            }
        }
        return sessionId
    }

    open suspend fun addSet(sessionExerciseId: Long, reps: Int, weightKg: Double?, rir: Int?): Long {
        val result = WorkoutValidation.validateSet(reps, weightKg, rir)
        if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reasons.joinToString(" "))

        return sessionDao.insertNextSet(
            SessionSetEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = 0, // assigned atomically by insertNextSet
                reps = reps,
                weightKg = weightKg,
                rir = rir,
                completed = true,
            ),
        )
    }

    open suspend fun updateSet(set: SessionSetEntity) {
        val result = WorkoutValidation.validateSet(set.reps, set.weightKg, set.rir)
        if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reasons.joinToString(" "))
        sessionDao.updateSet(set)
    }

    open suspend fun removeSet(setId: Long) = sessionDao.deleteSetById(setId)

    open suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long, exerciseName: String): Long =
        sessionDao.insertSessionExercise(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                sortOrder = sessionDao.nextExerciseSortOrder(sessionId),
                note = null,
            ),
        )

    open suspend fun removeSessionExercise(sessionExerciseId: Long) =
        sessionDao.deleteSessionExerciseById(sessionExerciseId)

    open suspend fun reorderSessionExercises(orderedSessionExerciseIds: List<Long>) {
        orderedSessionExerciseIds.forEachIndexed { i, id ->
            sessionDao.updateSessionExerciseSortOrder(id, i)
        }
    }

    open suspend fun setSessionExerciseNote(sessionExerciseId: Long, note: String?) =
        sessionDao.updateSessionExerciseNote(sessionExerciseId, note)

    open suspend fun setSessionNote(sessionId: Long, note: String?) =
        sessionDao.updateSessionNote(sessionId, note)

    open suspend fun completeSession(sessionId: Long, durationSeconds: Int? = null) {
        val current = sessionDao.getSessionWithDetails(sessionId)?.session ?: return
        sessionDao.updateSession(current.copy(
            status = SessionStatus.COMPLETED.name,
            completedAt = now(),
            durationSeconds = durationSeconds,
        ))
    }

    open suspend fun abandonSession(sessionId: Long) = setStatus(sessionId, SessionStatus.ABANDONED)

    open fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { it?.toDomain() }

    open suspend fun getLastCompletedSession(workoutId: Long): WorkoutSession? =
        sessionDao.getLastCompletedSession(workoutId)?.toDomain()

    open fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.observeCompletedSessions().map { list -> list.map { it.toDomain() } }

    open suspend fun getSession(sessionId: Long): WorkoutSession? =
        sessionDao.getSessionWithDetails(sessionId)?.toDomain()

    open suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryPoint> =
        sessionDao.getExerciseHistory(exerciseId).map {
            ExerciseHistoryPoint(date = it.date, reps = it.reps, weightKg = it.weightKg, rir = it.rir)
        }

    private suspend fun setStatus(sessionId: Long, status: SessionStatus) {
        val current = sessionDao.getSessionWithDetails(sessionId)?.session ?: return
        sessionDao.updateSession(
            current.copy(
                status = status.name,
                completedAt = if (status == SessionStatus.COMPLETED) now() else current.completedAt,
            ),
        )
    }
}
