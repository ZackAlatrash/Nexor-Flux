# Computed Insight Engine (Option B — Clean 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect non-obvious nutrition patterns deterministically and surface the top one as a cloud-phrased card on the home screen.

**Architecture:** A pure-Kotlin `domain/insight/` module (models + four detector functions + a ranking engine) computes the single highest-priority `InsightFact` from the last 14 days of per-day nutrition. A new cloud-only `WEEKLY_PATTERN` insight kind feeds that fact's statement to the cloud model, which rephrases it; the home screen shows it via the existing `GeneratedInsightCard`. Local/stub paths keep the kind hidden.

**Tech Stack:** Kotlin, JUnit4, Jetpack Compose, Gradle (`:app:testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-06-15-insight-engine-design.md`

---

## Design notes (read before starting)

- **No new Room/DataStore/repository code.** `DashboardViewModel.buildState` already builds `mealsByDate` for the 14-day window and iterates `(0..13)`; we build the `DayNutrition` list the same way.
- **Detectors are pure top-level `internal` functions** in package `com.zack.recomptracker.domain.insight`; `InsightEngine` is a stateless `object` (no DI, no `AppContainer` change).
- **Cloud-only:** `CloudInsightCoordinator` builds the real prompt. `GemmaInsightCoordinator` (production local) no-ops `WEEKLY_PATTERN` to `Disabled` so the card is hidden when the active backend is local. `StubInsightCoordinator` (tests/previews) returns a canned string so the card is visible in dev.
- **All numeric thresholds are private consts** in the detector file, easy to tune.

### Commands
- One test class: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.<ClassName>"`
- Whole module: `./gradlew :app:testDebugUnitTest`
- Type-check: `./gradlew :app:compileDebugKotlin`

---

## Task 1: Domain models

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/insight/InsightModels.kt`

No test — these are pure data declarations with no behavior (verified by compilation in later tasks).

- [ ] **Step 1: Create the models file**

```kotlin
package com.zack.recomptracker.domain.insight

import java.time.LocalDate

/** One calendar day's eaten nutrition. [logged] is false for days with no eaten intake. */
data class DayNutrition(
    val date: LocalDate,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val logged: Boolean,
)

/** Plan targets the detectors compare against. */
data class NutritionTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val calorieZoneLower: Int,
    val calorieZoneUpper: Int,
)

enum class InsightFactType { DERAILMENT_DAY, WEAKEST_MACRO, WEEKDAY_WEEKEND, STREAK }

