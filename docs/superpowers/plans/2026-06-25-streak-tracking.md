# Streak Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Workout, Calorie, and Steps habit streaks (current + longest), shown as a Dashboard card that taps through to a detail screen, with a configurable daily step goal.

**Architecture:** A pure-Kotlin `StreakCalculator` (domain) computes current/longest from a set of "qualifying days" + a rest-day tolerance. A `StreakRepository` (data) assembles those sets from existing repositories and exposes `Flow<Streaks>`. Current/longest are **derived on read** — no new Room table, no migration. The only new persisted value is `dailyStepGoal` in `UserProfilePreferences` (DataStore). UI uses existing design-system components only.

**Tech Stack:** Kotlin, Coroutines/Flow, Jetpack Compose + Material3, Room (read-only here), AndroidX DataStore, JUnit4 + kotlinx-coroutines-test. Build via Gradle.

**Working dir:** worktree `.claude/worktrees/streak-tracking` (branch `feat/streak-tracking`). Run all commands from the worktree root.

**Spec:** `docs/superpowers/specs/2026-06-25-streak-tracking-design.md`

---

## File Structure

New files:
- `app/src/main/java/com/zack/recomptracker/domain/streak/StreakModels.kt` — `StreakType`, `StreakResult`, `Streaks`
- `app/src/main/java/com/zack/recomptracker/domain/streak/StreakCalculator.kt` — pure streak math
- `app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt` — `buildStreaks` (pure) + `streaks(): Flow<Streaks>`
- `app/src/main/java/com/zack/recomptracker/ui/streak/StreakViewModel.kt` — exposes `StreakUiState`
- `app/src/main/java/com/zack/recomptracker/ui/streak/StreakStatsScreen.kt` — detail screen
- `app/src/test/java/com/zack/recomptracker/domain/StreakCalculatorTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/StreakBuilderTest.kt`
- `app/src/test/java/com/zack/recomptracker/data/MarkDayTrainedTest.kt`

Modified files:
- `data/preferences/UserProfilePreferences.kt`, `data/preferences/UserProfilePreferencesStore.kt` — add `dailyStepGoal`
- `data/repository/WorkoutSessionRepository.kt` — `markDayTrained` + trained sync on `completeSession`
- `core/AppContainer.kt` — DI wiring + ViewModel factory entry
- `ui/profile/ProfileViewModel.kt`, `ui/profile/ProfileScreen.kt` — step-goal field
- `ui/dashboard/DashboardScreen.kt` — Streaks summary card + wiring
- `ui/navigation/AppNavGraph.kt` — `Routes.StreakStats`, detail composable, Home wiring

---

## Task 1: Streak domain models + calculator

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/streak/StreakModels.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/streak/StreakCalculator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/StreakCalculatorTest.kt`

- [ ] **Step 1: Write the models**

Create `StreakModels.kt`:

```kotlin
package com.zack.recomptracker.domain.streak

enum class StreakType { WORKOUT, CALORIE, STEPS }

/**
 * Result of a streak computation.
 *
 * @property current calendar days spanned by the live chain (0 if the streak is broken)
 * @property longest largest spanned chain across all history (always >= current)
 * @property last7   qualified/missed flags for the last 7 calendar days, oldest -> newest.
 *                   Left empty by [StreakCalculator]; populated by the repository for the UI.
 */
data class StreakResult(
    val current: Int,
    val longest: Int,
    val last7: List<Boolean> = emptyList(),
) {
    companion object {
        val ZERO = StreakResult(current = 0, longest = 0)
    }
}

