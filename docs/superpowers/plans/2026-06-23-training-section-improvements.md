# Training Section Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five focused improvements to the live workout (Active Session) flow: prefill sets from last session, lock ticked sets, fix the Add Exercise button, pin the header with a clearer timer, and stop auto-saving the workout before the summary's Save/Discard.

**Architecture:** Logic changes (set prefill, deferred completion) live in `WorkoutSessionRepository` and two ViewModels and are covered by JUnit tests using the existing `FakeSessionDao` + `StandardTestDispatcher` patterns. Visual changes (locked cells, button, sticky header/timer) are Compose-only edits to `SetGrid.kt` and `ActiveSessionScreen.kt`, verified by a successful build plus manual in-app check (this module has no Compose UI test harness, matching existing project conventions).

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, Room, JUnit + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-23-training-section-improvements-design.md`

---

## File Map

| File | Responsibility | Change |
|---|---|---|
| `data/repository/WorkoutSessionRepository.kt` | Session creation | `startSession()` seeds sets from previous session actuals (blank otherwise) |
| `data/repository/WorkoutSessionRepositoryTest.kt` | Repo tests | Update one test, add one test |
| `ui/train/ActiveSessionViewModel.kt` | Active session logic | `finish()` records duration but keeps session ACTIVE |
| `ui/train/SessionSummaryViewModel.kt` | Summary logic | `save()` completes the session (ACTIVE→COMPLETED) |
| `ui/train/ActiveSessionViewModelTest.kt` (new) | VM test | finish() keeps ACTIVE + writes duration |
| `ui/train/SessionSummaryViewModelTest.kt` (new) | VM test | save() completes; discard() abandons |
| `ui/train/component/SetGrid.kt` | Set grid UI | Lock KG/REPS/RIR when a set is completed |
| `ui/train/ActiveSessionScreen.kt` | Active session UI | Fix Add Exercise button; pinned header + timer pill |

---

## Task 1: Prefill sets from the previous session

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt:26-63` (the `startSession` function)
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/WorkoutSessionRepositoryTest.kt`

- [ ] **Step 1: Update the existing prefill test to expect blank sets (no prior history)**

In `WorkoutSessionRepositoryTest.kt`, replace the test named
`startSession pre-fills session sets from planned sets as uncompleted` (lines ~142-156) with this updated test. With no previous completed session, sets are now seeded blank regardless of plan targets:

```kotlin
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
```

- [ ] **Step 2: Add a new test for prefilling from the last completed session**

Add this test below the one from Step 1 in `WorkoutSessionRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutSessionRepositoryTest"`
Expected: FAIL — the updated test expects `[0,0,0]`/`[null,null,null]` but current code seeds plan targets `[5,5,5]`/`[100.0,...]`; the new test expects `[6,5,4]` but current code ignores prior sessions.

- [ ] **Step 4: Rewrite `startSession` to prefill from the last completed session**

Replace the entire `startSession` function in `WorkoutSessionRepository.kt` (currently lines 24-63, including its KDoc) with:

```kotlin
    /** Creates an ACTIVE session snapshotting the template's name and exercises.
     *  Each set's KG/REPS are pre-filled from the matching exercise + set index of the
     *  last COMPLETED session for this routine (actual values). When there is no prior
     *  session — or no matching set — the set is left blank (reps=0, weightKg=null).
     *  Routine plan targets are not used for prefill. All sets start uncompleted. */
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
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutSessionRepositoryTest"`
Expected: PASS (all tests in the class, including the two changed/added).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt \
        app/src/test/java/com/zack/recomptracker/data/repository/WorkoutSessionRepositoryTest.kt
git commit -m "feat(train): prefill session sets from last completed session

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Defer completion until the summary's Save

Two coordinated changes: `finish()` stops completing the session; `SessionSummaryViewModel.save()` becomes the only place it completes.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt:344-352` (`finish`)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryViewModel.kt:164-168` (`save`)
- Test (new): `app/src/test/java/com/zack/recomptracker/ui/train/ActiveSessionViewModelTest.kt`
- Test (new): `app/src/test/java/com/zack/recomptracker/ui/train/SessionSummaryViewModelTest.kt`

- [ ] **Step 1: Write the failing test for `ActiveSessionViewModel.finish()`**

Create `app/src/test/java/com/zack/recomptracker/ui/train/ActiveSessionViewModelTest.kt`. It uses an open subclass of `WorkoutSessionRepository` to observe a single ACTIVE session and capture calls, and a no-op `ExerciseLibraryRepository`:

