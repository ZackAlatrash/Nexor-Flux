package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseHistoryRow
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseWithSets
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionRepositoryTest {

    private class FakeSessionDao : WorkoutSessionDao() {
        val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
        val exercises = mutableMapOf<Long, SessionExerciseEntity>()
        val sets = mutableMapOf<Long, SessionSetEntity>()
        private var sId = 0L
        private var seId = 0L
        private var setId = 0L

        override suspend fun insertSession(session: WorkoutSessionEntity): Long {
            val id = ++sId; sessions[id] = session.copy(id = id); return id
        }
        override suspend fun updateSession(session: WorkoutSessionEntity) { sessions[session.id] = session }
        override suspend fun deleteSessionById(id: Long) { sessions.remove(id) }
        override suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long {
            val id = ++seId; exercises[id] = exercise.copy(id = id); return id
        }
        override suspend fun insertSet(set: SessionSetEntity): Long {
            val id = ++setId; sets[id] = set.copy(id = id); return id
        }
        override suspend fun updateSet(set: SessionSetEntity) { sets[set.id] = set }
        override suspend fun deleteSetById(id: Long) { sets.remove(id) }
        override suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int =
            sets.values.count { it.sessionExerciseId == sessionExerciseId }
        override suspend fun getSessionWithDetails(id: Long): WorkoutSessionWithDetailsDb? =
            sessions[id]?.let { build(it) }
        override fun observeActiveSession(): Flow<WorkoutSessionWithDetailsDb?> =
            flowOf(sessions.values.firstOrNull { it.status == "ACTIVE" }?.let { build(it) })
        override suspend fun getLastCompletedSession(workoutId: Long): WorkoutSessionWithDetailsDb? =
            sessions.values.filter { it.workoutId == workoutId && it.status == "COMPLETED" }
                .maxByOrNull { it.date + (it.completedAt ?: "") }?.let { build(it) }
        override fun observeCompletedSessions(): Flow<List<WorkoutSessionWithDetailsDb>> =
            flowOf(sessions.values.filter { it.status == "COMPLETED" }.map { build(it) })
        override suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow> =
            exercises.values.filter { it.exerciseId == exerciseId }
                .flatMap { se ->
                    val session = sessions.getValue(se.sessionId)
                    if (session.status != "COMPLETED") emptyList()
                    else sets.values.filter { it.sessionExerciseId == se.id && it.completed }
                        .map { ExerciseHistoryRow(session.date, it.reps, it.weightKg, it.rir) }
                }.sortedBy { it.date }

        private fun build(s: WorkoutSessionEntity) = WorkoutSessionWithDetailsDb(
            session = s,
            exercises = exercises.values.filter { it.sessionId == s.id }.map { se ->
                SessionExerciseWithSets(se, sets.values.filter { it.sessionExerciseId == se.id })
            },
        )
    }

    private fun template() = WorkoutTemplate(
        id = 7, name = "Legs", note = null, createdAt = "t", updatedAt = "t",
        exercises = listOf(
            WorkoutTemplateExercise(
                id = 1,
                exercise = Exercise(10, "Squat", "Squat", null, null, null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), false),
                plannedSets = 3, targetReps = 5, sortOrder = 0, note = null,
            ),
        ),
    )

    private fun repo(dao: FakeSessionDao): WorkoutSessionRepository {
        var tick = 0
        return WorkoutSessionRepository(dao, now = { "2026-06-17T10:0${tick++}" }, today = { "2026-06-17" })
    }

    @Test
    fun `startSession snapshots template into active session`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)

        val sessionId = repo.startSession(template())

        val session = dao.sessions.getValue(sessionId)
        assertEquals("ACTIVE", session.status)
        assertEquals("Legs", session.workoutName)
        assertEquals(7L, session.workoutId)
        assertEquals("2026-06-17", session.date)
        assertEquals(1, dao.exercises.values.count { it.sessionId == sessionId })
        assertEquals("Squat", dao.exercises.values.first().exerciseName)
    }

    @Test
    fun `addSet assigns incrementing set numbers`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id

        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 1)

        val setNumbers = dao.sets.values.filter { it.sessionExerciseId == seId }.map { it.setNumber }.sorted()
        assertEquals(listOf(1, 2), setNumbers)
    }

    @Test
    fun `addSet rejects negative reps`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id

        val ex = runCatching { repo.addSet(seId, reps = -3, weightKg = 100.0, rir = null) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `removeSet deletes the set`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id
        val setId = repo.addSet(seId, reps = 5, weightKg = 100.0, rir = null)

        repo.removeSet(setId)

        assertTrue(dao.sets.isEmpty())
    }

    @Test
    fun `completeSession marks completed with timestamp`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        val sessionId = repo.startSession(template())

        repo.completeSession(sessionId)

        val session = dao.sessions.getValue(sessionId)
        assertEquals("COMPLETED", session.status)
        assertTrue(session.completedAt != null)
    }

    @Test
    fun `getLastCompletedSession returns domain model`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        val sessionId = repo.startSession(template())
        val seId = dao.exercises.values.first().id
        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        repo.completeSession(sessionId)

        val last = repo.getLastCompletedSession(workoutId = 7)!!

        assertEquals(SessionStatus.COMPLETED, last.status)
        assertEquals(100.0, last.exercises.first().sets.first().weightKg!!, 0.0001)
    }

    @Test
    fun `getExerciseHistory returns flat dated points`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        val sessionId = repo.startSession(template())
        val seId = dao.exercises.values.first().id
        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        repo.completeSession(sessionId)

        val history = repo.getExerciseHistory(exerciseId = 10)

        assertEquals(1, history.size)
        assertEquals("2026-06-17", history.first().date)
        assertEquals(100.0, history.first().weightKg!!, 0.0001)
    }
}
