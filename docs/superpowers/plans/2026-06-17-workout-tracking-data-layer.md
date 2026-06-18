# Workout Tracking Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Room/data/domain backend that lets users build workout templates from a seeded exercise library, run a resumable workout session logging sets (reps/weight/RIR), save completed sessions, and query "last time" + per-exercise progress for AI analysis. **No UI.**

**Architecture:** Six new Room tables in three groups (seeded `exercises` library; `workouts`+`workout_exercises` templates; `workout_sessions`+`session_exercises`+`session_sets` logs), mirroring the existing `recipes`/`recipe_ingredients` `@Relation` tree and `catalog_foods` seeded-catalog patterns. Pure-Kotlin domain layer (`domain/workout/`) holds models, validation, the free-exercise-db JSON codec, and a progress analyzer. Repositories map Room relation POJOs → domain models, mirroring `RecipeRepository`. DB version 9 → 10 via `MIGRATION_9_10`.

**Tech Stack:** Kotlin, Room 2.8.4, kotlinx.serialization, Coroutines/Flow, JUnit4, manual DI (`AppContainer`).

**Conventions verified in repo:**
- Entities: `data/local/entity/` · DAOs: `data/local/dao/` · Repos: `data/repository/` · Domain: `domain/` (no Android imports).
- DAO tree pattern: `RecipeDao` is `abstract class` with `@Transaction` queries + an `open suspend fun replaceIngredients()` that deletes then re-inserts with `copy(parentId=…, sortOrder=index, id=0)`.
- Seeded-catalog pattern: `catalog_foods` uses `source`/`sourceVersion`/`externalId`, `unique(source, externalId)`; `FoodCatalogRepository` parses an `InputStream` and does `deleteBySource` + `insertAll`.
- Asset seeding: `assets/knowledge/corpus.json` read with `kotlinx.serialization`, loaded under `runCatching` in `AppContainer`.
- Migrations: manual `Migration` objects appended to `addMigrations(...)` in `RecompDatabase.create`. No destructive fallback.
- Unit tests use fake DAOs (see `app/src/test/java/com/zack/recomptracker/data/LogRepositorySyncTest.kt`). Instrumented DB tests use in-memory Room (see `app/src/androidTest/java/com/zack/recomptracker/data/RecompDatabaseTest.kt`).
- Weight is kilograms everywhere (`bodyWeightKg`, `lift_performance.weight`).

---

## File Structure

**Domain (`app/src/main/java/com/zack/recomptracker/domain/workout/`)**
- `WorkoutModels.kt` — all domain models + `SessionStatus` enum + analyzer I/O types.
- `ExerciseLibraryJson.kt` — free-exercise-db `@Serializable` DTO, parser, list-column JSON encode/decode, DTO→entity mapper.
- `WorkoutValidation.kt` — pure validation funcs returning a `ValidationResult`.
- `WorkoutProgressAnalyzer.kt` — volume, estimated 1RM (Epley), best set, per-date trend points.

**Entities (`app/src/main/java/com/zack/recomptracker/data/local/entity/`)**
- `ExerciseEntity.kt`, `WorkoutEntity.kt`, `WorkoutExerciseEntity.kt`, `WorkoutSessionEntity.kt`, `SessionExerciseEntity.kt`, `SessionSetEntity.kt`
- `WorkoutWithExercisesDb.kt` — `WorkoutExerciseWithExercise` + `WorkoutWithExercisesDb`
- `WorkoutSessionWithDetailsDb.kt` — `SessionExerciseWithSets` + `WorkoutSessionWithDetailsDb` + `ExerciseHistoryRow`

**DAOs (`app/src/main/java/com/zack/recomptracker/data/local/dao/`)**
- `ExerciseDao.kt`, `WorkoutDao.kt`, `WorkoutSessionDao.kt`

**Repositories (`app/src/main/java/com/zack/recomptracker/data/repository/`)**
- `WorkoutMappers.kt` — internal entity→domain extension functions.
- `ExerciseLibraryRepository.kt`, `WorkoutRepository.kt`, `WorkoutSessionRepository.kt`

**Modified**
- `data/local/RecompDatabase.kt` — register 6 entities + 3 DAOs, bump to v10, add `MIGRATION_9_10`.
- `core/AppContainer.kt` — construct the 3 repositories, launch `seedIfEmpty` on `appScope`.

**Asset**
- `app/src/main/assets/exercises/exercises.json` — free-exercise-db `dist/exercises.json`.

**Tests**
- `app/src/test/java/com/zack/recomptracker/domain/workout/ExerciseLibraryJsonTest.kt`
- `app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutValidationTest.kt`
- `app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutProgressAnalyzerTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepositoryTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/repository/WorkoutRepositoryTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/repository/WorkoutSessionRepositoryTest.kt`
- `app/src/androidTest/java/com/zack/recomptracker/data/WorkoutDatabaseTest.kt`

---

## Task 1: Domain models + exercise-library JSON codec

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutModels.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/ExerciseLibraryJson.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/ExerciseLibraryJsonTest.kt`

- [ ] **Step 1: Write `WorkoutModels.kt`** (pure data classes — no test of its own; consumed by later steps)

```kotlin
package com.zack.recomptracker.domain.workout

/** Library exercise with decoded list fields. */
data class Exercise(
    val id: Long,
    val externalId: String,
    val name: String,
    val category: String?,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val images: List<String>,
    val userCreated: Boolean,
)

/** One line of a workout template. */
data class WorkoutTemplateExercise(
    val id: Long,
    val exercise: Exercise,
    val plannedSets: Int,
    val targetReps: Int?,
    val sortOrder: Int,
    val note: String?,
)

/** A reusable workout template. */
data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val exercises: List<WorkoutTemplateExercise>,
)

enum class SessionStatus { ACTIVE, COMPLETED, ABANDONED }

data class SessionSet(
    val id: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
    val completed: Boolean,
)

data class SessionExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val sortOrder: Int,
    val note: String?,
    val sets: List<SessionSet>,
)

data class WorkoutSession(
    val id: Long,
    val workoutId: Long?,
    val workoutName: String,
    val date: String,
    val startedAt: String,
    val completedAt: String?,
    val status: SessionStatus,
    val note: String?,
    val exercises: List<SessionExercise>,
)

/** Flat per-set record used for AI progress analysis. */
data class ExerciseHistoryPoint(
    val date: String,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
)

