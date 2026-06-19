# Training Stats Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Stats" tab to the Training screen showing two interactive body figures and broad muscle-category browsing of logged exercises, with a full-screen exercise-detail view (Est. 1RM progress chart, PRs, recent sessions).

**Architecture:** Pure-Kotlin domain layer (category mapping, stats derivation) feeds a `TrainViewModel` extension (entry screen) and a new `ExerciseStatsViewModel` (detail screen). A new `BodyMap` composable renders the full front/back silhouettes from the existing `MuscleArt` data with multi-muscle highlight and tap hit-testing. All visuals use the existing glass component library and theme colors. No Room schema change — the entry screen derives from existing reactive flows; the detail screen uses the existing `getExerciseHistory(exerciseId)`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Coroutines/Flow, manual DI (`AppContainer`), Navigation Compose. JUnit for pure-Kotlin unit tests (`:app:testDebugUnitTest`).

**Hard constraint (from spec):** Match the app's system theme and reuse the existing component library — `FrostedCard`, `SectionLabel`, `VioletBadge`, `LiquidGlassButton`, colors via `LocalAppAccent` / `LocalAppColors`. No hard-coded colors, no one-off composables where an existing one fits. The browser wireframes from design were structural only.

---

## File Structure

**Domain (pure Kotlin — `domain/workout/`):**
- Create `MuscleCategory.kt` — the six broad categories + `muscleCategoryFor(primaryMuscles)`.
- Create `TrainStatsBuilder.kt` — groups logged exercises into categories from history + library.
- Create `ExerciseStatsCalculator.kt` — derives quick stats, PRs, chart series, frequency from history.

**UI components (`ui/train/component/`):**
- Create `BodyMapGeometry.kt` — pure fit-transform + inverse math (testable).
- Create `BodyMap.kt` — full front+back silhouette composable, multi-highlight, tap hit-testing.
- Create `MuscleCategoryHighlight.kt` — category → highlight slugs + slug → category reverse map.

**UI screens (`ui/train/`):**
- Modify `TrainViewModel.kt` — add `STATS` tab, inject `ExerciseLibraryRepository`, expose `statsCategories`.
- Modify `TrainHomeScreen.kt` — render Stats tab content; new `onOpenExerciseStats` param.
- Create `ExerciseStatsViewModel.kt` — loads + computes one exercise's stats.
- Create `ExerciseStatsScreen.kt` — full-screen detail UI.
- Create `ProgressLineChart.kt` (`ui/component/charts/`) — custom Canvas line chart.

**Wiring:**
- Modify `core/AppContainer.kt` — pass `exerciseLibraryRepository` to `TrainViewModel`; register `ExerciseStatsViewModel`.
- Modify `ui/navigation/AppNavGraph.kt` — `Routes.ExerciseStats` + composable; wire `onOpenExerciseStats`.

**Tests (`app/src/test/.../domain/workout/`):**
- `MuscleCategoryTest.kt`, `TrainStatsBuilderTest.kt`, `ExerciseStatsCalculatorTest.kt`, `BodyMapGeometryTest.kt`.

---

