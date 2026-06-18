# Workout Tracking UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the page-by-page workout UI (Train tab → routines, builder, exercise picker, live session, summary, history) in the app's dark glass theme, on top of the existing workout data layer plus three backend additions.

**Architecture:** Compose screens + StateFlow ViewModels wired through `AppContainer`/`AppViewModelFactory`, reusing the existing glass component library. The visual contract is the committed mockup `docs/superpowers/specs/2026-06-17-workout-ui-mockups.html`; the design rules are in `docs/superpowers/specs/2026-06-17-workout-ui-design.md`. Backend additions (per-set planned targets, mid-session edits, editable duration) land first via TDD, then screens are built and **verified in the running app one at a time**.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room, Coroutines/Flow, Coil 2.7 (`AsyncImage`), manual DI (`AppContainer`), Kyant liquid-glass nav, JUnit4.

**Non-negotiable conventions (from the design spec):**
- **Cards:** use `FrostedCard` (solid, readable) for ALL primary content; `FrostedCard(surfaceTint = LocalAppAccent.current.tintedSurface)` for completed-set / selected-row tint. NEVER `NeutralCard` for primary content. `TintedCard` = AI only.
- **Accent:** every accent pixel reads `LocalAppAccent.current` (accent, `inkLighter`, `onAccent`, `tintedSurface`). No hardcoded violet.
- **Pills/buttons:** reuse `LiquidGlassButton`/existing pill buttons for CTAs (Start, Save, Add N, finish).
- **Images:** `AsyncImage` (Coil) on `Exercise.images.firstOrNull()` (remote raw URLs), placeholder = a barbell icon tile.
- **Corners:** `CornerCard` cards, `CornerSmall` input cells, `CornerChip` chips, `CornerPill` pills.
- **Destructive:** `ErrorRed` + confirm.
- **Per screen:** build it, then run the app and screenshot-verify against the mockup BEFORE the next screen (see Verification at each screen task).

---

## File Structure

**Backend (existing dirs):**
- `data/local/entity/PlannedSetEntity.kt` (new), `WorkoutExerciseEntity.kt` (modify), `WorkoutWithExercisesDb.kt` (modify), `WorkoutSessionEntity.kt` (modify: add `durationSeconds`).
- `data/local/dao/WorkoutDao.kt` (modify), `WorkoutSessionDao.kt` (modify).
- `data/local/RecompDatabase.kt` (modify: v10→v11, register `PlannedSetEntity`, `MIGRATION_10_11`).
- `data/repository/WorkoutRepository.kt`, `WorkoutSessionRepository.kt`, `WorkoutMappers.kt` (modify).
- `domain/workout/WorkoutModels.kt` (modify: `WorkoutTemplateExercise` carries `plannedSets: List<PlannedSet>`).

**UI (new dir `ui/train/`):**
- `TrainViewModel.kt`, `TrainHomeScreen.kt` (screen 1)
- `RoutineBuilderViewModel.kt`, `RoutineBuilderScreen.kt` (screen 2)
- `ExercisePickerViewModel.kt`, `ExercisePickerScreen.kt` + `ExerciseDetailSheet.kt` (screen 3 / 3b)
- `ActiveSessionViewModel.kt`, `ActiveSessionScreen.kt` (screen 4)
- `SessionSummaryViewModel.kt`, `SessionSummaryScreen.kt` (screen 5)
- `WorkoutHistoryViewModel.kt`, `WorkoutHistoryScreen.kt` + `SessionDetailScreen.kt` (screen 6 / 6b)
- `ui/train/component/ExerciseCard.kt`, `SetGrid.kt` — shared cards reused by builder/session/detail.

**Wiring:**
- `core/AppContainer.kt` (expose new repos already present; add ViewModel factory branches).
- `ui/navigation/AppNavGraph.kt` (+ `Routes.Train` and sub-routes), `ui/RecompApp.kt` (tab swap), `ui/navigation/TopLevelDestination.kt` if needed.

