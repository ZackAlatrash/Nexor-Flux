package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
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
    private val dailyLogDao: DailyLogDao? = null,
) {

    /** Creates an ACTIVE session snapshotting the template's name and exercises.
     *  Each set's KG/REPS are pre-filled from the matching exercise + set index of the
     *  last COMPLETED session for this routine (actual values). When there is no prior
     *  session — or no matching set — the set is left blank (reps=0, weightKg=null).
     *  Routine plan targets are not used for prefill. All sets start uncompleted. */
    open suspend fun startSession(template: WorkoutTemplate): Long {
        // Retire any in-progress session and insert the new one atomically, so two ACTIVE sessions
        // can never coexist (which would orphan the older workout's logged sets — P1-16).
        val sessionId = sessionDao.abandonActiveAndInsertSession(
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
        // exerciseId -> previous (reps, weightKg) ordered by set index, from the last completed session.
        val prevByExercise: Map<Long, List<Pair<Int, Double?>>> =
            sessionDao.getLastCompletedSession(template.id)?.toDomain()?.exercises
                ?.groupBy { it.exerciseId }
                ?.mapValues { (_, exs) ->
                    // A routine normally has one row per exercise; if duplicated, take the first.
                    exs.first().sets.sortedBy { it.setNumber }.map { it.reps to it.weightKg }
                }
                ?: emptyMap()

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
            val prevSets = prevByExercise[line.exercise.id]
            line.plannedSets.forEachIndexed { setIdx, ps ->
                val prev = prevSets?.getOrNull(setIdx)
                sessionDao.insertSet(
                    SessionSetEntity(
                        sessionExerciseId = seId,
                        setNumber = ps.setNumber,
                        reps = prev?.first ?: 0,
                        weightKg = prev?.second,
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
                completed = false,
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

    /**
     * Replace the exercise in a single session_exercises row (this workout only — the routine
     * template is never touched). Keeps the same number of sets but resets their values to blank,
     * because reps/weights are exercise-specific. No-op if the row count is 0.
     *
     * @param sessionExerciseId the session_exercises row to repoint
     * @param newExerciseId     library id of the replacement exercise
     * @param newExerciseName   denormalised name to store on the row
     */
    open suspend fun replaceSessionExercise(
        sessionExerciseId: Long,
        newExerciseId: Long,
        newExerciseName: String,
    ) {
        val setCount = sessionDao.getSessionExerciseSetCount(sessionExerciseId)
        sessionDao.updateSessionExerciseExercise(sessionExerciseId, newExerciseId, newExerciseName)
        sessionDao.deleteSetsForSessionExercise(sessionExerciseId)
        for (setNumber in 1..setCount) {
            sessionDao.insertSet(
                SessionSetEntity(
                    sessionExerciseId = sessionExerciseId,
                    setNumber = setNumber,
                    reps = 0,
                    weightKg = null,
                    rir = null,
                    completed = false,
                ),
            )
        }
    }

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
        dailyLogDao?.let { markDayTrained(it, current.date) }
    }

    open suspend fun updateSessionDuration(sessionId: Long, durationSeconds: Int?) =
        sessionDao.updateDuration(sessionId, durationSeconds)

    open suspend fun abandonSession(sessionId: Long) = setStatus(sessionId, SessionStatus.ABANDONED)

    open fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { it?.toDomain() }

    open suspend fun getLastCompletedSession(workoutId: Long): WorkoutSession? =
        sessionDao.getLastCompletedSession(workoutId)?.toDomain()

    open fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.observeCompletedSessions().map { list -> list.map { it.toDomain() } }

    /**
     * Windowed one-shot read of completed sessions dated on/after [start], mapped to the domain
     * model. Bounds the training snapshot load for the coach context builder instead of pulling all
     * history via [observeCompletedSessions] (docs/ai-redesign/08-technical-architecture.md §6).
     */
    open suspend fun getCompletedSessionsSince(start: LocalDate): List<WorkoutSession> =
        sessionDao.getCompletedSessionsSince(start.toString()).map { it.toDomain() }

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

/**
 * Sets the `trained` flag to true on the daily log for [date], creating the row if absent and
 * preserving any other metrics already recorded. Idempotent.
 */
internal suspend fun markDayTrained(dailyLogDao: DailyLogDao, date: String) {
    val existing = dailyLogDao.getByDate(date)
    when {
        existing == null -> dailyLogDao.upsert(DailyLogEntity(date = date, trained = true))
        !existing.trained -> dailyLogDao.upsert(existing.copy(trained = true))
    }
}
