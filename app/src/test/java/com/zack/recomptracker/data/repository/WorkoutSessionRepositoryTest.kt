package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseHistoryRow
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseWithSets
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.PlannedSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        override suspend fun abandonActiveSessions() {
            sessions.keys.toList().forEach { id ->
                val s = sessions.getValue(id)
                if (s.status == SessionStatus.ACTIVE.name) sessions[id] = s.copy(status = SessionStatus.ABANDONED.name)
            }
        }
        override suspend fun updateSession(session: WorkoutSessionEntity) { sessions[session.id] = session }
        override suspend fun deleteSessionById(id: Long) { sessions.remove(id) }
        override suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long {
            val id = ++seId; exercises[id] = exercise.copy(id = id); return id
        }
        override suspend fun insertSet(set: SessionSetEntity): Long {
            val id = ++setId; sets[id] = set.copy(id = id); return id
        }
        override suspend fun getAllSessions(): List<WorkoutSessionEntity> = sessions.values.toList()
        override suspend fun getAllSessionExercises(): List<SessionExerciseEntity> = exercises.values.toList()
        override suspend fun getAllSessionSets(): List<SessionSetEntity> = sets.values.toList()
        override suspend fun updateSet(set: SessionSetEntity) { sets[set.id] = set }
        override suspend fun updateSetWeight(setId: Long, weightKg: Double?) { sets[setId] = sets.getValue(setId).copy(weightKg = weightKg) }
        override suspend fun updateSetReps(setId: Long, reps: Int) { sets[setId] = sets.getValue(setId).copy(reps = reps) }
        override suspend fun updateSetRir(setId: Long, rir: Int?) { sets[setId] = sets.getValue(setId).copy(rir = rir) }
        override suspend fun updateSetCompleted(setId: Long, completed: Boolean) { sets[setId] = sets.getValue(setId).copy(completed = completed) }
        override suspend fun getSetById(setId: Long): SessionSetEntity? = sets[setId]
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
        override suspend fun getCompletedSessionsSince(startDate: String): List<WorkoutSessionWithDetailsDb> =
            sessions.values
                .filter { it.status == "COMPLETED" && it.date >= startDate }
                .sortedByDescending { it.date + (it.completedAt ?: "") }
                .map { build(it) }
        override suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow> =
            exercises.values.filter { it.exerciseId == exerciseId }
                .flatMap { se ->
                    val session = sessions.getValue(se.sessionId)
                    if (session.status != "COMPLETED") emptyList()
                    else sets.values.filter { it.sessionExerciseId == se.id && it.completed }
                        .map { ExerciseHistoryRow(session.date, it.reps, it.weightKg, it.rir) }
                }.sortedBy { it.date }

        override suspend fun nextExerciseSortOrder(sessionId: Long): Int =
            (exercises.values.filter { it.sessionId == sessionId }.maxOfOrNull { it.sortOrder } ?: -1) + 1

        override suspend fun deleteSessionExerciseById(id: Long) { exercises.remove(id) }

        override suspend fun updateSessionExerciseSortOrder(id: Long, sortOrder: Int) {
            exercises[id] = exercises.getValue(id).copy(sortOrder = sortOrder)
        }

        override suspend fun updateSessionExerciseNote(id: Long, note: String?) {
            exercises[id] = exercises.getValue(id).copy(note = note)
        }

        override suspend fun deleteSetsForSessionExercise(sessionExerciseId: Long) {
            sets.values.filter { it.sessionExerciseId == sessionExerciseId }
                .map { it.id }
                .forEach { sets.remove(it) }
        }

        override suspend fun updateSessionExerciseExercise(id: Long, exerciseId: Long, exerciseName: String) {
            exercises[id] = exercises.getValue(id).copy(exerciseId = exerciseId, exerciseName = exerciseName)
        }

        override suspend fun updateSessionNote(id: Long, note: String?) {
            sessions[id] = sessions.getValue(id).copy(note = note)
        }

        override suspend fun updateDuration(id: Long, durationSeconds: Int?) {
            sessions[id] = sessions.getValue(id).copy(durationSeconds = durationSeconds)
        }

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
                plannedSets = listOf(
                    PlannedSet(id = 0, setNumber = 1, targetReps = 5, targetWeightKg = 100.0),
                    PlannedSet(id = 0, setNumber = 2, targetReps = 5, targetWeightKg = 100.0),
                    PlannedSet(id = 0, setNumber = 3, targetReps = 5, targetWeightKg = 100.0),
                ),
                sortOrder = 0, note = null,
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
    fun `startSession seeds blank sets when there is no previous session`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)

        val sessionId = repo.startSession(template())

        val seId = dao.exercises.values.first { it.sessionId == sessionId }.id
        val sessionSets = dao.sets.values.filter { it.sessionExerciseId == seId }.sortedBy { it.setNumber }
        assertEquals(3, sessionSets.size)
        assertTrue(sessionSets.all { !it.completed })
        assertEquals(listOf(1, 2, 3), sessionSets.map { it.setNumber })
        // Plan targets are no longer used for prefill — blank when no history.
        assertEquals(listOf(0, 0, 0), sessionSets.map { it.reps })
        assertEquals(listOf(null, null, null), sessionSets.map { it.weightKg })
    }

    @Test
    fun `startSession prefills sets from last completed session actuals`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)

        // First session: log actuals for the Squat sets, mark completed, then complete the session.
        val s1 = repo.startSession(template())
        val se1 = dao.exercises.values.first { it.sessionId == s1 }.id
        val s1sets = dao.sets.values.filter { it.sessionExerciseId == se1 }.sortedBy { it.setNumber }
        dao.updateSet(s1sets[0].copy(reps = 6, weightKg = 102.5, completed = true))
        dao.updateSet(s1sets[1].copy(reps = 5, weightKg = 100.0, completed = true))
        dao.updateSet(s1sets[2].copy(reps = 4, weightKg = 97.5, completed = true))
        repo.completeSession(s1)

        // Second session for the same routine should prefill from those actuals (uncompleted).
        val s2 = repo.startSession(template())
        val se2 = dao.exercises.values.first { it.sessionId == s2 }.id
        val s2sets = dao.sets.values.filter { it.sessionExerciseId == se2 }.sortedBy { it.setNumber }
        assertEquals(listOf(6, 5, 4), s2sets.map { it.reps })
        assertEquals(listOf(102.5, 100.0, 97.5), s2sets.map { it.weightKg })
        assertTrue(s2sets.all { !it.completed })
    }

    @Test
    fun `addSet assigns incrementing set numbers`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id

        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 1)

        // Template pre-fills 3 uncompleted sets (setNumbers 1,2,3); new sets are 4 and 5 (also uncompleted)
        val allSets = dao.sets.values.filter { it.sessionExerciseId == seId }
        val setNumbers = allSets.map { it.setNumber }.sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), setNumbers)
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

        // Only pre-filled sets remain (the 3 planned ones)
        val remaining = dao.sets.values.filter { it.sessionExerciseId == seId }
        assertEquals(3, remaining.size)
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
        val setId = repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        // Mark the newly added set as completed so history/last-session queries include it
        val addedSet = dao.sets.getValue(setId)
        dao.updateSet(addedSet.copy(completed = true))
        repo.completeSession(sessionId)

        val last = repo.getLastCompletedSession(workoutId = 7)!!

        assertEquals(SessionStatus.COMPLETED, last.status)
        // The first completed set with our weight
        val completedSet = last.exercises.first().sets.first { it.completed && it.weightKg == 100.0 }
        assertEquals(100.0, completedSet.weightKg!!, 0.0001)
    }

    @Test
    fun `getCompletedSessionsSince windows by date, keeps only completed, maps to domain`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        // Seed three sessions directly: one in-window completed, one out-of-window completed, one
        // in-window but ACTIVE (must be excluded).
        dao.insertSession(WorkoutSessionEntity(
            workoutId = 1, workoutName = "In", date = "2026-06-20", startedAt = "2026-06-20T10:00",
            completedAt = "2026-06-20T11:00", status = "COMPLETED", note = null, durationSeconds = null,
        ))
        dao.insertSession(WorkoutSessionEntity(
            workoutId = 1, workoutName = "Old", date = "2026-05-01", startedAt = "2026-05-01T10:00",
            completedAt = "2026-05-01T11:00", status = "COMPLETED", note = null, durationSeconds = null,
        ))
        dao.insertSession(WorkoutSessionEntity(
            workoutId = 1, workoutName = "Active", date = "2026-06-25", startedAt = "2026-06-25T10:00",
            completedAt = null, status = "ACTIVE", note = null, durationSeconds = null,
        ))

        val result = repo.getCompletedSessionsSince(LocalDate.of(2026, 6, 1))

        assertEquals(1, result.size)
        assertEquals("In", result.first().workoutName)
        assertEquals(SessionStatus.COMPLETED, result.first().status)
        assertEquals("2026-06-20", result.first().date)
    }

    @Test
    fun `getCompletedSessionsSince returns newest first`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        dao.insertSession(WorkoutSessionEntity(
            workoutId = 1, workoutName = "Older", date = "2026-06-10", startedAt = "2026-06-10T10:00",
            completedAt = "2026-06-10T11:00", status = "COMPLETED", note = null, durationSeconds = null,
        ))
        dao.insertSession(WorkoutSessionEntity(
            workoutId = 1, workoutName = "Newer", date = "2026-06-28", startedAt = "2026-06-28T10:00",
            completedAt = "2026-06-28T11:00", status = "COMPLETED", note = null, durationSeconds = null,
        ))

        val result = repo.getCompletedSessionsSince(LocalDate.of(2026, 6, 1))

        assertEquals(listOf("Newer", "Older"), result.map { it.workoutName })
    }

    @Test
    fun `getExerciseHistory returns flat dated points`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)
        val sessionId = repo.startSession(template())
        val seId = dao.exercises.values.first().id
        val setId = repo.addSet(seId, reps = 5, weightKg = 100.0, rir = 2)
        // Mark the newly added set as completed so history query includes it
        val addedSet = dao.sets.getValue(setId)
        dao.updateSet(addedSet.copy(completed = true))
        repo.completeSession(sessionId)

        val history = repo.getExerciseHistory(exerciseId = 10)

        assertEquals(1, history.size)
        assertEquals("2026-06-17", history.first().date)
        assertEquals(100.0, history.first().weightKg!!, 0.0001)
    }

    @Test
    fun `addExerciseToSession appends at next sortOrder`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        val sid = repo.startSession(template())
        repo.addExerciseToSession(sid, exerciseId = 99, exerciseName = "Dip")
        val added = dao.exercises.values.single { it.exerciseName == "Dip" }
        assertEquals(sid, added.sessionId)
        assertEquals(1, added.sortOrder) // template() has 1 exercise at sortOrder 0
    }

    @Test
    fun `removeSessionExercise deletes it`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id
        repo.removeSessionExercise(seId)
        assertTrue(dao.exercises.values.none { it.id == seId })
    }

    @Test
    fun `reorderSessionExercises rewrites sortOrder`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        val sid = repo.startSession(template())
        val first = dao.exercises.values.first().id
        val second = repo.addExerciseToSession(sid, exerciseId = 99, exerciseName = "Dip")
        repo.reorderSessionExercises(listOf(second, first))
        assertEquals(0, dao.exercises.getValue(second).sortOrder)
        assertEquals(1, dao.exercises.getValue(first).sortOrder)
    }

    @Test
    fun `setSessionExerciseNote and setSessionNote persist`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        val sid = repo.startSession(template())
        val seId = dao.exercises.values.first().id
        repo.setSessionExerciseNote(seId, "tempo 3-1-1")
        repo.setSessionNote(sid, "felt strong")
        assertEquals("tempo 3-1-1", dao.exercises.getValue(seId).note)
        assertEquals("felt strong", dao.sessions.getValue(sid).note)
    }

    @Test fun `completeSession stores duration override`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        val sid = repo.startSession(template())
        repo.completeSession(sid, durationSeconds = 3120)
        val s = repo.getLastCompletedSession(workoutId = 7)!!
        assertEquals(3120, s.durationSeconds)
    }

    @Test
    fun `replaceSessionExercise repoints id and name and keeps set count but clears values`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        repo.startSession(template())
        val seId = dao.exercises.values.first().id
        // Simulate logged work on the original (Squat): values set + one completed.
        dao.sets.values.filter { it.sessionExerciseId == seId }.forEach {
            dao.updateSet(it.copy(reps = 5, weightKg = 100.0, rir = 2, completed = it.setNumber == 1))
        }

        repo.replaceSessionExercise(seId, newExerciseId = 42, newExerciseName = "Dumbbell Press")

        // Row repointed to the new exercise.
        val row = dao.exercises.getValue(seId)
        assertEquals(42L, row.exerciseId)
        assertEquals("Dumbbell Press", row.exerciseName)
        // Same number of sets, but all values reset to blank/uncompleted with sequential numbers.
        val newSets = dao.sets.values.filter { it.sessionExerciseId == seId }.sortedBy { it.setNumber }
        assertEquals(3, newSets.size)
        assertEquals(listOf(1, 2, 3), newSets.map { it.setNumber })
        assertTrue(newSets.all { it.reps == 0 && it.weightKg == null && it.rir == null && !it.completed })
    }

    @Test
    fun `replaceSessionExercise does not touch other session exercises`() = runTest {
        val dao = FakeSessionDao(); val repo = repo(dao)
        val sid = repo.startSession(template())
        val firstId = dao.exercises.values.first().id
        val otherId = repo.addExerciseToSession(sid, exerciseId = 99, exerciseName = "Dip")
        repo.addSet(otherId, reps = 8, weightKg = 20.0, rir = 1)

        repo.replaceSessionExercise(firstId, newExerciseId = 42, newExerciseName = "Dumbbell Press")

        // The untouched exercise keeps its id, name, and its logged set.
        val other = dao.exercises.getValue(otherId)
        assertEquals(99L, other.exerciseId)
        assertEquals("Dip", other.exerciseName)
        val otherSets = dao.sets.values.filter { it.sessionExerciseId == otherId }
        assertEquals(1, otherSets.size)
        assertEquals(8, otherSets.first().reps)
    }

    // ── P1-16: starting a session must not leave two ACTIVE sessions ─────────────

    @Test
    fun `starting a session abandons any existing active session so there is never a double-active`() = runTest {
        val dao = FakeSessionDao()
        val repo = repo(dao)

        val first = repo.startSession(template())   // in-progress workout
        val second = repo.startSession(template())  // user starts another before finishing the first

        // Exactly one ACTIVE session — the new one; the first is retired (not orphaned/double-active).
        assertEquals(1, dao.getAllSessions().count { it.status == SessionStatus.ACTIVE.name })
        assertEquals(SessionStatus.ACTIVE.name, dao.sessions.getValue(second).status)
        assertEquals(SessionStatus.ABANDONED.name, dao.sessions.getValue(first).status)

        // The retired session's logged sets are preserved (status change only, no delete).
        val firstExerciseIds = dao.exercises.values.filter { ex ->
            ex.sessionId == first
        }.map { it.id }.toSet()
        assertTrue(dao.sets.values.any { it.sessionExerciseId in firstExerciseIds })
    }
}
