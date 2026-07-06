package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.ExerciseHistoryPoint
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the mid-workout PR-banner detection in [ActiveSessionViewModel.detectPr]:
 * a completing set that beats prior best e1RM fires once; a lesser set fires nothing; and the same
 * exercise never re-fires within a session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelPrTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Fake repo: serves a fixed exercise history per id and swallows set writes. */
    private class FakeRepo(
        private val historyByExercise: Map<Long, List<ExerciseHistoryPoint>>,
    ) : WorkoutSessionRepository(PrNoopSessionDao()) {
        override fun observeActiveSession(): Flow<com.zack.recomptracker.domain.workout.WorkoutSession?> =
            MutableStateFlow(null)
        override suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryPoint> =
            historyByExercise[exerciseId].orEmpty()
        override suspend fun updateSet(set: SessionSetEntity) { /* no-op */ }
    }

    private fun vm(repo: WorkoutSessionRepository) = ActiveSessionViewModel(
        sessionRepository = repo,
        exerciseLibraryRepository = PrNoopLibraryRepo(),
    )

    private fun sessionExercise(exerciseId: Long, name: String) = SessionExercise(
        id = exerciseId * 10,
        exerciseId = exerciseId,
        exerciseName = name,
        sortOrder = 0,
        note = null,
        sets = emptyList(),
    )

    private fun set(reps: Int, weightKg: Double?) = SessionSet(
        id = 1L, setNumber = 1, reps = reps, weightKg = weightKg, rir = null, completed = true,
    )

    // Prior best e1RM for Bench = 100 × (1 + 5/30) ≈ 116.67 kg.
    private val benchHistory = mapOf(
        1L to listOf(ExerciseHistoryPoint(date = "2026-06-01", reps = 5, weightKg = 100.0, rir = null)),
    )

    @Test
    fun `set beating prior best e1RM fires the banner once`() = runTest {
        val repo = FakeRepo(benchHistory)
        val viewModel = vm(repo)
        // 110×5 → e1RM ≈ 128.3 > 116.67 prior best.
        viewModel.detectPr(sessionExercise(1L, "Bench Press"), set(reps = 5, weightKg = 110.0))

        val event = viewModel.prEvent.value
        assertEquals(1L, event?.exerciseId)
        assertEquals("Bench Press", event?.exerciseName)
        assertEquals(110.0 * (1 + 5.0 / 30.0), event?.e1rmKg!!, 0.001)
    }

    @Test
    fun `set not beating prior best fires nothing`() = runTest {
        val repo = FakeRepo(benchHistory)
        val viewModel = vm(repo)
        // 90×5 → e1RM ≈ 105 < 116.67 prior best.
        viewModel.detectPr(sessionExercise(1L, "Bench Press"), set(reps = 5, weightKg = 90.0))

        assertNull(viewModel.prEvent.value)
    }

    @Test
    fun `same exercise does not re-fire after its first PR`() = runTest {
        val repo = FakeRepo(benchHistory)
        val viewModel = vm(repo)
        val ex = sessionExercise(1L, "Bench Press")

        viewModel.detectPr(ex, set(reps = 5, weightKg = 110.0)) // fires
        viewModel.dismissPrEvent()
        viewModel.detectPr(ex, set(reps = 5, weightKg = 130.0)) // even bigger PR, but already fired

        assertNull("same exercise must fire at most once per session", viewModel.prEvent.value)
    }

    @Test
    fun `a PR on a different exercise fires again`() = runTest {
        val repo = FakeRepo(benchHistory) // squat (id 2) has no history → prior best 0
        val viewModel = vm(repo)

        viewModel.detectPr(sessionExercise(1L, "Bench Press"), set(reps = 5, weightKg = 110.0))
        viewModel.dismissPrEvent()
        viewModel.detectPr(sessionExercise(2L, "Squat"), set(reps = 5, weightKg = 60.0))

        assertEquals("Squat", viewModel.prEvent.value?.exerciseName)
    }

    @Test
    fun `bodyweight set with no weight never fires`() = runTest {
        val repo = FakeRepo(emptyMap())
        val viewModel = vm(repo)
        viewModel.detectPr(sessionExercise(3L, "Pull Up"), set(reps = 10, weightKg = null))

        assertNull(viewModel.prEvent.value)
    }
}

/** Minimal no-op library repo: getById is never needed for PR detection. */
private class PrNoopLibraryRepo : ExerciseLibraryRepository(PrNoopExerciseDao()) {
    override suspend fun getById(id: Long) = null
}

private class PrNoopExerciseDao : ExerciseDao {
    override fun observeAll(): Flow<List<ExerciseEntity>> = MutableStateFlow(emptyList())
    override suspend fun search(query: String): List<ExerciseEntity> = emptyList()
    override suspend fun getById(id: Long): ExerciseEntity? = null
    override suspend fun count(): Int = 0
    override suspend fun sourceVersion(source: String): String? = null
    override suspend fun insertAll(exercises: List<ExerciseEntity>) {}
    override suspend fun deleteBySource(source: String) {}
    override suspend fun insertReturningId(exercise: ExerciseEntity): Long = 0L
}

/** No-op session DAO; FakeRepo overrides the only methods exercised here. */
private class PrNoopSessionDao : WorkoutSessionDao() {
    override suspend fun insertSession(session: WorkoutSessionEntity): Long = 0L
    override suspend fun updateSession(session: WorkoutSessionEntity) {}
    override suspend fun deleteSessionById(id: Long) {}
    override suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long = 0L
    override suspend fun insertSet(set: SessionSetEntity): Long = 0L
    override suspend fun updateSet(set: SessionSetEntity) {}
    override suspend fun deleteSetById(id: Long) {}
    override suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int = 0
    override suspend fun getSessionWithDetails(id: Long): WorkoutSessionWithDetailsDb? = null
    override fun observeActiveSession(): Flow<WorkoutSessionWithDetailsDb?> = MutableStateFlow(null)
    override suspend fun getLastCompletedSession(workoutId: Long): WorkoutSessionWithDetailsDb? = null
    override fun observeCompletedSessions(): Flow<List<WorkoutSessionWithDetailsDb>> = MutableStateFlow(emptyList())
    override suspend fun getCompletedSessionsSince(startDate: String): List<WorkoutSessionWithDetailsDb> = emptyList()
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