/** A computed, non-obvious fact. [statement] carries the numbers and is the LLM input + dedup key. */
data class InsightFact(
    val type: InsightFactType,
    val priority: Int,
    val statement: String,
)
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/InsightModels.kt
git commit -m "feat(insight-engine): add domain models for computed insight facts"
```

---

## Task 2: Weekday/weekend divergence detector

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `PatternDetectorsTest.kt`:

```kotlin
package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PatternDetectorsTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    /** 14 consecutive days ending [end]; [calsFor] maps each date to its calories. proteins/etc default to target. */
    private fun days(
        end: LocalDate = LocalDate.of(2026, 6, 14),
        logged: (LocalDate) -> Boolean = { true },
        calsFor: (LocalDate) -> Int,
        proteinFor: (LocalDate) -> Double = { targets.proteinG.toDouble() },
    ): List<DayNutrition> = (0..13).map { offset ->
        val d = end.minusDays((13 - offset).toLong())
        DayNutrition(d, calsFor(d), proteinFor(d), targets.carbsG.toDouble(), targets.fatG.toDouble(), logged(d))
    }

    private fun isWeekend(d: LocalDate) =
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    @Test
    fun `weekday-weekend fires when weekend calories are much higher`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2800 else 2200 }),
            targets,
        )
        assertEquals(InsightFactType.WEEKDAY_WEEKEND, fact?.type)
        assertTrue(fact!!.statement.contains("2800"))
        assertTrue(fact.statement.contains("2200"))
        assertTrue(fact.statement.contains("+600"))
    }

    @Test
    fun `weekday-weekend does not fire when gap is small`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2250 else 2200 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `weekday-weekend needs enough weekend days`() {
        // Only weekdays logged → fewer than 2 weekend samples.
        val fact = detectWeekdayWeekend(
            days(logged = { !isWeekend(it) }, calsFor = { 2800 }),
            targets,
        )
        assertNull(fact)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: FAIL — `detectWeekdayWeekend` unresolved.

- [ ] **Step 3: Create the detector file with the first detector**

Create `PatternDetectors.kt`:

```kotlin
package com.zack.recomptracker.domain.insight

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val WEEKDAY_WEEKEND_MIN_GAP = 250.0

internal fun detectWeekdayWeekend(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val logged = days.filter { it.logged }
    val weekend = logged.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
    val weekday = logged.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
    if (weekday.size < 3 || weekend.size < 2) return null
    val weekendMean = weekend.map { it.calories }.average()
    val weekdayMean = weekday.map { it.calories }.average()
    val gap = weekendMean - weekdayMean
    if (abs(gap) < WEEKDAY_WEEKEND_MIN_GAP) return null
    val gapInt = gap.roundToInt()
    val signed = if (gapInt >= 0) "+$gapInt" else "$gapInt"
    val statement =
        "Your weekend days average ${weekendMean.roundToInt()} kcal vs ${weekdayMean.roundToInt()} on weekdays ($signed kcal)."
    val priority = 20 + ((abs(gap) - WEEKDAY_WEEKEND_MIN_GAP) / 50).toInt().coerceIn(0, 15)
    return InsightFact(InsightFactType.WEEKDAY_WEEKEND, priority, statement)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt
git commit -m "feat(insight-engine): weekday vs weekend divergence detector"
```

---

## Task 3: Derailment-day detector

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt`

- [ ] **Step 1: Add the failing tests**

Add these methods to `PatternDetectorsTest` (the `days`/`targets` helpers already exist):

```kotlin
    @Test
    fun `derailment fires when one day drives most of the surplus`() {
        // 7 logged days: six at target (no surplus), one at +1500.
        val end = LocalDate.of(2026, 6, 14)
        val spike = end // most recent day
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val cals = if (d == spike) 3700 else 2200
            DayNutrition(d, cals, 165.0, 320.0, 68.0, logged = offset >= 7) // only last 7 logged
        }
        val fact = detectDerailmentDay(list, targets)
        assertEquals(InsightFactType.DERAILMENT_DAY, fact?.type)
        assertTrue(fact!!.statement.contains("%"))
        assertTrue(fact.statement.contains("surplus"))
    }

    @Test
    fun `derailment does not fire when surplus is spread evenly`() {
        // Seven logged days each slightly over target → no single dominant day.
        val list = days(calsFor = { 2350 }).mapIndexed { i, d -> d.copy(logged = i >= 7) }
        val fact = detectDerailmentDay(list, targets)
        assertNull(fact)
    }

    @Test
    fun `derailment does not fire below minimum weekly surplus`() {
        val list = days(calsFor = { 2200 }).mapIndexed { i, d -> d.copy(logged = i >= 7) }
        assertNull(detectDerailmentDay(list, targets))
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: FAIL — `detectDerailmentDay` unresolved.

- [ ] **Step 3: Add the detector**

Append to `PatternDetectors.kt`:

```kotlin
private const val DERAILMENT_MIN_WEEKLY_SURPLUS = 700
private const val DERAILMENT_MIN_SHARE_PCT = 60

internal fun detectDerailmentDay(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val recent = days.filter { it.logged }.sortedBy { it.date }.takeLast(7)
    if (recent.size < 4) return null
    val surpluses = recent.map { it to (it.calories - targets.calories).coerceAtLeast(0) }
    val weeklySurplus = surpluses.sumOf { it.second }
    if (weeklySurplus < DERAILMENT_MIN_WEEKLY_SURPLUS) return null
    val sorted = surpluses.sortedByDescending { it.second }
    for (n in 1..2) {
        val top = sorted.take(n)
        if (top.any { it.second == 0 }) continue
        val sharePct = (top.sumOf { it.second }.toDouble() / weeklySurplus * 100).roundToInt()
        if (sharePct >= DERAILMENT_MIN_SHARE_PCT) {
            val label = top.joinToString(" and ") {
                it.first.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            }
            val statement = "$label drove $sharePct% of this week's calorie surplus."
            val priority = 30 + ((sharePct - DERAILMENT_MIN_SHARE_PCT) / 5).coerceIn(0, 15)
            return InsightFact(InsightFactType.DERAILMENT_DAY, priority, statement)
        }
    }
    return null
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt
git commit -m "feat(insight-engine): derailment-day detector"
```

---

## Task 4: Weakest-macro detector

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt`

- [ ] **Step 1: Add the failing tests**

Add to `PatternDetectorsTest`:

```kotlin
    @Test
    fun `weakest macro flags protein when calories are in zone`() {
        // Calories ~2200 (in 2100-2300 zone); protein ~60% of target, carbs & fat on target.
        val fact = detectWeakestMacro(
            days(calsFor = { 2200 }, proteinFor = { 100.0 }),
            targets,
        )
        assertEquals(InsightFactType.WEAKEST_MACRO, fact?.type)
        assertTrue(fact!!.statement.contains("protein"))
        assertTrue(fact.statement.contains("%"))
    }

    @Test
    fun `weakest macro does not fire when calories are out of zone`() {
        // Calories 2800 (above zone) → not a "calories on point" situation.
        val fact = detectWeakestMacro(
            days(calsFor = { 2800 }, proteinFor = { 100.0 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `weakest macro does not fire when all macros near target`() {
        val fact = detectWeakestMacro(
            days(calsFor = { 2200 }, proteinFor = { 165.0 }),
            targets,
        )
        assertNull(fact)
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: FAIL — `detectWeakestMacro` unresolved.

- [ ] **Step 3: Add the detector**

Append to `PatternDetectors.kt`:

```kotlin
private const val WEAKEST_MACRO_MAX_PCT = 85

internal fun detectWeakestMacro(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    val logged = days.filter { it.logged }
    if (logged.size < 4) return null
    val meanCalories = logged.map { it.calories }.average()
    if (meanCalories < targets.calorieZoneLower || meanCalories > targets.calorieZoneUpper) return null

    // rank 0 = protein wins ties (most actionable lever).
    data class Macro(val name: String, val pct: Int, val rank: Int)
    val macros = listOf(
        Macro("protein", attainPct(logged.map { it.proteinG }, targets.proteinG), 0),
        Macro("carbs", attainPct(logged.map { it.carbsG }, targets.carbsG), 1),
        Macro("fat", attainPct(logged.map { it.fatG }, targets.fatG), 2),
    )
    val weakest = macros.minWithOrNull(compareBy({ it.pct }, { it.rank })) ?: return null
    if (weakest.pct >= WEAKEST_MACRO_MAX_PCT) return null
    val statement = "Calories are on point, but ${weakest.name} is averaging ${weakest.pct}% of target — your main gap."
    val priority = 30 + ((WEAKEST_MACRO_MAX_PCT - weakest.pct) / 5).coerceIn(0, 15)
    return InsightFact(InsightFactType.WEAKEST_MACRO, priority, statement)
}

private fun attainPct(values: List<Double>, target: Int): Int {
    if (target <= 0) return 100
    return (values.average() / target * 100).roundToInt()
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt
git commit -m "feat(insight-engine): weakest-macro detector"
```

---

## Task 5: Streak detector

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt`

- [ ] **Step 1: Add the failing tests**

Add to `PatternDetectorsTest`:

```kotlin
    @Test
    fun `streak fires for consecutive protein-target days`() {
        val end = LocalDate.of(2026, 6, 14)
        // Last 5 days at/above target, earlier day below.
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val protein = if (offset >= 9) 180.0 else 120.0
            DayNutrition(d, 2200, protein, 320.0, 68.0, logged = true)
        }
        val fact = detectStreak(list, targets)
        assertEquals(InsightFactType.STREAK, fact?.type)
        assertTrue(fact!!.statement.contains("5 days"))
    }

    @Test
    fun `streak ignores a not-yet-logged most-recent day`() {
        val end = LocalDate.of(2026, 6, 14)
        // Today (most recent) unlogged; previous 4 at target.
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val logged = offset != 13
            val protein = if (offset in 9..12) 180.0 else 120.0
            DayNutrition(d, if (logged) 2200 else 0, if (logged) protein else 0.0, 320.0, 68.0, logged)
        }
        val fact = detectStreak(list, targets)
        assertEquals(InsightFactType.STREAK, fact?.type)
        assertTrue(fact!!.statement.contains("4 days"))
    }

    @Test
    fun `streak does not fire below minimum`() {
        val end = LocalDate.of(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val protein = if (offset >= 12) 180.0 else 120.0 // only last 2 hit
            DayNutrition(d, 2200, protein, 320.0, 68.0, logged = true)
        }
        assertNull(detectStreak(list, targets))
    }
```

- [ ] **Step 2: Run to verify the new tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: FAIL — `detectStreak` unresolved.

- [ ] **Step 3: Add the detector**

Append to `PatternDetectors.kt`:

```kotlin
private const val STREAK_MIN_DAYS = 3

internal fun detectStreak(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
    if (targets.proteinG <= 0) return null
    val ordered = days.sortedByDescending { it.date } // most recent first
    // Allow a not-yet-logged most-recent day (e.g. today) to not break the streak.
    val startIndex = if (ordered.isNotEmpty() && !ordered[0].logged) 1 else 0
    var streak = 0
    for (i in startIndex until ordered.size) {
        val d = ordered[i]
        if (d.logged && d.proteinG >= targets.proteinG) streak++ else break
    }
    if (streak < STREAK_MIN_DAYS) return null
    val statement = "$streak days running at your protein target — keep it going."
    val priority = 10 + ((streak - STREAK_MIN_DAYS) * 4).coerceAtMost(25)
    return InsightFact(InsightFactType.STREAK, priority, statement)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.PatternDetectorsTest"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/PatternDetectors.kt app/src/test/java/com/zack/recomptracker/domain/insight/PatternDetectorsTest.kt
git commit -m "feat(insight-engine): protein streak detector"
```

---

## Task 6: Ranking engine

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/insight/InsightEngine.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/insight/InsightEngineTest.kt`

- [ ] **Step 1: Write the failing test**

Create `InsightEngineTest.kt`:

```kotlin
package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InsightEngineTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    @Test
    fun `returns null when no detector fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val calm = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            DayNutrition(d, 2200, 165.0, 320.0, 68.0, logged = true)
        }
        assertNull(InsightEngine.detectTopFact(calm, targets))
    }

    @Test
    fun `picks the highest-priority fact`() {
        // Weekend spike (weekday/weekend fires) AND a single derailment day → derailment has higher base priority.
        val end = LocalDate.of(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            // last 7 days carry one big spike for derailment
            val cals = when {
                offset == 13 -> 3700
                weekend -> 2700
                else -> 2200
            }
            DayNutrition(d, cals, 165.0, 320.0, 68.0, logged = true)
        }
        val fact = InsightEngine.detectTopFact(list, targets)
        assertEquals(InsightFactType.DERAILMENT_DAY, fact?.type)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.InsightEngineTest"`
Expected: FAIL — `InsightEngine` unresolved.

- [ ] **Step 3: Create the engine**

Create `InsightEngine.kt`:

```kotlin
package com.zack.recomptracker.domain.insight

/** Runs all pattern detectors and returns the single highest-priority fact, or null. */
object InsightEngine {

    fun detectTopFact(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
        val facts = listOfNotNull(
            detectDerailmentDay(days, targets),
            detectWeakestMacro(days, targets),
            detectWeekdayWeekend(days, targets),
            detectStreak(days, targets),
        )
        // Highest priority wins; ties broken by a fixed type rank.
        return facts.maxWithOrNull(compareBy({ it.priority }, { typeRank(it.type) }))
    }

    private fun typeRank(type: InsightFactType): Int = when (type) {
        InsightFactType.DERAILMENT_DAY -> 3
        InsightFactType.WEAKEST_MACRO -> 2
        InsightFactType.WEEKDAY_WEEKEND -> 1
        InsightFactType.STREAK -> 0
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.insight.InsightEngineTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/insight/InsightEngine.kt app/src/test/java/com/zack/recomptracker/domain/insight/InsightEngineTest.kt
git commit -m "feat(insight-engine): top-fact ranking engine"
```

---

## Task 7: AI-layer wiring (new cloud-only insight kind)

This task is cohesive: adding the new sealed-interface subtype breaks the exhaustive `when`s in all coordinators until each handles it, so it's done as one compiling unit.

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/PatternInsightContext.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaInsightCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/StubInsightCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/PatternInsightPromptTest.kt`

- [ ] **Step 1: Write the failing test**

Create `PatternInsightPromptTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.insight.InsightFact
import com.zack.recomptracker.domain.insight.InsightFactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternInsightPromptTest {

    private val builder = InsightPromptBuilder()
    private val fact = InsightFact(
        InsightFactType.WEEKDAY_WEEKEND,
        priority = 27,
        statement = "Your weekend days average 2800 kcal vs 2200 on weekdays (+600 kcal).",
    )
    private val context = PatternInsightContext(fact)

    @Test
    fun `prompt includes the computed finding verbatim`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue(fact.statement in prompt)
    }

    @Test
    fun `prompt instructs to lead with the number and not invent`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue("Lead with the specific number" in prompt)
        assertTrue(prompt.contains("do not invent", ignoreCase = true))
    }

    @Test
    fun `prompt requests short supportive output`() {
        val prompt = builder.buildPatternInsightPrompt(context)
        assertTrue("2" in prompt)
        assertTrue(prompt.contains("supportive", ignoreCase = true) || prompt.contains("non-judgmental", ignoreCase = true))
    }

    @Test
    fun `context key is the fact statement and data is sufficient`() {
        assertEquals(fact.statement, context.key())
        assertTrue(context.hasSufficientData)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.PatternInsightPromptTest"`
Expected: FAIL — `PatternInsightContext` / `buildPatternInsightPrompt` unresolved (compile error).

- [ ] **Step 3a: Create `PatternInsightContext.kt`**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.insight.InsightFact

/** Carries one computed [InsightFact] for the cloud model to phrase. */
data class PatternInsightContext(val fact: InsightFact) {
    val hasSufficientData: Boolean get() = true
    fun key(): String = fact.statement
}
```

- [ ] **Step 3b: Extend `InsightRequest.kt`**

Add `WEEKLY_PATTERN` to the enum and the new subtype:

```kotlin
enum class InsightKind { PROGRESS_TREND, RECOVERY_READINESS, REST_OF_DAY, WEEKLY_PATTERN }
```

Add inside the `sealed interface InsightRequest` body (after `RestOfDay`):

```kotlin
    data class WeeklyPattern(val context: PatternInsightContext) : InsightRequest {
        override val kind = InsightKind.WEEKLY_PATTERN
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }
```

- [ ] **Step 3c: Add `buildPatternInsightPrompt` to `InsightPromptBuilder.kt`**

Add this method (e.g. after `buildRestOfDayPrompt`):

```kotlin
    fun buildPatternInsightPrompt(context: PatternInsightContext): String = buildString {
        appendLine("You are a body-recomposition coach highlighting one pattern you noticed in the athlete's recent data.")
        appendLine("Rephrase the finding below into exactly 1–2 short, encouraging sentences. Lead with the specific number. No preamble or filler.")
        appendLine("Use only the finding below; do not invent or calculate any new numbers.")
        appendLine("Keep a calm, supportive, non-judgmental tone — frame it as an observation, not a scolding.")
        appendLine()
        appendLine("Finding: ${context.fact.statement}")
    }
```

- [ ] **Step 3d: Handle the new kind in `CloudInsightCoordinator.kt`**

In `onInsightVisible`, extend the `when (request)` that builds `prompt`:

```kotlin
        val prompt = when (request) {
            is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context)
            is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context)
            is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context)
            is InsightRequest.WeeklyPattern -> promptBuilder.buildPatternInsightPrompt(request.context)
        }
```

- [ ] **Step 3e: No-op the new kind in `GemmaInsightCoordinator.kt` (cloud-only)**

At the **top** of `onInsightVisible`, before `if (!request.hasSufficientData)`:

```kotlin
        if (request.kind == InsightKind.WEEKLY_PATTERN) {
            // WEEKLY_PATTERN is cloud-only; keep it hidden on the local backend.
            insightStates.getValue(request.kind).value = AiInsightState.Disabled
            return
        }
```

And make the `when (request)` in `generateInsight` exhaustive (unreachable due to the guard):

```kotlin
            val prompt = when (request) {
                is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context)
                is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context)
                is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context)
                is InsightRequest.WeeklyPattern -> error("WEEKLY_PATTERN is cloud-only; handled in onInsightVisible")
            }
