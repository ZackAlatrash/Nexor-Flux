# Weekly Briefing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cloud-only, AI-narrated weekly briefing — a dashboard button (with a Monday reminder badge) that opens an overlay summarizing the week's verdict, lets the user apply a recommended calorie change, and hands off into the coach chat seeded with the briefing context.

**Architecture:** A pure-Kotlin layer computes the week's deterministic data (`WeeklyReviewComputer` → `WeeklyReviewData`) and an input signature. A cloud-only `WeeklyBriefingGenerator` asks the model for *prose only* (headline, narrative, per-signal interpretation, rationale, watch-next) and merges it onto the deterministic skeleton — the model never supplies numbers or the verdict. `WeeklyBriefingRepository` caches the result on the existing `weekly_reviews` row (Room v8→v9). `WeeklyReviewViewModel` drives the overlay state machine, the apply-with-confirm flow, the badge, and the coach handoff (via a one-shot `CoachHandoffStore` injected into the cloud coach's system-prompt builder).

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Room, DataStore, Coroutines/Flow, kotlinx.serialization, JUnit4 + kotlinx-coroutines-test. Manual DI via `AppContainer`. Cloud calls via the existing `OpenAiCompatClient`.

---

## Conventions for this plan

- **Build/type-check:** `./gradlew :app:compileDebugKotlin`
- **Unit tests:** `./gradlew :app:testDebugUnitTest`
- **Run one test class:** `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingTest"`
- New non-UI code goes under `app/src/main/java/com/zack/recomptracker/...`; tests mirror the path under `app/src/test/java/com/zack/recomptracker/...`.
- Commit after every task. Branch is already `feature/weekly-briefing`.

## Grounding principle (enforced by construction)

The model returns **only prose** (`BriefingNarration`). All numbers, directions, the verdict, and the apply-target come from `WeeklyReviewData`, computed deterministically by the existing `AdjustmentEngine` / `TrendCalculator` / `AdherenceCalculator`. `WeeklyBriefingGenerator` merges prose onto the deterministic skeleton, so the AI cannot contradict the engine.

## Two-phase gate

- `daysLogged < 7` → overlay shows **Building your first review** (`daysRemaining = (7 - daysLogged).coerceAtLeast(1)`). No model call.
- `daysLogged in 7..13`, or verdict is `WAIT_FOR_DATA` → **EARLY** phase: narrative + trends, **no Apply button**.
- `daysLogged >= 14` and verdict is `INCREASE_CALORIES`/`REDUCE_CALORIES` → **FULL** phase with an Apply button.

---

## File map

**Create**
- `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefing.kt` — briefing models + narration DTO + JSON parse
- `app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewData.kt` — deterministic skeleton types
- `app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewComputer.kt` — pure compute + signature
- `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingPromptBuilder.kt` — prompt text
- `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingGenerator.kt` — cloud generation + merge
- `app/src/main/java/com/zack/recomptracker/data/repository/WeeklyBriefingRepository.kt` — cache/generate
- `app/src/main/java/com/zack/recomptracker/ai/CoachHandoffStore.kt` — one-shot handoff context
- `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewViewModel.kt` — overlay state machine
- `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt` — overlay UI
- `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewButton.kt` — dashboard button + badge
- Test files mirroring the above under `app/src/test/...`