data class Streaks(
    val workout: StreakResult,
    val calorie: StreakResult,
    val steps: StreakResult,
) {
    fun entries(): List<Pair<StreakType, StreakResult>> = listOf(
        StreakType.WORKOUT to workout,
        StreakType.CALORIE to calorie,
        StreakType.STEPS to steps,
    )

    companion object {
        val EMPTY = Streaks(StreakResult.ZERO, StreakResult.ZERO, StreakResult.ZERO)
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `StreakCalculatorTest.kt`:

```kotlin
package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val calc = StreakCalculator()

    // 2026-06-22 is a Monday.
    private val mon = LocalDate.of(2026, 6, 22)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val thu = mon.plusDays(3)
    private val fri = mon.plusDays(4)

    @Test fun emptyHistoryIsZero() {
        val r = calc.compute(emptySet(), today = thu, restDays = 2)
        assertEquals(0, r.current)
        assertEquals(0, r.longest)
    }

    @Test fun workoutContinuesAcrossTwoRestDays() {
        // Mon workout, Tue/Wed rest, Thu workout -> spans 4 calendar days
        val r = calc.compute(setOf(mon, thu), today = thu, restDays = 2)
        assertEquals(4, r.current)
        assertEquals(4, r.longest)
    }

    @Test fun workoutAliveWhileResting() {
        // Mon workout only, today Thu (3 days later, within tolerance): alive, span to last workout = 1
        val r = calc.compute(setOf(mon), today = thu, restDays = 2)
        assertEquals(1, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun workoutBreaksAfterThreeRestDays() {
        // Mon workout only, today Fri (4 days later): broken
        val r = calc.compute(setOf(mon), today = fri, restDays = 2)
        assertEquals(0, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun workoutGapTooLargeStartsNewStreak() {
        // Mon then Fri (gap 4 > 3) -> separate chains; Friday is a fresh streak of 1
        val r = calc.compute(setOf(mon, fri), today = fri, restDays = 2)
        assertEquals(1, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun calorieConsecutiveDaysCount() {
        val r = calc.compute(setOf(mon, tue, wed), today = wed, restDays = 0)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun calorieGraceForUnloggedToday() {
        // In zone Mon/Tue/Wed, today Thu not yet logged -> still alive (grace), current 3
        val r = calc.compute(setOf(mon, tue, wed), today = thu, restDays = 0)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun calorieBreaksWhenAFullDayMissed() {
        // In zone Mon/Tue/Wed, today Fri (Thu missed, today not logged) -> broken
        val r = calc.compute(setOf(mon, tue, wed), today = fri, restDays = 0)
        assertEquals(0, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun longestCanExceedCurrent() {
        // Old 3-day chain (Mon-Wed), gap, then a single qualifying Fri
        val r = calc.compute(setOf(mon, tue, wed, fri), today = fri, restDays = 0)
        assertEquals(1, r.current)
        assertEquals(3, r.longest)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.StreakCalculatorTest"`
Expected: FAIL — `StreakCalculator` unresolved / does not compile.

- [ ] **Step 4: Write the calculator**

Create `StreakCalculator.kt`:

```kotlin
package com.zack.recomptracker.domain.streak

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure streak math. Each streak type contributes a set of "qualifying days" (days the goal was
 * met) and a rest-day tolerance; this computes the current and longest streaks.
 *
 * Two qualifying days belong to the same chain when their gap is <= restDays + 1.
 * - Calorie/Steps use restDays = 0 (strictly consecutive).
 * - Workout uses restDays = 2 (up to two rest days between workouts; gap <= 3 continues).
 *
 * current = calendar days spanned by the chain containing the most recent qualifying day,
 *           but only if that chain is still "alive": the most recent qualifying day is within
 *           restDays + 1 days of [today]. Otherwise 0. Interior rest days count; trailing rest
 *           days (after the last qualifying day, up to today) keep it alive but are not counted.
 * longest = the largest spanned chain across all history.
 */
class StreakCalculator {
    fun compute(qualifyingDays: Set<LocalDate>, today: LocalDate, restDays: Int): StreakResult {
        if (qualifyingDays.isEmpty()) return StreakResult.ZERO
        val maxGap = restDays + 1L
        val sorted = qualifyingDays.toSortedSet().toList()

        fun span(first: LocalDate, last: LocalDate): Int =
            (ChronoUnit.DAYS.between(first, last) + 1).toInt()

        var longest = 0
        var chainStart = sorted.first()
        var prev = sorted.first()
        for (i in 1 until sorted.size) {
            val day = sorted[i]
            if (ChronoUnit.DAYS.between(prev, day) > maxGap) {
                longest = maxOf(longest, span(chainStart, prev))
                chainStart = day
            }
            prev = day
        }
        longest = maxOf(longest, span(chainStart, prev))

        // prev is now the most recent qualifying day; chainStart is the start of its chain.
        val daysSinceLast = ChronoUnit.DAYS.between(prev, today)
        val current = if (daysSinceLast in 0..maxGap) span(chainStart, prev) else 0

        return StreakResult(current = current, longest = longest)
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.StreakCalculatorTest"`
Expected: PASS (10 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/streak app/src/test/java/com/zack/recomptracker/domain/StreakCalculatorTest.kt
git commit -m "feat(streak): pure-Kotlin streak calculator + models"
```

---

## Task 2: StreakRepository — qualifying-day assembly

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/StreakBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `StreakBuilderTest.kt` (tests the pure `buildStreaks`, which is module-internal):

```kotlin
package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.buildStreaks
import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakBuilderTest {
    private val calc = StreakCalculator()
    private val prefs = PlanPreferences() // zone defaults 2400..2600
    private val mon = LocalDate.of(2026, 6, 22)
    private val tue = mon.plusDays(1)

    private fun log(date: LocalDate, steps: Int? = null, trained: Boolean = false) =
        DailyLogEntity(date = date.toString(), steps = steps, trained = trained)

    @Test fun calorieUsesZoneRule() {
        val s = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(mon to 2500, tue to 3000), // mon in zone, tue over
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = tue,
            calculator = calc,
        )
        // Mon in zone; today Tue not qualified but Mon is yesterday -> grace alive, current 1
        assertEquals(1, s.calorie.current)
    }

    @Test fun stepsCountAtOrAboveGoal() {
        val s = buildStreaks(
            dailyLogs = listOf(log(mon, steps = 12000), log(tue, steps = 8000)),
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = 10000,
            today = tue,
            calculator = calc,
        )
        // Mon 12000 >= goal qualifies; Tue 8000 does not; Mon is yesterday -> grace, current 1
        assertEquals(1, s.steps.current)
    }

    @Test fun stepsZeroWhenNoGoal() {
        val s = buildStreaks(
            dailyLogs = listOf(log(mon, steps = 12000)),
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = mon,
            calculator = calc,
        )
        assertEquals(0, s.steps.current)
        assertEquals(0, s.steps.longest)
    }

    @Test fun workoutUnionsSessionsAndTrainedFlag() {
        val s = buildStreaks(
            dailyLogs = listOf(log(tue, trained = true)), // Tue via trained flag
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = listOf(mon),          // Mon via completed session
            prefs = prefs,
            dailyStepGoal = null,
            today = tue,
            calculator = calc,
        )
        // Mon + Tue workouts -> spans 2 calendar days
        assertEquals(2, s.workout.current)
    }

    @Test fun last7HasSevenFlags() {
        val s = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(mon to 2500),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = mon,
            calculator = calc,
        )
        assertEquals(7, s.calorie.last7.size)
        assertEquals(true, s.calorie.last7.last()) // today (mon) is in zone
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.StreakBuilderTest"`
Expected: FAIL — `buildStreaks` unresolved.

- [ ] **Step 3: Write the repository**

Create `StreakRepository.kt`:

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.domain.streak.StreakCalculator
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.domain.streak.Streaks
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Derives Workout / Calorie / Steps streaks from existing history on every emission.
 * No streak state is persisted — current/longest are always recomputed.
 */
class StreakRepository(
    private val logRepository: LogRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
    private val calculator: StreakCalculator,
) {
    fun streaks(): Flow<Streaks> = combine(
        logRepository.observeDailyLogs(),
        logRepository.observeMealEntries(),
        workoutSessionRepository.observeCompletedSessions(),
        planRepository.preferences,
        userProfileStore.preferences,
    ) { dailyLogs, meals, sessions, prefs, profile ->
        val eatenByDate = meals
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
        buildStreaks(
            dailyLogs = dailyLogs,
            eatenCaloriesByDate = eatenByDate,
            completedSessionDates = sessions.map { LocalDate.parse(it.date) },
            prefs = prefs,
            dailyStepGoal = profile.dailyStepGoal,
            today = dateProvider.today(),
            calculator = calculator,
        )
    }
}

/**
 * Pure assembly of the three streaks — unit-tested directly. Maps raw history into
 * qualifying-day sets, runs [StreakCalculator], and attaches a 7-day strip for the UI.
 *
 * Calorie success = eaten calories within the calorie zone (the dashboard's "in zone" rule).
 * Steps success   = steps >= dailyStepGoal (no streak when the goal is unset).
 * Workout success = a completed session OR a daily log with trained = true.
 */
internal fun buildStreaks(
    dailyLogs: List<DailyLogEntity>,
    eatenCaloriesByDate: Map<LocalDate, Int>,
    completedSessionDates: List<LocalDate>,
    prefs: PlanPreferences,
    dailyStepGoal: Int?,
    today: LocalDate,
    calculator: StreakCalculator,
): Streaks {
    val workoutDays: Set<LocalDate> = (
        completedSessionDates +
            dailyLogs.filter { it.trained }.map { LocalDate.parse(it.date) }
        ).toSet()

    val calorieDays: Set<LocalDate> = eatenCaloriesByDate
        .filterValues { cals ->
            cals > 0 &&
                prefs.calorieZoneLowerBound > 0 &&
                cals >= prefs.calorieZoneLowerBound &&
                cals <= prefs.calorieZoneUpperBound
        }
        .keys

    val stepDays: Set<LocalDate> = if (dailyStepGoal != null && dailyStepGoal > 0) {
        dailyLogs
            .filter { (it.steps ?: 0) >= dailyStepGoal }
            .map { LocalDate.parse(it.date) }
            .toSet()
    } else {
        emptySet()
    }

    fun result(days: Set<LocalDate>, restDays: Int): StreakResult =
        calculator.compute(days, today, restDays).copy(last7 = recentFlags(days, today))

    return Streaks(
        workout = result(workoutDays, restDays = 2),
        calorie = result(calorieDays, restDays = 0),
        steps = result(stepDays, restDays = 0),
    )
}

/** Met/missed flags for the last 7 calendar days ending at [today], oldest -> newest. */
private fun recentFlags(days: Set<LocalDate>, today: LocalDate, window: Int = 7): List<Boolean> =
    (window - 1 downTo 0).map { offset -> today.minusDays(offset.toLong()) in days }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.StreakBuilderTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt app/src/test/java/com/zack/recomptracker/data/StreakBuilderTest.kt
git commit -m "feat(streak): StreakRepository assembles qualifying days from history"
```

---

## Task 3: Add `dailyStepGoal` to user profile preferences

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt`

This is mechanical wiring that mirrors the existing `weeklyGymSessions` field exactly; it is covered by the compile check and the feature tests, so no separate unit test is added.

- [ ] **Step 1: Add the field to the data class**

In `UserProfilePreferences.kt`, add `dailyStepGoal` to the data class:

```kotlin
@Serializable
data class UserProfilePreferences(
    val name: String? = null,
    val profilePhotoUri: String? = null,
    val heightCm: Int? = null,
    val birthDate: String? = null,          // ISO yyyy-MM-dd
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,
    val goal: FitnessGoal? = null,
    val dailyStepGoal: Int? = null,         // steps/day target for the steps streak
)
```

- [ ] **Step 2: Add the DataStore key**

In `UserProfilePreferencesStore.kt`, inside `private object Keys`, add after `Goal`:

```kotlin
    val Goal = stringPreferencesKey("goal")
    val DailyStepGoal = intPreferencesKey("daily_step_goal")
```

- [ ] **Step 3: Add the read mapping**

In `toUserProfilePreferences()`, add after the `Goal` line inside `buildMap`:

```kotlin
        this@toUserProfilePreferences[Keys.Goal]?.let { put("goal", JsonPrimitive(it)) }
        this@toUserProfilePreferences[Keys.DailyStepGoal]?.let { put("dailyStepGoal", JsonPrimitive(it)) }
```

- [ ] **Step 4: Add the write mapping**

In `writeUserProfilePreferences()`, add after the `Goal` line:

```kotlin
    putOrRemove(Keys.Goal, profile.goal?.name)
    putOrRemove(Keys.DailyStepGoal, profile.dailyStepGoal)
```

- [ ] **Step 5: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt
git commit -m "feat(streak): add dailyStepGoal user profile setting"
```

---

## Task 4: Mark the day "trained" on session completion

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:146`
- Test: `app/src/test/java/com/zack/recomptracker/data/MarkDayTrainedTest.kt`

- [ ] **Step 1: Write the failing test**

Create `MarkDayTrainedTest.kt`:

```kotlin
package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.markDayTrained
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDayTrainedTest {
    private fun fakeDao(store: MutableMap<String, DailyLogEntity>) = object : DailyLogDao {
        override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(store[date]) }
        override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(store.values.toList()) }
        override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
        override suspend fun getByDate(date: String): DailyLogEntity? = store[date]
        override suspend fun getAll(): List<DailyLogEntity> = store.values.toList()
        override suspend fun upsert(log: DailyLogEntity) { store[log.date] = log }
        override suspend fun insertAll(logs: List<DailyLogEntity>) { logs.forEach { store[it.date] = it } }
        override suspend fun deleteAll() { store.clear() }
    }

    @Test fun createsRowWhenAbsent() = runTest {
        val store = mutableMapOf<String, DailyLogEntity>()
        markDayTrained(fakeDao(store), "2026-06-22")
        assertTrue(store["2026-06-22"]!!.trained)
    }

    @Test fun preservesOtherMetrics() = runTest {
        val store = mutableMapOf(
            "2026-06-22" to DailyLogEntity(date = "2026-06-22", steps = 9000, bodyWeightKg = 80.0),
        )
        markDayTrained(fakeDao(store), "2026-06-22")
        val log = store["2026-06-22"]!!
        assertTrue(log.trained)
        assertEquals(9000, log.steps)
        assertEquals(80.0, log.bodyWeightKg!!, 0.001)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.MarkDayTrainedTest"`
Expected: FAIL — `markDayTrained` unresolved.

- [ ] **Step 3: Add `markDayTrained` and wire it into `completeSession`**

In `WorkoutSessionRepository.kt`, add these imports near the existing imports:

```kotlin
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
```

Change the constructor to accept an optional `DailyLogDao` (added **last** so existing positional callers are unaffected):

```kotlin
open class WorkoutSessionRepository(
    private val sessionDao: WorkoutSessionDao,
    private val now: () -> String = { Instant.now().toString() },
    private val today: () -> String = { LocalDate.now().toString() },
    private val dailyLogDao: DailyLogDao? = null,
) {
```

Update `completeSession` to mark the day trained after the session is finalised:

```kotlin
    open suspend fun completeSession(sessionId: Long, durationSeconds: Int? = null) {
        val current = sessionDao.getSessionWithDetails(sessionId)?.session ?: return
        sessionDao.updateSession(current.copy(
            status = SessionStatus.COMPLETED.name,
            completedAt = now(),
            durationSeconds = durationSeconds,
        ))
        dailyLogDao?.let { markDayTrained(it, current.date) }
    }
```

Add this top-level internal helper at the end of the file (after the class closing brace):

```kotlin
/**
 * Sets the `trained` flag to true on the daily log for [date], creating the row if absent and
 * preserving any other metrics already recorded. Idempotent.
 */
internal suspend fun markDayTrained(dailyLogDao: DailyLogDao, date: String) {
    val existing = dailyLogDao.getByDate(date)
    when {
        existing == null -> dailyLogDao.upsert(DailyLogEntity(date = date, trained = true))
        !existing.trained -> dailyLogDao.upsert(existing.copy(trained = true))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.MarkDayTrainedTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Pass the DAO in AppContainer**

In `AppContainer.kt`, change line 146:

```kotlin
    val workoutSessionRepository = WorkoutSessionRepository(
        database.workoutSessionDao(),
        dailyLogDao = database.dailyLogDao(),
    )
```

- [ ] **Step 6: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WorkoutSessionRepository.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/data/MarkDayTrainedTest.kt
git commit -m "feat(streak): mark day trained=true when a session completes"
```

---

## Task 5: Wire StreakCalculator + StreakRepository into AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Construct the calculator and repository**

In `AppContainer.kt`, immediately after the `workoutSessionRepository` declaration (now ending at the `)` of the multi-line constructor from Task 4), add:

```kotlin
    val streakCalculator = StreakCalculator()
    val streakRepository = StreakRepository(
        logRepository = logRepository,
        workoutSessionRepository = workoutSessionRepository,
        planRepository = planRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
        calculator = streakCalculator,
    )
```

- [ ] **Step 2: Add imports**

Add to the imports in `AppContainer.kt`:

```kotlin
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.domain.streak.StreakCalculator
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(streak): construct StreakRepository in AppContainer"
```

---

## Task 6: StreakViewModel + factory registration

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/streak/StreakViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (factory `when` block)

- [ ] **Step 1: Create the ViewModel**

Create `StreakViewModel.kt`:

```kotlin
package com.zack.recomptracker.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.domain.streak.Streaks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StreakUiState(
    val streaks: Streaks = Streaks.EMPTY,
    val stepGoal: Int? = null,
)

class StreakViewModel(
    streakRepository: StreakRepository,
    userProfileStore: UserProfilePreferencesStore,
) : ViewModel() {
    val uiState: StateFlow<StreakUiState> =
        combine(streakRepository.streaks(), userProfileStore.preferences) { streaks, profile ->
            StreakUiState(streaks = streaks, stepGoal = profile.dailyStepGoal)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreakUiState(),
        )
}
```

- [ ] **Step 2: Register it in the factory**

In `AppContainer.kt`, add an entry to the `AppViewModelFactory` `when (modelClass)` block (e.g. right after the `ProfileViewModel::class.java -> ...` entry):

```kotlin
            StreakViewModel::class.java -> StreakViewModel(
                streakRepository = container.streakRepository,
                userProfileStore = container.userProfilePreferencesStore,
            )
```

Add the import to `AppContainer.kt`:

```kotlin
import com.zack.recomptracker.ui.streak.StreakViewModel
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/streak/StreakViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(streak): StreakViewModel + factory registration"
```

---

## Task 7: Profile screen — daily step goal field

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: Add the input buffer + setter to the ViewModel**

In `ProfileViewModel.kt`, add a buffer next to `_heightInput`:

```kotlin
    private val _heightInput = MutableStateFlow("")
    val heightInput: StateFlow<String> = _heightInput.asStateFlow()

    private val _stepGoalInput = MutableStateFlow("")
    val stepGoalInput: StateFlow<String> = _stepGoalInput.asStateFlow()
```

Seed it in `init` alongside the other seeds:

```kotlin
            _nameInput.value = seed.name.orEmpty()
            _heightInput.value = seed.heightCm?.toString() ?: ""
            _stepGoalInput.value = seed.dailyStepGoal?.toString() ?: ""
```

Add the setter next to `setHeight`:

```kotlin
    /** Daily step goal edit: digit-only, max 5 chars. Synchronous buffer update, async persist. */
    fun setDailyStepGoal(value: String) {
        val digits = value.filter { it.isDigit() }.take(5)
        _stepGoalInput.value = digits
        update(profile.value.copy(dailyStepGoal = digits.toIntOrNull()))
    }
```

- [ ] **Step 2: Render the field in the Profile screen**

In `ProfileScreen.kt`, collect the new buffer near the other collectors (after `heightInput`):

```kotlin
    val heightInput by viewModel.heightInput.collectAsStateWithLifecycle()
    val stepGoalInput by viewModel.stepGoalInput.collectAsStateWithLifecycle()
```

In the "Plan inputs" `FrostedCard`, add a step-goal field after the `ScoreStepper` for gym sessions:

```kotlin
                    ScoreStepper(
                        label = "Gym sessions / week",
                        value = profile.weeklyGymSessions ?: 0,
                        onValueChange = { viewModel.update(profile.copy(weeklyGymSessions = it)) },
                        range = 0..7,
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassInputField(
                        label = "Daily step goal",
                        value = stepGoalInput,
                        onValueChange = viewModel::setDailyStepGoal,
                        unit = "steps",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`GlassInputField`, `Spacer`, `KeyboardType`, `Modifier.fillMaxWidth` are already imported — they are used by the existing Height field.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/profile/ProfileViewModel.kt app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt
git commit -m "feat(streak): daily step goal field in Profile"
```

---

## Task 8: Streak detail screen + route

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/streak/StreakStatsScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Create the detail screen**

Create `StreakStatsScreen.kt`:

```kotlin
package com.zack.recomptracker.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun StreakStatsScreen(
    viewModel: StreakViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenScaffold(withNavBarInset = false) {
        item { SubScreenHeader(title = "Streaks", onBack = onBack) }
        item { StreakDetailCard(emoji = "🏋️", name = "Workout", result = ui.streaks.workout, hint = null) }
        item { StreakDetailCard(emoji = "🔥", name = "Calorie", result = ui.streaks.calorie, hint = null) }
        item {
            StreakDetailCard(
                emoji = "👟",
                name = "Steps",
                result = ui.streaks.steps,
                hint = if (ui.stepGoal == null) {
                    "Set a daily step goal in Profile to start this streak."
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun StreakDetailCard(
    emoji: String,
    name: String,
    result: StreakResult,
    hint: String?,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = emoji, style = AppType.statValue)
            Text(text = name, style = AppType.cardTitle, color = appColors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${result.current}", style = AppType.displayLarge, color = appColors.textPrimary)
                Text(text = "Current (days)", style = AppType.metaLabel, color = appColors.textMuted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${result.longest}", style = AppType.displayLarge, color = appColors.textPrimary)
                Text(text = "Best (days)", style = AppType.metaLabel, color = appColors.textMuted)
            }
        }
        if (result.last7.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                result.last7.forEach { met ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (met) accent.accent else appColors.cardBorder),
                    )
                }
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = hint, style = AppType.cardSubtitle, color = appColors.textMuted)
        }
    }
}
```

- [ ] **Step 2: Add the route constant**

In `AppNavGraph.kt`, inside `object Routes`, add near the other simple routes (e.g. after `Trends`):

```kotlin
    const val Trends    = "trends"
    const val StreakStats = "streak_stats"
```

- [ ] **Step 3: Add the composable destination**

In `AppNavGraph.kt`, add a destination alongside the other pushed sub-screens (e.g. near the `Routes.Trends` composable). Use the pushed-screen transitions `screenEnter`/`screenExit`:

```kotlin
        composable(
            route = Routes.StreakStats,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            StreakStatsScreen(
                viewModel = viewModel<StreakViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
```

Add the imports to `AppNavGraph.kt`:

```kotlin
import com.zack.recomptracker.ui.streak.StreakStatsScreen
import com.zack.recomptracker.ui.streak.StreakViewModel
```

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/streak/StreakStatsScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(streak): streak detail screen + route"
```

---

## Task 9: Dashboard streaks summary card

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` (Home destination)

- [ ] **Step 1: Add the card composables to DashboardScreen**

In `DashboardScreen.kt`, add these two private composables (e.g. just before `SevenDayChartCard`):

```kotlin
@Composable
private fun StreaksCard(streaks: Streaks, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Streaks")
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = appColors.textVeryMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StreakSummaryItem("🏋️", "Workout", streaks.workout.current, Modifier.weight(1f))
            StreakSummaryItem("🔥", "Calorie", streaks.calorie.current, Modifier.weight(1f))
            StreakSummaryItem("👟", "Steps", streaks.steps.current, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakSummaryItem(emoji: String, label: String, days: Int, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = emoji, style = AppType.statValueSmall, color = appColors.textPrimary)
        Text(text = "$days", style = AppType.statValue, color = appColors.textPrimary)
        Text(text = if (days == 1) "day" else "days", style = AppType.metaLabel, color = appColors.textMuted)
        Text(text = label, style = AppType.metaLabel, color = appColors.textVeryMuted)
    }
}
```

Ensure these imports exist in `DashboardScreen.kt` (add any that are missing):

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import com.zack.recomptracker.domain.streak.Streaks
import com.zack.recomptracker.ui.component.SectionLabel
```

- [ ] **Step 2: Thread streaks + callback through HomeDashboardScreen**

In `DashboardScreen.kt`, update the `HomeDashboardScreen` signature to accept the streak ViewModel and the navigation callback:

```kotlin
fun HomeDashboardScreen(
    viewModel: DashboardViewModel,
    weeklyReviewViewModel: WeeklyReviewViewModel,
    streakViewModel: StreakViewModel,
    onOpenCoach: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStreaks: () -> Unit,
) {
```

Add the import:

```kotlin
import com.zack.recomptracker.ui.streak.StreakViewModel
```

Collect the streak state near the other `collectAsStateWithLifecycle` calls in `HomeDashboardScreen`:

```kotlin
    val streakState by streakViewModel.uiState.collectAsStateWithLifecycle()
```

Pass it into the `HomeDashboardContent(...)` call (add these two arguments):

```kotlin
        crossMetricInsightState = crossMetricInsightState,
        onRetryCrossMetric = viewModel::retryCrossMetric,
        streaks = streakState.streaks,
        onOpenStreaks = onOpenStreaks,
    )
```

- [ ] **Step 3: Render the card in HomeDashboardContent**

In `DashboardScreen.kt`, add two parameters to `HomeDashboardContent` (after `onRetryCrossMetric`):

```kotlin
    crossMetricInsightState: AiInsightState = AiInsightState.Disabled,
    onRetryCrossMetric: () -> Unit = {},
    streaks: Streaks = Streaks.EMPTY,
    onOpenStreaks: (() -> Unit)? = null,
) {
```

Inside the `LazyColumn`, add a Streaks card item after the `SevenDayChartCard` item:

```kotlin
                item { SevenDayChartCard(state) }
                if (onOpenStreaks != null) {
                    item { StreaksCard(streaks = streaks, onClick = onOpenStreaks) }
                }
```

- [ ] **Step 4: Wire the Home destination in AppNavGraph**

In `AppNavGraph.kt`, update the `HomeDashboardScreen(...)` call inside the Home `composable` (around line 156) to pass the streak ViewModel and navigation callback:

```kotlin
            HomeDashboardScreen(
                viewModel = viewModel<DashboardViewModel>(factory = factory),
                weeklyReviewViewModel = viewModel<WeeklyReviewViewModel>(factory = factory),
                streakViewModel = viewModel<StreakViewModel>(factory = factory),
                onOpenCoach = {
                    navController.navigate(TopLevelDestination.Coach.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(TopLevelDestination.More.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenStreaks = { navController.navigate(Routes.StreakStats) },
            )
```

(`StreakViewModel` is already imported in `AppNavGraph.kt` from Task 8.)

- [ ] **Step 5: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(streak): dashboard streaks summary card"
```

---

## Task 10: Full verification + hand-off

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests pass, including `StreakCalculatorTest`, `StreakBuilderTest`, `MarkDayTrainedTest`.

- [ ] **Step 2: Assemble the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Hand off for visual verification**

Per project convention the assistant does not drive the emulator. Report build + test status and ask the user to verify in the running app:
- Profile → "Daily step goal" field saves and reloads.
- Dashboard shows the Streaks card; tapping it opens the detail screen.
- Detail screen shows current + best per streak; the steps card shows the "set a goal" hint until a goal is set.
- Completing a workout session marks that day trained (the workout streak reflects it).

- [ ] **Step 4: Final commit (if any uncommitted changes remain)**

```bash
git status
# commit anything outstanding, otherwise proceed
```