## Task 1: Muscle category enum + mapping

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/MuscleCategory.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/MuscleCategoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleCategoryTest {
    @Test fun mapsArmsFromBicepsAndTriceps() {
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Biceps")))
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Triceps")))
        assertEquals(MuscleCategory.ARMS, muscleCategoryFor(listOf("Forearms")))
    }

    @Test fun mapsBackFromLatsTrapsLowerBack() {
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Lats")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Middle Back")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Lower Back")))
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("Traps")))
    }

    @Test fun mapsLegsChestShouldersCore() {
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Quadriceps")))
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Hamstrings")))
        assertEquals(MuscleCategory.LEGS, muscleCategoryFor(listOf("Glutes")))
        assertEquals(MuscleCategory.CHEST, muscleCategoryFor(listOf("Chest")))
        assertEquals(MuscleCategory.SHOULDERS, muscleCategoryFor(listOf("Shoulders")))
        assertEquals(MuscleCategory.CORE, muscleCategoryFor(listOf("Abdominals")))
    }

    @Test fun unmappedOrEmptyIsNull() {
        assertNull(muscleCategoryFor(emptyList()))
        assertNull(muscleCategoryFor(listOf("Neck")))
        assertNull(muscleCategoryFor(listOf("Wings")))
    }

    @Test fun isCaseAndSpaceInsensitiveOnFirstEntry() {
        assertEquals(MuscleCategory.BACK, muscleCategoryFor(listOf("  middle back ", "biceps")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.MuscleCategoryTest"`
Expected: FAIL — `MuscleCategory` / `muscleCategoryFor` unresolved (compilation error).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.domain.workout

/**
 * The six broad, gym-style muscle groups the Stats screen browses by.
 * [displayName] is the user-facing label; [order] sets list order on screen.
 */
enum class MuscleCategory(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs"),
    CORE("Core"),
}

/**
 * Buckets a free-exercise-db `primaryMuscles` list into one broad [MuscleCategory] using the
 * first entry (case/space-insensitive). Returns null when empty or unmapped (e.g. neck-only) —
 * such exercises are omitted from the Stats list.
 */
fun muscleCategoryFor(primaryMuscles: List<String>): MuscleCategory? {
    val key = primaryMuscles.firstOrNull()?.trim()?.lowercase() ?: return null
    return when (key) {
        "chest" -> MuscleCategory.CHEST
        "shoulders" -> MuscleCategory.SHOULDERS
        "lats", "middle back", "lower back", "traps" -> MuscleCategory.BACK
        "biceps", "triceps", "forearms" -> MuscleCategory.ARMS
        "quadriceps", "hamstrings", "calves", "glutes", "abductors", "adductors" -> MuscleCategory.LEGS
        "abdominals" -> MuscleCategory.CORE
        else -> null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.MuscleCategoryTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/MuscleCategory.kt app/src/test/java/com/zack/recomptracker/domain/workout/MuscleCategoryTest.kt
git commit -m "feat(stats): muscle category enum + primaryMuscles mapping"
```

---

## Task 2: Stats entry builder (group logged exercises by category)

Groups the user's completed-session history into per-category exercise lists, using the exercise library for each exercise's `primaryMuscles`. Pure function — no Room, no Android.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/TrainStatsBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/TrainStatsBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainStatsBuilderTest {

    private fun exercise(id: Long, name: String, muscles: List<String>) = Exercise(
        id = id, externalId = "e$id", name = name, category = null, force = null, level = null,
        mechanic = null, equipment = null, primaryMuscles = muscles, secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun session(id: Long, date: String, exId: Long, exName: String, completed: Boolean) =
        WorkoutSession(
            id = id, workoutId = null, workoutName = "W", date = date, startedAt = "${date}T10:00",
            completedAt = "${date}T11:00", status = SessionStatus.COMPLETED, note = null, durationSeconds = null,
            exercises = listOf(
                SessionExercise(
                    id = id * 10, exerciseId = exId, exerciseName = exName, sortOrder = 0, note = null,
                    sets = listOf(SessionSet(id = id * 100, setNumber = 1, reps = 10, weightKg = 20.0, rir = null, completed = completed)),
                ),
            ),
        )

    @Test fun returnsAllSixCategoriesInFixedOrder() {
        val result = TrainStatsBuilder.build(emptyList(), emptyList())
        assertEquals(MuscleCategory.entries.toList(), result.map { it.category })
        assertTrue(result.all { it.exercises.isEmpty() })
    }

    @Test fun bucketsExerciseUnderItsCategoryWithCountAndLastDate() {
        val library = listOf(exercise(1, "Dumbbell Curl", listOf("Biceps")))
        val history = listOf(
            session(1, "2026-06-10", 1, "Dumbbell Curl", completed = true),
            session(2, "2026-06-17", 1, "Dumbbell Curl", completed = true),
        )
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertEquals(1, arms.exercises.size)
        val curl = arms.exercises.first()
        assertEquals("Dumbbell Curl", curl.name)
        assertEquals(2, curl.sessionCount)
        assertEquals("2026-06-17", curl.lastDate)
    }

    @Test fun ignoresExercisesWithNoCompletedSets() {
        val library = listOf(exercise(1, "Dumbbell Curl", listOf("Biceps")))
        val history = listOf(session(1, "2026-06-10", 1, "Dumbbell Curl", completed = false))
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertTrue(arms.exercises.isEmpty())
    }

    @Test fun dropsExercisesWithUnmappedOrMissingMuscles() {
        val library = listOf(exercise(1, "Neck Curl", listOf("Neck")))
        val history = listOf(session(1, "2026-06-10", 1, "Neck Curl", completed = true))
        val result = TrainStatsBuilder.build(history, library)
        assertTrue(result.all { it.exercises.isEmpty() })
    }

    @Test fun exercisesSortedByNameWithinCategory() {
        val library = listOf(
            exercise(1, "Triceps Pushdown", listOf("Triceps")),
            exercise(2, "Dumbbell Curl", listOf("Biceps")),
        )
        val history = listOf(
            session(1, "2026-06-10", 1, "Triceps Pushdown", completed = true),
            session(2, "2026-06-10", 2, "Dumbbell Curl", completed = true),
        )
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertEquals(listOf("Dumbbell Curl", "Triceps Pushdown"), arms.exercises.map { it.name })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.TrainStatsBuilderTest"`
Expected: FAIL — `TrainStatsBuilder` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.domain.workout

/**
 * Builds the Stats entry screen's data: the six muscle categories, each with the distinct
 * exercises the user has actually logged (≥1 completed set) that target that category.
 *
 * Pure: derives entirely from already-loaded domain data — completed-session [history] and the
 * exercise [library] (for each exercise's primaryMuscles). No Room / Android dependencies.
 */
object TrainStatsBuilder {

    /** One logged exercise within a category, with lightweight usage stats. */
    data class LoggedExerciseSummary(
        val exerciseId: Long,
        val name: String,
        val primaryMuscles: List<String>,
        val sessionCount: Int,
        val lastDate: String?,
    )

    /** A category and the logged exercises under it (possibly empty). */
    data class CategoryStats(
        val category: MuscleCategory,
        val exercises: List<LoggedExerciseSummary>,
    )

    fun build(history: List<WorkoutSession>, library: List<Exercise>): List<CategoryStats> {
        val musclesByExerciseId: Map<Long, List<String>> = library.associate { it.id to it.primaryMuscles }

        // exerciseId -> (name, set of session dates) for exercises with ≥1 completed set.
        data class Acc(var name: String, val dates: MutableSet<String> = mutableSetOf())
        val acc = mutableMapOf<Long, Acc>()

        for (session in history) {
            for (ex in session.exercises) {
                val hasCompleted = ex.sets.any { it.completed }
                if (!hasCompleted) continue
                val a = acc.getOrPut(ex.exerciseId) { Acc(ex.exerciseName) }
                a.name = ex.exerciseName
                a.dates += session.date
            }
        }

        val summaries: List<Pair<MuscleCategory, LoggedExerciseSummary>> = acc.mapNotNull { (id, a) ->
            val muscles = musclesByExerciseId[id] ?: emptyList()
            val category = muscleCategoryFor(muscles) ?: return@mapNotNull null
            category to LoggedExerciseSummary(
                exerciseId = id,
                name = a.name,
                primaryMuscles = muscles,
                sessionCount = a.dates.size,
                lastDate = a.dates.maxOrNull(),
            )
        }

        val byCategory = summaries.groupBy({ it.first }, { it.second })
        return MuscleCategory.entries.map { category ->
            CategoryStats(
                category = category,
                exercises = byCategory[category].orEmpty().sortedBy { it.name.lowercase() },
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.TrainStatsBuilderTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/TrainStatsBuilder.kt app/src/test/java/com/zack/recomptracker/domain/workout/TrainStatsBuilderTest.kt
git commit -m "feat(stats): build logged-exercises-by-category from history"
```

---

## Task 3: Exercise stats calculator (detail screen data)

Derives everything the detail screen shows from one exercise's flat history.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/workout/ExerciseStatsCalculator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/ExerciseStatsCalculatorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStatsCalculatorTest {

    private fun p(date: String, reps: Int, weight: Double?) = ExerciseHistoryPoint(date, reps, weight, null)

    @Test fun emptyHistoryHasNoData() {
        val s = ExerciseStatsCalculator.calculate(emptyList())
        assertFalse(s.hasData)
        assertNull(s.bestOneRepMax)
        assertTrue(s.topSetSeries.isEmpty())
        assertTrue(s.recentSessions.isEmpty())
    }

    @Test fun heaviestSetAndMaxRepsAndLastDate() {
        val history = listOf(
            p("2026-06-10", 10, 12.0),
            p("2026-06-10", 8, 15.0),
            p("2026-06-17", 12, 14.0),
        )
        val s = ExerciseStatsCalculator.calculate(history)
        assertTrue(s.hasData)
        assertEquals(15.0, s.heaviestWeightKg!!, 0.0001)
        assertEquals(8, s.heaviestReps)
        assertEquals(12, s.maxReps)
        assertEquals("2026-06-17", s.lastPerformedDate)
    }

    @Test fun bestOneRepMaxUsesEpleyAcrossHistory() {
        // Epley: 15 * (1 + 8/30) = 19.0 ; 14 * (1 + 12/30) = 19.6
        val history = listOf(p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(19.6, s.bestOneRepMax!!, 0.01)
    }

    @Test fun topSetSeriesIsMaxWeightPerDayAscending() {
        val history = listOf(
            p("2026-06-17", 12, 14.0),
            p("2026-06-10", 10, 12.0),
            p("2026-06-10", 8, 15.0),
        )
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(listOf("2026-06-10", "2026-06-17"), s.topSetSeries.map { it.date })
        assertEquals(15.0, s.topSetSeries[0].value, 0.0001)
        assertEquals(14.0, s.topSetSeries[1].value, 0.0001)
    }

    @Test fun bestDayVolumeIsMaxOfPerDayVolume() {
        // day1 volume = 10*12 + 8*15 = 240 ; day2 = 12*14 = 168
        val history = listOf(p("2026-06-10", 10, 12.0), p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(240.0, s.bestDayVolume!!, 0.0001)
    }

    @Test fun frequencyIsSessionsPerWeekOverSpan() {
        // 3 distinct days across exactly 2 weeks (14 days) => 3 / 2 = 1.5/wk
        val history = listOf(p("2026-06-01", 5, 10.0), p("2026-06-08", 5, 10.0), p("2026-06-15", 5, 10.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(1.5, s.sessionsPerWeek!!, 0.01)
    }

    @Test fun frequencyWithinOneWeekCountsAsThatManyPerWeek() {
        val history = listOf(p("2026-06-01", 5, 10.0), p("2026-06-03", 5, 10.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(2.0, s.sessionsPerWeek!!, 0.01)
    }

    @Test fun recentSessionsNewestFirstWithVolume() {
        val history = listOf(p("2026-06-10", 10, 12.0), p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals("2026-06-17", s.recentSessions.first().date)
        assertEquals(2, s.recentSessions.first { it.date == "2026-06-10" }.sets.size)
        assertEquals(240.0, s.recentSessions.first { it.date == "2026-06-10" }.volume, 0.0001)
    }

    @Test fun bodyweightOnlyHistoryHasNullStrengthStatsButHasData() {
        val history = listOf(p("2026-06-10", 12, null), p("2026-06-17", 15, null))
        val s = ExerciseStatsCalculator.calculate(history)
        assertTrue(s.hasData)
        assertNull(s.bestOneRepMax)
        assertNull(s.heaviestWeightKg)
        assertEquals(15, s.maxReps)
        assertNotNull(s.sessionsPerWeek)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ExerciseStatsCalculatorTest"`
Expected: FAIL — `ExerciseStatsCalculator` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.domain.workout

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Derives all detail-screen stats for a single exercise from its flat [ExerciseHistoryPoint] list
 * (completed sets only, as returned by WorkoutSessionRepository.getExerciseHistory). Pure Kotlin.
 */
object ExerciseStatsCalculator {

    /** A dated scalar for a chart series (e.g. top-set weight or est. 1RM on a day). */
    data class DayValue(val date: String, val value: Double)

    /** One performed day: the sets done and the day's total volume. */
    data class DaySession(
        val date: String,
        val sets: List<ExerciseHistoryPoint>,
        val volume: Double,
    )

    data class ExerciseStats(
        val hasData: Boolean,
        val bestOneRepMax: Double?,
        val heaviestWeightKg: Double?,
        val heaviestReps: Int?,
        val maxReps: Int?,
        val bestDayVolume: Double?,
        val sessionsPerWeek: Double?,
        val lastPerformedDate: String?,
        /** Per-day est. 1RM (days with a weighted set), ascending. Chart default series. */
        val oneRepMaxSeries: List<DayValue>,
        /** Per-day max weight (top set), ascending. */
        val topSetSeries: List<DayValue>,
        /** Per-day total volume, ascending. */
        val volumeSeries: List<DayValue>,
        /** Performed days, newest first. */
        val recentSessions: List<DaySession>,
    )

    fun calculate(history: List<ExerciseHistoryPoint>): ExerciseStats {
        if (history.isEmpty()) {
            return ExerciseStats(
                hasData = false, bestOneRepMax = null, heaviestWeightKg = null, heaviestReps = null,
                maxReps = null, bestDayVolume = null, sessionsPerWeek = null, lastPerformedDate = null,
                oneRepMaxSeries = emptyList(), topSetSeries = emptyList(), volumeSeries = emptyList(),
                recentSessions = emptyList(),
            )
        }

        val trend = WorkoutProgressAnalyzer.trendPoints(history) // ascending by date

        val oneRepMaxSeries = trend.mapNotNull { tp ->
            tp.bestEstimatedOneRepMax?.let { DayValue(tp.date, it) }
        }
        val volumeSeries = trend.map { DayValue(it.date, it.totalVolume) }
        val topSetSeries = history.groupBy { it.date }.toSortedMap().mapNotNull { (date, pts) ->
            pts.mapNotNull { it.weightKg }.maxOrNull()?.let { DayValue(date, it) }
        }

        val heaviest = history.filter { it.weightKg != null }.maxByOrNull { it.weightKg!! }
        val dates = history.map { it.date }.toSortedSet()

        val recentSessions = history.groupBy { it.date }.map { (date, pts) ->
            DaySession(
                date = date,
                sets = pts,
                volume = pts.sumOf { WorkoutProgressAnalyzer.setVolume(it.reps, it.weightKg) },
            )
        }.sortedByDescending { it.date }

        return ExerciseStats(
            hasData = true,
            bestOneRepMax = oneRepMaxSeries.maxByOrNull { it.value }?.value,
            heaviestWeightKg = heaviest?.weightKg,
            heaviestReps = heaviest?.reps,
            maxReps = history.maxOf { it.reps },
            bestDayVolume = volumeSeries.maxByOrNull { it.value }?.value,
            sessionsPerWeek = sessionsPerWeek(dates),
            lastPerformedDate = dates.lastOrNull(),
            oneRepMaxSeries = oneRepMaxSeries,
            topSetSeries = topSetSeries,
            volumeSeries = volumeSeries,
            recentSessions = recentSessions,
        )
    }

    /** Distinct training days divided by the span in weeks (min 1 week). Null if no parseable dates. */
    private fun sessionsPerWeek(dates: Set<String>): Double? {
        if (dates.isEmpty()) return null
        val parsed = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        if (parsed.isEmpty()) return null
        val spanDays = ChronoUnit.DAYS.between(parsed.first(), parsed.last()).toDouble()
        val weeks = max(1.0, spanDays / 7.0)
        return parsed.size / weeks
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.ExerciseStatsCalculatorTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/workout/ExerciseStatsCalculator.kt app/src/test/java/com/zack/recomptracker/domain/workout/ExerciseStatsCalculatorTest.kt
git commit -m "feat(stats): exercise stats calculator (PRs, series, frequency)"
```

---

## Task 4: Body map fit-transform geometry (pure, testable)

The math that scales the full silhouette to fit a canvas and inverts a tap back into path space.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/BodyMapGeometry.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/BodyMapGeometryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.ui.train.component.BodyMapGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMapGeometryTest {
    @Test fun scalesToFitNarrowerDimensionAndCenters() {
        // content 100x200 into canvas 100x100 -> scale 0.5, content drawn 50x100, centered horizontally
        val t = BodyMapGeometry.fit(contentLeft = 0f, contentTop = 0f, contentRight = 100f, contentBottom = 200f, canvasW = 100f, canvasH = 100f)
        assertEquals(0.5f, t.scale, 0.0001f)
        assertEquals(25f, t.dx, 0.0001f) // (100 - 100*0.5)/2 - 0*0.5
        assertEquals(0f, t.dy, 0.0001f)
    }

    @Test fun forwardAndInverseRoundTrip() {
        val t = BodyMapGeometry.fit(10f, 20f, 110f, 220f, 300f, 300f)
        val sx = 42f * t.scale + t.dx
        val sy = 88f * t.scale + t.dy
        assertEquals(42f, t.toContentX(sx), 0.001f)
        assertEquals(88f, t.toContentY(sy), 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.BodyMapGeometryTest"`
Expected: FAIL — `BodyMapGeometry` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ui.train.component

import kotlin.math.min

/**
 * Pure geometry for drawing a vector silhouette "fit-center" into a canvas and mapping taps back.
 * Screen = content * scale + (dx, dy). Inverse: content = (screen - d) / scale.
 */
object BodyMapGeometry {
    data class FitTransform(val scale: Float, val dx: Float, val dy: Float) {
        fun toContentX(screenX: Float): Float = (screenX - dx) / scale
        fun toContentY(screenY: Float): Float = (screenY - dy) / scale
    }

    fun fit(
        contentLeft: Float, contentTop: Float, contentRight: Float, contentBottom: Float,
        canvasW: Float, canvasH: Float,
    ): FitTransform {
        val cw = (contentRight - contentLeft).coerceAtLeast(0.0001f)
        val ch = (contentBottom - contentTop).coerceAtLeast(0.0001f)
        val scale = min(canvasW / cw, canvasH / ch)
        val dx = (canvasW - cw * scale) / 2f - contentLeft * scale
        val dy = (canvasH - ch * scale) / 2f - contentTop * scale
        return FitTransform(scale, dx, dy)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.BodyMapGeometryTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/BodyMapGeometry.kt app/src/test/java/com/zack/recomptracker/domain/workout/BodyMapGeometryTest.kt
git commit -m "feat(stats): body-map fit-transform geometry"
```

---

## Task 5: Category → highlight slugs + slug → category map

Maps a `MuscleCategory` to the real asset slugs to highlight on each view, and the reverse (tapped slug → category). Slugs verified against `body_front.json` / `body_back.json`.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleCategoryHighlight.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/workout/MuscleCategoryHighlightTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.ui.train.component.categoryForSlug
import com.zack.recomptracker.ui.train.component.highlightFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleCategoryHighlightTest {
    @Test fun armsHighlightsBicepsFrontAndTricepsBack() {
        val h = highlightFor(MuscleCategory.ARMS)
        assertTrue("biceps" in h.front)
        assertTrue("triceps" in h.back)
        assertTrue("forearm" in h.front)
    }

    @Test fun chestHighlightsChestFrontOnly() {
        val h = highlightFor(MuscleCategory.CHEST)
        assertEquals(setOf("chest"), h.front)
        assertTrue(h.back.isEmpty())
    }

    @Test fun tappingBicepsSlugResolvesToArms() {
        assertEquals(MuscleCategory.ARMS, categoryForSlug("biceps"))
        assertEquals(MuscleCategory.BACK, categoryForSlug("upper-back"))
        assertEquals(MuscleCategory.LEGS, categoryForSlug("hamstring"))
        assertEquals(MuscleCategory.CORE, categoryForSlug("abs"))
    }

    @Test fun nonMuscleSlugResolvesToNull() {
        assertEquals(null, categoryForSlug("hair"))
        assertEquals(null, categoryForSlug("head"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.MuscleCategoryHighlightTest"`
Expected: FAIL — `highlightFor` / `categoryForSlug` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ui.train.component

import com.zack.recomptracker.domain.workout.MuscleCategory

/** Slugs to fill with the accent on each view for a given category. Slugs match body_*.json. */
data class CategoryHighlight(val front: Set<String>, val back: Set<String>)

/**
 * Real asset slugs per category.
 * Front slugs available: abs, adductors, biceps, calves, chest, deltoids, forearm, neck, obliques,
 *   quadriceps, trapezius, triceps, tibialis (+ non-muscle: ankles, feet, hair, hands, head, knees).
 * Back slugs available: adductors, calves, deltoids, forearm, gluteal, hamstring, lower-back, neck,
 *   trapezius, triceps, upper-back (+ non-muscle: ankles, feet, hair, hands, head).
 */
private val HIGHLIGHTS: Map<MuscleCategory, CategoryHighlight> = mapOf(
    MuscleCategory.CHEST to CategoryHighlight(front = setOf("chest"), back = emptySet()),
    MuscleCategory.BACK to CategoryHighlight(front = emptySet(), back = setOf("upper-back", "lower-back", "trapezius")),
    MuscleCategory.SHOULDERS to CategoryHighlight(front = setOf("deltoids"), back = setOf("deltoids")),
    MuscleCategory.ARMS to CategoryHighlight(front = setOf("biceps", "triceps", "forearm"), back = setOf("triceps", "forearm")),
    MuscleCategory.LEGS to CategoryHighlight(
        front = setOf("quadriceps", "calves", "adductors"),
        back = setOf("hamstring", "gluteal", "calves", "adductors"),
    ),
    MuscleCategory.CORE to CategoryHighlight(front = setOf("abs", "obliques"), back = emptySet()),
)

fun highlightFor(category: MuscleCategory): CategoryHighlight =
    HIGHLIGHTS[category] ?: CategoryHighlight(emptySet(), emptySet())

/** Reverse lookup: which category a tapped body slug belongs to (null for non-muscle slugs). */
private val SLUG_TO_CATEGORY: Map<String, MuscleCategory> = buildMap {
    HIGHLIGHTS.forEach { (category, h) ->
        (h.front + h.back).forEach { slug -> putIfAbsent(slug, category) }
    }
}

fun categoryForSlug(slug: String): MuscleCategory? = SLUG_TO_CATEGORY[slug]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.workout.MuscleCategoryHighlightTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/MuscleCategoryHighlight.kt app/src/test/java/com/zack/recomptracker/domain/workout/MuscleCategoryHighlightTest.kt
git commit -m "feat(stats): category highlight slug mapping + reverse lookup"
```

---

## Task 6: BodyMap composable (full front+back, multi-highlight, tap)

A composable drawing both silhouettes uncropped, highlighting a category's slugs, and reporting taps as categories. Verified by build + on-device (Canvas + Region can't be unit-tested headlessly).

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/component/BodyMap.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.zack.recomptracker.ui.train.component

import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlin.math.ceil

private val FaintBody = Color.White.copy(alpha = 0.13f)

/**
 * Two full body silhouettes (front + back) side by side. All muscles drawn faint; the slugs for
 * [selected] are filled with the theme accent. Tapping a muscle reports its [MuscleCategory] via
 * [onMuscleTap] (no-op for non-muscle regions). Reuses the shared MuscleArt path data.
 */
@Composable
fun BodyMap(
    selected: MuscleCategory?,
    onMuscleTap: (MuscleCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    remember { MuscleArt.load(context); true }
    val front = MuscleArt.front()
    val back = MuscleArt.back()
    val highlight = selected?.let { highlightFor(it) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BodyFigure(
            label = "FRONT", paths = front, highlightSlugs = highlight?.front.orEmpty(),
            tint = accent.accent, faintColor = FaintBody, labelColor = appColors.textMuted,
            onMuscleTap = onMuscleTap, modifier = Modifier.weight(1f),
        )
        BodyFigure(
            label = "BACK", paths = back, highlightSlugs = highlight?.back.orEmpty(),
            tint = accent.accent, faintColor = FaintBody, labelColor = appColors.textMuted,
            onMuscleTap = onMuscleTap, modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BodyFigure(
    label: String,
    paths: List<MuscleArt.MusclePath>,
    highlightSlugs: Set<String>,
    tint: Color,
    faintColor: Color,
    labelColor: Color,
    onMuscleTap: (MuscleCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Union bounds of all paths = the whole silhouette extent.
    val bounds = remember(paths) {
        if (paths.isEmpty()) RectF(0f, 0f, 1f, 1f) else {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            paths.forEach {
                val bb = it.path.getBounds()
                l = minOf(l, bb.left); t = minOf(t, bb.top); r = maxOf(r, bb.right); b = maxOf(b, bb.bottom)
            }
            RectF(l, t, r, b)
        }
    }
    val aspect = ((bounds.width()) / (bounds.height())).coerceIn(0.3f, 1.0f)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .pointerInput(paths, highlightSlugs) {
                    detectTapGestures { offset ->
                        val t = BodyMapGeometry.fit(bounds.left, bounds.top, bounds.right, bounds.bottom, size.width.toFloat(), size.height.toFloat())
                        val slug = hitSlug(paths, t.toContentX(offset.x), t.toContentY(offset.y))
                        slug?.let { categoryForSlug(it) }?.let(onMuscleTap)
                    }
                },
        ) {
            val t = BodyMapGeometry.fit(bounds.left, bounds.top, bounds.right, bounds.bottom, size.width, size.height)
            drawFigure(paths, highlightSlugs, tint, faintColor, t)
        }
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = labelColor, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun DrawScope.drawFigure(
    paths: List<MuscleArt.MusclePath>,
    highlightSlugs: Set<String>,
    tint: Color,
    faintColor: Color,
    t: BodyMapGeometry.FitTransform,
) {
    translate(left = t.dx, top = t.dy) {
        scale(t.scale, t.scale, pivot = Offset.Zero) {
            paths.forEach { mp ->
                drawPath(mp.path, if (mp.slug in highlightSlugs) tint else faintColor)
            }
        }
    }
}

/** Point-in-path test in path coordinate space. Returns the first slug whose region contains (x,y). */
private fun hitSlug(paths: List<MuscleArt.MusclePath>, x: Float, y: Float): String? {
    paths.forEach { mp ->
        val ap = mp.path.asAndroidPath()
        val bb = RectF()
        ap.computeBounds(bb, true)
        if (x < bb.left || x > bb.right || y < bb.top || y > bb.bottom) return@forEach
        val region = Region()
        val clip = Region(bb.left.toInt(), bb.top.toInt(), ceil(bb.right).toInt(), ceil(bb.bottom).toInt())
        region.setPath(ap, clip)
        if (region.contains(x.toInt(), y.toInt())) return mp.slug
    }
    return null
}
```

> Note: `scale(...)` inside `translate { }` uses `androidx.compose.ui.graphics.drawscope.scale`; add `import androidx.compose.ui.graphics.drawscope.scale` and `import androidx.compose.ui.graphics.asAndroidPath`. Adjust imports if the IDE flags them.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any missing imports (`drawscope.scale`, `asAndroidPath`) until it compiles.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/component/BodyMap.kt
git commit -m "feat(stats): BodyMap composable (full front/back, multi-highlight, tap)"
```

---

## Task 7: Add STATS tab + extend TrainViewModel

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:504-507`

- [ ] **Step 1: Update TrainViewModel**

Replace the entire contents of `TrainViewModel.kt` with:

```kotlin
package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.TrainStatsBuilder
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TrainTab { ROUTINES, HISTORY, STATS }

data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
    val statsCategories: List<TrainStatsBuilder.CategoryStats> = emptyList(),
)

class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)

    private val core = combine(
        workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(),
        sessionRepository.observeCompletedSessions(),
        exerciseLibraryRepository.observeAll(),
    ) { routines, active, history, library ->
        Quad(routines, active, history, TrainStatsBuilder.build(history, library))
    }

    val state: StateFlow<TrainUiState> = combine(tab, core) { t, c ->
        TrainUiState(
            tab = t,
            routines = c.routines,
            activeSession = c.active,
            history = c.history,
            statsCategories = c.stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())

    private data class Quad(
        val routines: List<WorkoutTemplate>,
        val active: WorkoutSession?,
        val history: List<WorkoutSession>,
        val stats: List<TrainStatsBuilder.CategoryStats>,
    )

    fun selectTab(t: TrainTab) { tab.value = t }
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)
}
```

- [ ] **Step 2: Update AppContainer to pass the new dependency**

In `AppContainer.kt`, find the `TrainViewModel::class.java ->` branch (around line 504) and replace it with:

```kotlin
            TrainViewModel::class.java -> TrainViewModel(
                workoutRepository = container.workoutRepository,
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
            )
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (The `when (tab)` segmented control in TrainHomeScreen still compiles because `TrainTab.entries` now has three values; the label `if (tab == ROUTINES) "Routines" else "History"` is fixed in Task 8.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(stats): STATS tab + stats categories in TrainViewModel"
```

---

## Task 8: Stats tab content (bodies + category accordion)

Renders the Stats tab: the `BodyMap` and the six category rows that expand to logged exercises. Two-way highlight is local Compose state.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt`

- [ ] **Step 1: Fix the segmented-pill label for three tabs**

In `TrainHomeScreen.kt`, replace the pill `Text(...)` label expression (currently `text = if (tab == TrainTab.ROUTINES) "Routines" else "History"`, around line 147) with:

```kotlin
                        Text(
                            text = when (tab) {
                                TrainTab.ROUTINES -> "Routines"
                                TrainTab.HISTORY -> "History"
                                TrainTab.STATS -> "Stats"
                            },
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                            color = if (isActive) accent.onAccent else appColors.textPrimary.copy(alpha = 0.55f),
                        )
```

- [ ] **Step 2: Add the new screen param**

In the `TrainHomeScreen` signature, add a new callback param after `onOpenSession`:

```kotlin
    onOpenSession: (Long) -> Unit = {},
    onOpenExerciseStats: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
```

- [ ] **Step 3: Render the Stats tab block**

In `TrainHomeScreen`, immediately after the closing brace of the `if (state.tab == TrainTab.HISTORY) { ... }` block (around line 299, before the final `}` that closes the `LazyColumn`), add:

```kotlin
        // ── Stats tab ──────────────────────────────────────────────────────────
        if (state.tab == TrainTab.STATS) {
            item { StatsContent(state = state, onOpenExerciseStats = onOpenExerciseStats) }
        }
```

- [ ] **Step 4: Add the StatsContent composables**

Append to the bottom of `TrainHomeScreen.kt`:

```kotlin
// ── Stats tab content ───────────────────────────────────────────────────────────

@Composable
private fun StatsContent(
    state: TrainUiState,
    onOpenExerciseStats: (Long) -> Unit,
) {
    val appColors = LocalAppColors.current
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.zack.recomptracker.domain.workout.MuscleCategory?>(null) }

    val anyLogged = state.statsCategories.any { it.exercises.isNotEmpty() }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        com.zack.recomptracker.ui.train.component.BodyMap(
            selected = selected,
            onMuscleTap = { category -> selected = if (selected == category) null else category },
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )

        if (!anyLogged) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Log a workout to see your stats by muscle.",
                    fontSize = 14.sp,
                    color = appColors.textMuted,
                )
            }
            return@Column
        }

        Text(
            text = "BY MUSCLE",
            fontSize = 11.sp,
            color = appColors.textPrimary.copy(alpha = 0.55f),
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        state.statsCategories.forEach { cat ->
            MuscleCategoryRow(
                category = cat,
                expanded = selected == cat.category,
                onToggle = { selected = if (selected == cat.category) null else cat.category },
                onOpenExerciseStats = onOpenExerciseStats,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun MuscleCategoryRow(
    category: com.zack.recomptracker.domain.workout.TrainStatsBuilder.CategoryStats,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenExerciseStats: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val count = category.exercises.size

    FrostedCard(
        modifier = modifier.fillMaxWidth().clickable { onToggle() },
        contentPadding = 13.dp,
        surfaceTint = if (expanded) accent.tintedSurface else Color.Unspecified,
        borderColor = if (expanded) accent.tintedBorder else Color.Unspecified,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.category.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = appColors.textPrimary,
            )
            Text(
                text = if (count == 0) "none" else "$count exercise${if (count == 1) "" else "s"}",
                fontSize = 12.sp,
                color = appColors.textMuted,
            )
        }

        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (category.exercises.isEmpty()) {
                    Text(
                        text = "No exercises logged yet.",
                        fontSize = 13.sp,
                        color = appColors.textMuted,
                    )
                } else {
                    category.exercises.forEach { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CornerSmall))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onOpenExerciseStats(ex.exerciseId) }
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ex.name,
                                fontSize = 13.sp,
                                color = appColors.textPrimary.copy(alpha = 0.9f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = appColors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix imports if needed (most are already imported in this file: `clickable`, `RoundedCornerShape`, `CornerSmall`, `ChevronRight`, `TextOverflow`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/TrainHomeScreen.kt
git commit -m "feat(stats): Stats tab content — body map + category accordion"
```

---

## Task 9: Custom Canvas progress line chart

A themed line chart for the detail screen. Matches the app's existing custom-Canvas chart approach (`ui/component/charts/`).

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/charts/ProgressLineChart.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.zack.recomptracker.ui.component.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Minimal line chart: plots [values] in order, scaled to their own min/max, with dot markers and
 * an accent stroke. Themed entirely via LocalAppAccent / LocalAppColors. Shows a placeholder when
 * fewer than 2 points (a single point can't show a trend).
 */
@Composable
fun ProgressLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    if (values.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                text = if (values.isEmpty()) "No data yet" else "Log this exercise again to see a trend",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = appColors.textMuted,
            )
        }
        return
    }

    val minV = values.min()
    val maxV = values.max()
    val span = (maxV - minV).takeIf { it > 0f } ?: 1f
    val lineColor = accent.accent
    val dotColor = accent.accentLighter

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val padX = 12.dp.toPx()
        val padY = 14.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2

        fun pointAt(i: Int): Offset {
            val x = padX + if (values.size == 1) w / 2 else w * i / (values.size - 1)
            val norm = (values[i] - minV) / span
            val y = padY + h * (1f - norm)
            return Offset(x, y)
        }

        // baseline
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(padX, padY + h),
            end = Offset(padX + w, padY + h),
            strokeWidth = 1.dp.toPx(),
        )

        val path = Path()
        values.indices.forEach { i ->
            val pt = pointAt(i)
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
        values.indices.forEach { i ->
            drawCircle(color = dotColor, radius = 3.dp.toPx(), center = pointAt(i))
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/charts/ProgressLineChart.kt
git commit -m "feat(stats): themed custom-canvas progress line chart"
```

---

## Task 10: ExerciseStatsViewModel

Loads one exercise's history + library entry and exposes computed stats.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (register VM)

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.zack.recomptracker.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.ExerciseStatsCalculator
import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.domain.workout.muscleCategoryFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExerciseStatsUiState(
    val loading: Boolean = true,
    val exerciseName: String = "",
    val category: MuscleCategory? = null,
    val primaryMuscleLabel: String? = null,
    val stats: ExerciseStatsCalculator.ExerciseStats? = null,
)

class ExerciseStatsViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: -1L

    private val _state = MutableStateFlow(ExerciseStatsUiState())
    val state: StateFlow<ExerciseStatsUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val exercise = exerciseLibraryRepository.getById(exerciseId)
            val history = sessionRepository.getExerciseHistory(exerciseId)
            val stats = ExerciseStatsCalculator.calculate(history)
            _state.value = ExerciseStatsUiState(
                loading = false,
                exerciseName = exercise?.name ?: "Exercise",
                category = exercise?.let { muscleCategoryFor(it.primaryMuscles) },
                primaryMuscleLabel = exercise?.primaryMuscles?.firstOrNull(),
                stats = stats,
            )
        }
    }
}
```

- [ ] **Step 2: Register in AppContainer**

In `AppContainer.kt`, add a new branch in the `AppViewModelFactory.create` `when` (e.g. right after the `SessionDetailViewModel::class.java ->` branch around line 524):

```kotlin
            ExerciseStatsViewModel::class.java -> ExerciseStatsViewModel(
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
```

And add the import near the other train VM imports (around line 79-84):

```kotlin
import com.zack.recomptracker.ui.train.ExerciseStatsViewModel
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(stats): ExerciseStatsViewModel + DI registration"
```

---

## Task 11: ExerciseStatsScreen (full-screen detail)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsScreen.kt`

- [ ] **Step 1: Write the screen**

```kotlin
package com.zack.recomptracker.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.workout.ExerciseStatsCalculator
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.charts.ProgressLineChart
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class ChartMetric(val label: String) { ONE_RM("Est. 1RM"), TOP_SET("Top set"), VOLUME("Volume") }

@Composable
fun ExerciseStatsScreen(
    viewModel: ExerciseStatsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(22.dp).clickable { onBack() },
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = state.exerciseName,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary,
                )
            }
        }

        val stats = state.stats
        if (state.loading) {
            item { CenterText("Loading…") }
        } else if (stats == null || !stats.hasData) {
            item { CenterText("No history for this exercise yet.") }
        } else {
            item {
                state.primaryMuscleLabel?.let { muscle ->
                    val label = listOfNotNull(state.category?.displayName, muscle).joinToString(" · ")
                    VioletBadge(text = label.uppercase(), modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                }
            }
            item { QuickStats(stats, modifier = Modifier.padding(14.dp)) }
            item { ChartCard(stats, accent.accent, modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) }
            item { PersonalRecords(stats, modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) }
            item {
                SectionLabel("RECENT SESSIONS", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            items(itemsList(stats.recentSessions)) { day ->
                RecentSessionCard(day, modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp))
            }
        }
    }
}

// Stable wrapper so LazyListScope.items() has a typed list.
private fun itemsList(days: List<ExerciseStatsCalculator.DaySession>): List<ExerciseStatsCalculator.DaySession> = days.take(8)

@Composable
private fun CenterText(text: String) {
    val appColors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = 14.sp, color = appColors.textMuted)
    }
}

@Composable
private fun QuickStats(stats: ExerciseStatsCalculator.ExerciseStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("EST. 1RM (BEST)", stats.bestOneRepMax?.let { "${it.roundToInt()} kg" } ?: "—", Modifier.weight(1f))
            StatChip("HEAVIEST SET", if (stats.heaviestWeightKg != null) "${stats.heaviestWeightKg!!.roundToInt()} kg × ${stats.heaviestReps}" else "${stats.maxReps} reps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("FREQUENCY", stats.sessionsPerWeek?.let { "${(it * 10).roundToInt() / 10.0}× / wk" } ?: "—", Modifier.weight(1f))
            StatChip("LAST DONE", stats.lastPerformedDate?.let { friendlyDate(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier, contentPadding = 11.dp) {
        Text(text = label, fontSize = 9.sp, color = appColors.textFaint, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(3.dp))
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
    }
}

@Composable
private fun ChartCard(stats: ExerciseStatsCalculator.ExerciseStats, accentColor: Color, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var metric by remember { mutableStateOf(ChartMetric.ONE_RM) }

    val values: List<Float> = when (metric) {
        ChartMetric.ONE_RM -> stats.oneRepMaxSeries.map { it.value.toFloat() }
        ChartMetric.TOP_SET -> stats.topSetSeries.map { it.value.toFloat() }
        ChartMetric.VOLUME -> stats.volumeSeries.map { it.value.toFloat() }
    }

    FrostedCard(modifier = modifier.fillMaxWidth(), contentPadding = 13.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartMetric.entries.forEach { m ->
                val active = m == metric
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) accent.accent else Color.White.copy(alpha = 0.06f))
                        .clickable { metric = m }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = m.label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) accent.onAccent else appColors.textMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressLineChart(values = values)
    }
}

@Composable
private fun PersonalRecords(stats: ExerciseStatsCalculator.ExerciseStats, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel("PERSONAL RECORDS", modifier = Modifier.padding(bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrChip("MAX WEIGHT", stats.heaviestWeightKg?.let { "${it.roundToInt()} kg" } ?: "—", Modifier.weight(1f))
            PrChip("MOST REPS", stats.maxReps?.toString() ?: "—", Modifier.weight(1f))
            PrChip("BEST VOL/DAY", stats.bestDayVolume?.let { it.roundToInt().toString() } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrChip(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier, contentPadding = 10.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 8.sp, color = appColors.textFaint, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
        }
    }
}

@Composable
private fun RecentSessionCard(day: ExerciseStatsCalculator.DaySession, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = modifier.fillMaxWidth(), contentPadding = 11.dp) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = friendlyDate(day.date), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = appColors.textPrimary)
            Text(text = "vol ${day.volume.roundToInt()}", fontSize = 12.sp, color = appColors.textMuted)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = day.sets.joinToString(" · ") { s ->
                val w = s.weightKg?.let { "${it.roundToInt()}kg" } ?: "BW"
                "${s.reps}×$w"
            },
            fontSize = 12.sp,
            color = appColors.textPrimary.copy(alpha = 0.7f),
        )
    }
}

private fun friendlyDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("MMM d")) }.getOrDefault(iso)
```

> Note: `items(...)` requires `import androidx.compose.foundation.lazy.items`. Add it.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Add the `lazy.items` import if flagged.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/train/ExerciseStatsScreen.kt
git commit -m "feat(stats): ExerciseStatsScreen — quick stats, chart, PRs, sessions"
```

---

## Task 12: Navigation wiring

Route to the detail screen and pass `onOpenExerciseStats` into `TrainHomeScreen`.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route**

In `object Routes`, after the `SessionDetail` lines (around line 105), add:

```kotlin
    const val ExerciseStats = "exercise_stats/{exerciseId}"
    fun exerciseStats(exerciseId: Long) = "exercise_stats/$exerciseId"
```

- [ ] **Step 2: Pass the callback into TrainHomeScreen**

In the `composable(route = Routes.Train) { ... }` block, add the new param to the `TrainHomeScreen(...)` call (after `onOpenSession`):

```kotlin
                onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                onOpenExerciseStats = { exerciseId -> navController.navigate(Routes.exerciseStats(exerciseId)) },
                modifier = Modifier,
```

- [ ] **Step 3: Add the detail composable**

After the `composable(route = Routes.SessionDetail) { ... }` block (around line 311), add:

```kotlin
        composable(
            route = Routes.ExerciseStats,
            arguments = listOf(
                androidx.navigation.navArgument("exerciseId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            com.zack.recomptracker.ui.train.ExerciseStatsScreen(
                viewModel = viewModel<com.zack.recomptracker.ui.train.ExerciseStatsViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
                modifier = Modifier,
            )
        }
```

- [ ] **Step 4: Verify the whole app builds + all unit tests pass**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all new domain tests pass (Tasks 1–5).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(stats): route to ExerciseStatsScreen + wire from Train"
```

---

## Task 13: On-device verification

No code — manual verification on a running device/emulator (per the project's "verify each screen in the app" workflow; the user performs UI verification).

- [ ] **Step 1: Install and open**

Run: `./gradlew :app:installDebug`
Then open the app → Train tab.

- [ ] **Step 2: Verify the checklist**

- [ ] A third **Stats** pill appears next to Routines / History; tapping it shows the Stats content.
- [ ] Two body figures (front + back) render full and uncropped at a prominent size, all muscles faint, none highlighted initially.
- [ ] Tapping a category row highlights its muscles on the bodies (Arms → biceps front + triceps back, etc.) and expands its logged exercises.
- [ ] Tapping a muscle **on a body** expands and highlights the matching category (two-way).
- [ ] Categories with no logged exercises show "none" / "No exercises logged yet."
- [ ] With no workout history at all, the empty-state message shows under the bodies.
- [ ] Tapping a logged exercise opens the full-screen detail; back returns to Stats.
- [ ] Detail shows quick stats, a chart defaulting to **Est. 1RM**, working Top-set/Volume toggles, PRs, and recent sessions newest-first.
- [ ] An exercise logged on a single day shows the chart placeholder instead of a misleading line.
- [ ] All surfaces use the app's glass cards and theme accent (no off-theme colors); confirm in both light and dark themes.

- [ ] **Step 3: Commit any fixes**

Make styling/behavior fixes found during verification, rebuild, and commit with a descriptive message.

---

## Self-Review

**Spec coverage:**
- Stats sub-section as 3rd tab → Tasks 7, 8. ✓
- Two body figures, faint, none highlighted at rest → Task 6 (`BodyMap`). ✓
- Broad categories (Chest/Back/Shoulders/Arms/Legs/Core) → Task 1. ✓
- Category → body highlight → Tasks 5, 6, 8. ✓
- Body → category (two-way) → Task 6 (`onMuscleTap`/`categoryForSlug`) + Task 8. ✓
- Per-category logged exercises with counts → Task 2, rendered Task 8. ✓
- Empty categories show muted state; no-history empty state → Task 8. ✓
- Full-screen exercise detail → Tasks 10–12. ✓
- Quick stats (best 1RM, heaviest set, frequency, last done) → Task 3 + Task 11. ✓
- Chart with Est.1RM (default) / Top-set / Volume toggles → Tasks 3, 9, 11. ✓
- PRs (max weight, most reps, best volume/day) → Tasks 3, 11. ✓
- Recent sessions (date + sets) → Tasks 3, 11. ✓
- Single-point chart guard → Task 9 (`values.size < 2`). ✓
- Reuse MuscleArt; no auto-crop; multi-highlight; hit-testing → Tasks 4–6. ✓
- Theme/glass reuse hard constraint → enforced in Tasks 8, 9, 11 (FrostedCard, SectionLabel, VioletBadge, LocalAppAccent/Colors). ✓
- Testing: domain unit tests → Tasks 1–5; manual UI verify → Task 13. ✓

**Spec deviations (intentional, simpler):**
- **No new DAO query / instrumented test.** Spec proposed a "distinct logged exercises by muscle" DAO query. The plan derives this from the existing `observeCompletedSessions()` + `observeAll()` flows in a pure, unit-tested builder (Task 2) — less code, fully reactive, no schema/androidTest. ✓
- **Custom Canvas chart instead of Vico.** Spec listed Vico first with custom Canvas as the stated fallback; the plan takes the fallback for full theme control and consistency with existing `charts/` components, and to avoid version-specific Vico API risk. ✓

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `MuscleCategory`, `TrainStatsBuilder.CategoryStats`/`LoggedExerciseSummary`, `ExerciseStatsCalculator.ExerciseStats`/`DayValue`/`DaySession`, `CategoryHighlight`, `BodyMapGeometry.FitTransform`, and `ExerciseStatsUiState` are defined once and referenced consistently across tasks. `TrainViewModel` constructor change (Task 7) matches the `AppContainer` factory change (Task 7). `onOpenExerciseStats` added in Task 8 matches the nav wiring in Task 12. ✓