/** One day's aggregated progress for a single exercise. */
data class ExerciseTrendPoint(
    val date: String,
    val totalVolume: Double,
    val bestEstimatedOneRepMax: Double?,
)
```

- [ ] **Step 2: Write the failing test** `ExerciseLibraryJsonTest.kt`

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryJsonTest {

    private val sample = """
    [
      {
        "id": "Barbell_Squat",
        "name": "Barbell Squat",
        "force": "push",
        "level": "intermediate",
        "mechanic": "compound",
        "equipment": "barbell",
        "category": "strength",
        "primaryMuscles": ["quadriceps"],
        "secondaryMuscles": ["glutes", "hamstrings"],
        "instructions": ["Step 1.", "Step 2."],
        "images": ["Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"]
      },
      {
        "id": "Bodyweight_Plank",
        "name": "Plank",
        "level": "beginner",
        "category": "strength",
        "primaryMuscles": ["abdominals"],
        "secondaryMuscles": [],
        "instructions": ["Hold."],
        "images": []
      }
    ]
    """.trimIndent()

    @Test
    fun `parses array into entities mapping id to externalId`() {
        val entities = ExerciseLibraryJson.parse(sample)
            .map { it.toEntity(source = "free-exercise-db", sourceVersion = "v1") }

        assertEquals(2, entities.size)
        val squat = entities[0]
        assertEquals("free-exercise-db", squat.source)
        assertEquals("v1", squat.sourceVersion)
        assertEquals("Barbell_Squat", squat.externalId)
        assertEquals("Barbell Squat", squat.name)
        assertEquals("compound", squat.mechanic)
        assertEquals(false, squat.userCreated)
    }

    @Test
    fun `encodes and decodes list columns round-trip`() {
        val squat = ExerciseLibraryJson.parse(sample).first().toEntity("free-exercise-db", "v1")

        assertEquals(listOf("quadriceps"), ExerciseLibraryJson.decodeList(squat.primaryMuscles))
        assertEquals(listOf("glutes", "hamstrings"), ExerciseLibraryJson.decodeList(squat.secondaryMuscles))
        assertEquals(listOf("Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"), ExerciseLibraryJson.decodeList(squat.images))
    }

    @Test
    fun `tolerates missing optional fields`() {
        val plank = ExerciseLibraryJson.parse(sample)[1].toEntity("free-exercise-db", "v1")

        assertNull(plank.force)
        assertNull(plank.mechanic)
        assertNull(plank.equipment)
        assertEquals("beginner", plank.level)
        assertTrue(ExerciseLibraryJson.decodeList(plank.images).isEmpty())
    }

    @Test
    fun `decodeList returns empty for blank`() {
        assertTrue(ExerciseLibraryJson.decodeList("").isEmpty())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ExerciseLibraryJsonTest"`
Expected: FAIL — `ExerciseLibraryJson` / `ExerciseEntity` unresolved.

- [ ] **Step 4: Create `ExerciseEntity.kt`** (needed by the codec mapper)

`app/src/main/java/com/zack/recomptracker/data/local/entity/ExerciseEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceVersion: String,
    val externalId: String,
    val name: String,
    val category: String?,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val instructions: String,
    val images: String,
    val userCreated: Boolean = false,
)
```

- [ ] **Step 5: Create `ExerciseLibraryJson.kt`**

```kotlin
package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class FreeExerciseDbExerciseDto(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

object ExerciseLibraryJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList = ListSerializer(String.serializer())

    fun parse(raw: String): List<FreeExerciseDbExerciseDto> =
        json.decodeFromString(ListSerializer(FreeExerciseDbExerciseDto.serializer()), raw)

    fun encodeList(list: List<String>): String = json.encodeToString(stringList, list)

    fun decodeList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(stringList, raw)
}

/** Top-level so it is usable as a plain extension across packages (and same-package tests). */
fun FreeExerciseDbExerciseDto.toEntity(source: String, sourceVersion: String): ExerciseEntity =
    ExerciseEntity(
        source = source,
        sourceVersion = sourceVersion,
        externalId = id,
        name = name,
        category = category,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        primaryMuscles = ExerciseLibraryJson.encodeList(primaryMuscles),
        secondaryMuscles = ExerciseLibraryJson.encodeList(secondaryMuscles),
        instructions = ExerciseLibraryJson.encodeList(instructions),
        images = ExerciseLibraryJson.encodeList(images),
        userCreated = false,
    )
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ExerciseLibraryJsonTest"`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutModels.kt \
        app/src/main/java/com/zack/recomptracker/domain/workout/ExerciseLibraryJson.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/ExerciseEntity.kt \
        app/src/test/java/com/zack/recomptracker/domain/workout/ExerciseLibraryJsonTest.kt
git commit -m "feat(workout): add domain models + free-exercise-db JSON codec"
```

---

## Task 2: Workout validation (pure domain)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutValidation.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutValidationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutValidationTest {

    @Test
    fun `valid template draft passes`() {
        val result = WorkoutValidation.validateTemplate(name = "Push Day", exerciseCount = 3, plannedSets = listOf(3, 4, 3))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `blank name fails`() {
        val result = WorkoutValidation.validateTemplate(name = "  ", exerciseCount = 1, plannedSets = listOf(3))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("name", ignoreCase = true) })
    }

    @Test
    fun `no exercises fails`() {
        val result = WorkoutValidation.validateTemplate(name = "Empty", exerciseCount = 0, plannedSets = emptyList())
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("exercise", ignoreCase = true) })
    }

    @Test
    fun `planned sets below one fails`() {
        val result = WorkoutValidation.validateTemplate(name = "Bad", exerciseCount = 2, plannedSets = listOf(3, 0))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("set", ignoreCase = true) })
    }

    @Test
    fun `valid set passes`() {
        assertEquals(ValidationResult.Valid, WorkoutValidation.validateSet(reps = 10, weightKg = 60.0, rir = 2))
    }

    @Test
    fun `bodyweight set with null weight passes`() {
        assertEquals(ValidationResult.Valid, WorkoutValidation.validateSet(reps = 12, weightKg = null, rir = null))
    }

    @Test
    fun `negative reps fails`() {
        val result = WorkoutValidation.validateSet(reps = -1, weightKg = 60.0, rir = null)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `negative weight fails`() {
        val result = WorkoutValidation.validateSet(reps = 5, weightKg = -10.0, rir = null)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `rir out of range fails`() {
        val result = WorkoutValidation.validateSet(reps = 5, weightKg = 60.0, rir = 11)
        assertTrue(result is ValidationResult.Invalid)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.WorkoutValidationTest"`
Expected: FAIL — `WorkoutValidation` / `ValidationResult` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.domain.workout

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reasons: List<String>) : ValidationResult
}

object WorkoutValidation {