```kotlin
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
    override suspend fun updateSet(set: com.zack.recomptracker.data.local.entity.SessionSetEntity) {}
    override suspend fun deleteSetById(id: Long) {}
    override suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int = 0
    override suspend fun getSessionWithDetails(id: Long): com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb? = null
    override fun observeActiveSession(): Flow<com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb?> = MutableStateFlow(null)
    override suspend fun getLastCompletedSession(workoutId: Long): com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb? = null
    override fun observeCompletedSessions(): Flow<List<com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb>> = MutableStateFlow(emptyList())
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
```

> Before writing, open `domain/workout/WorkoutModels.kt` and confirm the `WorkoutSession` constructor parameter names/order used above (`id, workoutId, workoutName, date, startedAt, completedAt, status, note, durationSeconds, exercises`). Adjust the `activeSession()` literal if the model differs. Also confirm `WorkoutSessionDao` is an `abstract class` (the override list above assumes the same surface as `FakeSessionDao` in `WorkoutSessionRepositoryTest`); if a method is missing or extra, mirror that file's override set exactly.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.ActiveSessionViewModelTest"`
Expected: FAIL — current `finish()` calls `completeSession`, so `repo.completedId` is `5L` (assertNull fails).

- [ ] **Step 3: Update `ActiveSessionViewModel.finish()`**

Replace the `finish()` function in `ActiveSessionViewModel.kt` (currently lines 338-352, including its KDoc) with:

```kotlin
    // ── Finish ────────────────────────────────────────────────────────────────

    /**
     * Prepare the session for its summary WITHOUT completing it. Records the elapsed
     * duration but leaves the session ACTIVE, so backing out of the summary keeps the
     * workout in progress. The session is only finalised by Save (complete) or Discard
     * (abandon) on the summary screen. Returns the session id, or null if none loaded.
     */
    suspend fun finish(): Long? {
        val s = session.value ?: return null
        val duration = runCatching {
            val start = Instant.parse(s.startedAt)
            ChronoUnit.SECONDS.between(start, Instant.now()).toInt().coerceAtLeast(0)
        }.getOrElse { null }
        runCatching { sessionRepository.updateSessionDuration(s.id, duration) }
        return s.id
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.ActiveSessionViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing test for `SessionSummaryViewModel.save()`**

Create `app/src/test/java/com/zack/recomptracker/ui/train/SessionSummaryViewModelTest.kt`. It reuses a real `WorkoutSessionRepository` over a fake DAO so `completeSession`/`abandonSession` actually mutate status:

```kotlin
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
```

> Confirm the `SessionSummaryViewModel` constructor parameter order is `(sessionRepository, exerciseLibraryRepository, savedStateHandle)` — it is in the current file. Confirm entity constructor field names (`WorkoutSessionEntity`, `SessionExerciseEntity`, `SessionSetEntity`) match; mirror `WorkoutSessionRepositoryTest`'s usage if unsure.

- [ ] **Step 6: Run the save/discard tests to verify save fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.SessionSummaryViewModelTest"`
Expected: `discard abandons the session` PASSES; `save completes the session` FAILS — current `save()` only updates duration/note, so status stays `ACTIVE`.

- [ ] **Step 7: Update `SessionSummaryViewModel.save()`**

Replace the `save()` function in `SessionSummaryViewModel.kt` (currently lines 164-168) with:

```kotlin
    suspend fun save() {
        val s = _state.value
        // Finalise the session: ACTIVE → COMPLETED. This is the only place a workout is saved.
        sessionRepository.completeSession(sessionId, s.durationSeconds)
        sessionRepository.setSessionNote(sessionId, s.note.takeIf { it.isNotBlank() })
    }
```

- [ ] **Step 8: Run both VM test classes to verify all pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.train.*ViewModelTest"`
Expected: PASS (ActiveSessionViewModelTest + SessionSummaryViewModelTest).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/train/SessionSummaryViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/train/ActiveSessionViewModelTest.kt \
        app/src/test/java/com/zack/recomptracker/ui/train/SessionSummaryViewModelTest.kt
git commit -m "fix(train): keep workout active until summary Save/Discard

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Lock ticked sets (KG / REPS / RIR)

