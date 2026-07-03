# Coach-Managed Training Routines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the AI coach create and edit the user's training routines and create custom exercises (with muscle groups) via confirmed chat tools, and add a muscle-group field to the manual create-exercise dialog.

**Architecture:** Five new coach tools dispatched through `CoachToolExecutor`, sitting on the existing `WorkoutRepository` (create/update/validate) and `ExerciseLibraryRepository` (search/create). Edits apply as deltas onto the current routine (no whole-rewrite). Write tools route through the existing `COACH_WRITE_TOOLS` confirmation flow. No DB migration — the schema already stores muscles.

**Tech Stack:** Kotlin, coroutines/Flow, Room, kotlinx.serialization (lenient JSON for nested tool args), JUnit + mockito-kotlin + subclassed repo fakes, Jetpack Compose (manual dialog).

**Spec:** `docs/superpowers/specs/2026-07-01-coach-training-routines-design.md`
**Branch:** `redesign/ai-coaching`

---

## File Structure

- `data/repository/ExerciseLibraryRepository.kt` — extend `addCustomExercise` to persist muscles (Task 1).
- `ui/train/ExercisePickerViewModel.kt` + `ExercisePickerScreen.kt` — manual muscle field (Task 2).
- `ai/CoachToolExecutor.kt` — inject the two repos + implement the 5 tools + JSON arg DTOs (Tasks 3–6).
- `ai/CoachTools.kt` — new tool schemas, `ROUTINE_TOOL_SCHEMAS`, `CLOUD_COACH_TOOL_SCHEMAS` append, `COACH_WRITE_TOOLS` additions (Tasks 3–6).
- `core/AppContainer.kt` — pass the two repos into `CoachToolExecutor` (Task 3).
- `ai/CloudCoachCoordinator.kt` — confirm-dialog summaries + tool-status labels (Task 7).
- `ai/CoachToolsAdapter.kt` — system-prompt routing guidance (Task 8).
- Tests: `data/repository/ExerciseLibraryRepositoryTest.kt`, `ui/train/ExercisePickerViewModelTest.kt`, `ai/CoachToolExecutorRoutineToolsTest.kt`, `ai/CloudCoachCoordinator` display-text test.

