# Adherence Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify the three inconsistent "adherence" definitions onto one graded calculator, split logging-consistency out as its own signal, and lower the engine's adherence gate to 80.

**Architecture:** `AdherenceCalculator` exposes one per-day graded primitive (`dailyAdherencePercent`), an "adherence quality" aggregate (`calculate`, averaged over logged days only), and a separate `loggingConsistency`. Dashboard, Progress, and the AI coach tool all route through it; the ad-hoc `loggedDays/7` math is deleted. The Adjustment Engine keeps its existing `daysLogged < 14` logging gate and its adherence-quality gate, with the default lowered 85 → 80 and the `LOW_ADHERENCE` wording corrected.

**Tech Stack:** Kotlin, JUnit4, Mockito-Kotlin, Gradle. Pure-domain logic in `domain/adherence`.

**Reference spec:** `docs/superpowers/specs/2026-06-11-adherence-redesign-design.md`

**Commands:**
- Unit tests (single class): `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.AdherenceCalculatorTest"`
- Full unit tests: `./gradlew :app:testDebugUnitTest`
- Type-check: `./gradlew :app:compileDebugKotlin`

---

### Task 1: Rewrite `AdherenceCalculator` (graded quality + separate logging)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/adherence/AdherenceCalculator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/AdherenceCalculatorTest.kt` (full rewrite)

- [ ] **Step 1: Replace the test file with the new API's failing tests**

Overwrite `AdherenceCalculatorTest.kt` with:

```kotlin
package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorTest {
    private val calculator = AdherenceCalculator()
    private val start = LocalDate.of(2026, 5, 1)

    // --- calculate: adherence quality (graded average over LOGGED days only) ---

    @Test
    fun calculateReturnsZeroWhenNoLoggedDays() {
        val days = listOf(NutritionDay(start, calories = 0))
        assertEquals(0.0, calculator.calculate(days, targetCalories = 2550), 0.001)
    }

    @Test
    fun calculateReturnsZeroWhenTargetInvalid() {
        val days = listOf(NutritionDay(start, calories = 2550))
        assertEquals(0.0, calculator.calculate(days, targetCalories = 0), 0.001)
    }

    @Test
    fun calculateIsOneHundredWhenAllLoggedDaysOnTarget() {
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 2550),
        )
        assertEquals(100.0, calculator.calculate(days, targetCalories = 2550), 0.001)
    }

    @Test
    fun calculateAveragesGradedScoresOverLoggedDaysOnly() {
        // Day 1: exactly on target -> 100
        // Day 2: 20% over (3060) -> 80
        // Day 3: not logged -> excluded from the average entirely
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 3060),
            NutritionDay(start.plusDays(2), calories = 0),
        )
        // (100 + 80) / 2 logged days = 90
        assertEquals(90.0, calculator.calculate(days, targetCalories = 2550), 0.001)
    }

    @Test
    fun calculateDeDuplicatesByDate() {
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start, calories = 0),
        )
        assertEquals(100.0, calculator.calculate(days, targetCalories = 2550), 0.001)
    }

    // --- loggingConsistency: logged days / expected days ---

    @Test
    fun loggingConsistencyZeroWhenNoExpectedDays() {
        assertEquals(0.0, calculator.loggingConsistency(emptyList(), expectedDays = 0), 0.001)
    }

    @Test
    fun loggingConsistencyCountsLoggedDaysOverExpected() {
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 2500),
        )
        assertEquals(28.571, calculator.loggingConsistency(days, expectedDays = 7), 0.01)
    }

    @Test
    fun loggingConsistencyIgnoresZeroCalorieDays() {
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 0),
        )
        assertEquals(50.0, calculator.loggingConsistency(days, expectedDays = 2), 0.001)
    }

    // --- dailyAdherencePercent: per-day graded primitive ---

    @Test
    fun dailyAdherenceIsHundredOnTarget() {
        assertEquals(100.0, calculator.dailyAdherencePercent(2550, 2550), 0.001)
    }

    @Test
    fun dailyAdherenceGradesDeviation() {
        // 10% off -> 90
        assertEquals(90.0, calculator.dailyAdherencePercent(2805, 2550), 0.001)
    }

    @Test
    fun dailyAdherenceZeroWhenNotLogged() {
        assertEquals(0.0, calculator.dailyAdherencePercent(0, 2550), 0.001)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail to compile / fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.AdherenceCalculatorTest"`
Expected: FAIL — `calculate` signature no longer matches (old one needs `expectedDays`), `loggingConsistency` unresolved.