```

- [ ] **Step 3f: Give the stub a canned string in `StubInsightCoordinator.kt`**

Extend `stubInsightText`:

```kotlin
    private fun stubInsightText(request: InsightRequest): String = when (request) {
        is InsightRequest.ProgressTrend -> "Your trends look stable this period."
        is InsightRequest.RecoveryReadiness -> "Your recovery looks on track today."
        is InsightRequest.RestOfDay -> "You're tracking well for the day."
        is InsightRequest.WeeklyPattern -> request.context.fact.statement
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.PatternInsightPromptTest"`
Expected: PASS (4 tests). Then confirm nothing else broke in the AI package:
`./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/ app/src/test/java/com/zack/recomptracker/ai/PatternInsightPromptTest.kt
git commit -m "feat(insight-engine): cloud-only WEEKLY_PATTERN insight kind + prompt"
```

---

## Task 8: Mapper + DashboardViewModel wiring

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/dashboard/PatternInsightMapper.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/dashboard/PatternInsightMapperTest.kt`

- [ ] **Step 1: Write the failing test**

Create `PatternInsightMapperTest.kt`:

```kotlin
package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.insight.DayNutrition
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PatternInsightMapperTest {

    private val prefs = PlanPreferences(
        targetCalories = 2200, targetProteinG = 165, targetCarbsG = 320, targetFatG = 68,
        calorieZoneLowerBound = 2100, calorieZoneUpperBound = 2300,
    )

    @Test
    fun `returns null when no pattern fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val days = (0..13).map { offset ->
            DayNutrition(end.minusDays((13 - offset).toLong()), 2200, 165.0, 320.0, 68.0, logged = true)
        }
        assertNull(buildPatternInsightContext(days, prefs))
    }

    @Test
    fun `returns a context when a pattern fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val days = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            DayNutrition(d, if (weekend) 2900 else 2200, 165.0, 320.0, 68.0, logged = true)
        }
        val ctx = buildPatternInsightContext(days, prefs)
        assertNotNull(ctx)
        assertNotNull(ctx!!.fact)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.PatternInsightMapperTest"`
Expected: FAIL — `buildPatternInsightContext` unresolved.

- [ ] **Step 3a: Create the mapper**

Create `PatternInsightMapper.kt`:

```kotlin
package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.insight.DayNutrition
import com.zack.recomptracker.domain.insight.InsightEngine
import com.zack.recomptracker.domain.insight.NutritionTargets

/**
 * Pure mapper: runs [InsightEngine] over the per-day window and wraps the top fact
 * in a [PatternInsightContext]. Returns null when no pattern fires (card stays hidden).
 */
fun buildPatternInsightContext(
    days: List<DayNutrition>,
    prefs: PlanPreferences,
): PatternInsightContext? {
    val targets = NutritionTargets(
        calories = prefs.targetCalories,
        proteinG = prefs.targetProteinG,
        carbsG = prefs.targetCarbsG,
        fatG = prefs.targetFatG,
        calorieZoneLower = prefs.calorieZoneLowerBound,
        calorieZoneUpper = prefs.calorieZoneUpperBound,
    )
    val fact = InsightEngine.detectTopFact(days, targets) ?: return null
    return PatternInsightContext(fact)
}
```

- [ ] **Step 3b: Wire `DashboardViewModel.kt`**

Add imports:

```kotlin
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.domain.insight.DayNutrition
```

Add a field to `DashboardUiState` (after `adjustmentInput`):

```kotlin
    val patternInsightContext: PatternInsightContext? = null,
```

In `buildState`, just before the `return DashboardUiState(...)`, build the day list and context (reuses the existing `mealsByDate`, `last14Start`):

```kotlin
        val patternDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            val totals = mealsByDate[date].orEmpty().macroTotals()
            DayNutrition(
                date = date,
                calories = totals.calories,
                proteinG = totals.proteinG,
                carbsG = totals.carbsG,
                fatG = totals.fatG,
                logged = totals.calories > 0,
            )
        }
        val patternInsightContext = buildPatternInsightContext(patternDays, preferences)
```

Add `patternInsightContext = patternInsightContext,` to the `DashboardUiState(...)` constructor call.

Add the exposed state + visibility/retry handlers (next to `aiInsightState`):

```kotlin
    val patternInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.WEEKLY_PATTERN)

    fun onPatternInsightVisible() {
        val ctx = _uiState.value.patternInsightContext ?: return
        aiInsightCoordinator.onInsightVisible(InsightRequest.WeeklyPattern(ctx))
    }

    fun retryPatternInsight() {
        val ctx = _uiState.value.patternInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.WeeklyPattern(ctx))
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.PatternInsightMapperTest"`
Expected: PASS (2 tests). Then type-check the ViewModel change: `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/PatternInsightMapper.kt app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/zack/recomptracker/ui/dashboard/PatternInsightMapperTest.kt
git commit -m "feat(insight-engine): compute pattern insight in DashboardViewModel"
```

---

## Task 9: Home screen card (reuse existing AI card components)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

No unit test (Compose UI); verified by compilation. Reuses the existing `GeneratedInsightCard` — no new composable.

- [ ] **Step 1: Add the import**

Ensure `DashboardScreen.kt` imports the existing card and AI state:

```kotlin
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.component.GeneratedInsightCard
```

- [ ] **Step 2: Collect state + trigger visibility in `HomeDashboardScreen`**

Inside `HomeDashboardScreen`, after the existing `collectAsStateWithLifecycle()` calls, add:

```kotlin
    val patternInsightState by viewModel.patternInsightState.collectAsStateWithLifecycle()
    LaunchedEffect(state.patternInsightContext?.key()) {
        viewModel.onPatternInsightVisible()
    }
```

Pass them to `HomeDashboardContent`:

```kotlin
    HomeDashboardContent(
        state = state,
        showWeeklyReviewBadge = badge,
        onOpenWeeklyReview = { weeklyReviewViewModel.open() },
        patternInsightState = patternInsightState,
        onRetryPatternInsight = viewModel::retryPatternInsight,
    )
```

(`LaunchedEffect` is already imported in this file; if not, add `import androidx.compose.runtime.LaunchedEffect`.)

- [ ] **Step 3: Render the card in `HomeDashboardContent`**

Add the two parameters (defaulted, so previews/other callers keep working):

```kotlin
@Composable
fun HomeDashboardContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    showWeeklyReviewBadge: Boolean = false,
    onOpenWeeklyReview: (() -> Unit)? = null,
    patternInsightState: AiInsightState = AiInsightState.Disabled,
    onRetryPatternInsight: () -> Unit = {},
) {
```

As the **first** item in the `LazyColumn` (before `MotivationalCard`), add the card, shown only when there's a fact and the cloud model is actually producing output (so it's hidden for non-cloud users and quiet weeks):