**Build/verify commands:**
- Type-check: `./gradlew :app:compileDebugKotlin`
- Unit tests (scoped): `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineTools*" --tests "*ExerciseLibraryRepository*" --tests "*ExercisePickerViewModel*" --tests "*AiCoachBoundaryTest*"`
- Full suite: `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail — known `.env.test` network test)
- APK: `./gradlew :app:assembleDebug`

---

## Task 1: Persist muscles in `addCustomExercise`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepository.kt` (the `addCustomExercise` method, ~line 22)
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepositoryTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ExerciseLibraryRepositoryTest {

    /** Fake DAO that captures the inserted entity and returns a fixed id. */
    private class CapturingExerciseDao : ExerciseDao by mock() {
        var inserted: ExerciseEntity? = null
        override suspend fun insertReturningId(entity: ExerciseEntity): Long {
            inserted = entity
            return 42L
        }
    }

    @Test
    fun `addCustomExercise persists specified primary and secondary muscles as json`() = runTest {
        val dao = CapturingExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        val id = repo.addCustomExercise(
            name = "Cable Y-Raise",
            primaryMuscles = listOf("Shoulders"),
            secondaryMuscles = listOf("Back"),
        )

        assertEquals(42L, id)
        assertEquals(ExerciseLibraryJson.encodeList(listOf("Shoulders")), dao.inserted!!.primaryMuscles)
        assertEquals(ExerciseLibraryJson.encodeList(listOf("Back")), dao.inserted!!.secondaryMuscles)
        assertEquals("Cable Y-Raise", dao.inserted!!.name)
        assertEquals(true, dao.inserted!!.userCreated)
    }

    @Test
    fun `addCustomExercise defaults to empty muscles when none provided`() = runTest {
        val dao = CapturingExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        repo.addCustomExercise("Sled Push")

        assertEquals(ExerciseLibraryJson.encodeList(emptyList()), dao.inserted!!.primaryMuscles)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ExerciseLibraryRepositoryTest*"`
Expected: FAIL — `addCustomExercise` has no `primaryMuscles`/`secondaryMuscles` params (compile error).

- [ ] **Step 3: Extend `addCustomExercise`**

Replace the existing method in `ExerciseLibraryRepository.kt`. Add the import `import com.zack.recomptracker.domain.workout.ExerciseLibraryJson` at the top if not present.

```kotlin
    open suspend fun addCustomExercise(
        name: String,
        primaryMuscles: List<String> = emptyList(),
        secondaryMuscles: List<String> = emptyList(),
    ): Long {
        val entity = ExerciseEntity(
            source = "user",
            sourceVersion = "1",
            externalId = "user_" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_"),
            name = name.trim(),
            category = null,
            force = null,
            level = null,
            mechanic = null,
            equipment = null,
            primaryMuscles = ExerciseLibraryJson.encodeList(primaryMuscles),
            secondaryMuscles = ExerciseLibraryJson.encodeList(secondaryMuscles),
            instructions = "[]",
            images = "[]",
            userCreated = true,
        )
        return exerciseDao.insertReturningId(entity)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ExerciseLibraryRepositoryTest*"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepository.kt app/src/test/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepositoryTest.kt
git commit -m "feat(exercise): addCustomExercise persists muscle groups"
```

---

## Task 2: Muscle field in the manual create-exercise dialog

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerViewModel.kt` (the `createCustom` method, ~line 95)
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerScreen.kt` (the create-exercise `AlertDialog`, ~line 342)
- Test: `app/src/test/java/com/zack/recomptracker/ui/train/ExercisePickerViewModelTest.kt` (create)

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ExercisePickerViewModelTest {

    private class FakeExerciseLibraryRepository : ExerciseLibraryRepository(mock<ExerciseDao>()) {
        var lastName: String? = null
        var lastPrimary: List<String>? = null
        override suspend fun addCustomExercise(
            name: String,
            primaryMuscles: List<String>,
            secondaryMuscles: List<String>,
        ): Long {
            lastName = name
            lastPrimary = primaryMuscles
            return 7L
        }
    }

    @Test
    fun `createCustom passes name and selected muscles through to the repository`() = runTest {
        val repo = FakeExerciseLibraryRepository()
        val vm = ExercisePickerViewModel(repository = repo)

        val id = vm.createCustom(name = "Cable Y-Raise", primaryMuscles = listOf("Shoulders"))

        assertEquals(7L, id)
        assertEquals("Cable Y-Raise", repo.lastName)
        assertEquals(listOf("Shoulders"), repo.lastPrimary)
    }
}
```

Note: if `ExercisePickerViewModel`'s constructor needs more than `repository`, pass the same fakes/mocks the existing tests use — check the current constructor and mirror it.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*ExercisePickerViewModelTest*"`
Expected: FAIL — `createCustom` has no `primaryMuscles` param.

- [ ] **Step 3: Add the muscle param to `createCustom`**

In `ExercisePickerViewModel.kt`, replace `createCustom`:

```kotlin
    suspend fun createCustom(name: String, primaryMuscles: List<String> = emptyList()): Long {
        val id = repository.addCustomExercise(name, primaryMuscles = primaryMuscles)
        _selected.value = _selected.value + id
        return id
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*ExercisePickerViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Add the muscle selector to the dialog (UI — no unit test; user verifies on device)**

In `ExercisePickerScreen.kt`, inside the `if (showCreateDialog)` block, add a selected-muscle state next to `customName`, and a chip row in the dialog `text`. Muscle options reuse the existing taxonomy minus "All" (`MUSCLE_FILTER_CHIPS` = `listOf("All","Chest","Back","Shoulders","Legs","Arms","Core")`).

```kotlin
    if (showCreateDialog) {
        var customName by remember { mutableStateOf("") }
        var customMuscle by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; customName = ""; customMuscle = null },
            title = { Text("Custom exercise", color = appColors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Exercise name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Muscle group (optional)", style = AppType.label, color = appColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        MUSCLE_FILTER_CHIPS.filter { it != "All" }.forEach { muscle ->
                            val active = customMuscle == muscle
                            FilterChip(
                                selected = active,
                                onClick = { customMuscle = if (active) null else muscle },
                                label = { Text(muscle) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customName.trim()
                        if (name.isNotEmpty()) {
                            val muscles = listOfNotNull(customMuscle)
                            scope.launch {
                                val id = viewModel.createCustom(name, muscles)
                                if (replaceMode) onReplacePick(id)
                            }
                            showCreateDialog = false
                            customName = ""
                            customMuscle = null
                        }
                    },
                    enabled = customName.isNotBlank(),
                ) { Text("Create", color = accent.inkLight) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; customName = ""; customMuscle = null }) {
                    Text("Cancel", color = appColors.textMuted)
                }
            },
            containerColor = appColors.frostedSurface,
        )
    }
```

Add the imports used above if missing: `androidx.compose.foundation.horizontalScroll`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.layout.Arrangement`, `androidx.compose.foundation.layout.Column`, `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`, `androidx.compose.material3.FilterChip`, `com.zack.recomptracker.ui.theme.AppType`.

- [ ] **Step 6: Verify compile + tests**