Compose-only change. No unit test (no Compose UI test harness in this module); verified by build + manual check.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt`

- [ ] **Step 1: Add a `locked` parameter to `SetInputCell`**

In `SetGrid.kt`, replace the `SetInputCell` composable signature and its `BasicTextField` so a locked cell is non-editable and non-focusable. Change the signature (currently lines 678-686) to add `locked`:

```kotlin
@Composable
private fun SetInputCell(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    completed: Boolean,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
```

Then in the same composable's `BasicTextField`, add `enabled = !locked` and guard focus tracking. Replace the `BasicTextField(...)` opening and its `.onFocusChanged` modifier so they read:

```kotlin
    BasicTextField(
        value = localValue,
        onValueChange = {
            localValue = it
            onChanged(it)
        },
        singleLine = true,
        enabled = !locked,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
```

and change the focus modifier line from `.onFocusChanged { focused = it.isFocused }` to:

```kotlin
            .onFocusChanged { focused = !locked && it.isFocused }
```

(Leave the rest of `SetInputCell` unchanged. When `enabled = false`, the field cannot be focused or edited, and `focused` stays false so it keeps the static completed styling.)

- [ ] **Step 2: Pass `locked = row.completed` from the session grid**

In `SessionSetGrid`, the KG and REPS cells (currently lines ~289-312) each call `SetInputCell(...)`. Add `locked = row.completed,` to both. The KG cell becomes:

```kotlin
                        // KG cell
                        SetInputCell(
                            value = row.weightKg?.let { formatWeight(it) } ?: "",
                            placeholder = "–",
                            keyboardType = KeyboardType.Decimal,
                            completed = row.completed,
                            locked = row.completed,
                            modifier = Modifier.weight(1f),
                            onChanged = { raw ->
                                onKgChanged(row, if (raw.isBlank()) null else raw.toDoubleOrNull())
                            },
                        )
```

and the REPS cell becomes:

```kotlin
                        // REPS cell
                        SetInputCell(
                            value = row.reps?.takeIf { it > 0 }?.toString() ?: "",
                            placeholder = "–",
                            keyboardType = KeyboardType.Number,
                            completed = row.completed,
                            locked = row.completed,
                            modifier = Modifier.weight(1f),
                            onChanged = { raw ->
                                onRepsChanged(row, if (raw.isBlank()) null else raw.toIntOrNull())
                            },
                        )
```

- [ ] **Step 3: Disable the RIR steppers when the row is completed**

In `SessionSetGrid`'s RIR stepper block, both the decrement and increment `Box`es use `.clickable { ... }` (currently lines ~369-403). Add `enabled = !row.completed` to each `clickable`. The decrement Box's clickable becomes:

```kotlin
                                    .clickable(enabled = !row.completed) {
                                        val cur = row.rir ?: 0
                                        onRirChanged(row, (cur - 1).coerceAtLeast(0))
                                    },
```

and the increment Box's clickable becomes:

```kotlin
                                    .clickable(enabled = !row.completed) {
                                        val cur = row.rir ?: 0
                                        onRirChanged(row, cur + 1)
                                    },
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/SetGrid.kt
git commit -m "feat(train): lock a set's inputs once it is ticked

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 6: Manual verification (hand off to user)**

In a running build, start a workout, enter reps/weight on a set, tick it. Confirm: the KG/REPS cells can no longer be focused/edited and the RIR +/− do nothing; unticking the set restores editing. Report build/test status and hand off for the user to confirm visually.

---

## Task 4: Fix the "Add Exercise" button

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt:400-426` (the `+ Exercise` button item)

- [ ] **Step 1: Remove the redundant spacer and the double "+" label**

In `ActiveSessionScreen.kt`, the add-exercise button currently renders an `Add` icon, a manual `Spacer(Modifier.width(6.dp))`, and a `Text("+ Exercise")`. Replace the button's content lambda (the `Icon`, `Spacer`, `Text` between `buttonHeight`/`modifier` and the closing brace) so it reads:

```kotlin
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Add Exercise",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
```

(The manual `Spacer` is removed — `LiquidGlassButton` already centers content with `Arrangement.spacedBy(8.dp)` — and the label no longer starts with "+".)

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "fix(train): clean up the Add Exercise button

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Pinned header + accent timer pill

Restructure `ActiveSessionScreen` so the header no longer scrolls and the timer reads as an accent pill. Two new private composables (`ActiveSessionHeader`, `ElapsedTimerPill`) keep the screen focused.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt`

- [ ] **Step 1: Add the `CornerChip` import**

In `ActiveSessionScreen.kt`, next to the existing `import com.zack.recomptracker.ui.theme.CornerSmall` line, add:

```kotlin
import com.zack.recomptracker.ui.theme.CornerChip
```

- [ ] **Step 2: Remove the now-unused `menuOpen` state**

In the `ActiveSessionScreen` composable body, delete this line (the overflow-menu state moves into the new header composable):

```kotlin
    var menuOpen by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Replace the `LazyColumn { … }` body with a `Column` (pinned header + scrolling list)**

Replace the entire `LazyColumn(...) { … }` block (currently lines 180-427: from `LazyColumn(` through its matching closing `}` right before the `// ── Exercise details popup` comment) with the following. This drops the old top-bar `item {}` and moves the header out of the scroll:

```kotlin
    Column(modifier = modifier.fillMaxSize()) {
        // ── Pinned header (always visible) ────────────────────────────────────
        ActiveSessionHeader(
            workoutName = s.workoutName,
            elapsedFlow = viewModel.elapsed,
            onMinimize = onMinimize,
            onFinish = {
                scope.launch {
                    viewModel.finish()?.let { sid -> onFinish(sid) }
                }
            },
            onDiscardRequest = { showDiscardDialog = true },
        )

        // ── Scrolling content ─────────────────────────────────────────────────
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ── Session notes ─────────────────────────────────────────────────
            item {
                FrostedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 14.dp, bottom = 14.dp),
                    contentPadding = 12.dp,
                ) {
                    Text(
                        text = "SESSION NOTES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textSecondary,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    SessionNoteField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        onFocusLost = { viewModel.setNote(noteText) },
                    )
                }
            }

            // ── Exercise cards ────────────────────────────────────────────────
            items(displayExercises, key = { it.id }) { se ->
                ReorderableItem(reorderState, key = se.id) { isDragging ->
                    val visual = exerciseVisuals[se.exerciseId]
                    ExerciseCard(
                        exerciseName = se.exerciseName,
                        imageUrl = visual?.imagePath,
                        fallbackMuscles = visual?.primaryMuscles,
                        subtitle = "",
                        onMoveUp = if (displayExercises.first().id != se.id) {
                            {
                                val idx = displayExercises.indexOfFirst { it.id == se.id }
                                if (idx > 0) {
                                    displayExercises.add(idx - 1, displayExercises.removeAt(idx))
                                    viewModel.reorderExercises(displayExercises.map { it.id })
                                }
                            }
                        } else null,
                        onMoveDown = if (displayExercises.last().id != se.id) {
                            {
                                val idx = displayExercises.indexOfFirst { it.id == se.id }
                                if (idx < displayExercises.size - 1) {
                                    displayExercises.add(idx + 1, displayExercises.removeAt(idx))
                                    viewModel.reorderExercises(displayExercises.map { it.id })
                                }
                            }
                        } else null,
                        onRemove = { viewModel.removeExercise(se) },
                        onReplace = { onReplaceExercise(se.id) },
                        onShowDetails = { viewModel.showExerciseDetails(se.exerciseId) },
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.longPressDraggableHandle(
                            onDragStarted = { dragHaptics.start() },
                            onDragStopped = {
                                dragHaptics.end()
                                viewModel.reorderExercises(displayExercises.map { it.id })
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp),
                    ) {
                        // Build session set rows for this exercise
                        val prevList = prevMap[se.exerciseId] ?: emptyList()
                        val sessionRows = se.sets.mapIndexed { idx, set ->
                            SessionSetRow(
                                id = set.id,
                                setNumber = set.setNumber,
                                prev = prevList.getOrNull(idx),
                                reps = set.reps.takeIf { it > 0 },
                                weightKg = set.weightKg,
                                rir = set.rir,
                                completed = set.completed,
                            )
                        }

                        SetGrid(
                            mode = SetGridMode.SESSION,
                            sets = emptyList(), // not used in SESSION mode
                            onAddSet = {},
                            onRemoveSet = {},
                            onSetChanged = { _, _, _ -> },
                            sessionSets = sessionRows,
                            onKgChanged = { row, kg ->
                                val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                                viewModel.updateKg(se, matchingSet, kg)
                            },
                            onRepsChanged = { row, reps ->
                                val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                                viewModel.updateReps(se, matchingSet, reps)
                            },
                            onRirChanged = { row, rir ->
                                val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                                viewModel.updateRir(se, matchingSet, rir)
                            },
                            onToggleComplete = { row ->
                                val matchingSet = se.sets.firstOrNull { it.id == row.id } ?: return@SetGrid
                                viewModel.toggleComplete(se, matchingSet)
                            },
                            onSessionAddSet = { viewModel.addSet(se) },
                            onSessionRemoveSet = { setId -> viewModel.removeSet(setId) },
                        )
                    }
                }
            }

            // ── Add Exercise button ───────────────────────────────────────────
            item {
                LiquidGlassButton(
                    onClick = onAddExercise,
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                    buttonHeight = 44.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 4.dp, bottom = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = accent.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Add Exercise",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent.onAccent,
                    )
                }
            }
        }
    }
```

> Note: this block already incorporates Task 4's button fix (no manual `Spacer`, "Add Exercise" label). If Task 4 is already committed, the button content here matches it; if you are doing Task 5 first, this still produces the correct button.

- [ ] **Step 4: Add the `ActiveSessionHeader` and `ElapsedTimerPill` composables**

In `ActiveSessionScreen.kt`, immediately **above** the existing `// ── Session note text field` comment / `SessionNoteField` composable, add these two private composables:

```kotlin
// ── Pinned header (title · timer pill · Finish · overflow) ────────────────────

@Composable
private fun ActiveSessionHeader(
    workoutName: String,
    elapsedFlow: StateFlow<Long>,
    onMinimize: () -> Unit,
    onFinish: () -> Unit,
    onDiscardRequest: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Minimize (collapse chevron)
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            // Routine title (prominent) + elapsed timer pill beneath it
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workoutName,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ElapsedTimerPill(elapsedFlow = elapsedFlow)
            }

            Spacer(Modifier.width(10.dp))

            // Finish — filled accent pill (icon + label)
            LiquidGlassButton(
                onClick = onFinish,
                tint = accent.accent,
                surfaceColor = Color.White.copy(alpha = 0.08f),
                buttonHeight = 40.dp,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Finish",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.onAccent,
                )
            }

            // Overflow menu — fast path to discard the active workout.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Discard workout", color = ErrorRed) },
                        onClick = {
                            menuOpen = false
                            onDiscardRequest()
                        },
                    )
                }
            }
        }

        // Subtle divider so the header reads as pinned above the scrolling list.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(appColors.frostedBorder),
        )
    }
}

// ── Elapsed timer pill (accent-tinted, isolated recomposition) ────────────────

@Composable
private fun ElapsedTimerPill(elapsedFlow: StateFlow<Long>) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerChip))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerChip))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        ElapsedTimerText(
            elapsedFlow = elapsedFlow,
            color = accent.inkLight,
        )
    }
}
```

- [ ] **Step 5: Make the timer text larger (more obvious)**

In the existing `ElapsedTimerText` composable (near the bottom of the file), bump the size/weight so the pinned timer reads clearly. Change its `Text(...)` from `fontSize = 13.sp, fontWeight = FontWeight.Medium` to:

```kotlin
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
```

- [ ] **Step 6: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (If the compiler flags an unused import such as `Arrangement`, leave imports that are still referenced; only remove one if the compiler explicitly reports it unused and the build is configured to fail on it — this project does not fail on unused imports, so prefer leaving them.)

- [ ] **Step 7: Assemble the debug APK to confirm a full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ActiveSessionScreen.kt
git commit -m "feat(train): pin the active-session header with an accent timer pill

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 9: Manual verification (hand off to user)**

In a running build, start a workout and scroll the exercise list. Confirm: the header (title · timer pill · Finish · overflow) stays pinned at the top while the list scrolls; the timer is an accent pill and easy to read. Report build/test status and hand off for the user to confirm visually.

---

## Final verification

- [ ] **Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **End-to-end manual check of the deferred-save fix (item 5)**

In a running build: start a workout → tap **Finish** → on the summary, press the phone's back/home button. Confirm the workout is **still in progress** on Train Home (not saved). Re-open it, tap Finish again, and this time tap **Save Workout** → confirm it now appears in history. Repeat once more choosing **Discard** → confirm it is gone.

---

## Self-Review notes

- **Spec coverage:** Item 1 → Task 1. Item 2 → Task 3. Item 3 → Task 4 (and re-applied in Task 5 Step 3). Item 4 → Task 5. Item 5 → Task 2. All five covered.
- **Test-vs-build:** Logic (items 1, 5) is TDD'd. Visual items (2, 3, 4) build + manual, matching this module's lack of a Compose UI test harness.
- **Type consistency:** `finish()` returns `Long?`; `updateSessionDuration(sessionId, durationSeconds)` and `completeSession(sessionId, durationSeconds)` signatures match `WorkoutSessionRepository`. `SetInputCell(locked: Boolean = false)` is the single shared signature used by PLAN (defaults false) and SESSION (`locked = row.completed`).
- **Cross-task file ordering:** Tasks 4 and 5 both edit `ActiveSessionScreen.kt`. Task 5 Step 3's replacement block already contains the Task 4 button fix, so either order yields the same final file.