    fun validateTemplate(name: String, exerciseCount: Int, plannedSets: List<Int>): ValidationResult {
        val reasons = buildList {
            if (name.isBlank()) add("Workout name must not be blank.")
            if (exerciseCount < 1) add("A workout must contain at least one exercise.")
            if (plannedSets.any { it < 1 }) add("Each exercise must have at least one planned set.")
        }
        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    fun validateSet(reps: Int, weightKg: Double?, rir: Int?): ValidationResult {
        val reasons = buildList {
            if (reps < 0) add("Reps must not be negative.")
            if (weightKg != null && weightKg < 0.0) add("Weight must not be negative.")
            if (rir != null && rir !in 0..10) add("RIR must be between 0 and 10.")
        }
        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.WorkoutValidationTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutValidation.kt \
        app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutValidationTest.kt
git commit -m "feat(workout): add workout/set validation"
```

---

## Task 3: Workout progress analyzer (pure domain)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutProgressAnalyzer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutProgressAnalyzerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutProgressAnalyzerTest {

    private fun set(reps: Int, weight: Double?, completed: Boolean = true, n: Int = 1) =
        SessionSet(id = 0, setNumber = n, reps = reps, weightKg = weight, rir = null, completed = completed)

    @Test
    fun `set volume multiplies reps and weight`() {
        assertEquals(600.0, WorkoutProgressAnalyzer.setVolume(reps = 10, weightKg = 60.0), 0.0001)
    }

    @Test
    fun `set volume treats null weight as zero`() {
        assertEquals(0.0, WorkoutProgressAnalyzer.setVolume(reps = 12, weightKg = null), 0.0001)
    }

    @Test
    fun `estimated one rep max uses Epley`() {
        // 100 * (1 + 5/30) = 116.6667
        assertEquals(116.6667, WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 5, weightKg = 100.0)!!, 0.001)
    }

    @Test
    fun `estimated one rep max is null for bodyweight`() {
        assertNull(WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 5, weightKg = null))
    }

    @Test
    fun `estimated one rep max is null for zero reps`() {
        assertNull(WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 0, weightKg = 100.0))
    }

    @Test
    fun `session volume sums only completed sets`() {
        val sets = listOf(set(10, 60.0), set(8, 70.0), set(5, 80.0, completed = false))
        // 600 + 560 = 1160 (third set not completed)
        assertEquals(1160.0, WorkoutProgressAnalyzer.sessionVolume(sets), 0.0001)
    }

    @Test
    fun `best set picks highest estimated one rep max`() {
        val sets = listOf(set(10, 60.0, n = 1), set(3, 90.0, n = 2), set(8, 70.0, n = 3))
        // 1RMs: 60*1.333=80, 90*1.1=99, 70*1.267=88.7 -> set 2 wins
        assertEquals(2, WorkoutProgressAnalyzer.bestSet(sets)!!.setNumber)
    }

    @Test
    fun `best set is null when no weighted completed sets`() {
        assertNull(WorkoutProgressAnalyzer.bestSet(listOf(set(12, null), set(5, 80.0, completed = false))))
    }

    @Test
    fun `trend points aggregate per date`() {
        val history = listOf(
            ExerciseHistoryPoint(date = "2026-06-10", reps = 10, weightKg = 60.0, rir = 2),
            ExerciseHistoryPoint(date = "2026-06-10", reps = 8, weightKg = 70.0, rir = 1),
            ExerciseHistoryPoint(date = "2026-06-17", reps = 5, weightKg = 90.0, rir = 0),
        )
        val trend = WorkoutProgressAnalyzer.trendPoints(history)

        assertEquals(2, trend.size)
        assertEquals("2026-06-10", trend[0].date)
        assertEquals(1160.0, trend[0].totalVolume, 0.0001) // 600 + 560
        assertEquals("2026-06-17", trend[1].date)
        assertEquals(90.0 * (1 + 5 / 30.0), trend[1].bestEstimatedOneRepMax!!, 0.001)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzerTest"`
Expected: FAIL — `WorkoutProgressAnalyzer` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.domain.workout

object WorkoutProgressAnalyzer {

    fun setVolume(reps: Int, weightKg: Double?): Double = reps * (weightKg ?: 0.0)

    /** Epley formula. Null for bodyweight (no weight) or non-positive reps. */
    fun estimatedOneRepMax(reps: Int, weightKg: Double?): Double? {
        if (weightKg == null || reps <= 0) return null
        return weightKg * (1.0 + reps / 30.0)
    }

    fun sessionVolume(sets: List<SessionSet>): Double =
        sets.filter { it.completed }.sumOf { setVolume(it.reps, it.weightKg) }

    /** Completed, weighted set with the highest estimated 1RM. */
    fun bestSet(sets: List<SessionSet>): SessionSet? =
        sets.filter { it.completed }
            .mapNotNull { s -> estimatedOneRepMax(s.reps, s.weightKg)?.let { s to it } }
            .maxByOrNull { it.second }
            ?.first

    /** One [ExerciseTrendPoint] per date, ascending. */
    fun trendPoints(history: List<ExerciseHistoryPoint>): List<ExerciseTrendPoint> =
        history.groupBy { it.date }
            .toSortedMap()
            .map { (date, points) ->
                ExerciseTrendPoint(
                    date = date,
                    totalVolume = points.sumOf { setVolume(it.reps, it.weightKg) },
                    bestEstimatedOneRepMax = points.mapNotNull { estimatedOneRepMax(it.reps, it.weightKg) }.maxOrNull(),
                )
            }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzerTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/WorkoutProgressAnalyzer.kt \
        app/src/test/java/com/zack/recomptracker/domain/workout/WorkoutProgressAnalyzerTest.kt
git commit -m "feat(workout): add progress analyzer (volume, est 1RM, trends)"
```

---

## Task 4: Entities, relation POJOs, DAOs, DB migration v10

**Files:**
- Create: `WorkoutEntity.kt`, `WorkoutExerciseEntity.kt`, `WorkoutSessionEntity.kt`, `SessionExerciseEntity.kt`, `SessionSetEntity.kt`, `WorkoutWithExercisesDb.kt`, `WorkoutSessionWithDetailsDb.kt` (all in `data/local/entity/`)
- Create: `ExerciseDao.kt`, `WorkoutDao.kt`, `WorkoutSessionDao.kt` (in `data/local/dao/`)
- Modify: `data/local/RecompDatabase.kt`
- Test: `app/src/androidTest/java/com/zack/recomptracker/data/WorkoutDatabaseTest.kt`

- [ ] **Step 1: Create the five remaining entities**

`WorkoutEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
)
```

`WorkoutExerciseEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val plannedSets: Int,
    val targetReps: Int?,
    val sortOrder: Int,
    val note: String?,
)
```

`WorkoutSessionEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("workoutId"), Index("date"), Index("status")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long?,
    val workoutName: String,
    val date: String,
    val startedAt: String,
    val completedAt: String?,
    val status: String,
    val note: String?,
)
```

`SessionExerciseEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val sortOrder: Int,
    val note: String?,
)
```

`SessionSetEntity.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
    val completed: Boolean = true,
)
```

- [ ] **Step 2: Create relation POJOs**

`WorkoutWithExercisesDb.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class WorkoutWithExercisesDb(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val exercises: List<WorkoutExerciseWithExercise>,
)
```

`WorkoutSessionWithDetailsDb.kt`:

```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SessionSetEntity>,
)