**Tests:** `app/src/test/java/com/zack/recomptracker/...` (domain/repo unit tests with fake DAOs); `app/src/androidTest/.../data/WorkoutPlannedSetsDatabaseTest.kt` (migration/relations).

---

## Task 1: Backend — per-set planned targets

Replace `WorkoutExerciseEntity.plannedSets:Int` + `targetReps:Int?` with a `planned_sets` child table holding optional per-set `targetReps` + `targetWeightKg`. Starting a session pre-fills `session_sets` from these.

**Files:** Create `PlannedSetEntity.kt`; modify `WorkoutExerciseEntity.kt`, `WorkoutWithExercisesDb.kt`, `WorkoutDao.kt`, `RecompDatabase.kt`, `WorkoutModels.kt`, `WorkoutMappers.kt`, `WorkoutRepository.kt`, `WorkoutSessionRepository.kt`. Test: `WorkoutRepositoryTest.kt`, `WorkoutSessionRepositoryTest.kt`, `WorkoutPlannedSetsDatabaseTest.kt`.

- [ ] **Step 1: Write the failing test** (extend `WorkoutRepositoryTest` — saving a workout persists per-set planned targets, optional values allowed)

```kotlin
@Test
fun `saveWorkout persists per-set planned targets including nulls`() = runTest {
    val (repo, dao) = repo()
    val id = repo.saveWorkout(
        name = "Push", note = null,
        lines = listOf(
            NewWorkoutLine(exerciseId = 1, plannedSets = listOf(
                PlannedSetDraft(targetReps = 15, targetWeightKg = 10.0),
                PlannedSetDraft(targetReps = null, targetWeightKg = null),
            )),
        ),
    )
    val loaded = repo.getById(id)!!
    val sets = loaded.exercises.single().plannedSets
    assertEquals(2, sets.size)
    assertEquals(15, sets[0].targetReps)
    assertEquals(10.0, sets[0].targetWeightKg!!, 0.0001)
    assertNull(sets[1].targetReps)
    assertNull(sets[1].targetWeightKg)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutRepositoryTest"`
Expected: FAIL — `PlannedSetDraft`, `NewWorkoutLine(plannedSets=List)`, `plannedSets` unresolved.

- [ ] **Step 3: Create `PlannedSetEntity.kt`**

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "planned_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class PlannedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val targetReps: Int?,
    val targetWeightKg: Double?,
)
```

- [ ] **Step 4: Modify `WorkoutExerciseEntity.kt`** — remove `plannedSets` and `targetReps` columns (count + targets now live in `planned_sets`):

```kotlin
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val note: String?,
)
```

- [ ] **Step 5: Modify `WorkoutWithExercisesDb.kt`** — nest planned sets under each exercise line:

```kotlin
data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val plannedSets: List<PlannedSetEntity>,
)
```

- [ ] **Step 6: Modify `WorkoutModels.kt`** — domain models for planned sets:

```kotlin
data class PlannedSet(val id: Long, val setNumber: Int, val targetReps: Int?, val targetWeightKg: Double?)

data class WorkoutTemplateExercise(
    val id: Long,
    val exercise: Exercise,
    val plannedSets: List<PlannedSet>,
    val sortOrder: Int,
    val note: String?,
)
```

- [ ] **Step 7: Modify `WorkoutMappers.kt`** — map planned sets (sorted by setNumber):

```kotlin
internal fun WorkoutExerciseWithExercise.toDomain(): WorkoutTemplateExercise = WorkoutTemplateExercise(
    id = workoutExercise.id,
    exercise = exercise.toDomain(),
    plannedSets = plannedSets.sortedBy { it.setNumber }
        .map { PlannedSet(it.id, it.setNumber, it.targetReps, it.targetWeightKg) },
    sortOrder = workoutExercise.sortOrder,
    note = workoutExercise.note,
)
```

- [ ] **Step 8: Modify `WorkoutDao.kt`** — insert planned sets when replacing exercises:

```kotlin
@Insert abstract suspend fun insertPlannedSet(set: PlannedSetEntity): Long
@Query("DELETE FROM planned_sets WHERE workoutExerciseId = :workoutExerciseId")
abstract suspend fun deletePlannedSetsByExerciseId(workoutExerciseId: Long)