Run: `./gradlew :app:compileDebugKotlin` (Expected: BUILD SUCCESSFUL) and `./gradlew :app:testDebugUnitTest --tests "*ExercisePickerViewModelTest*"` (Expected: PASS).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerViewModel.kt app/src/main/java/com/zack/recomptracker/ui/train/ExercisePickerScreen.kt app/src/test/java/com/zack/recomptracker/ui/train/ExercisePickerViewModelTest.kt
git commit -m "feat(train): muscle-group field in the manual create-exercise dialog"
```

---

## Task 3: Executor read tools — `get_routines` + `search_exercises` (+ inject repos, wire container)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (constructor + dispatch + 2 methods + a shared lenient `Json`)
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt` (`ROUTINE_TOOL_SCHEMAS`, `CLOUD_COACH_TOOL_SCHEMAS`)
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (~line 254, executor construction)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt` (create)

- [ ] **Step 1: Write the failing test (shared fakes + the two reads)**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.NewWorkoutLine
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.PlannedSet
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CoachToolExecutorRoutineToolsTest {

    private val fixedDate = java.time.LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun exercise(id: Long, name: String, primary: List<String> = emptyList()) =
        Exercise(id, "ext_$id", name, null, null, null, null, null, primary, emptyList(), emptyList(), emptyList(), false)

    /** Fake exercise library: search matches by case-insensitive substring. */
    class FakeExerciseLibrary(private val all: List<Exercise>) : ExerciseLibraryRepository(mock<ExerciseDao>()) {
        val created = mutableListOf<Triple<String, List<String>, List<String>>>()
        override suspend fun search(query: String): List<Exercise> =
            all.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getById(id: Long): Exercise? = all.firstOrNull { it.id == id }
        override suspend fun addCustomExercise(name: String, primaryMuscles: List<String>, secondaryMuscles: List<String>): Long {
            created += Triple(name, primaryMuscles, secondaryMuscles)
            return 999L
        }
    }

    /** Fake workout repo: in-memory templates; records create/update calls. */
    class FakeWorkoutRepo(initial: List<WorkoutTemplate> = emptyList()) : WorkoutRepository(mock<WorkoutDao>()) {
        val templates = initial.toMutableList()
        var lastSaved: Triple<String, String?, List<NewWorkoutLine>>? = null
        var lastUpdated: Triple<Long, String, List<NewWorkoutLine>>? = null
        override fun observeAll(): Flow<List<WorkoutTemplate>> = flowOf(templates)
        override suspend fun getById(id: Long): WorkoutTemplate? = templates.firstOrNull { it.id == id }
        override suspend fun saveWorkout(name: String, note: String?, lines: List<NewWorkoutLine>): Long {
            if (lines.isEmpty()) throw IllegalArgumentException("A workout must contain at least one exercise.")
            lastSaved = Triple(name, note, lines); return 1L
        }
        override suspend fun updateWorkout(workoutId: Long, name: String, note: String?, lines: List<NewWorkoutLine>) {
            lastUpdated = Triple(workoutId, name, lines)
        }
    }

    private fun executor(
        workoutRepo: WorkoutRepository,
        library: ExerciseLibraryRepository,
    ) = CoachToolExecutor(
        logRepository = mock<LogRepository>(),
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
        workoutRepository = workoutRepo,
        exerciseLibraryRepository = library,
    )

    private fun template(id: Long, name: String, ex: List<Pair<String, Int>>) = WorkoutTemplate(
        id = id, name = name, note = null, createdAt = "", updatedAt = "",
        exercises = ex.mapIndexed { i, (exName, setCount) ->
            WorkoutTemplateExercise(
                id = i.toLong(),
                exercise = exercise(i.toLong(), exName),
                plannedSets = (1..setCount).map { PlannedSet(it.toLong(), it, 8, null) },
                sortOrder = i, note = null,
            )
        },
    )

    @Test
    fun `get_routines returns the users routines with exercises and set counts`() = runTest {
        val repo = FakeWorkoutRepo(listOf(template(1, "Push Day", listOf("Bench Press" to 4))))
        val json = executor(repo, FakeExerciseLibrary(emptyList())).execute("get_routines", emptyMap())
        assertTrue(json.contains("Push Day"))
        assertTrue(json.contains("Bench Press"))
    }

    @Test
    fun `search_exercises returns library matches by name`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press", listOf("chest"))))
        val json = executor(FakeWorkoutRepo(), lib).execute("search_exercises", mapOf("query" to "bench"))
        assertTrue(json.contains("Barbell Bench Press"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: FAIL — `CoachToolExecutor` has no `workoutRepository`/`exerciseLibraryRepository` params and no `get_routines`/`search_exercises` handling.

- [ ] **Step 3: Add ctor params, a lenient `Json`, dispatch entries, and the two read methods**

In `CoachToolExecutor.kt`: add imports
```kotlin
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.NewWorkoutLine
import com.zack.recomptracker.data.repository.PlannedSetDraft
import com.zack.recomptracker.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
```
(`kotlinx.coroutines.flow.first` is already imported — don't duplicate.)

Add the two params to the constructor (after `workoutSessionRepository`):
```kotlin
    private val workoutRepository: WorkoutRepository? = null,
    private val exerciseLibraryRepository: ExerciseLibraryRepository? = null,