- [ ] **Step 3: Rewrite `AdherenceCalculator.kt`**

Replace the whole file with:

```kotlin
package com.zack.recomptracker.domain.adherence

import java.time.LocalDate
import kotlin.math.abs

data class NutritionDay(
    val date: LocalDate,
    val calories: Int,
)

class AdherenceCalculator {

    /**
     * Per-day graded closeness to target: 100 - |calories - target| / target * 100,
     * clamped to 0..100. Returns 0 when nothing was logged (calories <= 0). This is the
     * single source of truth for a day's score — every adherence consumer routes through it.
     */
    fun dailyAdherencePercent(calories: Int, targetCalories: Int): Double {
        if (calories <= 0 || targetCalories <= 0) return 0.0
        val delta = abs(calories - targetCalories).toDouble() / targetCalories.toDouble()
        return (100.0 - (delta * 100.0)).coerceIn(0.0, 100.0)
    }

    /**
     * Adherence QUALITY: the average graded daily score across LOGGED days only.
     * Days with no intake (calories <= 0) are excluded from both numerator and denominator,
     * so this answers "how close to target on the days you tracked" — independent of how often
     * you tracked (that is [loggingConsistency]). Returns 0 if there are no logged days or the
     * target is invalid.
     */
    fun calculate(days: List<NutritionDay>, targetCalories: Int): Double {
        if (targetCalories <= 0) return 0.0
        val logged = days.distinctBy { it.date }.filter { it.calories > 0 }
        if (logged.isEmpty()) return 0.0
        val sum = logged.sumOf { dailyAdherencePercent(it.calories, targetCalories) }
        return sum / logged.size.toDouble()
    }

    /**
     * Logging CONSISTENCY: the fraction of expected days that have any intake logged.
     * Separate from adherence quality so a diligent-but-imperfect logger and a non-logger are
     * not conflated. Returns 0 if expectedDays <= 0.
     */
    fun loggingConsistency(days: List<NutritionDay>, expectedDays: Int): Double {
        if (expectedDays <= 0) return 0.0
        val loggedDays = days.distinctBy { it.date }.count { it.calories > 0 }
        return (loggedDays.toDouble() / expectedDays.toDouble()) * 100.0
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.AdherenceCalculatorTest"`
Expected: PASS (all 11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/adherence/AdherenceCalculator.kt \
        app/src/test/java/com/zack/recomptracker/domain/AdherenceCalculatorTest.kt
git commit -m "feat: graded adherence quality + separate logging consistency

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Lower adherence gate 85 → 80 and correct `LOW_ADHERENCE` wording

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/adjustment/AdjustmentModels.kt:23`
- Modify: `app/src/main/java/com/zack/recomptracker/domain/adjustment/AdjustmentEngine.kt:21`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt:14`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt:30`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt:25`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt:190`
- Test: `app/src/test/java/com/zack/recomptracker/domain/AdjustmentEngineTest.kt`

- [ ] **Step 1: Write a failing test for the new 80 boundary**

Add this test to `AdjustmentEngineTest.kt` (the existing `input(...)` helper defaults `daysLogged = 14` and other signals to neutral; if your helper differs, pass `daysLogged = 14` explicitly):

```kotlin
    @Test
    fun adherenceBetween80And85NoLongerBlocks() {
        // 82% would have failed the old 85 gate; with the new 80 default it must pass through
        // to the trend rules instead of returning WAIT_FOR_DATA / LOW_ADHERENCE.
        val result = AdjustmentEngine().evaluate(input(adherencePercent = 82.0))
        assertFalse(result.reasonCodes.contains("LOW_ADHERENCE"))
    }
```

Ensure `import org.junit.Assert.assertFalse` is present at the top of the test file (add it if missing).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.AdjustmentEngineTest"`
Expected: FAIL — with the default still 85, `82.0 < 85` triggers `LOW_ADHERENCE`.

- [ ] **Step 3: Lower the threshold default in `AdjustmentModels.kt`**

Change line 23 from:

```kotlin
    val adherenceMinimumPercent: Double = 85.0,
```
to:
```kotlin
    val adherenceMinimumPercent: Double = 80.0,
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.AdjustmentEngineTest"`
Expected: PASS (existing LOW_ADHERENCE test uses 70.0, still < 80, so it still passes).

- [ ] **Step 5: Reword the engine summary in `AdjustmentEngine.kt`**

Change line 21 from:
```kotlin
                summary = "Hold calories and improve logging consistency before reviewing.",
```
to:
```kotlin
                summary = "Hold calories — intake was too far from target too often to draw a conclusion.",
```

- [ ] **Step 6: Correct the `LOW_ADHERENCE` description in `InsightPromptBuilder.kt`**

Change line 190 from:
```kotlin
            "LOW_ADHERENCE" to "Logging consistency is too low to draw a reliable conclusion",
```
to:
```kotlin
            "LOW_ADHERENCE" to "Intake was too far from target too often to draw a reliable conclusion",
```

- [ ] **Step 7: Lower the matching defaults so customised vs default users stay consistent**

In `PlanPreferences.kt` line 14, change `= 85.0,` to `= 80.0,`.

In `AppPreferences.kt` line 30, change:
```kotlin
            adherenceMinimumPercent = prefs[Keys.AdherenceMinimumPercent] ?: 85.0,
```
to:
```kotlin
            adherenceMinimumPercent = prefs[Keys.AdherenceMinimumPercent] ?: 80.0,
```

In `PlanViewModel.kt` line 25, change:
```kotlin
    val adherenceMinimumPercent: String = "85",
```
to:
```kotlin
    val adherenceMinimumPercent: String = "80",
```

- [ ] **Step 8: Run the full unit suite + type-check**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/adjustment/AdjustmentModels.kt \
        app/src/main/java/com/zack/recomptracker/domain/adjustment/AdjustmentEngine.kt \
        app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt \
        app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt \
        app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt \
        app/src/test/java/com/zack/recomptracker/domain/AdjustmentEngineTest.kt
git commit -m "feat: lower adherence gate to 80, fix LOW_ADHERENCE wording

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Update `DashboardViewModel` to the new `calculate` signature

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt:183`

- [ ] **Step 1: Update the call site**

Change line 183 from:
```kotlin
        val adherence    = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories, expectedDays = 14)
