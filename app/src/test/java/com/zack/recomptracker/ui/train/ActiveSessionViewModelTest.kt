package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.SessionExercise
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

    private fun vm(repo: WorkoutSessionRepository) = ActiveSessionViewModel(
        sessionRepository = repo,
        exerciseLibraryRepository = NoopLibraryRepo(),
    )

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