**Modify**
- `app/src/main/java/com/zack/recomptracker/data/local/entity/WeeklyReviewEntity.kt` — add 3 nullable columns
- `app/src/main/java/com/zack/recomptracker/data/local/dao/WeeklyReviewDao.kt` — add `getByWeekStart`
- `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt` — v8→v9 migration
- `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt` — `UiPreferences` badge keys
- `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` — append handoff context
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` — wire everything + factory
- `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` — host button + overlay
- `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` — coach handoff navigation

---

## Task 1: Briefing models + narration JSON parse

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefing.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyBriefingTest {

    @Test
    fun `parses a clean narration object`() {
        val json = """
            {"headline":"Recomp — hold calories.","narrative":"Solid week.",
             "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
             "strength":"Up.","recovery":"Good."},
             "action_rationale":"No change needed.","watch_next":"Watch the scale."}
        """.trimIndent()
        val n = parseBriefingNarration(json)!!
        assertEquals("Recomp — hold calories.", n.headline)
        assertEquals("Down.", n.interpretations["waist"])
        assertEquals("Watch the scale.", n.watchNext)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val json = "```json\n{\"headline\":\"H\",\"narrative\":\"N\",\"interpretations\":{}," +
            "\"action_rationale\":\"A\",\"watch_next\":\"W\"}\n```"
        val n = parseBriefingNarration(json)!!
        assertEquals("H", n.headline)
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(parseBriefingNarration("not json at all"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingTest"`
Expected: FAIL — unresolved reference `parseBriefingNarration` / `WeeklyBriefing`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** EARLY = 7–13 logged days or no actionable verdict; FULL = 14+ days with a real verdict. */
enum class BriefingPhase { EARLY, FULL }

enum class SignalDirection { UP, DOWN, FLAT }

/** One per-signal row. value/direction are deterministic; interpretation is AI prose. */
data class SignalLine(
    val label: String,
    val value: String,
    val direction: SignalDirection,
    val interpretation: String,
)

/** verdict/applyTargetCalories are deterministic; rationale is AI prose. */
data class ActionBlock(
    val verdict: String,
    val rationale: String,
    val applyTargetCalories: Int?,
)

/** The fully merged briefing the UI renders. */
data class WeeklyBriefing(
    val weekStart: String,
    val phase: BriefingPhase,
    val headline: String,
    val narrative: String,
    val signals: List<SignalLine>,
    val action: ActionBlock,
    val watchNext: String,
)

/** Prose-only payload the model returns. No numbers, no verdict. */
data class BriefingNarration(
    val headline: String,
    val narrative: String,
    val interpretations: Map<String, String>,
    val actionRationale: String,
    val watchNext: String,
)

@Serializable
private data class BriefingNarrationDto(
    val headline: String = "",
    val narrative: String = "",
    val interpretations: Map<String, String> = emptyMap(),
    @SerialName("action_rationale") val actionRationale: String = "",
    @SerialName("watch_next") val watchNext: String = "",
)

private val briefingJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Strips ``` fences, isolates the outermost {...}, and parses. Returns null on any failure. */
fun parseBriefingNarration(raw: String): BriefingNarration? {
    val unfenced = raw.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```")
        .trim()
    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val slice = unfenced.substring(start, end + 1)
    return try {
        val dto = briefingJson.decodeFromString(BriefingNarrationDto.serializer(), slice)
        if (dto.headline.isBlank() && dto.narrative.isBlank()) return null
        BriefingNarration(
            headline = dto.headline,
            narrative = dto.narrative,
            interpretations = dto.interpretations,
            actionRationale = dto.actionRationale,
            watchNext = dto.watchNext,
        )
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefing.kt app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingTest.kt
git commit -m "feat(briefing): weekly briefing models + narration JSON parse"
```

---

## Task 2: Deterministic skeleton types (`WeeklyReviewData`)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewData.kt`

No test of its own (plain data holder; exercised in Task 3).

- [ ] **Step 1: Write the file**

```kotlin
package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult

/** One deterministic per-signal row (no AI prose yet). */
data class SignalSkeleton(
    val id: String,            // "weight" | "waist" | "adherence" | "strength" | "recovery"
    val label: String,
    val value: String,
    val direction: SignalDirection,
)

/**
 * Everything the briefing needs that is computed, not generated. The model is handed this and
 * may only add prose on top of it.
 */
data class WeeklyReviewData(
    val weekStart: String,
    val phase: BriefingPhase,
    val daysLogged: Int,
    val input: AdjustmentInput,
    val result: AdjustmentResult,
    val verdictLabel: String,
    val signals: List<SignalSkeleton>,
    val currentTargetCalories: Int,
    /** Non-null only in FULL phase with an INCREASE/REDUCE verdict. */
    val applyTargetCalories: Int?,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewData.kt
git commit -m "feat(briefing): deterministic WeeklyReviewData skeleton types"
```

---

## Task 3: `WeeklyReviewComputer` — pure compute + signature

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewComputer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/review/WeeklyReviewComputerTest.kt`

This builds a `WeeklyReviewData` from an already-computed `AdjustmentInput` + `AdjustmentResult` + current target + weekStart, and derives the bucketed signature. It deliberately takes the *already-computed* input/result (the caller — Task 9 ViewModel — runs the existing calculators), so this stays a pure mapping with no Android or repository deps.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewComputerTest {

    private val computer = WeeklyReviewComputer()

    private fun input(
        days: Int = 14,
        weight: Double = 0.0,
        waist: Double = -0.2,
        adherence: Double = 90.0,
        perf: PerformanceTrend = PerformanceTrend.UP,
        recovery: RecoveryTrend = RecoveryTrend.GOOD,
    ) = AdjustmentInput(
        daysLogged = days, adherencePercent = adherence, weeksSincePhaseStart = 4,
        weightTrendKgPerWeek = weight, waistTrendCmPerWeek = waist,
        performanceTrend = perf, recoveryTrend = recovery,
    )

    private fun result(
        verdict: AdjustmentVerdict = AdjustmentVerdict.HOLD,
        change: Int = 0,
    ) = AdjustmentResult(verdict, change, listOf("X"), "summary")

    @Test
    fun `full phase with reduce verdict yields apply target`() {
        val data = computer.build("2026-06-08", input(), result(AdjustmentVerdict.REDUCE_CALORIES, -100), 2550)
        assertEquals(BriefingPhase.FULL, data.phase)
        assertEquals(2450, data.applyTargetCalories)
        assertEquals(5, data.signals.size)
    }

    @Test
    fun `early phase under 14 days has null apply target`() {
        val data = computer.build("2026-06-08", input(days = 9), result(AdjustmentVerdict.WAIT_FOR_DATA, 0), 2550)
        assertEquals(BriefingPhase.EARLY, data.phase)
        assertNull(data.applyTargetCalories)
    }

    @Test
    fun `hold verdict is full phase but no apply target`() {
        val data = computer.build("2026-06-08", input(), result(AdjustmentVerdict.HOLD, 0), 2550)
        assertEquals(BriefingPhase.FULL, data.phase)
        assertNull(data.applyTargetCalories)
    }

    @Test
    fun `signature is stable for identical inputs`() {
        val a = computer.signature(computer.build("2026-06-08", input(), result(), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(), result(), 2550))
        assertEquals(a, b)
    }

    @Test
    fun `signature ignores sub-bucket wiggle`() {
        val a = computer.signature(computer.build("2026-06-08", input(waist = -0.20), result(), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(waist = -0.22), result(), 2550))
        assertEquals(a, b)
    }

    @Test
    fun `signature changes when verdict changes`() {
        val a = computer.signature(computer.build("2026-06-08", input(), result(AdjustmentVerdict.HOLD, 0), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(), result(AdjustmentVerdict.REDUCE_CALORIES, -100), 2550))
        assertNotEquals(a, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.review.WeeklyReviewComputerTest"`
Expected: FAIL — unresolved reference `WeeklyReviewComputer`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import kotlin.math.abs
import kotlin.math.roundToInt

class WeeklyReviewComputer {

    fun build(
        weekStart: String,
        input: AdjustmentInput,
        result: AdjustmentResult,
        currentTargetCalories: Int,
    ): WeeklyReviewData {
        val actionable = result.verdict == AdjustmentVerdict.INCREASE_CALORIES ||
            result.verdict == AdjustmentVerdict.REDUCE_CALORIES
        val phase = if (input.daysLogged >= 14 && result.verdict != AdjustmentVerdict.WAIT_FOR_DATA) {
            BriefingPhase.FULL
        } else {
            BriefingPhase.EARLY
        }
        val applyTarget = if (phase == BriefingPhase.FULL && actionable) {
            currentTargetCalories + result.recommendedCalorieChange
        } else {
            null
        }
        return WeeklyReviewData(
            weekStart = weekStart,
            phase = phase,
            daysLogged = input.daysLogged,
            input = input,
            result = result,
            verdictLabel = verdictLabel(result.verdict),
            signals = signals(input),
            currentTargetCalories = currentTargetCalories,
            applyTargetCalories = applyTarget,
        )
    }

    /** Bucketed hash of the inputs that should trigger a regeneration when they shift. */
    fun signature(data: WeeklyReviewData): String {
        val i = data.input
        return listOf(
            data.weekStart,
            bucket(i.weightTrendKgPerWeek, 0.05),
            bucket(i.waistTrendCmPerWeek, 0.1),
            (i.adherencePercent / 5.0).roundToInt(),
            i.performanceTrend.name,
            i.recoveryTrend.name,
            data.result.verdict.name,
            data.result.recommendedCalorieChange,
            data.phase.name,
        ).joinToString("|")
    }

    private fun bucket(value: Double, width: Double): Int = (value / width).roundToInt()

    private fun verdictLabel(v: AdjustmentVerdict): String = when (v) {
        AdjustmentVerdict.WAIT_FOR_DATA -> "Gathering data"
        AdjustmentVerdict.HOLD -> "Hold calories"
        AdjustmentVerdict.INCREASE_CALORIES -> "Increase calories"
        AdjustmentVerdict.REDUCE_CALORIES -> "Reduce calories"
    }

    private fun signals(i: AdjustmentInput): List<SignalSkeleton> = listOf(
        SignalSkeleton(
            id = "weight", label = "Weight",
            value = signed(i.weightTrendKgPerWeek, "kg/wk"),
            direction = dir(i.weightTrendKgPerWeek, 0.1),
        ),
        SignalSkeleton(
            id = "waist", label = "Waist",
            value = signed(i.waistTrendCmPerWeek, "cm/wk"),
            direction = dir(i.waistTrendCmPerWeek, 0.1),
        ),
        SignalSkeleton(
            id = "adherence", label = "Adherence",
            value = "${i.adherencePercent.roundToInt()}%",
            direction = SignalDirection.FLAT,
        ),
        SignalSkeleton(
            id = "strength", label = "Strength",
            value = i.performanceTrend.name.lowercase().replaceFirstChar { it.uppercase() },
            direction = when (i.performanceTrend) {
                PerformanceTrend.UP -> SignalDirection.UP
                PerformanceTrend.DOWN -> SignalDirection.DOWN
                else -> SignalDirection.FLAT
            },
        ),
        SignalSkeleton(
            id = "recovery", label = "Recovery",
            value = i.recoveryTrend.name.lowercase().replaceFirstChar { it.uppercase() },
            direction = when (i.recoveryTrend) {
                RecoveryTrend.GOOD -> SignalDirection.UP
                RecoveryTrend.POOR -> SignalDirection.DOWN
                else -> SignalDirection.FLAT
            },
        ),
    )

    private fun dir(value: Double, deadband: Double): SignalDirection = when {
        value > deadband -> SignalDirection.UP
        value < -deadband -> SignalDirection.DOWN
        else -> SignalDirection.FLAT
    }

    private fun signed(value: Double, unit: String): String {
        val rounded = (value * 10).roundToInt() / 10.0
        val sign = if (rounded > 0) "+" else if (rounded < 0) "−" else ""
        return "$sign${abs(rounded)} $unit"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.review.WeeklyReviewComputerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewComputer.kt app/src/test/java/com/zack/recomptracker/domain/review/WeeklyReviewComputerTest.kt
git commit -m "feat(briefing): WeeklyReviewComputer with bucketed input signature"
```

---

## Task 4: Room v8→v9 — cache columns on `weekly_reviews`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/entity/WeeklyReviewEntity.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/dao/WeeklyReviewDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`

No unit test (Room migration testing needs exported schemas, which this project disables with `exportSchema = false`). Verified by build; cache correctness is tested at the repository level in Task 7 with a fake DAO.

- [ ] **Step 1: Add nullable columns to the entity**

Replace the body of `WeeklyReviewEntity` with:

```kotlin
@Serializable
@Entity(tableName = "weekly_reviews")
data class WeeklyReviewEntity(
    @PrimaryKey val weekStart: String,
    val verdict: String,
    val recommendedCalorieChange: Int,
    val reasonCodes: String,
    val generatedAt: String,
    val briefingJson: String? = null,
    val briefingSignature: String? = null,
    val briefingGeneratedAt: String? = null,
)
```

- [ ] **Step 2: Add a single-row query to the DAO**

In `WeeklyReviewDao`, add this method inside the interface (after `getAll()`):

```kotlin
    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart LIMIT 1")
    suspend fun getByWeekStart(weekStart: String): WeeklyReviewEntity?
```

- [ ] **Step 3: Add the migration and bump the version**

In `RecompDatabase.kt`, change `version = 8` to `version = 9`. Add this migration object in the `companion object` next to `MIGRATION_7_8`:

```kotlin
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingJson TEXT")
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingSignature TEXT")
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingGeneratedAt TEXT")
            }
        }
```

Register it in `addMigrations(...)` (append `, MIGRATION_8_9`):

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local/
git commit -m "feat(briefing): Room v8->v9 cache columns on weekly_reviews"
```

---

## Task 5: `WeeklyBriefingPromptBuilder`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingPromptBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingPromptBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBriefingPromptBuilderTest {

    private fun data() = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "maintenance"),
        2550,
    )

    @Test
    fun `prompt includes the deterministic verdict and signal ids`() {
        val p = WeeklyBriefingPromptBuilder().build(data())
        assertTrue(p.contains("Hold calories"))
        assertTrue(p.contains("\"weight\""))
        assertTrue(p.contains("\"waist\""))
        assertTrue(p.contains("\"recovery\""))
    }

    @Test
    fun `prompt forbids inventing numbers and asks for json keys`() {
        val p = WeeklyBriefingPromptBuilder().build(data())
        assertTrue(p.contains("do not change") || p.contains("Do not change"))
        assertTrue(p.contains("interpretations"))
        assertTrue(p.contains("watch_next"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingPromptBuilderTest"`
Expected: FAIL — unresolved reference `WeeklyBriefingPromptBuilder`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.review.WeeklyReviewData

class WeeklyBriefingPromptBuilder {

    fun build(data: WeeklyReviewData): String = buildString {
        appendLine("You are a precise, supportive body-recomposition coach writing a user's WEEKLY briefing.")
        appendLine("All numbers and the verdict below are FINAL and computed by the app. Do not change, recompute, or contradict them. Write prose only.")
        appendLine()
        appendLine("Phase: ${data.phase.name} (${if (data.phase.name == "EARLY") "early read — too soon to change calories" else "full review"})")
        appendLine("Verdict: ${data.verdictLabel}")
        appendLine("Days logged: ${data.daysLogged} | Adherence: ${data.input.adherencePercent.toInt()}%")
        appendLine("Recommended calorie change: ${data.result.recommendedCalorieChange} kcal")
        appendLine("Engine summary (for your understanding): ${data.result.summary}")
        appendLine()
        appendLine("Signals (id — value — direction):")
        data.signals.forEach { appendLine("- ${it.id} — ${it.value} — ${it.direction.name}") }
        appendLine()
        appendLine("Return ONLY a JSON object with EXACTLY these keys and no markdown:")
        appendLine("""{""")
        appendLine(""""headline": one vivid sentence stating the verdict and why,""")
        appendLine(""""narrative": 2-3 sentences summarizing the week,""")
        appendLine(""""interpretations": an object with a one-sentence reading for each signal id: weight, waist, adherence, strength, recovery,""")
        appendLine(""""action_rationale": 1-2 sentences explaining the recommended action,""")
        appendLine(""""watch_next": one sentence on what to watch next week""")
        append("}")
        appendLine()
        if (data.phase.name == "EARLY") {
            append("This is an early read: in action_rationale, make clear it is too soon to change calories and to keep logging.")
        } else {
            append("Be direct and concrete. Reference the actual numbers above without altering them.")
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingPromptBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingPromptBuilderTest.kt
git commit -m "feat(briefing): weekly briefing prompt builder with grounding instructions"
```

---

## Task 6: `WeeklyBriefingGenerator` — cloud call + merge

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingGenerator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingGeneratorTest.kt`

Uses the non-streaming `completion()` (empty tool schemas) so the full JSON arrives in one `text`. Retries once on parse failure, then falls back to a deterministic briefing (engine summary as narrative, blank interpretations).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.ParsedChatResponse
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBriefingGeneratorTest {

    private fun data() = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "Maintenance trend."),
        2550,
    )

    private val config = CloudConfig("https://x", "k", "m")

    private class FakeClient(private val text: String) : OpenAiCompatClient() {
        var calls = 0
        override fun streamCompletion(config: CloudConfig, systemPrompt: String, userPrompt: String): Flow<String> = flowOf()
        override suspend fun completion(
            config: CloudConfig,
            messages: List<ChatRequestMessage>,
            toolSchemasJson: List<String>,
        ): ParsedChatResponse {
            calls++
            return ParsedChatResponse(text, emptyList())
        }
    }

    @Test
    fun `merges model prose onto deterministic skeleton`() = runTest {
        val json = """{"headline":"Recomp.","narrative":"Good week.",
            "interpretations":{"weight":"Flat.","waist":"Down.","adherence":"Strong.",
            "strength":"Up.","recovery":"Good."},
            "action_rationale":"Hold.","watch_next":"Scale."}"""
        val gen = WeeklyBriefingGenerator(FakeClient(json))
        val b = gen.generate(config, data())!!
        assertEquals("Recomp.", b.headline)
        assertEquals("Hold calories", b.action.verdict)
        // interpretation merged onto the deterministic waist value
        assertEquals("Down.", b.signals.first { it.label == "Waist" }.interpretation)
    }

    @Test
    fun `falls back to engine summary after parse failures`() = runTest {
        val client = FakeClient("totally not json")
        val gen = WeeklyBriefingGenerator(client)
        val b = gen.generate(config, data())!!
        assertEquals(2, client.calls) // one retry
        assertTrue(b.narrative.contains("Maintenance trend."))
        assertEquals("Hold calories", b.action.verdict)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingGeneratorTest"`
Expected: FAIL — unresolved reference `WeeklyBriefingGenerator`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.review.WeeklyReviewData

/**
 * Cloud-only generator. Asks the model for prose (BriefingNarration), then merges that prose onto
 * the deterministic [WeeklyReviewData] skeleton so numbers/verdict can never be altered by the model.
 */
class WeeklyBriefingGenerator(
    private val client: OpenAiCompatClient,
    private val promptBuilder: WeeklyBriefingPromptBuilder = WeeklyBriefingPromptBuilder(),
) {
    /** Returns the merged briefing, or null only if the cloud call itself throws. */
    suspend fun generate(config: CloudConfig, data: WeeklyReviewData): WeeklyBriefing? {
        val prompt = promptBuilder.build(data)
        val narration = requestNarration(config, prompt) ?: requestNarration(config, prompt)
        return merge(data, narration)
    }

    private suspend fun requestNarration(config: CloudConfig, prompt: String): BriefingNarration? {
        return try {
            val response = client.completion(
                config = config,
                messages = listOf(
                    ChatRequestMessage(role = "system", content = SYSTEM_PROMPT),
                    ChatRequestMessage(role = "user", content = prompt),
                ),
                toolSchemasJson = emptyList(),
            )
            parseBriefingNarration(response.text)
        } catch (e: Exception) {
            null
        }
    }

    private fun merge(data: WeeklyReviewData, narration: BriefingNarration?): WeeklyBriefing {
        val signals = data.signals.map { s ->
            SignalLine(
                label = s.label,
                value = s.value,
                direction = s.direction,
                interpretation = narration?.interpretations?.get(s.id).orEmpty(),
            )
        }
        return WeeklyBriefing(
            weekStart = data.weekStart,
            phase = data.phase,
            headline = narration?.headline?.takeIf { it.isNotBlank() } ?: data.verdictLabel,
            narrative = narration?.narrative?.takeIf { it.isNotBlank() } ?: data.result.summary,
            signals = signals,
            action = ActionBlock(
                verdict = data.verdictLabel,
                rationale = narration?.actionRationale?.takeIf { it.isNotBlank() } ?: data.result.summary,
                applyTargetCalories = data.applyTargetCalories,
            ),
            watchNext = narration?.watchNext.orEmpty(),
        )
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are a precise body-recomposition coach. Output only the requested JSON. Never invent numbers."
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklyBriefingGeneratorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/WeeklyBriefingGenerator.kt app/src/test/java/com/zack/recomptracker/ai/WeeklyBriefingGeneratorTest.kt
git commit -m "feat(briefing): cloud generator that merges model prose onto grounded skeleton"
```

---

## Task 7: `WeeklyBriefingRepository` — cache or generate

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/WeeklyBriefingRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/WeeklyBriefingRepositoryTest.kt`

Persists/serializes the merged `WeeklyBriefing` as JSON on the `weekly_reviews` row keyed by `weekStart`. On `briefingFor`, returns the cached briefing when the stored signature matches; otherwise generates, persists, and returns. A fake `WeeklyReviewDao` and a generation lambda make this fully unit-testable.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyBriefingRepositoryTest {

    private class FakeDao : WeeklyReviewDao {
        val rows = mutableMapOf<String, WeeklyReviewEntity>()
        override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flowOf(rows.values.toList())
        override suspend fun getAll(): List<WeeklyReviewEntity> = rows.values.toList()
        override suspend fun getByWeekStart(weekStart: String): WeeklyReviewEntity? = rows[weekStart]
        override suspend fun upsert(review: WeeklyReviewEntity) { rows[review.weekStart] = review }
        override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) { reviews.forEach { rows[it.weekStart] = it } }
        override suspend fun deleteAll() { rows.clear() }
    }

    private fun briefing(headline: String) = WeeklyBriefing(
        weekStart = "2026-06-08", phase = BriefingPhase.FULL, headline = headline,
        narrative = "n", signals = emptyList(),
        action = ActionBlock("Hold calories", "r", null), watchNext = "w",
    )

    @Test
    fun `generates and caches when no row exists`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        var calls = 0
        val result = repo.briefingFor("2026-06-08", "sig-1") { calls++; briefing("first") }
        assertEquals("first", result.headline)
        assertEquals(1, calls)
        assertEquals("sig-1", dao.rows["2026-06-08"]?.briefingSignature)
    }

    @Test
    fun `returns cached briefing when signature matches`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        repo.briefingFor("2026-06-08", "sig-1") { briefing("first") }
        var calls = 0
        val result = repo.briefingFor("2026-06-08", "sig-1") { calls++; briefing("second") }
        assertEquals("first", result.headline)
        assertEquals(0, calls)
    }

    @Test
    fun `regenerates when signature changes`() = runTest {
        val dao = FakeDao()
        val repo = WeeklyBriefingRepository(dao)
        repo.briefingFor("2026-06-08", "sig-1") { briefing("first") }
        val result = repo.briefingFor("2026-06-08", "sig-2") { briefing("second") }
        assertEquals("second", result.headline)
        assertEquals("sig-2", dao.rows["2026-06-08"]?.briefingSignature)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WeeklyBriefingRepositoryTest"`
Expected: FAIL — unresolved reference `WeeklyBriefingRepository`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.ai.SignalLine
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Caches the generated [WeeklyBriefing] on the existing weekly_reviews row. A briefing is reused
 * only when the stored [WeeklyReviewEntity.briefingSignature] matches the requested signature.
 */
class WeeklyBriefingRepository(
    private val weeklyReviewDao: WeeklyReviewDao,
) {
    /**
     * Returns the cached briefing for [weekStart] when [signature] matches the stored one,
     * otherwise runs [generate], persists the result against [signature], and returns it.
     */
    suspend fun briefingFor(
        weekStart: String,
        signature: String,
        generate: suspend () -> WeeklyBriefing,
    ): WeeklyBriefing {
        val row = weeklyReviewDao.getByWeekStart(weekStart)
        val cached = row
            ?.takeIf { it.briefingSignature == signature }
            ?.briefingJson
            ?.let { runCatching { json.decodeFromString(BriefingDto.serializer(), it) }.getOrNull() }
        if (cached != null) return cached.toModel(weekStart)

        val fresh = generate()
        val existing = weeklyReviewDao.getByWeekStart(weekStart)
        if (existing != null) {
            weeklyReviewDao.upsert(
                existing.copy(
                    briefingJson = json.encodeToString(BriefingDto.serializer(), fresh.toDto()),
                    briefingSignature = signature,
                    briefingGeneratedAt = "cached",
                ),
            )
        }
        return fresh
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class BriefingDto(
    val phase: String,
    val headline: String,
    val narrative: String,
    val signals: List<SignalDto>,
    val verdict: String,
    val rationale: String,
    val applyTargetCalories: Int?,
    val watchNext: String,
) {
    fun toModel(weekStart: String) = WeeklyBriefing(
        weekStart = weekStart,
        phase = BriefingPhase.valueOf(phase),
        headline = headline,
        narrative = narrative,
        signals = signals.map { SignalLine(it.label, it.value, SignalDirection.valueOf(it.direction), it.interpretation) },
        action = ActionBlock(verdict, rationale, applyTargetCalories),
        watchNext = watchNext,
    )
}

@Serializable
private data class SignalDto(
    val label: String,
    val value: String,
    val direction: String,
    val interpretation: String,
)

private fun WeeklyBriefing.toDto() = BriefingDto(
    phase = phase.name,
    headline = headline,
    narrative = narrative,
    signals = signals.map { SignalDto(it.label, it.value, it.direction.name, it.interpretation) },
    verdict = action.verdict,
    rationale = action.rationale,
    applyTargetCalories = action.applyTargetCalories,
    watchNext = watchNext,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.WeeklyBriefingRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/WeeklyBriefingRepository.kt app/src/test/java/com/zack/recomptracker/data/repository/WeeklyBriefingRepositoryTest.kt
git commit -m "feat(briefing): caching repository keyed by week + input signature"
```

---

## Task 8: Badge state in `UiPreferences`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt`

The badge is "this week's signature differs from the last one the user opened." Store the last-seen signature. Verified by build; the badge derivation is unit-tested in the ViewModel (Task 9).

- [ ] **Step 1: Add the flow + setter to `UiPreferences`**

Inside `class UiPreferences`, add this flow alongside the others (e.g. after `aiBackend`):

```kotlin
    val lastSeenBriefingSignature: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map { it[Keys.LastSeenBriefingSignature] ?: "" }
```

Add this setter alongside the other `suspend fun set...` methods:

```kotlin
    suspend fun setLastSeenBriefingSignature(signature: String) {
        context.uiDataStore.edit { it[Keys.LastSeenBriefingSignature] = signature }
    }
```

Add this key inside `UiPreferences`'s `private object Keys`:

```kotlin
        val LastSeenBriefingSignature = stringPreferencesKey("last_seen_briefing_signature")
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt
git commit -m "feat(briefing): persist last-seen briefing signature for the badge"
```

---

## Task 9: `WeeklyReviewViewModel` — overlay state machine

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewViewModel.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/review/WeeklyReviewViewModelTest.kt`

The ViewModel observes logs/meals/performances/preferences (same sources as `DashboardViewModel`), computes `AdjustmentInput`/`AdjustmentResult` with the injected calculators+engine, maps to `WeeklyReviewData` via `WeeklyReviewComputer`, and exposes: `uiState` (overlay), `badge` (Boolean), plus `open()`, `regenerate()`, apply-with-confirm, and `discussWithCoach()`.

To keep the ViewModel testable without Room, it depends on small interfaces it already has in the app (`LogRepository` flows, `PlanRepository`) plus injected function types for cloud config and generation. The test injects fakes.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.review

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.domain.review.WeeklyReviewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyReviewViewModelTest {

    private fun briefing() = WeeklyBriefing(
        "2026-06-08", BriefingPhase.FULL, "H", "N", emptyList(),
        ActionBlock("Reduce calories", "r", 2450), "w",
    )

    private fun fixture(
        cloudActive: Boolean = true,
        daysLogged: Int = 14,
    ): WeeklyReviewDeps {
        val data = WeeklyReviewData(
            weekStart = "2026-06-08", phase = BriefingPhase.FULL, daysLogged = daysLogged,
            input = com.zack.recomptracker.domain.adjustment.AdjustmentInput(
                daysLogged, 90.0, 4, 0.0, -0.2,
                com.zack.recomptracker.domain.adjustment.PerformanceTrend.UP,
                com.zack.recomptracker.domain.adjustment.RecoveryTrend.GOOD,
            ),
            result = com.zack.recomptracker.domain.adjustment.AdjustmentResult(
                com.zack.recomptracker.domain.adjustment.AdjustmentVerdict.REDUCE_CALORIES, -100,
                listOf("X"), "s",
            ),
            verdictLabel = "Reduce calories", signals = emptyList(),
            currentTargetCalories = 2550, applyTargetCalories = 2450,
        )
        return WeeklyReviewDeps(
            cloudActive = MutableStateFlow(cloudActive),
            reviewData = MutableStateFlow(data),
            signature = "sig-1",
            generate = { briefing() },
        )
    }

    @Test
    fun `open shows upsell when cloud inactive`() = runTest {
        val vm = WeeklyReviewViewModel(fixture(cloudActive = false).toVm())
        vm.open(); advanceUntilIdle()
        assertTrue(vm.uiState.value is WeeklyReviewUiState.Upsell)
    }

    @Test
    fun `open shows insufficient data under 7 days`() = runTest {
        val vm = WeeklyReviewViewModel(fixture(daysLogged = 4).toVm())
        vm.open(); advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is WeeklyReviewUiState.InsufficientData)
        assertEquals(3, (s as WeeklyReviewUiState.InsufficientData).daysRemaining)
    }

    @Test
    fun `open generates and becomes ready`() = runTest {
        val vm = WeeklyReviewViewModel(fixture().toVm())
        vm.open(); advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is WeeklyReviewUiState.Ready)
        assertEquals("H", (s as WeeklyReviewUiState.Ready).briefing.headline)
    }

    @Test
    fun `apply with confirm saves the target`() = runTest {
        val deps = fixture()
        var savedTarget: Int? = null
        val vm = WeeklyReviewViewModel(deps.toVm(saveTarget = { savedTarget = it }))
        vm.open(); advanceUntilIdle()
        vm.requestApply(2450)
        assertEquals(2450, vm.pendingApply.value)
        vm.confirmApply(); advanceUntilIdle()
        assertEquals(2450, savedTarget)
        assertEquals(null, vm.pendingApply.value)
    }
}
```

> Note: `WeeklyReviewDeps` and `.toVm()` are small test helpers defined in this test file. Define them at the bottom of the test:

```kotlin
private class WeeklyReviewDeps(
    val cloudActive: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    val reviewData: kotlinx.coroutines.flow.MutableStateFlow<WeeklyReviewData?>,
    val signature: String,
    val generate: suspend (WeeklyReviewData) -> WeeklyBriefing,
) {
    fun toVm(saveTarget: suspend (Int) -> Unit = {}) = WeeklyReviewConfig(
        cloudActiveFlow = cloudActive,
        reviewDataFlow = reviewData,
        signatureOf = { signature },
        briefingFor = { _, _, gen -> gen() },
        generate = generate,
        saveCalorieTarget = saveTarget,
        markSeen = {},
        lastSeenSignatureFlow = kotlinx.coroutines.flow.MutableStateFlow(""),
        startCoachHandoff = {},
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.review.WeeklyReviewViewModelTest"`
Expected: FAIL — unresolved references `WeeklyReviewViewModel`, `WeeklyReviewConfig`, `WeeklyReviewUiState`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.domain.review.WeeklyReviewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeeklyReviewUiState {
    object Hidden : WeeklyReviewUiState
    object Upsell : WeeklyReviewUiState
    data class InsufficientData(val daysRemaining: Int) : WeeklyReviewUiState
    object Generating : WeeklyReviewUiState
    data class Ready(val briefing: WeeklyBriefing) : WeeklyReviewUiState
    data class Error(val message: String) : WeeklyReviewUiState
}

/**
 * All collaborators the ViewModel needs, as plain flows/lambdas so it is unit-testable without Room
 * or Android. Production values are assembled in [AppContainer].
 */
class WeeklyReviewConfig(
    val cloudActiveFlow: Flow<Boolean>,
    val reviewDataFlow: Flow<WeeklyReviewData?>,
    val signatureOf: (WeeklyReviewData) -> String,
    val briefingFor: suspend (weekStart: String, signature: String, generate: suspend () -> WeeklyBriefing) -> WeeklyBriefing,
    val generate: suspend (WeeklyReviewData) -> WeeklyBriefing,
    val saveCalorieTarget: suspend (Int) -> Unit,
    val markSeen: suspend (String) -> Unit,
    val lastSeenSignatureFlow: Flow<String>,
    val startCoachHandoff: (WeeklyReviewData, WeeklyBriefing) -> Unit,
)

class WeeklyReviewViewModel(
    private val config: WeeklyReviewConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeeklyReviewUiState>(WeeklyReviewUiState.Hidden)
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    private val _pendingApply = MutableStateFlow<Int?>(null)
    val pendingApply: StateFlow<Int?> = _pendingApply.asStateFlow()

    @Volatile private var latestData: WeeklyReviewData? = null
    @Volatile private var latestBriefing: WeeklyBriefing? = null

    /** True when the current week's signature differs from the last one the user opened. */
    val badge: StateFlow<Boolean> =
        combine(config.reviewDataFlow, config.lastSeenSignatureFlow, config.cloudActiveFlow) { data, lastSeen, cloud ->
            data != null && cloud && config.signatureOf(data) != lastSeen
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            config.reviewDataFlow.collect { latestData = it }
        }
    }

    fun open() {
        viewModelScope.launch {
            val cloud = firstCloudActive()
            if (!cloud) { _uiState.value = WeeklyReviewUiState.Upsell; return@launch }
            val data = latestData
            if (data == null || data.daysLogged < 7) {
                val remaining = ((7 - (data?.daysLogged ?: 0)).coerceAtLeast(1))
                _uiState.value = WeeklyReviewUiState.InsufficientData(remaining)
                return@launch
            }
            _uiState.value = WeeklyReviewUiState.Generating
            try {
                val signature = config.signatureOf(data)
                val briefing = config.briefingFor(data.weekStart, signature) { config.generate(data) }
                latestBriefing = briefing
                config.markSeen(signature)
                _uiState.value = WeeklyReviewUiState.Ready(briefing)
            } catch (e: Exception) {
                _uiState.value = WeeklyReviewUiState.Error("Couldn't build your review — try again.")
            }
        }
    }

    fun regenerate() = open()

    fun dismiss() { _uiState.value = WeeklyReviewUiState.Hidden }

    fun requestApply(target: Int) { _pendingApply.value = target }
    fun cancelApply() { _pendingApply.value = null }

    fun confirmApply() {
        val target = _pendingApply.value ?: return
        viewModelScope.launch {
            config.saveCalorieTarget(target)
            _pendingApply.value = null
        }
    }

    fun discussWithCoach() {
        val data = latestData ?: return
        val briefing = latestBriefing ?: return
        config.startCoachHandoff(data, briefing)
    }

    private suspend fun firstCloudActive(): Boolean = config.cloudActiveFlow.first()
}
```

Add this import to the file's import block (alongside the other `kotlinx.coroutines.flow.*` imports):

```kotlin
import kotlinx.coroutines.flow.first
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.review.WeeklyReviewViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewViewModel.kt app/src/test/java/com/zack/recomptracker/ui/review/WeeklyReviewViewModelTest.kt
git commit -m "feat(briefing): WeeklyReviewViewModel state machine, badge, apply-confirm"
```

---

## Task 10: Coach handoff — one-shot context injection

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/CoachHandoffStore.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachHandoffStoreTest.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt`

`CoachHandoffStore` holds an optional context block. The cloud coach's `CoachToolsAdapter.systemPromptSnapshot()` appends and consumes it (so it applies to exactly the next conversation). The ViewModel sets it before navigating and clears coach history to force a re-seed.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachHandoffStoreTest {

    @Test
    fun `consume returns then clears the context`() {
        val store = CoachHandoffStore()
        store.set("BRIEFING CONTEXT")
        assertEquals("BRIEFING CONTEXT", store.consume())
        assertNull(store.consume())
    }

    @Test
    fun `consume is null when nothing set`() {
        assertNull(CoachHandoffStore().consume())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachHandoffStoreTest"`
Expected: FAIL — unresolved reference `CoachHandoffStore`.

- [ ] **Step 3: Write `CoachHandoffStore`**

```kotlin
package com.zack.recomptracker.ai

/**
 * One-shot carrier for weekly-briefing context handed to the coach. The cloud coach consumes it
 * when seeding the next conversation, so it influences exactly one chat session.
 */
class CoachHandoffStore {
    @Volatile private var pending: String? = null

    fun set(context: String) { pending = context }

    /** Returns the pending context (if any) and clears it. */
    fun consume(): String? {
        val value = pending
        pending = null
        return value
    }
}
```

- [ ] **Step 4: Wire it into `CoachToolsAdapter`**

Add a constructor parameter and append the consumed block. Change the constructor:

```kotlin
class CoachToolsAdapter(
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
    private val handoffStore: CoachHandoffStore,
) : CoachReadTools {
```

At the end of `systemPromptSnapshot()`, append the handoff block. Replace the final line of `systemPromptSnapshot()`:

```kotlin
    override suspend fun systemPromptSnapshot(): String {
        val prefs = planRepository.preferences.first()
        val profile = userProfileStore.preferences.first()
        val today = dateProvider.today()
        val todaySummary = withContext(Dispatchers.IO) { toolExecutor.execute("get_today_summary", emptyMap()) }
        val base = buildPrompt(prefs, profile, today, todaySummary)
        val handoff = handoffStore.consume()
        return if (handoff.isNullOrBlank()) base else base + "\n\n" + handoff
    }
```

- [ ] **Step 5: Run the store test to verify it passes + build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.CoachHandoffStoreTest"`
Expected: PASS.
Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `CoachToolsAdapter` constructor call in `AppContainer` now misses `handoffStore`. That is fixed in Task 12; this is expected. (Do not fix it here.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachHandoffStore.kt app/src/test/java/com/zack/recomptracker/ai/CoachHandoffStoreTest.kt app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt
git commit -m "feat(briefing): coach handoff store injected into cloud coach system prompt"
```

---

## Task 11: Overlay + dashboard button UI

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewButton.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt`

Pure Compose; no new tests (UI). Verified by build in Task 12.

- [ ] **Step 1: Write `WeeklyReviewButton`**

```kotlin
package com.zack.recomptracker.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WeeklyReviewButton(
    showBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedButton(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Weekly Review")
            }
        }
        if (showBadge) {
            Badge(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
        }
    }
}
```

- [ ] **Step 2: Write `WeeklyBriefingOverlay`**

```kotlin
package com.zack.recomptracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.ai.WeeklyBriefing

@Composable
fun WeeklyBriefingOverlay(
    state: WeeklyReviewUiState,
    pendingApply: Int?,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onRequestApply: (Int) -> Unit,
    onConfirmApply: () -> Unit,
    onCancelApply: () -> Unit,
    onDiscussWithCoach: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (state is WeeklyReviewUiState.Hidden) return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Weekly Review", style = MaterialTheme.typography.titleLarge)
                when (state) {
                    WeeklyReviewUiState.Hidden -> Unit
                    WeeklyReviewUiState.Upsell -> {
                        Text("Weekly Review is powered by cloud AI. Turn on cloud AI in Settings to unlock your weekly briefing.")
                        Button(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                    is WeeklyReviewUiState.InsufficientData -> {
                        Text("Building your first review")
                        Text("Keep logging — about ${state.daysRemaining} more day(s) of data and your first briefing unlocks.")
                    }
                    WeeklyReviewUiState.Generating -> {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("  Reading your week…")
                        }
                    }
                    is WeeklyReviewUiState.Error -> {
                        Text(state.message)
                        Button(onClick = onRegenerate) { Text("Try again") }
                    }
                    is WeeklyReviewUiState.Ready -> BriefingBody(
                        briefing = state.briefing,
                        onRequestApply = onRequestApply,
                        onRegenerate = onRegenerate,
                        onDiscussWithCoach = onDiscussWithCoach,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    if (pendingApply != null) {
        AlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Update calorie target?") },
            text = { Text("Set your daily target to $pendingApply kcal?") },
            confirmButton = { TextButton(onClick = onConfirmApply) { Text("Apply") } },
            dismissButton = { TextButton(onClick = onCancelApply) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BriefingBody(
    briefing: WeeklyBriefing,
    onRequestApply: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onDiscussWithCoach: () -> Unit,
) {
    Text(briefing.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (briefing.narrative.isNotBlank()) Text(briefing.narrative)
    Divider()
    briefing.signals.forEach { s ->
        Column {
            Text("${s.label}: ${s.value}", fontWeight = FontWeight.Medium)
            if (s.interpretation.isNotBlank()) {
                Text(s.interpretation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Divider()
    Text(briefing.action.verdict, fontWeight = FontWeight.SemiBold)
    if (briefing.action.rationale.isNotBlank()) Text(briefing.action.rationale)
    val applyTarget = briefing.action.applyTargetCalories
    if (applyTarget != null) {
        Button(onClick = { onRequestApply(applyTarget) }) { Text("Apply: set target to $applyTarget kcal") }
    }
    if (briefing.watchNext.isNotBlank()) {
        Divider()
        Text("Watch next week", fontWeight = FontWeight.Medium)
        Text(briefing.watchNext)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onRegenerate) { Text("Regenerate") }
        TextButton(onClick = onDiscussWithCoach) { Text("Discuss with coach") }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `AppContainer` still misses Task 10/12 wiring. This is expected; full build success comes in Task 12. (You may instead defer the build check to Task 12.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/review/WeeklyReviewButton.kt app/src/main/java/com/zack/recomptracker/ui/review/WeeklyBriefingOverlay.kt
git commit -m "feat(briefing): weekly review button and briefing overlay UI"
```

---

## Task 12: Wire everything in `AppContainer` + dashboard + nav

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

This task makes the app build and run. It assembles the `WeeklyReviewData` flow from the same repositories `DashboardViewModel` uses, exposes a `cloudBriefingActive` flow, builds the `WeeklyReviewConfig`, registers the ViewModel in the factory, and hosts the button+overlay on the dashboard with navigation to the coach.

- [ ] **Step 1: Add collaborators + flows in `AppContainer`**

Add these imports near the other `ai`/`domain`/`remote` imports:

```kotlin
import com.zack.recomptracker.ai.CoachHandoffStore
import com.zack.recomptracker.ai.WeeklyBriefingGenerator
import com.zack.recomptracker.data.repository.WeeklyBriefingRepository
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import com.zack.recomptracker.domain.review.WeeklyReviewData
import com.zack.recomptracker.ui.review.WeeklyReviewConfig
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.PerformancePoint
import com.zack.recomptracker.domain.trend.RecoveryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
```

Add a single shared `CoachHandoffStore`, and pass it to the existing `CoachToolsAdapter(...)` construction (Task 10 added the parameter). Update the `cloudCoachCoordinator` block's `tools = CoachToolsAdapter(...)` to include `handoffStore = coachHandoffStore`:

```kotlin
    val coachHandoffStore = CoachHandoffStore()
```

(place this line just above the `// ── Cloud coordinators ──` comment) and:

```kotlin
        tools = CoachToolsAdapter(
            toolExecutor = coachToolExecutor,
            planRepository = planRepository,
            userProfileStore = userProfilePreferencesStore,
            dateProvider = dateProvider,
            handoffStore = coachHandoffStore,
        ),
```

Add the briefing collaborators after `val coachCoordinator = ...` (before `viewModelFactory`):

```kotlin
    val weeklyReviewComputer = WeeklyReviewComputer()
    val weeklyBriefingGenerator = WeeklyBriefingGenerator(openAiCompatClient)
    val weeklyBriefingRepository = WeeklyBriefingRepository(database.weeklyReviewDao())

    /** Feature gate: cloud is the chosen backend, config is complete, and AI is enabled. */
    val cloudBriefingActive: StateFlow<Boolean> = combine(
        uiPreferences.aiBackend,
        cloudConfigComplete,
        uiPreferences.aiInsightsEnabled,
    ) { backend, complete, enabled ->
        backend == AiBackend.CLOUD && complete && enabled
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    /** The current review week's deterministic data, recomputed from the same sources as the dashboard. */
    val weeklyReviewDataFlow: Flow<WeeklyReviewData?> = combine(
        logRepository.observeDailyLogs(),
        logRepository.observeMealEntriesSince(dateProvider.today().minusDays(27)),
        logRepository.observePerformances(),
        planRepository.preferences,
    ) { logs, meals, performances, prefs ->
        computeWeeklyReviewData(logs, meals, performances, prefs)
    }
```

Add this private helper method inside `AppContainer` (mirrors `DashboardViewModel.buildState`, focused on the AdjustmentInput; reuse the same calculators/engine):

```kotlin
    private fun computeWeeklyReviewData(
        logs: List<com.zack.recomptracker.data.local.entity.DailyLogEntity>,
        allMeals: List<MealEntryEntity>,
        performances: List<com.zack.recomptracker.data.local.entity.LiftPerformanceEntity>,
        prefs: PlanPreferences,
    ): WeeklyReviewData? {
        val today = dateProvider.today()
        val meals = allMeals.filterNot { it.planned }
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val logs28 = logs.filter { LocalDate.parse(it.date) in last28Start..today }
        val meals14 = meals.filter { LocalDate.parse(it.date) in last14Start..today }
        val mealsByDate = meals14.groupBy { LocalDate.parse(it.date) }
        val nutritionDays = (0..13).map { off ->
            val d = last14Start.plusDays(off.toLong())
            NutritionDay(d, com.zack.recomptracker.data.repository.macroTotals(mealsByDate[d].orEmpty()).calories)
        }
        val loggedDates = logs28.map { it.date }.toSet() + meals14.map { it.date }.toSet()
        val daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today }
        if (daysLogged == 0) return null
        val weightPoints = logs28.map { MeasurementPoint(LocalDate.parse(it.date), it.bodyWeightKg) }
        val waistPoints = logs28.map { MeasurementPoint(LocalDate.parse(it.date), it.waistCm) }
        val perfPoints = performances
            .filter { LocalDate.parse(it.date) in last28Start..today }
            .map { PerformancePoint(LocalDate.parse(it.date), it.weight, it.reps, it.sets) }
        val recPoints = logs
            .filter { LocalDate.parse(it.date) in last14Start..today }
            .map { RecoveryPoint(LocalDate.parse(it.date), it.sleepHours, it.energyScore, it.sorenessScore) }
        val weeksSincePhase = prefs.maintenancePhaseStartDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 7 }?.toInt() ?: 4
        val input = AdjustmentInput(
            daysLogged = daysLogged,
            adherencePercent = adherenceCalculator.calculate(nutritionDays, prefs.targetCalories, expectedDays = 14),
            weeksSincePhaseStart = weeksSincePhase,
            weightTrendKgPerWeek = trendCalculator.trendPerWeek(weightPoints),
            waistTrendCmPerWeek = trendCalculator.trendPerWeek(waistPoints),
            performanceTrend = trendCalculator.performanceTrend(perfPoints),
            recoveryTrend = trendCalculator.recoveryTrend(recPoints),
        )
        val result = adjustmentEngine.evaluate(input)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        return weeklyReviewComputer.build(weekStart, input, result, prefs.targetCalories)
    }
```

> If `macroTotals` is an extension on `List<MealEntryEntity>` (it is used as `meals.macroTotals()` in `DashboardViewModel`), call it as `mealsByDate[d].orEmpty().macroTotals().calories` instead of the function form, and drop the `com.zack.recomptracker.data.repository.macroTotals(...)` wrapper. Match the existing import `com.zack.recomptracker.data.repository.macroTotals`.

- [ ] **Step 2: Register the ViewModel in the factory**

In `AppViewModelFactory.create`, add this branch before the `else ->`:

```kotlin
            WeeklyReviewViewModel::class.java -> WeeklyReviewViewModel(
                WeeklyReviewConfig(
                    cloudActiveFlow = container.cloudBriefingActive,
                    reviewDataFlow = container.weeklyReviewDataFlow,
                    signatureOf = { container.weeklyReviewComputer.signature(it) },
                    briefingFor = { weekStart, signature, generate ->
                        container.weeklyBriefingRepository.briefingFor(weekStart, signature, generate)
                    },
                    generate = { data ->
                        val config = container.cloudConfigForBriefing()
                            ?: error("Cloud not configured")
                        container.weeklyBriefingGenerator.generate(config, data)
                            ?: error("Generation failed")
                    },
                    saveCalorieTarget = { target ->
                        val prefs = container.planRepository.preferences.first()
                        container.planRepository.save(prefs.copy(targetCalories = target))
                    },
                    markSeen = { signature -> container.uiPreferences.setLastSeenBriefingSignature(signature) },
                    lastSeenSignatureFlow = container.uiPreferences.lastSeenBriefingSignature,
                    startCoachHandoff = { data, briefing ->
                        container.coachHandoffStore.set(buildCoachHandoffContext(data, briefing))
                        container.coachCoordinator.clearHistory()
                    },
                ),
            )
```

Add a public accessor for the cloud config on `AppContainer` (the existing `cloudConfigFlow` is private):

```kotlin
    fun cloudConfigForBriefing(): CloudConfig? = cloudConfigFlow.value
```

Add a top-level helper in `AppContainer.kt` (below the file's classes) that builds the handoff context text — this is the "quiet, don't re-explain" directive plus the data the coach should see:

```kotlin
private fun buildCoachHandoffContext(
    data: WeeklyReviewData,
    briefing: com.zack.recomptracker.ai.WeeklyBriefing,
): String = buildString {
    appendLine("=== WEEKLY BRIEFING CONTEXT ===")
    appendLine("The user just read this week's briefing and opened chat to ask about it.")
    appendLine("Do NOT re-explain or summarize the briefing. Greet in at most one short line, then wait for their question and answer concisely from the data below.")
    appendLine()
    appendLine("Week starting: ${data.weekStart} | Phase: ${data.phase.name}")
    appendLine("Verdict: ${briefing.action.verdict} | Days logged: ${data.daysLogged} | Adherence: ${data.input.adherencePercent.toInt()}%")
    appendLine("Recommended calorie change: ${data.result.recommendedCalorieChange} kcal")
    appendLine("Signals:")
    briefing.signals.forEach { appendLine("- ${it.label}: ${it.value} — ${it.interpretation}") }
    appendLine("Headline shown: ${briefing.headline}")
    appendLine("Narrative shown: ${briefing.narrative}")
    append("=== END WEEKLY BRIEFING CONTEXT ===")
}
```

> Note: `Flow.first()` is the terminal operator used above (`container.planRepository.preferences.first()`); the `import kotlinx.coroutines.flow.first` added in Step 1 covers it.

- [ ] **Step 3: Host button + overlay on the dashboard**

In `DashboardScreen.kt`, the real screen is `fun DashboardScreen(viewModel: DashboardViewModel)` (around line 620). Add a `WeeklyReviewViewModel` param and render the button as the first item + the overlay. Change the signature and body. Update the composable to:

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    weeklyReviewViewModel: WeeklyReviewViewModel,
    onOpenCoach: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val reviewState by weeklyReviewViewModel.uiState.collectAsStateWithLifecycle()
    val badge by weeklyReviewViewModel.badge.collectAsStateWithLifecycle()
    val pendingApply by weeklyReviewViewModel.pendingApply.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) {
        viewModel.onAiCardVisible(state.result)
    }

    LazyColumn(
        // keep the existing modifier/arrangement/contentPadding arguments unchanged
    ) {
        item {
            WeeklyReviewButton(
                showBadge = badge,
                onClick = { weeklyReviewViewModel.open() },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        // ... keep all existing items unchanged ...
    }

    WeeklyBriefingOverlay(
        state = reviewState,
        pendingApply = pendingApply,
        onDismiss = { weeklyReviewViewModel.dismiss() },
        onRegenerate = { weeklyReviewViewModel.regenerate() },
        onRequestApply = { weeklyReviewViewModel.requestApply(it) },
        onConfirmApply = { weeklyReviewViewModel.confirmApply() },
        onCancelApply = { weeklyReviewViewModel.cancelApply() },
        onDiscussWithCoach = {
            weeklyReviewViewModel.discussWithCoach()
            weeklyReviewViewModel.dismiss()
            onOpenCoach()
        },
        onOpenSettings = {
            weeklyReviewViewModel.dismiss()
            onOpenSettings()
        },
    )
}
```

Add the imports at the top of `DashboardScreen.kt`:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.zack.recomptracker.ui.review.WeeklyReviewButton
import com.zack.recomptracker.ui.review.WeeklyBriefingOverlay
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
```

> If `LaunchedEffect`/`Modifier`/`dp` are already imported, skip those lines. Keep the existing `LazyColumn` arguments (modifier, contentPadding, verticalArrangement) exactly as they were — only the new leading `item { WeeklyReviewButton(...) }` and the trailing overlay are added.

- [ ] **Step 4: Provide the new params at the Home composable in `AppNavGraph.kt`**

In `AppNavGraph.kt`, the Home destination (`route = TopLevelDestination.Home.route`, ~line 94) renders the dashboard. Provide the `WeeklyReviewViewModel` and navigation lambdas. Inside that `composable { ... }` block, where `DashboardScreen(...)` is invoked, update the call:

```kotlin
            DashboardScreen(
                viewModel = viewModel<DashboardViewModel>(factory = factory),
                weeklyReviewViewModel = viewModel<WeeklyReviewViewModel>(factory = factory),
                onOpenCoach = {
                    navController.navigate(TopLevelDestination.Coach.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = { navController.navigate(Routes.Plan) },
            )
```

Add imports at the top of `AppNavGraph.kt` if missing:

```kotlin
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
```

> The Home composable already obtains `factory` and `navController`; reuse them. `Routes.Plan` opens the plan/targets screen, the closest "settings" surface for the cloud-AI upsell; if a dedicated cloud-AI settings route exists (check `Routes` and the `More` screen wiring), prefer that.

- [ ] **Step 5: Build the whole app**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any signature mismatches (parameter names) reported.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all new + existing tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(briefing): wire weekly review into container, dashboard, and navigation"
```

---

## Task 13: Backup round-trips the new fields

**Files:**
- Verify: `app/src/main/java/com/zack/recomptracker/domain/export/BackupModels.kt`
- Verify: `app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt`

`BackupModels.weeklyReviews` is typed `List<WeeklyReviewEntity>` and `WeeklyReviewEntity` is `@Serializable` with defaulted new fields, so export/import already round-trips them (the new nullable fields default to null for older backups). This task confirms that and adds a regression test.

- [ ] **Step 1: Write a serialization round-trip test**

**File:** `app/src/test/java/com/zack/recomptracker/data/WeeklyReviewEntitySerializationTest.kt`

```kotlin
package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewEntitySerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trips including new briefing fields`() {
        val entity = WeeklyReviewEntity(
            weekStart = "2026-06-08", verdict = "HOLD", recommendedCalorieChange = 0,
            reasonCodes = "X", generatedAt = "t",
            briefingJson = "{}", briefingSignature = "sig", briefingGeneratedAt = "cached",
        )
        val decoded = json.decodeFromString(
            WeeklyReviewEntity.serializer(),
            json.encodeToString(WeeklyReviewEntity.serializer(), entity),
        )
        assertEquals(entity, decoded)
    }

    @Test
    fun `old backup without briefing fields decodes with nulls`() {
        val old = """{"weekStart":"2026-06-01","verdict":"HOLD","recommendedCalorieChange":0,
            "reasonCodes":"X","generatedAt":"t"}"""
        val decoded = json.decodeFromString(WeeklyReviewEntity.serializer(), old)
        assertNull(decoded.briefingJson)
        assertNull(decoded.briefingSignature)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.WeeklyReviewEntitySerializationTest"`
Expected: PASS (no production change needed — the defaulted `@Serializable` fields already handle both directions).

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/data/WeeklyReviewEntitySerializationTest.kt
git commit -m "test(briefing): backup round-trips new weekly_reviews columns"
```

---

## Task 14: Manual smoke verification

**Files:** none (manual). Use the `run` or `verify` skill / an emulator.

- [ ] **Step 1: Build & install**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Install on an emulator/device.

- [ ] **Step 2: Off-cloud path**

With AI backend NOT set to cloud (or cloud not configured), open the dashboard. Confirm the **Weekly Review** button is visible. Tap it → the overlay shows the **upsell** with an "Open Settings" action. No network call.

- [ ] **Step 3: Cloud path, insufficient data**

Switch AI backend to cloud and configure base URL / model / API key. On a profile with fewer than 7 logged days, tap the button → **Building your first review** with a days-remaining message.

- [ ] **Step 4: Cloud path, full briefing + apply**

On a profile with 14+ logged days and a REDUCE/INCREASE verdict, tap the button → a sectioned briefing renders (headline, narrative, 5 signal rows, action, watch-next). Tap **Apply** → confirm dialog → accept → verify the plan target updated (check the dashboard "Current targets" / Plan screen).

- [ ] **Step 5: Badge + caching**

Reopen the briefing → it loads instantly (cached, no spinner). Confirm the badge cleared after first open. (Optional) change enough data to flip the verdict, reopen → a fresh briefing generates and the badge reappeared beforehand.

- [ ] **Step 6: Coach handoff**

In the briefing, tap **Discuss with coach** → the coach screen opens. Type a question about the week. Confirm the coach answers concisely using the week's data and does NOT re-explain the whole briefing before you ask.

- [ ] **Step 7: Final commit (docs only, if any notes)**

```bash
git commit --allow-empty -m "chore(briefing): manual smoke verification complete"
```

---

## Self-review notes (addressed)

- **Spec coverage:** trigger/badge (T8, T9, T12), overlay + 4 states (T9, T11), 7/14 two-phase (T3, T9), sectioned briefing from JSON (T1, T5, T6), grounding by construction (T6), apply-with-confirm (T9, T11), cloud-only gating (T9, T12), cache + auto-refresh by signature (T3, T7), Room v9 + backup (T4, T13), coach handoff seeded + quiet (T10, T12). All covered.
- **Cross-task type consistency:** `WeeklyBriefing`, `BriefingNarration`, `SignalLine`/`SignalDirection`, `WeeklyReviewData`/`SignalSkeleton`, `WeeklyReviewConfig`/`WeeklyReviewUiState`, `CoachHandoffStore.consume()` are used identically across tasks.
- **Known integration risk:** Task 12 reproduces `DashboardViewModel`'s input computation (via the shared `WeeklyReviewComputer`). The flow plumbing is duplicated but the *logic* lives once in `WeeklyReviewComputer`. Adopting it inside `DashboardViewModel` is a deliberate out-of-scope follow-up to avoid destabilizing a working screen.