@Transaction
open suspend fun replaceExercises(workoutId: Long, lines: List<Pair<WorkoutExerciseEntity, List<PlannedSetEntity>>>) {
    deleteExercisesByWorkoutId(workoutId)
    lines.forEachIndexed { index, (line, planned) ->
        val exId = insertWorkoutExercise(line.copy(workoutId = workoutId, sortOrder = index, id = 0))
        planned.forEachIndexed { n, ps ->
            insertPlannedSet(ps.copy(workoutExerciseId = exId, setNumber = n + 1, id = 0))
        }
    }
}
```

(`deleteExercisesByWorkoutId` cascades `planned_sets` via the FK, but the explicit delete keeps it simple.)

- [ ] **Step 9: Modify `WorkoutRepository.kt`** — `NewWorkoutLine.plannedSets: List<PlannedSetDraft>`, validation = ≥1 planned set per exercise:

```kotlin
data class PlannedSetDraft(val targetReps: Int? = null, val targetWeightKg: Double? = null)
data class NewWorkoutLine(val exerciseId: Long, val plannedSets: List<PlannedSetDraft>, val note: String? = null)
```

In `saveWorkout`/`updateWorkout`, build `List<Pair<WorkoutExerciseEntity, List<PlannedSetEntity>>>` and call the new `replaceExercises`; validate via `WorkoutValidation.validateTemplate(name, lines.size, lines.map { it.plannedSets.size })` (the existing rule "≥1 planned set" now counts list size).

- [ ] **Step 10: Modify `WorkoutSessionRepository.startSession`** — pre-fill `session_sets` from planned sets:

```kotlin
template.exercises.sortedBy { it.sortOrder }.forEachIndexed { index, line ->
    val seId = sessionDao.insertSessionExercise(SessionExerciseEntity(
        sessionId = sessionId, exerciseId = line.exercise.id,
        exerciseName = line.exercise.name, sortOrder = index, note = line.note,
    ))
    line.plannedSets.forEach { ps ->
        sessionDao.insertSet(SessionSetEntity(
            sessionExerciseId = seId, setNumber = ps.setNumber,
            reps = ps.targetReps ?: 0, weightKg = ps.targetWeightKg, rir = null, completed = false,
        ))
    }
}
```

(Sets start `completed = false`, pre-filled with planned targets; the user confirms each during the session.)

- [ ] **Step 11: Modify `RecompDatabase.kt`** — register `PlannedSetEntity`, bump to v11, add migration:

```kotlin
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS planned_sets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "workoutExerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, " +
                "targetReps INTEGER, targetWeightKg REAL, " +
                "FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id) ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_sets_workoutExerciseId ON planned_sets (workoutExerciseId)")
        // Drop the now-unused columns by rebuilding workout_exercises without them.
        db.execSQL(
            "CREATE TABLE workout_exercises_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, " +
                "exerciseId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, note TEXT, " +
                "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
        )
        db.execSQL("INSERT INTO workout_exercises_new (id, workoutId, exerciseId, sortOrder, note) " +
            "SELECT id, workoutId, exerciseId, sortOrder, note FROM workout_exercises")
        db.execSQL("DROP TABLE workout_exercises")
        db.execSQL("ALTER TABLE workout_exercises_new RENAME TO workout_exercises")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_exerciseId ON workout_exercises (exerciseId)")
    }
}
```

Add `PlannedSetEntity::class` to `@Database(entities=[…])`, `version = 11`, append `MIGRATION_10_11` to `addMigrations(...)`.

- [ ] **Step 12: Update existing repo tests** for the new `NewWorkoutLine` shape (fakes return `plannedSets` rows). Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.*"` → PASS.

