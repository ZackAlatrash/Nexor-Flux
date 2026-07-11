package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun activeSession(): WorkoutSession = WorkoutSession(
        id = 5L,
        workoutId = 7L,
        workoutName = "Legs",
        date = "2026-06-23",
        startedAt = Instant.now().minus(30, ChronoUnit.MINUTES).toString(),
        completedAt = null,
        status = SessionStatus.ACTIVE,
        note = null,
        durationSeconds = null,
        exercises = emptyList<SessionExercise>(),
    )

    /** Records completeSession / updateSessionDuration calls; emits one ACTIVE session. */
    private class FakeRepo(session: WorkoutSession) : WorkoutSessionRepository(NoopSessionDao()) {
        val active = MutableStateFlow<WorkoutSession?>(session)
        var completedId: Long? = null
        var durationWritten: Pair<Long, Int?>? = null
        override fun observeActiveSession(): Flow<WorkoutSession?> = active
        override suspend fun completeSession(sessionId: Long, durationSeconds: Int?) {
            completedId = sessionId
        }
        override suspend fun updateSessionDuration(sessionId: Long, durationSeconds: Int?) {
            durationWritten = sessionId to durationSeconds
        }
    }

    /** Stores one set's columns; overrides the per-column repo writes AND the old whole-entity write. */
    private class StoringFakeRepo(session: WorkoutSession, seed: SessionSet) :
        WorkoutSessionRepository(NoopSessionDao()) {
        private val active = MutableStateFlow<WorkoutSession?>(session)
        private val setId = seed.id
        var storedReps = seed.reps
        var storedWeight = seed.weightKg
        var storedRir = seed.rir
        var storedCompleted = seed.completed
        override fun observeActiveSession(): Flow<WorkoutSession?> = active
        override suspend fun updateSetReps(setId: Long, reps: Int) { if (setId == this.setId) storedReps = reps }
        override suspend fun updateSetWeight(setId: Long, weightKg: Double?) { if (setId == this.setId) storedWeight = weightKg }
        override suspend fun updateSetRir(setId: Long, rir: Int?) { if (setId == this.setId) storedRir = rir }
        override suspend fun updateSetCompleted(setId: Long, completed: Boolean) { if (setId == this.setId) storedCompleted = completed }
        override suspend fun getSet(setId: Long): SessionSet? =
            if (setId == this.setId) SessionSet(setId, 1, storedReps, storedWeight, storedRir, storedCompleted) else null
        // Old whole-entity path — only reached by pre-fix code; kept so this test stays load-bearing.
        override suspend fun updateSet(set: com.zack.recomptracker.data.local.entity.SessionSetEntity) {
            if (set.id == setId) {
                storedReps = set.reps; storedWeight = set.weightKg; storedRir = set.rir; storedCompleted = set.completed
            }
        }
    }

    private fun vm(repo: WorkoutSessionRepository) = ActiveSessionViewModel(
        sessionRepository = repo,
        exerciseLibraryRepository = NoopLibraryRepo(),
    )

    @Test
    fun `typing reps then toggling complete keeps the reps and completes`() = runTest {
        val set0 = SessionSet(id = 100L, setNumber = 1, reps = 1, weightKg = null, rir = null, completed = false)
        val se = SessionExercise(id = 1L, exerciseId = 10L, exerciseName = "Squat", sortOrder = 0, note = null, sets = listOf(set0))
        val repo = StoringFakeRepo(activeSession().copy(exercises = listOf(se)), set0)
        val viewModel = vm(repo)
        val job = launch { viewModel.session.collect {} }
        advanceUntilIdle()

        // User quickly types "12" into reps, then taps the complete checkbox while the checkbox
        // handler still holds the stale set snapshot (reps = 1), because the flow hasn't re-emitted.
        viewModel.updateReps(se, set0, 12)
        viewModel.toggleComplete(se, set0)
        advanceUntilIdle()

        assertEquals(12, repo.storedReps)          // reps not reverted to the stale 1
        assertEquals(true, repo.storedCompleted)   // completed judged against the current 12, not the snapshot
        job.cancel()
    }

    @Test
    fun `finish records duration but does not complete the session`() = runTest {
        val repo = FakeRepo(activeSession())
        val viewModel = vm(repo)
        // Subscribe so the WhileSubscribed session StateFlow becomes active.
        val job = launch { viewModel.session.collect {} }
        advanceUntilIdle()

        val returnedId = viewModel.finish()

        assertEquals(5L, returnedId)
        assertNull("finish() must not complete the session", repo.completedId)
        assertEquals(5L, repo.durationWritten?.first)
        // ~30 min elapsed → a positive duration was recorded.
        assert((repo.durationWritten?.second ?: 0) > 0)
        job.cancel()
    }
}

