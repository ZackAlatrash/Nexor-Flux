package com.zack.recomptracker.ui.train

import androidx.lifecycle.SavedStateHandle
import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.ExerciseHistoryRow
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseWithSets
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSummaryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Minimal in-memory session DAO holding one ACTIVE session with one exercise + set. */
    private class FakeDao : WorkoutSessionDao() {
        val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
        val exercises = mutableMapOf<Long, SessionExerciseEntity>()
        val sets = mutableMapOf<Long, SessionSetEntity>()
        override suspend fun insertSession(session: WorkoutSessionEntity): Long {
            sessions[session.id] = session; return session.id
        }
        override suspend fun updateSession(session: WorkoutSessionEntity) { sessions[session.id] = session }
        override suspend fun deleteSessionById(id: Long) { sessions.remove(id) }
        override suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long {
            exercises[exercise.id] = exercise; return exercise.id
        }
        override suspend fun insertSet(set: SessionSetEntity): Long { sets[set.id] = set; return set.id }
        override suspend fun updateSet(set: SessionSetEntity) { sets[set.id] = set }
        override suspend fun deleteSetById(id: Long) { sets.remove(id) }
        override suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int =
            sets.values.count { it.sessionExerciseId == sessionExerciseId }
        override suspend fun getSessionWithDetails(id: Long): WorkoutSessionWithDetailsDb? =
            sessions[id]?.let { build(it) }
        override fun observeActiveSession(): Flow<WorkoutSessionWithDetailsDb?> =
            flowOf(sessions.values.firstOrNull { it.status == "ACTIVE" }?.let { build(it) })
        override suspend fun getLastCompletedSession(workoutId: Long): WorkoutSessionWithDetailsDb? = null
        override fun observeCompletedSessions(): Flow<List<WorkoutSessionWithDetailsDb>> = flowOf(emptyList())
        override suspend fun getCompletedSessionsSince(startDate: String): List<WorkoutSessionWithDetailsDb> = emptyList()
        override suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow> = emptyList()
        override suspend fun nextExerciseSortOrder(sessionId: Long): Int = 0
        override suspend fun deleteSessionExerciseById(id: Long) { exercises.remove(id) }
        override suspend fun updateSessionExerciseSortOrder(id: Long, sortOrder: Int) {}
        override suspend fun updateSessionExerciseNote(id: Long, note: String?) {}
        override suspend fun deleteSetsForSessionExercise(sessionExerciseId: Long) {}
        override suspend fun updateSessionExerciseExercise(id: Long, exerciseId: Long, exerciseName: String) {}
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

    private class NoopLibraryRepo : ExerciseLibraryRepository(NoopExerciseDao()) {
        override suspend fun getById(id: Long) = null
    }
    private class NoopExerciseDao : ExerciseDao {
        override fun observeAll(): Flow<List<ExerciseEntity>> = MutableStateFlow(emptyList())
        override suspend fun search(query: String): List<ExerciseEntity> = emptyList()
        override suspend fun getById(id: Long): ExerciseEntity? = null
        override suspend fun count(): Int = 0
        override suspend fun sourceVersion(source: String): String? = null
        override suspend fun insertAll(exercises: List<ExerciseEntity>) {}
        override suspend fun deleteBySource(source: String) {}
        override suspend fun insertReturningId(exercise: ExerciseEntity): Long = 0L
    }

    private fun seedActive(dao: FakeDao) {
        dao.sessions[5L] = WorkoutSessionEntity(
            id = 5L, workoutId = 7L, workoutName = "Legs", date = "2026-06-23",
            startedAt = "2026-06-23T10:00:00Z", completedAt = null, status = "ACTIVE",
            note = null, durationSeconds = 1800,
        )
        dao.exercises[1L] = SessionExerciseEntity(
            id = 1L, sessionId = 5L, exerciseId = 10L, exerciseName = "Squat", sortOrder = 0, note = null,
        )
        dao.sets[1L] = SessionSetEntity(
            id = 1L, sessionExerciseId = 1L, setNumber = 1, reps = 5, weightKg = 100.0, rir = null, completed = true,
        )
    }

    @Test
    fun `save completes the session`() = runTest {
        val dao = FakeDao().also { seedActive(it) }
        val repo = WorkoutSessionRepository(dao, now = { "2026-06-23T11:00:00Z" }, today = { "2026-06-23" })
        val viewModel = SessionSummaryViewModel(repo, NoopLibraryRepo(), SavedStateHandle(mapOf("sessionId" to 5L)))
        advanceUntilIdle() // let init load()

        viewModel.save()

        assertEquals("COMPLETED", dao.sessions.getValue(5L).status)
    }

    @Test
    fun `discard abandons the session`() = runTest {
        val dao = FakeDao().also { seedActive(it) }
        val repo = WorkoutSessionRepository(dao, now = { "2026-06-23T11:00:00Z" }, today = { "2026-06-23" })
        val viewModel = SessionSummaryViewModel(repo, NoopLibraryRepo(), SavedStateHandle(mapOf("sessionId" to 5L)))
        advanceUntilIdle()

        viewModel.discard()

        assertEquals("ABANDONED", dao.sessions.getValue(5L).status)
    }
}