- [ ] **Step 13a: Update the EXISTING instrumented test** `app/src/androidTest/java/com/zack/recomptracker/data/WorkoutDatabaseTest.kt` — it constructs `WorkoutExerciseEntity(plannedSets=…, targetReps=…)` and calls the old `replaceExercises(workoutId, List<WorkoutExerciseEntity>)`, both of which no longer compile. Update those call sites to the new entity (no `plannedSets`/`targetReps`) and the new `replaceExercises(workoutId, List<Pair<WorkoutExerciseEntity, List<PlannedSetEntity>>>)` signature (pass an empty planned-set list where the test doesn't care).

- [ ] **Step 13b: Write new instrumented test** `WorkoutPlannedSetsDatabaseTest.kt` (in-memory Room): save a workout with 2 planned sets (one with null targets), reload via `getWithExercises`, assert `planned_sets` round-trip + cascade-delete when the parent exercise is removed. Compile-gate both: `./gradlew :app:assembleDebugAndroidTest`.

- [ ] **Step 14: Verify + commit**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin` → BUILD SUCCESSFUL.
```bash
git add -A && git commit -m "feat(workout): per-set planned targets (planned_sets table) + session pre-fill"
```

---

## Task 2: Backend — mid-session edits

Add session-editing methods so the Active Session can add/remove/reorder/replace exercises and edit notes.

**Files:** `WorkoutSessionDao.kt`, `WorkoutSessionRepository.kt`; Test: `WorkoutSessionRepositoryTest.kt`.

- [ ] **Step 1: Write failing tests** (fake-DAO): `addExerciseToSession` appends a session_exercise at the next sortOrder; `removeSessionExercise` deletes it (cascades sets); `reorderSessionExercises` rewrites sortOrder; `updateSessionExerciseNote` and `updateSessionNote` persist text.

```kotlin
@Test fun `addExerciseToSession appends at next sortOrder`() = runTest {
    val dao = FakeSessionDao(); val repo = repo(dao)
    val sid = repo.startSession(template())
    repo.addExerciseToSession(sid, exerciseId = 99, exerciseName = "Dip")
    val added = dao.exercises.values.single { it.exerciseName == "Dip" }
    assertEquals(sid, added.sessionId)
    assertEquals(1, added.sortOrder) // template had 1 exercise at sortOrder 0
}
```

- [ ] **Step 2: Run → FAIL** (`addExerciseToSession` unresolved).
- [ ] **Step 3: Add DAO queries** to `WorkoutSessionDao.kt`:

```kotlin
@Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM session_exercises WHERE sessionId = :sessionId")
abstract suspend fun nextExerciseSortOrder(sessionId: Long): Int
@Query("DELETE FROM session_exercises WHERE id = :id")
abstract suspend fun deleteSessionExerciseById(id: Long)
@Query("UPDATE session_exercises SET sortOrder = :sortOrder WHERE id = :id")
abstract suspend fun updateSessionExerciseSortOrder(id: Long, sortOrder: Int)
@Query("UPDATE session_exercises SET note = :note WHERE id = :id")
abstract suspend fun updateSessionExerciseNote(id: Long, note: String?)
@Query("UPDATE workout_sessions SET note = :note WHERE id = :id")
abstract suspend fun updateSessionNote(id: Long, note: String?)
```

- [ ] **Step 4: Add repository methods** to `WorkoutSessionRepository.kt`:

```kotlin
open suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long, exerciseName: String): Long =
    sessionDao.insertSessionExercise(SessionExerciseEntity(
        sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseName,
        sortOrder = sessionDao.nextExerciseSortOrder(sessionId), note = null,
    ))