/** Minimal no-op library repo: getById is never needed for finish(). */
private class NoopLibraryRepo : ExerciseLibraryRepository(NoopExerciseDao()) {
    override suspend fun getById(id: Long) = null
}

private class NoopExerciseDao : ExerciseDao {
    override fun observeAll(): Flow<List<ExerciseEntity>> = MutableStateFlow(emptyList())
    override suspend fun search(query: String): List<ExerciseEntity> = emptyList()
    override suspend fun getById(id: Long): ExerciseEntity? = null
    override suspend fun getAll(): List<ExerciseEntity> = emptyList()
    override suspend fun count(): Int = 0
    override suspend fun sourceVersion(source: String): String? = null
    override suspend fun insertAll(exercises: List<ExerciseEntity>) {}
    override suspend fun deleteBySource(source: String) {}
    override suspend fun insertReturningId(exercise: ExerciseEntity): Long = 0L
}

/** No-op session DAO; FakeRepo overrides the only methods exercised here. */
private class NoopSessionDao : WorkoutSessionDao() {
    override suspend fun insertSession(session: com.zack.recomptracker.data.local.entity.WorkoutSessionEntity): Long = 0L
    override suspend fun abandonActiveSessions() {}
    override suspend fun updateSetWeight(setId: Long, weightKg: Double?) {}
    override suspend fun updateSetReps(setId: Long, reps: Int) {}
    override suspend fun updateSetRir(setId: Long, rir: Int?) {}
    override suspend fun updateSetCompleted(setId: Long, completed: Boolean) {}
    override suspend fun getSetById(setId: Long): com.zack.recomptracker.data.local.entity.SessionSetEntity? = null
    override suspend fun updateSession(session: com.zack.recomptracker.data.local.entity.WorkoutSessionEntity) {}
    override suspend fun deleteSessionById(id: Long) {}
    override suspend fun insertSessionExercise(exercise: com.zack.recomptracker.data.local.entity.SessionExerciseEntity): Long = 0L
    override suspend fun insertSet(set: com.zack.recomptracker.data.local.entity.SessionSetEntity): Long = 0L
    override suspend fun getAllSessions(): List<com.zack.recomptracker.data.local.entity.WorkoutSessionEntity> = emptyList()
    override suspend fun getAllSessionExercises(): List<com.zack.recomptracker.data.local.entity.SessionExerciseEntity> = emptyList()
    override suspend fun getAllSessionSets(): List<com.zack.recomptracker.data.local.entity.SessionSetEntity> = emptyList()
    override suspend fun updateSet(set: com.zack.recomptracker.data.local.entity.SessionSetEntity) {}
    override suspend fun deleteSetById(id: Long) {}
    override suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int = 0
    override suspend fun getSessionWithDetails(id: Long): com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb? = null
    override fun observeActiveSession(): Flow<com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb?> = MutableStateFlow(null)
    override suspend fun getLastCompletedSession(workoutId: Long): com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb? = null
    override fun observeCompletedSessions(): Flow<List<com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb>> = MutableStateFlow(emptyList())
    override suspend fun getCompletedSessionsSince(startDate: String): List<com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb> = emptyList()
    override suspend fun getExerciseHistory(exerciseId: Long): List<com.zack.recomptracker.data.local.entity.ExerciseHistoryRow> = emptyList()
    override suspend fun nextExerciseSortOrder(sessionId: Long): Int = 0
    override suspend fun deleteSessionExerciseById(id: Long) {}
    override suspend fun updateSessionExerciseSortOrder(id: Long, sortOrder: Int) {}
    override suspend fun updateSessionExerciseNote(id: Long, note: String?) {}
    override suspend fun deleteSetsForSessionExercise(sessionExerciseId: Long) {}
    override suspend fun updateSessionExerciseExercise(id: Long, exerciseId: Long, exerciseName: String) {}
    override suspend fun updateSessionNote(id: Long, note: String?) {}
    override suspend fun updateDuration(id: Long, durationSeconds: Int?) {}
}