```

Add a private lenient JSON near the top of the class body:
```kotlin
    private val toolJson = Json { ignoreUnknownKeys = true; isLenient = true }
```

Add ONLY the two read dispatch entries in `execute(...)` (the write entries are added in their own tasks, so this task compiles on its own):
```kotlin
        "get_routines" -> getRoutines()
        "search_exercises" -> searchExercises(args)
```

Implement the two reads:
```kotlin
    private suspend fun getRoutines(): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val routines = repo.observeAll().first()
        val items = routines.joinToString(",") { r ->
            val ex = r.exercises.joinToString(",") { line ->
                val first = line.plannedSets.firstOrNull()
                """{"name":"${line.exercise.name.esc()}","sets":${line.plannedSets.size}""" +
                    (first?.targetReps?.let { ""","reps":$it""" } ?: "") +
                    (first?.targetWeightKg?.let { ""","weight_kg":$it""" } ?: "") + "}"
            }
            """{"name":"${r.name.esc()}","exercises":[$ex]}"""
        }
        return """{"routines":[$items]}"""
    }

    private suspend fun searchExercises(args: Map<String, String>): String {
        val query = args["query"]?.trim().orEmpty()
        if (query.isBlank()) return """{"error":"search_exercises requires 'query'"}"""
        val lib = exerciseLibraryRepository ?: return """{"error":"exercise library unavailable"}"""
        val matches = lib.search(query).take(8).joinToString(",") { e ->
            """{"name":"${e.name.esc()}","primary_muscles":[${e.primaryMuscles.joinToString(",") { "\"${it.esc()}\"" }}]""" +
                (e.equipment?.let { ""","equipment":"${it.esc()}"""" } ?: "") + "}"
        }
        return """{"matches":[$matches]}"""
    }
```

(`esc()` is the existing private string-escaping extension used by other tools in this file — reuse it.)

- [ ] **Step 4: Add the read schemas to `CoachTools.kt`**

Add a new list and append it to the cloud set (leave the shared/legacy `COACH_TOOL_SCHEMAS` untouched so the deprecated on-device 2B path is unaffected):

```kotlin
/** Cloud-coach routine-management tools (create/edit routines + custom exercises). */
val ROUTINE_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"get_routines","description":"List the user's saved training routines with each exercise's name, number of sets, and target reps/weight. Call before editing a routine.","parameters":{"type":"object","properties":{},"required":[]}}""",
    """{"name":"search_exercises","description":"Search the exercise library by name. Returns up to 8 matches with primary muscles. Use to find real exercises before adding them to a routine.","parameters":{"type":"object","properties":{"query":{"type":"string","description":"Exercise name to search for"}},"required":["query"]}}""",
    """{"name":"create_routine","description":"Create a new training routine. 'sets' is the number of sets for that exercise; 'reps' and 'weight_kg' are optional targets.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Routine name, e.g. 'Push Day'"},"exercises":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"sets":{"type":"integer"},"reps":{"type":"integer"},"weight_kg":{"type":"number"}},"required":["name","sets"]}}},"required":["name","exercises"]}}""",
    """{"name":"edit_routine","description":"Edit an existing routine by applying only the changes you specify (existing exercises are preserved). Use 'add' to append exercises, 'remove' to drop them by name, 'retarget' to change an exercise's sets/reps/weight, and 'new_name' to rename.","parameters":{"type":"object","properties":{"name":{"type":"string","description":"Name of the routine to edit"},"add":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"sets":{"type":"integer"},"reps":{"type":"integer"},"weight_kg":{"type":"number"}},"required":["name","sets"]}},"remove":{"type":"array","items":{"type":"string"}},"retarget":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"sets":{"type":"integer"},"reps":{"type":"integer"},"weight_kg":{"type":"number"}},"required":["name"]}},"new_name":{"type":"string"}},"required":["name"]}}""",
    """{"name":"create_exercise","description":"Create a custom exercise in the library when the user names one that isn't found. Specify the muscle group(s) it works.","parameters":{"type":"object","properties":{"name":{"type":"string"},"primary_muscles":{"type":"array","items":{"type":"string"}},"secondary_muscles":{"type":"array","items":{"type":"string"}},"equipment":{"type":"string"}},"required":["name","primary_muscles"]}}""",
)
```

Change the cloud list definition:
```kotlin
/** The cloud coach's full tool list: shared tools + web search + routine management. */
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA + ROUTINE_TOOL_SCHEMAS
```

Add the three write tools to the confirmation set:
```kotlin
val COACH_WRITE_TOOLS: Set<String> =
    setOf("log_meal", "log_metric", "update_calorie_target", "create_routine", "edit_routine", "create_exercise")
```

(This advertises all five routine tools + registers the three writes now. The write *handlers* land in Tasks 4–6; until then a call to one returns a graceful `{"error":"unknown tool ..."}` from the dispatch `else` — not a crash. This is transient within the plan, since the branch isn't shipped mid-plan.)

- [ ] **Step 5: Wire the repos into the executor in `AppContainer.kt`**

At the `CoachToolExecutor(...)` construction (~line 254), add:
```kotlin
        workoutRepository = workoutRepository,
        exerciseLibraryRepository = exerciseLibraryRepository,
```
(Both `workoutRepository` and `exerciseLibraryRepository` are already declared as container vals at ~lines 173–174.)

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"` (Expected: the two read tests PASS) and `./gradlew :app:compileDebugKotlin` (Expected: BUILD SUCCESSFUL).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt
git commit -m "feat(coach): routine read tools (get_routines, search_exercises) + wiring"
```

---

## Task 4: Executor write tool — `create_routine`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (add DTOs + `createRoutine`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt` (add cases)

- [ ] **Step 1: Write failing tests**

Add to `CoachToolExecutorRoutineToolsTest`:
```kotlin
    @Test
    fun `create_routine resolves exercises and saves with correct set counts`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press")))
        val repo = FakeWorkoutRepo()
        val json = executor(repo, lib).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"bench press","sets":4,"reps":8}]"""),
        )
        assertTrue(json.contains("\"success\":true"))
        val saved = repo.lastSaved!!
        assertTrue(saved.first == "Push Day")
        assertTrue(saved.third.size == 1)
        assertTrue(saved.third[0].plannedSets.size == 4)
        assertTrue(saved.third[0].plannedSets.all { it.targetReps == 8 })
    }

    @Test
    fun `create_routine allows an exercise with only a set count and no reps`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press")))
        val repo = FakeWorkoutRepo()
        executor(repo, lib).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"bench press","sets":3}]"""),
        )
        val line = repo.lastSaved!!.third[0]
        assertTrue(line.plannedSets.size == 3)
        assertTrue(line.plannedSets.all { it.targetReps == null })
    }

    @Test
    fun `create_routine reports unresolved exercises without saving`() = runTest {
        val repo = FakeWorkoutRepo()
        val json = executor(repo, FakeExerciseLibrary(emptyList())).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"Zercher Thruster","sets":3}]"""),
        )
        assertTrue(json.contains("error"))
        assertTrue(json.contains("Zercher Thruster"))
        assertTrue(repo.lastSaved == null)
    }

    @Test
    fun `create_routine surfaces validation errors from the repository`() = runTest {
        val json = executor(FakeWorkoutRepo(), FakeExerciseLibrary(emptyList())).execute(
            "create_routine",
            mapOf("name" to "Empty", "exercises" to "[]"),
        )
        assertTrue(json.contains("error"))
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: FAIL — `create_routine` returns `unknown tool` / not implemented.

- [ ] **Step 3: Implement DTOs, exercise resolution, and `createRoutine`**

Add the dispatch entry in `execute(...)`:
```kotlin
        "create_routine" -> createRoutine(args)
```

Add serializable DTOs at the bottom of `CoachToolExecutor.kt` (top-level, private):
```kotlin
@kotlinx.serialization.Serializable
private data class RoutineExerciseArg(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val weight_kg: Double? = null,
)

@kotlinx.serialization.Serializable
private data class RetargetArg(
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val weight_kg: Double? = null,
)
```

Add helpers + `createRoutine` in the class:
```kotlin
    /** Resolve an exercise name to a library id (best fuzzy match), or null if none. */
    private suspend fun resolveExerciseId(name: String): Long? {
        val lib = exerciseLibraryRepository ?: return null
        val hits = lib.search(name)
        return hits.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            ?: hits.firstOrNull { it.name.startsWith(name, ignoreCase = true) }?.id
            ?: hits.firstOrNull()?.id
    }

    /** Build `sets` uniform planned-set drafts carrying the same optional reps/weight target. */
    private fun uniformSets(sets: Int, reps: Int?, weightKg: Double?): List<PlannedSetDraft> =
        (1..sets.coerceIn(1, 20)).map { PlannedSetDraft(targetReps = reps, targetWeightKg = weightKg) }

    private suspend fun createRoutine(args: Map<String, String>): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create_routine requires 'name'"}"""
        val parsed = runCatching {
            toolJson.decodeFromString<List<RoutineExerciseArg>>(args["exercises"] ?: "[]")
        }.getOrElse { return """{"error":"could not parse 'exercises' list"}""" }
        if (parsed.isEmpty()) return """{"error":"a routine needs at least one exercise"}"""

        val lines = mutableListOf<NewWorkoutLine>()
        val unresolved = mutableListOf<String>()
        for (e in parsed) {
            val id = resolveExerciseId(e.name)
            if (id == null) { unresolved += e.name; continue }
            lines += NewWorkoutLine(exerciseId = id, plannedSets = uniformSets(e.sets, e.reps, e.weight_kg))
        }
        if (unresolved.isNotEmpty()) {
            return """{"error":"not found in library: ${unresolved.joinToString(", ") { it.esc() }}. Use create_exercise or pick another name."}"""
        }
        return runCatching {
            repo.saveWorkout(name, null, lines)
            """{"success":true,"routine":"${name.esc()}","exercises":${lines.size}}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not save routine").esc()}"}""" }
    }
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: PASS (all create_routine cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt
git commit -m "feat(coach): create_routine tool with exercise resolution"
```

---

## Task 5: Executor write tool — `edit_routine` (deltas)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (add `editRoutine`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt` (add cases)

- [ ] **Step 1: Write failing tests**

```kotlin
    @Test
    fun `edit_routine adds and removes exercises as deltas, preserving the rest`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(10, "Cable Fly")))
        val repo = FakeWorkoutRepo(listOf(template(1, "Push Day", listOf("Bench Press" to 4, "Dips" to 3))))
        val json = executor(repo, lib).execute(
            "edit_routine",
            mapOf("name" to "Push Day", "add" to """[{"name":"cable fly","sets":3,"reps":12}]""", "remove" to """["Dips"]"""),
        )
        assertTrue(json.contains("\"success\":true"))
        val updated = repo.lastUpdated!!
        assertTrue(updated.first == 1L)
        // Bench Press preserved (4 sets) + Cable Fly added (3 sets); Dips removed → 2 lines.
        assertTrue(updated.third.size == 2)
    }

    @Test
    fun `edit_routine retarget changes only the set count of one exercise`() = runTest {
        val repo = FakeWorkoutRepo(listOf(template(1, "Push Day", listOf("Bench Press" to 4))))
        executor(repo, FakeExerciseLibrary(emptyList())).execute(
            "edit_routine",
            mapOf("name" to "Push Day", "retarget" to """[{"name":"Bench Press","sets":5}]"""),
        )
        assertTrue(repo.lastUpdated!!.third[0].plannedSets.size == 5)
    }

    @Test
    fun `edit_routine renames the routine`() = runTest {
        val repo = FakeWorkoutRepo(listOf(template(1, "Push Day", listOf("Bench Press" to 4))))
        executor(repo, FakeExerciseLibrary(emptyList())).execute(
            "edit_routine",
            mapOf("name" to "Push Day", "new_name" to "Upper A"),
        )
        assertTrue(repo.lastUpdated!!.second == "Upper A")
    }

    @Test
    fun `edit_routine reports when the routine is not found`() = runTest {
        val repo = FakeWorkoutRepo(emptyList())
        val json = executor(repo, FakeExerciseLibrary(emptyList())).execute(
            "edit_routine",
            mapOf("name" to "Nope", "new_name" to "X"),
        )
        assertTrue(json.contains("error"))
        assertTrue(repo.lastUpdated == null)
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: FAIL — `edit_routine` not implemented.

- [ ] **Step 3: Implement `editRoutine`**

Add the dispatch entry in `execute(...)`:
```kotlin
        "edit_routine" -> editRoutine(args)
```

```kotlin
    private suspend fun editRoutine(args: Map<String, String>): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"edit_routine requires 'name'"}"""
        val current = repo.observeAll().first().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return """{"error":"routine '${name.esc()}' not found"}"""

        val add = runCatching { toolJson.decodeFromString<List<RoutineExerciseArg>>(args["add"] ?: "[]") }.getOrDefault(emptyList())
        val remove = runCatching { toolJson.decodeFromString<List<String>>(args["remove"] ?: "[]") }.getOrDefault(emptyList())
        val retarget = runCatching { toolJson.decodeFromString<List<RetargetArg>>(args["retarget"] ?: "[]") }.getOrDefault(emptyList())
        val newName = args["new_name"]?.trim()?.takeIf { it.isNotBlank() }

        // Start from the current lines (preserve everything not explicitly changed).
        val lines = current.exercises.sortedBy { it.sortOrder }.map { line ->
            NewWorkoutLine(
                exerciseId = line.exercise.id,
                plannedSets = line.plannedSets.map { PlannedSetDraft(it.targetReps, it.targetWeightKg) },
                note = line.note,
            )
        }.toMutableList()

        // remove by exercise name
        if (remove.isNotEmpty()) {
            val removeIds = current.exercises
                .filter { ex -> remove.any { it.equals(ex.exercise.name, ignoreCase = true) } }
                .map { it.exercise.id }.toSet()
            lines.removeAll { it.exerciseId in removeIds }
        }

        // retarget existing lines (by matching exercise id resolved from current template)
        for (rt in retarget) {
            val ex = current.exercises.firstOrNull { it.exercise.name.equals(rt.name, ignoreCase = true) } ?: continue
            val idx = lines.indexOfFirst { it.exerciseId == ex.exercise.id }
            if (idx < 0) continue
            val existingFirst = lines[idx].plannedSets.firstOrNull()
            val setCount = rt.sets ?: lines[idx].plannedSets.size
            val reps = rt.reps ?: existingFirst?.targetReps
            val weight = rt.weight_kg ?: existingFirst?.targetWeightKg
            lines[idx] = lines[idx].copy(plannedSets = uniformSets(setCount, reps, weight))
        }

        // add new exercises (resolved from the library)
        val unresolved = mutableListOf<String>()
        for (a in add) {
            val id = resolveExerciseId(a.name)
            if (id == null) { unresolved += a.name; continue }
            lines += NewWorkoutLine(exerciseId = id, plannedSets = uniformSets(a.sets, a.reps, a.weight_kg))
        }
        if (unresolved.isNotEmpty()) {
            return """{"error":"not found in library: ${unresolved.joinToString(", ") { it.esc() }}. Use create_exercise first."}"""
        }

        return runCatching {
            repo.updateWorkout(current.id, newName ?: current.name, current.note, lines)
            """{"success":true,"routine":"${(newName ?: current.name).esc()}","exercises":${lines.size}}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not update routine").esc()}"}""" }
    }
```

Note: `NewWorkoutLine` is a `data class`, so `.copy(...)` is available.

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: PASS (all edit_routine cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt
git commit -m "feat(coach): edit_routine tool applying add/remove/retarget/rename as deltas"
```

---

## Task 6: Executor write tool — `create_exercise`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (add `createExercise`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt` (add cases)

- [ ] **Step 1: Write failing tests**

```kotlin
    @Test
    fun `create_exercise stores the specified muscles`() = runTest {
        val lib = FakeExerciseLibrary(emptyList())
        val json = executor(FakeWorkoutRepo(), lib).execute(
            "create_exercise",
            mapOf("name" to "Cable Y-Raise", "primary_muscles" to """["Shoulders"]"""),
        )
        assertTrue(json.contains("\"success\":true"))
        assertTrue(lib.created.single().first == "Cable Y-Raise")
        assertTrue(lib.created.single().second == listOf("Shoulders"))
    }

    @Test
    fun `create_exercise requires a name`() = runTest {
        val json = executor(FakeWorkoutRepo(), FakeExerciseLibrary(emptyList()))
            .execute("create_exercise", mapOf("primary_muscles" to """["Shoulders"]"""))
        assertTrue(json.contains("error"))
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: FAIL — `create_exercise` not implemented.

- [ ] **Step 3: Implement `createExercise`**

Add the dispatch entry in `execute(...)`:
```kotlin
        "create_exercise" -> createExercise(args)
```

```kotlin
    private suspend fun createExercise(args: Map<String, String>): String {
        val lib = exerciseLibraryRepository ?: return """{"error":"exercise library unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create_exercise requires 'name'"}"""
        fun muscles(key: String): List<String> =
            runCatching { toolJson.decodeFromString<List<String>>(args[key] ?: "[]") }
                .getOrDefault(emptyList())
                .map { it.trim() }.filter { it.isNotBlank() }
        val primary = muscles("primary_muscles")
        val secondary = muscles("secondary_muscles")
        return runCatching {
            val id = lib.addCustomExercise(name, primaryMuscles = primary, secondaryMuscles = secondary)
            """{"success":true,"exercise":"${name.esc()}","id":$id}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not create exercise").esc()}"}""" }
    }
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorRoutineToolsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorRoutineToolsTest.kt
git commit -m "feat(coach): create_exercise tool with user-specified muscles"
```

---

## Task 7: Confirmation summaries + tool-status labels

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt` (`pendingActionDisplayText` ~line 276, `toolStatusText` ~line 264)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorRoutineConfirmTest.kt` (create)

- [ ] **Step 1: Write the failing display-text test**

The confirm summary is produced by the private `pendingActionDisplayText`. Test it via the public confirm flow, or (simpler) extract the summary into an internal top-level function `routineActionSummary(toolName, args)` in `CloudCoachCoordinator.kt` and test that directly. Use the extraction approach:

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoachCoordinatorRoutineConfirmTest {
    @Test
    fun `create_routine summary lists the routine name`() {
        val s = routineActionSummary("create_routine", mapOf("name" to "Push Day",
            "exercises" to """[{"name":"Bench Press","sets":4,"reps":8}]"""))
        assertTrue(s.contains("Push Day"))
        assertTrue(s.contains("Bench Press"))
    }

    @Test
    fun `edit_routine summary describes the changes`() {
        val s = routineActionSummary("edit_routine", mapOf("name" to "Push Day",
            "remove" to """["Dips"]""", "new_name" to "Upper A"))
        assertTrue(s.contains("Push Day"))
        assertTrue(s.contains("Dips") || s.contains("Upper A"))
    }

    @Test
    fun `create_exercise summary names the exercise`() {
        val s = routineActionSummary("create_exercise", mapOf("name" to "Cable Y-Raise",
            "primary_muscles" to """["Shoulders"]"""))
        assertTrue(s.contains("Cable Y-Raise"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorRoutineConfirmTest*"`
Expected: FAIL — `routineActionSummary` unresolved.

- [ ] **Step 3: Add `routineActionSummary` and call it from `pendingActionDisplayText`**

Add a top-level internal function in `CloudCoachCoordinator.kt`:
```kotlin
internal fun routineActionSummary(toolName: String, args: Map<String, String>): String = when (toolName) {
    "create_routine" -> {
        val name = args["name"].orEmpty()
        val ex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["exercises"].orEmpty())
            .map { it.groupValues[1] }.toList()
        "Create routine \"$name\"" + if (ex.isNotEmpty()) " — ${ex.joinToString(", ")}" else ""
    }
    "edit_routine" -> {
        val parts = buildList {
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["add"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("add ${it.joinToString(", ")}") }
            Regex("\"([^\"]+)\"").findAll(args["remove"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("remove ${it.joinToString(", ")}") }
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(args["retarget"].orEmpty())
                .map { it.groupValues[1] }.toList().takeIf { it.isNotEmpty() }?.let { add("retarget ${it.joinToString(", ")}") }
            args["new_name"]?.takeIf { it.isNotBlank() }?.let { add("rename to \"$it\"") }
        }
        "Edit \"${args["name"].orEmpty()}\"" + if (parts.isNotEmpty()) " — ${parts.joinToString("; ")}" else ""
    }
    "create_exercise" -> {
        val muscles = Regex("\"([^\"]+)\"").findAll(args["primary_muscles"].orEmpty())
            .map { it.groupValues[1] }.toList()
        "Create custom exercise \"${args["name"].orEmpty()}\"" + if (muscles.isNotEmpty()) " (${muscles.joinToString(", ")})" else ""
    }
    else -> toolName
}
```

In `pendingActionDisplayText`, add branches before the `else`:
```kotlin
            "create_routine", "edit_routine", "create_exercise" -> routineActionSummary(toolName, args)
```

In `toolStatusText`, add:
```kotlin
        "get_routines" -> "Reading your routines…"
        "search_exercises" -> "Searching exercises…"
        "create_routine" -> "Creating routine…"
        "edit_routine" -> "Updating routine…"
        "create_exercise" -> "Creating exercise…"
```

- [ ] **Step 4: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CloudCoachCoordinatorRoutineConfirmTest*"` (Expected: PASS) and `./gradlew :app:compileDebugKotlin` (Expected: BUILD SUCCESSFUL).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/CloudCoachCoordinatorRoutineConfirmTest.kt
git commit -m "feat(coach): confirm-dialog summaries + status labels for routine tools"
```

---

## Task 8: System-prompt routing guidance

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` (`COACH_PROMPT_GUIDELINES`)

- [ ] **Step 1: Locate the guidelines constant**

Run: `grep -n "COACH_PROMPT_GUIDELINES" app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`
Read the existing string so the new lines match its numbering/format.

- [ ] **Step 2: Add routine guidance**

Append these rules (renumber to follow the existing list) to `COACH_PROMPT_GUIDELINES`:
```
- To build a routine: call search_exercises to find real exercises, then create_routine. 'sets' is the number of sets; reps/weight are optional.
- To change a routine: call get_routines first, then edit_routine with ONLY the changes (add/remove/retarget/new_name). Existing exercises are preserved.
- Only call create_exercise when the user names an exercise not found in the library; ask for the muscle group(s) if they didn't say.
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt
git commit -m "feat(coach): system-prompt routing for routine tools"
```

---

## Task 9: Full verification

- [ ] **Step 1: Boundary guard**

Run: `./gradlew :app:testDebugUnitTest --tests "*AiCoachBoundaryTest*"`
Expected: PASS (no `ai/local` imports introduced).

- [ ] **Step 2: Full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: only `InsightHarnessTest > runInsightHarness` fails (known `.env.test` network test); everything else green.

- [ ] **Step 3: APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device (user)**

Install and verify in chat: *"Create a push day with bench press 4 sets and overhead press 3 sets of 10"* → confirm dialog → routine appears in Train. *"Add cable fly, 3 sets, to my push day"* → edit confirm. *"Create a custom exercise called Cable Y-Raise for shoulders"* → confirm. Manual: exercise picker → Create custom exercise → muscle chip → saved with muscle shown in detail.

---

## Notes for the implementer

- `CoachToolExecutor` args arrive as `Map<String, String>`; nested arrays/objects are **JSON strings** (see `parseArgsToStringMap`). Parse them with the class's lenient `toolJson`.
- Reuse the existing private `esc()` extension in `CoachToolExecutor.kt` for all interpolated strings in JSON returns.
- Every tool returns a JSON string: a success payload or `{"error":"..."}` — matching the existing contract so the coordinator relays failures to the model.
- Repos are `open class` with `open` methods — subclass them for fakes (as the existing training-tools test does).
- Keep the new tools out of the shared/legacy `COACH_TOOL_SCHEMAS`; they live in `ROUTINE_TOOL_SCHEMAS`, appended only to `CLOUD_COACH_TOOL_SCHEMAS`.