open suspend fun removeSessionExercise(sessionExerciseId: Long) = sessionDao.deleteSessionExerciseById(sessionExerciseId)
open suspend fun reorderSessionExercises(orderedSessionExerciseIds: List<Long>) {
    orderedSessionExerciseIds.forEachIndexed { i, id -> sessionDao.updateSessionExerciseSortOrder(id, i) }
}
open suspend fun setSessionExerciseNote(sessionExerciseId: Long, note: String?) = sessionDao.updateSessionExerciseNote(sessionExerciseId, note)
open suspend fun setSessionNote(sessionId: Long, note: String?) = sessionDao.updateSessionNote(sessionId, note)
```

- [ ] **Step 5: Run → PASS** (`:app:testDebugUnitTest --tests "*WorkoutSessionRepositoryTest"`).
- [ ] **Step 6: Commit** — `feat(workout): mid-session exercise + note edits`.

---

## Task 3: Backend — editable duration

Let the summary override the computed workout duration.

**Files:** `WorkoutSessionEntity.kt`, `WorkoutSessionDao.kt`, `WorkoutSessionRepository.kt`, `RecompDatabase.kt` (v11→v12), `WorkoutMappers.kt`, `WorkoutModels.kt`; Test: `WorkoutSessionRepositoryTest.kt`.

- [ ] **Step 1: Write failing test** — `completeSession(durationSeconds = 3120)` stores the override; `WorkoutSession.durationSeconds == 3120`.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3:** Add `val durationSeconds: Int?` to `WorkoutSessionEntity` (nullable), to `WorkoutSession` domain model, and map it in `WorkoutMappers`.
- [ ] **Step 4:** Change `completeSession(sessionId: Long, durationSeconds: Int? = null)` to write `status=COMPLETED`, `completedAt=now()`, and `durationSeconds`.
- [ ] **Step 5:** `RecompDatabase` v11→v12 + `MIGRATION_11_12`:
```kotlin
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationSeconds INTEGER")
    }
}
```
Bump `version = 12`, append migration.
- [ ] **Step 6: Run → PASS**, then `./gradlew :app:compileDebugKotlin`.
- [ ] **Step 7: Commit** — `feat(workout): editable session duration override`.

---

## Task 4: Navigation — Train tab replaces More; More → Home gear

**Files:** `ui/navigation/AppNavGraph.kt`, `ui/RecompApp.kt`, `core/AppContainer.kt`, `ui/train/TrainHomeScreen.kt` (stub), `ui/dashboard/HomeDashboardScreen.kt` (gear icon).

- [ ] **Step 1:** Add `const val Train = "train"` to `Routes`. Add a `composable(Routes.Train){ TrainHomeScreen(...) }` to `AppNavGraph` (stub screen for now: a `FrostedCard` reading "Train" — real screen in Task 5).
- [ ] **Step 2:** In `RecompApp.kt`: replace `TopLevelDestination.More.route` with `Routes.Train` in `tabRoutes`, `routeToTabIndex`, and `topLevelRoutes`; change the 5th `LiquidBottomTab` to Train (barbell icon — use `Icons.Filled` equivalent or the existing icon set; label "Train"); it navigates to `Routes.Train`.
- [ ] **Step 3:** In `HomeDashboardScreen`, add a gear `IconButton` (`Icons.Filled.Settings`-style from the existing icon usage) next to the page title, calling the existing `onOpenSettings` (already navigates to More). Confirm More is still reachable.
- [ ] **Step 4: Verify in app** — `./gradlew :app:installDebug`, launch, screenshot: bottom bar shows Home·Body·Food·Coach·Train; tapping Train shows the stub; the Home gear opens More. (Use the android-emulator skill.)
- [ ] **Step 5: Commit** — `feat(workout): add Train tab (replaces More), More moves to Home gear`.

---

## Screen tasks (5–10): shared rules

Each screen task = **ViewModel (state + events, full code) → screen composable (structure + exact component reuse, layout per the matching mockup section) → wire into AppNavGraph + AppViewModelFactory + AppContainer → build → run app → screenshot-verify against the mockup → commit.** The mockup file is the pixel contract; match spacing/sizing/colors to it. Use `FrostedCard` for every content card and `LocalAppAccent` for every accent pixel.

Verification step (identical each screen): `./gradlew :app:installDebug`, navigate to the screen, capture a screenshot via the android-emulator skill, and compare side-by-side to the mockup section. Fix visual drift before committing. Do NOT proceed to the next screen until this screen matches.

---

## Task 5: Screen 1 — Train Home

**Mockup:** section "1 · TRAIN HOME". **Files:** `ui/train/TrainViewModel.kt`, `ui/train/TrainHomeScreen.kt`.

- [ ] **Step 1: ViewModel** — expose routines + active session + history:
```kotlin
data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
)
enum class TrainTab { ROUTINES, HISTORY }
class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)
    val state: StateFlow<TrainUiState> = combine(
        tab, workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(), sessionRepository.observeCompletedSessions(),
    ) { t, routines, active, history -> TrainUiState(t, routines, active, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())
    fun selectTab(t: TrainTab) { tab.value = t }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
}
```
- [ ] **Step 2: Screen** — header ("Train" + `+`), Routines/History segmented pill, resume banner (`FrostedCard surfaceTint = accent.tintedSurface`, only when `activeSession != null`), routine cards (`FrostedCard`: name, "N exercises · M sets" where M = `exercises.sumOf { it.plannedSets.size }`, first 3 exercises with `AsyncImage` thumbnail + set count, "and X more", `LiquidGlassButton` Start, ⋮ menu). History tab → list of `WorkoutHistory` cards (reuse Task 10 card). Empty state per spec. Bottom padding = `FloatingNavHeight`.
- [ ] **Step 3: Wire** — `AppNavGraph` `Routes.Train` → `TrainHomeScreen(viewModel(factory), onCreateRoutine, onEditRoutine, onStart→ActiveSession, onResume, onOpenHistorySession)`. Add `TrainViewModel` branch to `AppViewModelFactory` (inject `workoutRepository`, `workoutSessionRepository` from `AppContainer`).
- [ ] **Step 4: Verify in app** (screenshot vs mockup 1) → **Commit** `feat(workout): Train Home screen`.

---

## Task 6: Screen 2 — Routine Builder

**Mockup:** section "2 · ROUTINE BUILDER". **Files:** `ui/train/RoutineBuilderViewModel.kt`, `ui/train/RoutineBuilderScreen.kt`, `ui/train/component/ExerciseCard.kt` (plan-mode variant), `ui/train/component/SetGrid.kt`.

- [ ] **Step 1: ViewModel** — editable draft (name, note, exercises each with a mutable list of `PlannedSetDraft`); load existing via `workoutRepository.getById` when editing; `addExercises(List<Exercise>)`, `removeExercise`, `reorder`, `addSet`, `removeSet`, `setTarget(exIdx,setIdx,reps,weight)`, `save()` → `saveWorkout`/`updateWorkout`. `canSave = name.isNotBlank() && exercises.isNotEmpty()`. Receives picked exercises via `SavedStateHandle` (like `RecipeBuilder` receives picked ingredients).
- [ ] **Step 2: Screen** — header (✕, title, `LiquidGlassButton` Save disabled until `canSave`), name field, note field, per-exercise `FrostedCard` with drag handle + thumbnail + name + ⋮, and a `SetGrid` in plan mode (`SET · KG · REPS` rows, **empty cells allowed**, Add set, swipe-to-remove), `LiquidGlassButton` Add exercise → picker. Match mockup 2 (2nd exercise shows empty `–` targets).
- [ ] **Step 3: Wire** — `Routes.RoutineBuilder?workoutId={workoutId}` (nullable, like `RecipeBuilder`); navigates to picker and back with selections via `savedStateHandle`. Add factory branch.
- [ ] **Step 4: Verify in app** (vs mockup 2) → **Commit** `feat(workout): Routine Builder screen`.

---

## Task 7: Screen 3 / 3b — Exercise Picker + Detail

**Mockup:** section "3 · EXERCISE PICKER". **Files:** `ui/train/ExercisePickerViewModel.kt`, `ui/train/ExercisePickerScreen.kt`, `ui/train/ExerciseDetailSheet.kt`.

- [ ] **Step 1: ViewModel** — `query`, `muscleFilter`, `selected: Set<Long>` over `exerciseLibraryRepository.observeAll()`/`search`; derived filtered list; `toggle(id)`, `setQuery`, `setFilter`, `confirm(): List<Exercise>`. Detail loads `getById`.
- [ ] **Step 2: Screen** — ✕ + title, search field, horizontal filter chips (muscle/equipment, "All" active), list of `FrostedCard` rows (`AsyncImage` thumbnail, name, "muscle · equipment", select circle; selected → `surfaceTint = accent` + accent check), sticky `LiquidGlassButton` "Add N" (live count), a "Create custom exercise" row. Thumbnail tap → `ExerciseDetailSheet` (`FrostedCard` with `AsyncImage` images, step instructions, primary/secondary muscle chips). No-results state.
- [ ] **Step 3: Wire** — picker is reached from builder ("Add exercise") and session ("+ Exercise"); returns selected exercise ids via `savedStateHandle` to the caller. Add factory branch.
- [ ] **Step 4: Verify in app** (vs mockup 3) → **Commit** `feat(workout): Exercise Picker + detail`.

---

## Task 8: Screen 4 — Active Session

**Mockup:** section "4 · ACTIVE SESSION". **Files:** `ui/train/ActiveSessionViewModel.kt`, `ui/train/ActiveSessionScreen.kt` (reuses `ExerciseCard`/`SetGrid` in session mode).

- [ ] **Step 1: ViewModel** — observe the active session (`observeActiveSession()` / by id); an elapsed-timer flow (`flow { while(true){ emit(now - startedAt); delay(1000) } }`); per-exercise `prev` from `getLastCompletedSession(workoutId)` / `getExerciseHistory`; events: `updateSetKg/Reps/Rir`, `toggleSetComplete` (writes `completed`; requires reps>0 to complete), `addSet`, `removeSet`, `addExercise`→picker, `removeExercise`, `reorderExercise`, `replaceExercise`, `setSessionNote`, `setExerciseNote`, `finish()` → navigate to Summary. Set edits persist via `updateSet`/`insertNextSet`/`removeSet`; exercise edits via Task-2 methods.
- [ ] **Step 2: Screen** — top bar (collapse chevron → minimize/back keeping session ACTIVE; stopwatch; elapsed timer; `LiquidGlassButton` finish), session-notes field, stacked exercise `FrostedCard`s each with `SetGrid` in **session mode** (`SET · PREV · KG · REPS · ✓`; completed row = `surfaceTint = accent` + accent check via `onAccent`; pending = editable cells + hollow check; tap row → reveal RIR stepper), Add set, collapsed next exercises, `LiquidGlassButton` "+ Exercise". No rest timer, no rank band. Match mockup 4 exactly (this is the centerpiece — spend the most verification effort here).
- [ ] **Step 3: Wire** — `Routes.ActiveSession` (current active session); reachable from Train Home Start/Resume and `+Exercise`→picker→back. Factory branch.
- [ ] **Step 4: Verify in app** (vs mockup 4; check completed-row accent tint, PREV column, editable cells, RIR reveal) → **Commit** `feat(workout): Active Session screen`.

---

## Task 9: Screen 5 — Session Summary

**Mockup:** section "5 · SESSION SUMMARY". **Files:** `ui/train/SessionSummaryViewModel.kt`, `ui/train/SessionSummaryScreen.kt`.

- [ ] **Step 1: ViewModel** — load the session (`getSessionWithDetails`); compute stats via `WorkoutProgressAnalyzer` (total volume = Σ `sessionVolume`, sets count, PRs = exercises whose best est-1RM beats their prior history from `getExerciseHistory`); editable `durationSeconds` (default from `startedAt..now`); `setDuration`, `setNote`, `save()` → `completeSession(sessionId, durationSeconds)`, `discard()` → `abandonSession`/delete.
- [ ] **Step 2: Screen** — completion header (accent check + routine + date), 2×2 metric tiles in `FrostedCard`s (Duration with pencil → time picker; Volume; Sets; New PRs), per-exercise recap rows (`FrostedCard`: name, "N sets · top set · volume", PR badge chip), note field, `LiquidGlassButton` Save workout, `ErrorRed` Discard (confirm). Match mockup 5.
- [ ] **Step 3: Wire** — `Routes.SessionSummary/{sessionId}`; entered from Active Session finish; Save → pop to Train Home. Factory branch.
- [ ] **Step 4: Verify in app** (vs mockup 5; edit duration round-trips) → **Commit** `feat(workout): Session Summary screen`.

---

## Task 10: Screen 6 / 6b — Workout History + Session Detail

**Mockup:** sections "6 · WORKOUT HISTORY" and "6b · SESSION DETAIL + EXERCISE PROGRESS". **Files:** `ui/train/WorkoutHistoryScreen.kt` (rendered inside Train Home's History tab), `ui/train/SessionDetailScreen.kt`, `ui/train/WorkoutHistoryViewModel.kt`.

- [ ] **Step 1: ViewModel** — History list comes from `TrainViewModel.history` (reuse). Detail VM loads `getSessionWithDetails(id)` + per-exercise `getExerciseHistory` → `WorkoutProgressAnalyzer.trendPoints` for the sparkline.
- [ ] **Step 2: History content** — month group headers + session `FrostedCard`s (routine, date, PR badge, duration · sets · volume) shown under Train Home's History tab. Empty state per spec.
- [ ] **Step 3: Session Detail** — back + header (routine, date · duration · volume), per-exercise read-only `SetGrid` (`SET · KG · REPS`, PR badge), and a per-exercise est-1RM **sparkline** (draw with `Canvas`/`drawPath` over `trendPoints`, accent stroke) + delta label. Match mockup 6/6b.
- [ ] **Step 4: Wire** — `Routes.SessionDetail/{sessionId}` from a history card tap. Factory branch for the detail VM.
- [ ] **Step 5: Verify in app** (vs mockups 6 + 6b) → **Commit** `feat(workout): Workout History + Session Detail`.

---

## Final verification

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] Full flow on emulator: create routine → start → log sets (completed rows accent-tinted) → +Exercise mid-session → finish → edit duration → save → see it in History → open detail + progress sparkline. Screenshot each and compare to mockups.
- [ ] Switch the app accent theme (e.g. to Cyan) and confirm the whole workout UI follows (no hardcoded violet).
- [ ] Instrumented DB tests for migrations 10→11, 11→12 pass in CI/emulator.

## Notes for the implementer
- Migrations are additive/rebuild-only; never destructive. `workout_exercises` is rebuilt in `MIGRATION_10_11` to drop columns — keep the FK + index names identical to the entity so Room's runtime validation passes.
- Reuse `ExerciseCard` + `SetGrid` across builder (plan mode), session (live mode), and detail (read-only mode) via a mode enum — don't write three grids.
- Every accent pixel via `LocalAppAccent.current`; every content card `FrostedCard`. Verify readability on the busy themed background at each screen.
- Keep the localhost mockup (`localhost:8137`) open while implementing each screen for side-by-side comparison.