data class WorkoutSessionWithDetailsDb(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<SessionExerciseWithSets>,
)

/** Flat projection for AI exercise-history queries. */
data class ExerciseHistoryRow(
    val date: String,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
)
```

- [ ] **Step 3: Create `ExerciseDao.kt`**

```kotlin
package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun search(query: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT sourceVersion FROM exercises WHERE source = :source LIMIT 1")
    suspend fun sourceVersion(source: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE source = :source")
    suspend fun deleteBySource(source: String)
}
```

- [ ] **Step 4: Create `WorkoutDao.kt`**

```kotlin
package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updatedAt DESC")
    abstract fun observeAllWithExercises(): Flow<List<WorkoutWithExercisesDb>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun getWithExercises(id: Long): WorkoutWithExercisesDb?

    @Insert
    abstract suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    abstract suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    abstract suspend fun deleteWorkoutById(id: Long)

    @Insert
    abstract suspend fun insertWorkoutExercise(line: WorkoutExerciseEntity): Long

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    abstract suspend fun deleteExercisesByWorkoutId(workoutId: Long)

    @Transaction
    open suspend fun replaceExercises(workoutId: Long, lines: List<WorkoutExerciseEntity>) {
        deleteExercisesByWorkoutId(workoutId)
        lines.forEachIndexed { index, line ->
            insertWorkoutExercise(line.copy(workoutId = workoutId, sortOrder = index, id = 0))
        }
    }
}
```

- [ ] **Step 5: Create `WorkoutSessionDao.kt`**

```kotlin
package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.ExerciseHistoryRow
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutSessionDao {

    @Insert
    abstract suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    abstract suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun deleteSessionById(id: Long)

    @Insert
    abstract suspend fun insertSessionExercise(exercise: SessionExerciseEntity): Long

    @Insert
    abstract suspend fun insertSet(set: SessionSetEntity): Long

    @Update
    abstract suspend fun updateSet(set: SessionSetEntity)

    @Query("DELETE FROM session_sets WHERE id = :id")
    abstract suspend fun deleteSetById(id: Long)

    @Query("SELECT COUNT(*) FROM session_sets WHERE sessionExerciseId = :sessionExerciseId")
    abstract suspend fun getSessionExerciseSetCount(sessionExerciseId: Long): Int

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getSessionWithDetails(id: Long): WorkoutSessionWithDetailsDb?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    abstract fun observeActiveSession(): Flow<WorkoutSessionWithDetailsDb?>

    @Transaction
    @Query(
        "SELECT * FROM workout_sessions WHERE workoutId = :workoutId AND status = 'COMPLETED' " +
            "ORDER BY date DESC, completedAt DESC LIMIT 1",
    )
    abstract suspend fun getLastCompletedSession(workoutId: Long): WorkoutSessionWithDetailsDb?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY date DESC, completedAt DESC")
    abstract fun observeCompletedSessions(): Flow<List<WorkoutSessionWithDetailsDb>>

    @Query(
        "SELECT s.date AS date, st.reps AS reps, st.weightKg AS weightKg, st.rir AS rir " +
            "FROM session_sets st " +
            "JOIN session_exercises se ON st.sessionExerciseId = se.id " +
            "JOIN workout_sessions s ON se.sessionId = s.id " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND st.completed = 1 " +
            "ORDER BY s.date",
    )
    abstract suspend fun getExerciseHistory(exerciseId: Long): List<ExerciseHistoryRow>
}
```

- [ ] **Step 6: Register entities, DAOs, and migration in `RecompDatabase.kt`**

Add imports (alongside existing entity/dao imports):

```kotlin
import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
```

In the `@Database(entities = [...])` list add the six entities and bump `version = 9` to `version = 10`:

```kotlin
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
```

Add the abstract DAO accessors (after `recipeDao()`):

```kotlin
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
```

Add `MIGRATION_9_10` inside `companion object` (next to the other migrations):

```kotlin
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "source TEXT NOT NULL, sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, " +
                        "name TEXT NOT NULL, category TEXT, force TEXT, level TEXT, mechanic TEXT, equipment TEXT, " +
                        "primaryMuscles TEXT NOT NULL, secondaryMuscles TEXT NOT NULL, instructions TEXT NOT NULL, " +
                        "images TEXT NOT NULL, userCreated INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_source_externalId ON exercises (source, externalId)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_name ON exercises (name)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workouts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, note TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, " +
                        "plannedSets INTEGER NOT NULL, targetReps INTEGER, sortOrder INTEGER NOT NULL, note TEXT, " +
                        "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises (workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_exerciseId ON workout_exercises (exerciseId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "workoutId INTEGER, workoutName TEXT NOT NULL, date TEXT NOT NULL, " +
                        "startedAt TEXT NOT NULL, completedAt TEXT, status TEXT NOT NULL, note TEXT, " +
                        "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE SET NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions (workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_date ON workout_sessions (date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_status ON workout_sessions (status)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, exerciseName TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, note TEXT, " +
                        "FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_exercises_sessionId ON session_exercises (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_exercises_exerciseId ON session_exercises (exerciseId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_sets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionExerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, reps INTEGER NOT NULL, " +
                        "weightKg REAL, rir INTEGER, completed INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(sessionExerciseId) REFERENCES session_exercises(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_sets_sessionExerciseId ON session_sets (sessionExerciseId)")
            }
        }
```

Append it to the builder's `addMigrations(...)` call:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
```

> **Note on FK column types:** Room generates `weightKg`/`completed` etc. to match the entity. `completed` is a `Boolean` → SQLite `INTEGER`. Keep `DEFAULT 1`. The schema strings above match the entity definitions exactly so Room's runtime schema validation passes.

- [ ] **Step 7: Verify the code compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room annotation processing generates the DAO impls without schema errors).