```kotlin
                if (state.patternInsightContext != null &&
                    (patternInsightState is AiInsightState.Generating ||
                        patternInsightState is AiInsightState.Ready ||
                        patternInsightState is AiInsightState.Error)
                ) {
                    item {
                        GeneratedInsightCard(
                            title = "Coach spotted",
                            state = patternInsightState,
                            onRetry = onRetryPatternInsight,
                        )
                    }
                }
```

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(insight-engine): show computed pattern card on home (reusing GeneratedInsightCard)"
```

---

## Task 10: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the whole unit-test module**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests green, including the existing AI/coordinator suites (which must still compile and pass with the new `WEEKLY_PATTERN` kind). If a pre-existing coordinator/routing test fails because it now must account for the new kind, update it minimally to match the no-op/disabled behavior and re-run.

- [ ] **Step 2: Type-check the app**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Final commit (only if Step 1 required incidental test fixes)**

```bash
git add -A
git commit -m "test(insight-engine): update coordinator tests for WEEKLY_PATTERN kind"
```

If no changes were needed, skip this commit.

---

## Self-review checklist (completed by plan author)

- **Spec coverage:** models (T1); 4 detectors (T2–T5); ranking engine (T6); cloud-only kind + context + prompt + coordinator dispatch + local/stub handling (T7); mapper + ViewModel + 14-day day-list construction with `logged` flag (T8); home card reusing `GeneratedInsightCard` (T9); verification (T10). Non-goals (expenditure, goal projection, schema/prefs) untouched. ✅
- **Placeholder scan:** every step has concrete code + exact commands/expected output. No TBD/TODO. ✅
- **Type consistency:** `DayNutrition`/`NutritionTargets`/`InsightFact`/`InsightFactType` defined in T1, used identically in T2–T8. Detector signatures `(List<DayNutrition>, NutritionTargets) -> InsightFact?` consistent across T2–T6 and `InsightEngine.detectTopFact`. `PatternInsightContext(fact)` with `.key()`/`.hasSufficientData` consistent T7–T9. `InsightRequest.WeeklyPattern(context)` + `InsightKind.WEEKLY_PATTERN` consistent across coordinators. Field names verified against `MacroTotals` (calories/proteinG/carbsG/fatG) and `PlanPreferences` (targetCalories/targetProteinG/targetCarbsG/targetFatG/calorieZoneLowerBound/calorieZoneUpperBound). ✅
- **Cloud-only invariant:** cloud builds prompt; Gemma no-ops to `Disabled`; stub returns canned text; home card only renders for Generating/Ready/Error. ✅