```
to:
```kotlin
        val adherence    = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories)
```

Note: the engine's `daysLogged` input (line 199) is computed separately from `loggedDates` and is unchanged — it remains the logging-consistency signal.

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no remaining references to the old 3-arg `calculate`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt
git commit -m "refactor: dashboard uses graded adherence calculate()

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Route the coach `get_weekly_trends` tool through the calculator

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (imports, new private val, `getWeeklyTrends`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt:438-464`

Rationale for a private `val` instead of constructor injection: `AdherenceCalculator` is stateless and trivially constructed, and injecting it would force edits to ~39 existing test instantiations and `AppContainer`. A private val keeps the change local.

- [ ] **Step 1: Rewrite the weekly-trends test to expect graded adherence + days_logged**

Replace the test at lines 438-464 with:

```kotlin
    @Test
    fun `get_weekly_trends returns graded adherence and days_logged`() = runTest {
        val today = LocalDate.of(2026, 6, 5)
        val start = today.minusDays(6) // 2026-05-30
        // getWeekMacros returns only dates that have entries; absent = not logged
        val macroMap = mapOf(
            LocalDate.of(2026, 5, 30) to MacroTotals(calories = 2400),
            LocalDate.of(2026, 5, 31) to MacroTotals(calories = 2300),
            // 2026-06-01 absent → not logged
            LocalDate.of(2026, 6, 2) to MacroTotals(calories = 2550),
            LocalDate.of(2026, 6, 3) to MacroTotals(calories = 2200),
            LocalDate.of(2026, 6, 4) to MacroTotals(calories = 2600),
            LocalDate.of(2026, 6, 5) to MacroTotals(calories = 2450),
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2550)))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertTrue("Should contain week_start", result.contains("2026-05-30"))
        assertTrue("Should contain week_end", result.contains("2026-06-05"))
        // 6 of 7 days logged, surfaced as its own signal
        assertTrue("Should report days_logged", result.contains("\"days_logged\":6"))
        // Graded quality across the 6 logged days averages ~94 (not the old 6/7 = 85 count)
        assertTrue("Should report graded adherence ~94", result.contains("\"adherence_percent\":94"))
    }

    @Test
    fun `get_weekly_trends does not score an over-target day as fully adherent`() = runTest {
        val today = LocalDate.of(2026, 6, 5)
        val start = today.minusDays(6)
        // Single logged day, far over target → graded score must be well below 100.
        val macroMap = mapOf(LocalDate.of(2026, 6, 5) to MacroTotals(calories = 4000))
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2550)))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertFalse("Over-target day must not be 100% adherent", result.contains("\"adherence_percent\":100"))
        assertTrue("Should still report one logged day", result.contains("\"days_logged\":1"))
    }
```