- [ ] **Step 8: Write the instrumented DB test** `WorkoutDatabaseTest.kt`

```kotlin
package com.zack.recomptracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WorkoutDatabaseTest {
    private lateinit var database: RecompDatabase

    private fun exercise(externalId: String, name: String) = ExerciseEntity(
        source = "test", sourceVersion = "v1", externalId = externalId, name = name,
        category = "strength", force = null, level = "beginner", mechanic = null, equipment = null,
        primaryMuscles = "[]", secondaryMuscles = "[]", instructions = "[]", images = "[]",
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecompDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun workoutTemplateWithExercisesRoundTrips() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val workoutDao = database.workoutDao()
        exerciseDao.insertAll(listOf(exercise("Squat", "Squat"), exercise("Bench", "Bench Press")))
        val squatId = exerciseDao.search("Squat").first().id
        val benchId = exerciseDao.search("Bench").first().id

        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Day A", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )
        workoutDao.replaceExercises(
            workoutId,
            listOf(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = squatId, plannedSets = 3, targetReps = 5, sortOrder = 0, note = null),
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = benchId, plannedSets = 4, targetReps = 8, sortOrder = 1, note = null),
            ),
        )

        val loaded = workoutDao.getWithExercises(workoutId)!!
        assertEquals(2, loaded.exercises.size)
        assertEquals("Squat", loaded.exercises.first { it.workoutExercise.sortOrder == 0 }.exercise.name)
    }

    @Test
    fun lastCompletedSessionReturnsMostRecentAndHistoryIsFlat() = runBlocking {
        val exerciseDao = database.exerciseDao()
        val sessionDao = database.workoutSessionDao()
        val workoutDao = database.workoutDao()
        exerciseDao.insertAll(listOf(exercise("Squat", "Squat")))
        val squatId = exerciseDao.search("Squat").first().id
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Legs", note = null, createdAt = "2026-06-01T10:00", updatedAt = "2026-06-01T10:00"),
        )

        suspend fun logSession(date: String, weight: Double) {
            val sessionId = sessionDao.insertSession(
                WorkoutSessionEntity(
                    workoutId = workoutId, workoutName = "Legs", date = date,
                    startedAt = date + "T10:00", completedAt = date + "T11:00",
                    status = "COMPLETED", note = null,
                ),
            )
            val seId = sessionDao.insertSessionExercise(
                SessionExerciseEntity(sessionId = sessionId, exerciseId = squatId, exerciseName = "Squat", sortOrder = 0, note = null),
            )
            sessionDao.insertSet(SessionSetEntity(sessionExerciseId = seId, setNumber = 1, reps = 5, weightKg = weight, rir = 2, completed = true))
        }

        logSession("2026-06-10", 100.0)
        logSession("2026-06-17", 110.0)

        val last = sessionDao.getLastCompletedSession(workoutId)!!
        assertEquals("2026-06-17", last.session.date)
        assertEquals(110.0, last.exercises.first().sets.first().weightKg!!, 0.0001)

        val history = sessionDao.getExerciseHistory(squatId)
        assertEquals(2, history.size)
        assertEquals("2026-06-10", history[0].date) // ordered by date asc
        assertEquals(110.0, history[1].weightKg!!, 0.0001)
    }

    @Test
    fun deletingWorkoutNullsSessionLinkButKeepsHistory() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        val workoutDao = database.workoutDao()
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "Temp", note = null, createdAt = "2026-06-17T10:00", updatedAt = "2026-06-17T10:00"),
        )
        val sessionId = sessionDao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId, workoutName = "Temp", date = "2026-06-17",
                startedAt = "2026-06-17T10:00", completedAt = "2026-06-17T11:00", status = "COMPLETED", note = null,
            ),
        )

        workoutDao.deleteWorkoutById(workoutId)

        val session = sessionDao.getSessionWithDetails(sessionId)!!
        assertNull(session.session.workoutId)
        assertEquals("Temp", session.session.workoutName) // snapshot survives
    }
}
```

- [ ] **Step 9: Compile the instrumented test (and run it if a device/emulator is available)**

Compile-check: `./gradlew :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

If an emulator/device is connected: `./gradlew :app:connectedDebugAndroidTest --tests "com.zack.recomptracker.data.WorkoutDatabaseTest"`
Expected: 3 tests PASS. (If no device is available, note this in the commit and ensure it runs in CI.)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/entity/WorkoutEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/WorkoutExerciseEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/WorkoutSessionEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/SessionExerciseEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/SessionSetEntity.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/WorkoutWithExercisesDb.kt \
        app/src/main/java/com/zack/recomptracker/data/local/entity/WorkoutSessionWithDetailsDb.kt \
        app/src/main/java/com/zack/recomptracker/data/local/dao/ExerciseDao.kt \
        app/src/main/java/com/zack/recomptracker/data/local/dao/WorkoutDao.kt \
        app/src/main/java/com/zack/recomptracker/data/local/dao/WorkoutSessionDao.kt \
        app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt \
        app/src/androidTest/java/com/zack/recomptracker/data/WorkoutDatabaseTest.kt
git commit -m "feat(workout): add Room entities, DAOs, relations, MIGRATION_9_10"
```

---

## Task 5: Entity→domain mappers + ExerciseLibraryRepository + seeded asset

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/WorkoutMappers.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepository.kt`
- Create asset: `app/src/main/assets/exercises/exercises.json`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepositoryTest.kt`

- [ ] **Step 1: Create `WorkoutMappers.kt`** (entity→domain; reused by Tasks 6–7)

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseWithSets
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseWithExercise
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise

internal fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    externalId = externalId,
    name = name,
    category = category,
    force = force,
    level = level,
    mechanic = mechanic,
    equipment = equipment,
    primaryMuscles = ExerciseLibraryJson.decodeList(primaryMuscles),
    secondaryMuscles = ExerciseLibraryJson.decodeList(secondaryMuscles),
    instructions = ExerciseLibraryJson.decodeList(instructions),
    images = ExerciseLibraryJson.decodeList(images),
    userCreated = userCreated,
)

internal fun WorkoutWithExercisesDb.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = workout.id,
    name = workout.name,
    note = workout.note,
    createdAt = workout.createdAt,
    updatedAt = workout.updatedAt,
    exercises = exercises.sortedBy { it.workoutExercise.sortOrder }.map { it.toDomain() },
)

internal fun WorkoutExerciseWithExercise.toDomain(): WorkoutTemplateExercise = WorkoutTemplateExercise(
    id = workoutExercise.id,
    exercise = exercise.toDomain(),
    plannedSets = workoutExercise.plannedSets,
    targetReps = workoutExercise.targetReps,
    sortOrder = workoutExercise.sortOrder,
    note = workoutExercise.note,
)

internal fun SessionSetEntity.toDomain(): SessionSet = SessionSet(
    id = id,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    rir = rir,
    completed = completed,
)

internal fun SessionExerciseWithSets.toDomain(): SessionExercise = SessionExercise(
    id = sessionExercise.id,
    exerciseId = sessionExercise.exerciseId,
    exerciseName = sessionExercise.exerciseName,
    sortOrder = sessionExercise.sortOrder,
    note = sessionExercise.note,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
)

internal fun WorkoutSessionWithDetailsDb.toDomain(): WorkoutSession = WorkoutSession(
    id = session.id,
    workoutId = session.workoutId,
    workoutName = session.workoutName,
    date = session.date,
    startedAt = session.startedAt,
    completedAt = session.completedAt,
    status = runCatching { SessionStatus.valueOf(session.status) }.getOrDefault(SessionStatus.ACTIVE),
    note = session.note,
    exercises = exercises.sortedBy { it.sessionExercise.sortOrder }.map { it.toDomain() },
)
```

- [ ] **Step 2: Write the failing test** `ExerciseLibraryRepositoryTest.kt`

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLibraryRepositoryTest {

    private class FakeExerciseDao : ExerciseDao {
        val rows = mutableListOf<ExerciseEntity>()
        var insertCalls = 0
        override fun observeAll(): Flow<List<ExerciseEntity>> = flowOf(rows)
        override suspend fun search(query: String): List<ExerciseEntity> =
            rows.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getById(id: Long): ExerciseEntity? = rows.firstOrNull { it.id == id }
        override suspend fun count(): Int = rows.size
        override suspend fun sourceVersion(source: String): String? =
            rows.firstOrNull { it.source == source }?.sourceVersion
        override suspend fun insertAll(exercises: List<ExerciseEntity>) {
            insertCalls++
            var nextId = (rows.maxOfOrNull { it.id } ?: 0L)
            rows.addAll(exercises.map { it.copy(id = ++nextId) })
        }
        override suspend fun deleteBySource(source: String) {
            rows.removeAll { it.source == source }
        }
    }

    private val sampleJson = """
        [{"id":"Squat","name":"Barbell Squat","level":"intermediate","category":"strength",
          "primaryMuscles":["quadriceps"],"secondaryMuscles":[],"instructions":["Go."],"images":[]}]
    """.trimIndent()

    @Test
    fun `seedIfEmpty inserts when library empty`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)

        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        assertEquals(1, dao.rows.size)
        assertEquals("Barbell Squat", dao.rows.first().name)
    }

    @Test
    fun `seedIfEmpty is a no-op when same version already present`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }
        val callsAfterFirst = dao.insertCalls

        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        assertEquals(callsAfterFirst, dao.insertCalls) // not re-seeded
    }

    @Test
    fun `seedIfEmpty re-seeds when version changes`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        repo.seedIfEmpty(version = "v2") { sampleJson.byteInputStream() }

        assertEquals(1, dao.rows.size) // replaced, not duplicated
        assertEquals("v2", dao.rows.first().sourceVersion)
    }

    @Test
    fun `search maps to domain with decoded lists`() = runTest {
        val dao = FakeExerciseDao()
        val repo = ExerciseLibraryRepository(dao)
        repo.seedIfEmpty(version = "v1") { sampleJson.byteInputStream() }

        val results = repo.search("squat")

        assertEquals(1, results.size)
        assertEquals(listOf("quadriceps"), results.first().primaryMuscles)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.ExerciseLibraryRepositoryTest"`
Expected: FAIL — `ExerciseLibraryRepository` unresolved.