Ensure these imports exist at the top of the test file (add any missing):
`import com.zack.recomptracker.data.preferences.PlanPreferences`, `import kotlinx.coroutines.flow.flowOf`, `import org.junit.Assert.assertFalse`. (`PlanPreferences` and `flowOf` are already used elsewhere in this file; `assertFalse` is used at line 256.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest"`
Expected: FAIL — current output has no `days_logged`, and `adherence_percent` is still `85` from `loggedDays/7`.

- [ ] **Step 3: Add imports and a private calculator in `CoachToolExecutor.kt`**

Add these imports next to the existing domain import on line 10 (`import com.zack.recomptracker.domain.food.MealEntryTypes`):

```kotlin
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
```

Add a private val inside the class body, right after the constructor (after line 18 `) {`):

```kotlin
    private val adherenceCalculator = AdherenceCalculator()
```

- [ ] **Step 4: Replace `getWeeklyTrends()` (lines 57-74)**

Replace the whole function with:

```kotlin
    private suspend fun getWeeklyTrends(): String {
        val today = dateProvider.today()
        val start = today.minusDays(6)
        val macroMap = logRepository.getWeekMacros(start, today)
        val targetCalories = planRepository.preferences.first().targetCalories
        val dailyEntries = (0..6).joinToString(separator = ",") { offset ->
            val date = start.plusDays(offset.toLong())
            val m = macroMap[date]
            val cals = m?.calories ?: 0
            val prot = m?.proteinG ?: 0.0
            val carbs = m?.carbsG ?: 0.0
            val fat = m?.fatG ?: 0.0
            """{"date":"$date","calories":$cals,"protein_g":$prot,"carbs_g":$carbs,"fat_g":$fat}"""
        }
        val nutritionDays = (0..6).map { offset ->
            val date = start.plusDays(offset.toLong())
            NutritionDay(date, macroMap[date]?.calories ?: 0)
        }
        // adherence_percent = graded closeness on logged days; days_logged = the separate
        // logging-consistency signal. A logged-but-over-target day no longer reads as 100%.
        val adherencePercent = adherenceCalculator.calculate(nutritionDays, targetCalories).toInt()
        val daysLogged = macroMap.values.count { it.calories > 0 }
        return """{"week_start":"$start","week_end":"$today","daily_macros":[$dailyEntries],"adherence_percent":$adherencePercent,"days_logged":$daysLogged}"""
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachToolExecutorTest"`
Expected: PASS (both new weekly-trends tests plus the rest of the file).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt \
        app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorTest.kt
git commit -m "feat: coach get_weekly_trends reports graded adherence + days_logged

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Update the AI-coach docs for the new tool output

**Files:**
- Modify: `docs/ai-coach.md` (the `get_weekly_trends` row of the Tool List table)

- [ ] **Step 1: Update the `get_weekly_trends` table row**

Find the row:
```
| `get_weekly_trends` | — | JSON: 7-day daily macros + adherence % | Last 7 days ending today. |
```
Replace it with:
```
| `get_weekly_trends` | — | JSON: 7-day daily macros, `adherence_percent` (graded closeness on logged days), `days_logged` | Last 7 days ending today. `adherence_percent` is the average per-day graded score over days that were logged (not a logged-days count); `days_logged` is the separate logging-consistency signal. |
```

- [ ] **Step 2: Commit**

```bash
git add docs/ai-coach.md
git commit -m "docs: document graded adherence + days_logged in get_weekly_trends"
```

---

### Final verification

- [ ] **Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Type-check the whole module**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

---

## Self-Review Notes

- **Spec coverage:** #1 unify → Tasks 3 & 4 (Dashboard + coach onto `calculate`); #2 separate → `loggingConsistency` (Task 1) + `days_logged` (Task 4) + corrected `LOW_ADHERENCE` wording (Task 2); #3 graded → Task 1 `calculate`. #4 explicitly out of scope — no task. Threshold 85→80 fork → Task 2. Logged-days-only denominator fork → Task 1 `calculate`.
- **Type consistency:** `calculate(days, targetCalories)` (2-arg) used identically in Tasks 3 & 4; `NutritionDay(date, calories)`, `loggingConsistency(days, expectedDays)` consistent across tasks.
- **No placeholders:** every code/edit step shows exact before/after.
- **Untouched & verified safe:** `ProgressViewModel` already uses `dailyAdherencePercent` (unchanged); existing `AdjustmentEngineTest` uses 70.0/90.0 which stay correct against the 80 gate; `AppContainer` `AdherenceCalculator()` no-arg constructor unchanged.