- [ ] **Step 4: Create `ExerciseLibraryRepository.kt`**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.toEntity
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class ExerciseLibraryRepository(private val exerciseDao: ExerciseDao) {

    open fun observeAll(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    open suspend fun search(query: String): List<Exercise> =
        exerciseDao.search(query.trim()).map { it.toDomain() }

    open suspend fun getById(id: Long): Exercise? = exerciseDao.getById(id)?.toDomain()

    /**
     * Seeds the library from a free-exercise-db JSON stream the first time, or when [version]
     * differs from what is stored. Idempotent otherwise. [openStream] is invoked only when a
     * (re)seed is actually needed.
     */
    open suspend fun seedIfEmpty(version: String, openStream: () -> InputStream) {
        val storedVersion = exerciseDao.sourceVersion(SOURCE)
        if (storedVersion == version && exerciseDao.count() > 0) return

        val raw = openStream().bufferedReader().use { it.readText() }
        val entities = ExerciseLibraryJson.parse(raw).map { it.toEntity(SOURCE, version) }
        exerciseDao.deleteBySource(SOURCE)
        exerciseDao.insertAll(entities)
    }

    companion object {
        const val SOURCE = "free-exercise-db"
        /** Bump when the bundled exercises.json is refreshed to trigger a re-seed. */
        const val VERSION = "2026-06-17"
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.ExerciseLibraryRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Download the bundled exercise library asset**

```bash
mkdir -p "app/src/main/assets/exercises"
curl -fsSL https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json \
  -o "app/src/main/assets/exercises/exercises.json"
```

Verify it parses as a non-empty JSON array:

```bash
test -s "app/src/main/assets/exercises/exercises.json" && \
  head -c 1 "app/src/main/assets/exercises/exercises.json"
```

Expected: prints `[` (a JSON array). If the download fails, the seeding is guarded by `runCatching` in Task 7 wiring and the app still runs with an empty library — but the asset SHOULD be present for the feature to work.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WorkoutMappers.kt \
        app/src/main/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepository.kt \
        app/src/main/assets/exercises/exercises.json \
        app/src/test/java/com/zack/recomptracker/data/repository/ExerciseLibraryRepositoryTest.kt
git commit -m "feat(workout): add exercise library repository + seeded free-exercise-db asset"
```

---

## Task 6: WorkoutRepository (template CRUD)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/WorkoutRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/WorkoutRepositoryTest.kt`

**Interface contract:** `WorkoutRepository.saveWorkout` takes the workout name, optional note, and a list of `NewWorkoutLine` (a small input type, defined below, decoupling callers from Room entities). It validates via `WorkoutValidation`, inserts the workout, then `replaceExercises`.

- [ ] **Step 1: Write the failing test** `WorkoutRepositoryTest.kt`

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseWithExercise
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRepositoryTest {

    private fun exerciseEntity(id: Long, name: String) = ExerciseEntity(
        id = id, source = "test", sourceVersion = "v1", externalId = name, name = name,
        category = null, force = null, level = null, mechanic = null, equipment = null,
        primaryMuscles = "[]", secondaryMuscles = "[]", instructions = "[]", images = "[]",
    )

    private class FakeWorkoutDao(
        private val library: Map<Long, ExerciseEntity>,
    ) : WorkoutDao() {
        val workouts = mutableMapOf<Long, WorkoutEntity>()
        val lines = mutableListOf<WorkoutExerciseEntity>()
        private var nextWorkoutId = 0L
        private var nextLineId = 0L

        override fun observeAllWithExercises(): Flow<List<WorkoutWithExercisesDb>> = flowOf(snapshot())
        override suspend fun getWithExercises(id: Long): WorkoutWithExercisesDb? =
            workouts[id]?.let { buildDb(it) }
        override suspend fun insertWorkout(workout: WorkoutEntity): Long {
            val id = ++nextWorkoutId
            workouts[id] = workout.copy(id = id)
            return id
        }
        override suspend fun updateWorkout(workout: WorkoutEntity) { workouts[workout.id] = workout }
        override suspend fun deleteWorkoutById(id: Long) { workouts.remove(id); lines.removeAll { it.workoutId == id } }
        override suspend fun insertWorkoutExercise(line: WorkoutExerciseEntity): Long {
            val id = ++nextLineId
            lines.add(line.copy(id = id))
            return id
        }
        override suspend fun deleteExercisesByWorkoutId(workoutId: Long) { lines.removeAll { it.workoutId == workoutId } }

        private fun buildDb(w: WorkoutEntity) = WorkoutWithExercisesDb(
            workout = w,
            exercises = lines.filter { it.workoutId == w.id }.map { line ->
                WorkoutExerciseWithExercise(line, library.getValue(line.exerciseId))
            },
        )
        private fun snapshot() = workouts.values.map { buildDb(it) }
    }

    private fun repo(): Pair<WorkoutRepository, FakeWorkoutDao> {
        val library = mapOf(1L to exerciseEntity(1, "Squat"), 2L to exerciseEntity(2, "Bench"))
        val dao = FakeWorkoutDao(library)
        return WorkoutRepository(dao) { "2026-06-17T10:00" } to dao
    }

    @Test
    fun `saveWorkout persists workout and ordered exercises`() = runTest {
        val (repo, dao) = repo()

        val id = repo.saveWorkout(
            name = "Day A",
            note = null,
            lines = listOf(
                NewWorkoutLine(exerciseId = 2, plannedSets = 4, targetReps = 8),
                NewWorkoutLine(exerciseId = 1, plannedSets = 3, targetReps = 5),
            ),
        )

        val loaded = repo.getById(id)!!
        assertEquals("Day A", loaded.name)
        assertEquals(listOf(0, 1), loaded.exercises.map { it.sortOrder })
        assertEquals("Bench", loaded.exercises[0].exercise.name)
        assertEquals(4, loaded.exercises[0].plannedSets)
    }

    @Test
    fun `saveWorkout rejects blank name`() = runTest {
        val (repo, _) = repo()
        val ex = runCatching {
            repo.saveWorkout(name = "  ", note = null, lines = listOf(NewWorkoutLine(1, 3, 5)))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `saveWorkout rejects empty exercise list`() = runTest {
        val (repo, _) = repo()
        val ex = runCatching {
            repo.saveWorkout(name = "Empty", note = null, lines = emptyList())
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `deleteWorkout removes it`() = runTest {
        val (repo, dao) = repo()
        val id = repo.saveWorkout("Day A", null, listOf(NewWorkoutLine(1, 3, 5)))

        repo.deleteWorkout(id)

        assertTrue(dao.workouts.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutRepositoryTest"`
Expected: FAIL — `WorkoutRepository` / `NewWorkoutLine` unresolved.

- [ ] **Step 3: Create `WorkoutRepository.kt`**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.domain.workout.ValidationResult
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutValidation
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Caller-facing template line, decoupled from Room entities. */
data class NewWorkoutLine(
    val exerciseId: Long,
    val plannedSets: Int,
    val targetReps: Int? = null,
    val note: String? = null,
)

open class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val now: () -> String = { Instant.now().toString() },
) {

    open fun observeAll(): Flow<List<WorkoutTemplate>> =
        workoutDao.observeAllWithExercises().map { list -> list.map { it.toDomain() } }

    open suspend fun getById(id: Long): WorkoutTemplate? =
        workoutDao.getWithExercises(id)?.toDomain()

    open suspend fun saveWorkout(name: String, note: String?, lines: List<NewWorkoutLine>): Long {
        validate(name, lines)
        val timestamp = now()
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = name.trim(), note = note?.trim(), createdAt = timestamp, updatedAt = timestamp),
        )
        workoutDao.replaceExercises(workoutId, lines.toEntities(workoutId))
        return workoutId
    }

    open suspend fun updateWorkout(workoutId: Long, name: String, note: String?, lines: List<NewWorkoutLine>) {
        validate(name, lines)
        val existing = workoutDao.getWithExercises(workoutId)?.workout
        val createdAt = existing?.createdAt ?: now()
        workoutDao.updateWorkout(
            WorkoutEntity(id = workoutId, name = name.trim(), note = note?.trim(), createdAt = createdAt, updatedAt = now()),
        )
        workoutDao.replaceExercises(workoutId, lines.toEntities(workoutId))
    }

    open suspend fun deleteWorkout(workoutId: Long) = workoutDao.deleteWorkoutById(workoutId)

    private fun validate(name: String, lines: List<NewWorkoutLine>) {
        val result = WorkoutValidation.validateTemplate(
            name = name,
            exerciseCount = lines.size,
            plannedSets = lines.map { it.plannedSets },
        )
        if (result is ValidationResult.Invalid) {
            throw IllegalArgumentException(result.reasons.joinToString(" "))
        }
    }

    private fun List<NewWorkoutLine>.toEntities(workoutId: Long): List<WorkoutExerciseEntity> =
        mapIndexed { index, line ->
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = line.exerciseId,
                plannedSets = line.plannedSets,
                targetReps = line.targetReps,
                sortOrder = index,
                note = line.note,
            )
        }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WorkoutRepository.kt \
        app/src/test/java/com/zack/recomptracker/data/repository/WorkoutRepositoryTest.kt
git commit -m "feat(workout): add workout template repository"
```

---

## Task 7: WorkoutSessionRepository (active session lifecycle + history)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/WorkoutSessionRepositoryTest.kt`

**Behavior:**
- `startSession(template)` creates a `workout_sessions` row (`status=ACTIVE`, `startedAt`/`date` from the injected clock, `workoutName` snapshot) and one `session_exercises` row per template exercise (snapshotting `exerciseName`, ordered). Returns the new session id. No sets yet.
- `addSet(sessionExerciseId, reps, weightKg, rir)` validates via `WorkoutValidation.validateSet`, computes the next `setNumber`, inserts a `session_sets` row, returns its id.
- `updateSet` / `removeSet` edit/delete a set row.
- `completeSession(sessionId)` sets `status=COMPLETED`, `completedAt=now`.
- `abandonSession(sessionId)` sets `status=ABANDONED`.
- `getLastCompletedSession(workoutId)`, `observeActiveSession()`, `getExerciseHistory(exerciseId)` (returns `List<ExerciseHistoryPoint>`).

- [ ] **Step 1: Write the failing test** `WorkoutSessionRepositoryTest.kt`

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutSessionRepositoryTest"`
Expected: FAIL — `WorkoutSessionRepository` unresolved.

- [ ] **Step 3: Create `WorkoutSessionRepository.kt`**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
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
) {

    /** Creates an ACTIVE session snapshotting the template's name and exercises. */
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
            ),
        )
        template.exercises.sortedBy { it.sortOrder }.forEachIndexed { index, line ->
            sessionDao.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = line.exercise.id,
                    exerciseName = line.exercise.name,
                    sortOrder = index,
                    note = line.note,
                ),
            )
        }
        return sessionId
    }

    open suspend fun addSet(sessionExerciseId: Long, reps: Int, weightKg: Double?, rir: Int?): Long {
        val result = WorkoutValidation.validateSet(reps, weightKg, rir)
        if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reasons.joinToString(" "))

        val existing = sessionDao.getSessionExerciseSetCount(sessionExerciseId)
        return sessionDao.insertSet(
            SessionSetEntity(
                sessionExerciseId = sessionExerciseId,
                setNumber = existing + 1,
                reps = reps,
                weightKg = weightKg,
                rir = rir,
                completed = true,
            ),
        )
    }

    open suspend fun updateSet(set: SessionSetEntity) {
        val result = WorkoutValidation.validateSet(set.reps, set.weightKg, set.rir)
        if (result is ValidationResult.Invalid) throw IllegalArgumentException(result.reasons.joinToString(" "))
        sessionDao.updateSet(set)
    }

    open suspend fun removeSet(setId: Long) = sessionDao.deleteSetById(setId)

    open suspend fun completeSession(sessionId: Long) = setStatus(sessionId, SessionStatus.COMPLETED)

    open suspend fun abandonSession(sessionId: Long) = setStatus(sessionId, SessionStatus.ABANDONED)

    open fun observeActiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { it?.toDomain() }

    open suspend fun getLastCompletedSession(workoutId: Long): WorkoutSession? =
        sessionDao.getLastCompletedSession(workoutId)?.toDomain()

    open fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessionDao.observeCompletedSessions().map { list -> list.map { it.toDomain() } }

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
```

> **Note:** `addSet` calls `sessionDao.getSessionExerciseSetCount(...)`, which was already defined on `WorkoutSessionDao` in Task 4 and overridden in the `FakeSessionDao` above.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WorkoutSessionRepositoryTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt \
        app/src/test/java/com/zack/recomptracker/data/repository/WorkoutSessionRepositoryTest.kt
git commit -m "feat(workout): add workout session repository (lifecycle + history)"
```

---

## Task 8: Wire repositories into AppContainer + seed on startup

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add imports** (with the other `data.repository` imports near the top of `AppContainer.kt`)

```kotlin
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Construct the repositories** (immediately AFTER the `appScope` declaration — currently `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` — so `appScope` is initialized first). `WorkoutSessionRepository` defaults its `now`/`today` clocks to the system clock, which is correct for production, so construct it plainly:

```kotlin
    val exerciseLibraryRepository = ExerciseLibraryRepository(database.exerciseDao())
    val workoutRepository = WorkoutRepository(database.workoutDao())
    val workoutSessionRepository = WorkoutSessionRepository(database.workoutSessionDao())
```

- [ ] **Step 3: Seed the library on startup** — add an `init` block AFTER those properties (and after `appScope`), mirroring the defensive `runCatching` posture used for the knowledge corpus:

```kotlin
    init {
        appScope.launch {
            runCatching {
                exerciseLibraryRepository.seedIfEmpty(ExerciseLibraryRepository.VERSION) {
                    context.applicationContext.assets.open("exercises/exercises.json")
                }
            }.onFailure {
                Log.w("RecompWorkout", "Exercise library seed failed — library will be empty", it)
            }
        }
    }
```

> `context` is the `AppContainer` constructor parameter and is in scope. `Log` is already imported. Place this `init` block below the three repository `val`s so they are initialized before it runs.

- [ ] **Step 4: Verify the whole app compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing tests plus the new workout domain/repository tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(workout): wire workout repositories into AppContainer + seed library on startup"
```

---

## Final verification

- [ ] **Build the debug APK** to confirm assets + migration package correctly:

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **(If a device/emulator is available) run instrumented DB tests:**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.zack.recomptracker.data.WorkoutDatabaseTest"`
Expected: 3 tests PASS (validates `MIGRATION_9_10` schema matches entities, relations load, FK SET NULL/CASCADE behave).

- [ ] **Confirm scope:** no UI files touched; `lift_performance`, weekly review, and AI coordinators unchanged; six new tables; library seeded from bundled asset.

---

## Notes for the implementer

- **Strings, not enums, in the DB.** `WorkoutSessionEntity.status` is a `String`; the domain layer converts via `SessionStatus.valueOf(...)` (guarded). Room queries filter on the literal `'ACTIVE'`/`'COMPLETED'` — keep those literals in sync with the enum names.
- **Ordering is by stored column, not Room relation order.** `@Relation` lists are unordered; mappers sort by `sortOrder`/`setNumber`. Don't rely on insertion order.
- **Seeding is idempotent and version-gated.** Bumping `ExerciseLibraryRepository.VERSION` (when you refresh `exercises.json`) triggers a one-time replace on next launch.
- **`getExerciseHistory` is keyed on the local `exercises.id`** (stable within a device). For cross-device/AI identity, `exercises.externalId` is the durable key — expose it if a future sync needs it.
- **Out of scope (documented in the spec):** UI, image binaries, weekly-review/AI trend bridge, backup of the new tables, cardio/time-based sets.
```
