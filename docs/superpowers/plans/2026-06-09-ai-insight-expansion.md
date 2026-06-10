# AI Insight Expansion — Phase 1 (AI Layer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the on-device AI generation layer for three new insight types — Progress Trend Analysis, Recovery Readiness, and Rest-of-Day — with correct, unit-tested prompts and a generalized coordinator, without touching any screen UI.

**Architecture:** Each insight is a pure context (data in) → a prompt-builder method (pure function) → a per-kind generation `StateFlow` (result out). A new `InsightRequest` sealed type carries the context, its dedup key, and its data-sufficiency flag. The existing weekly-verdict path and model-lifecycle behavior are left untouched; the new per-kind mechanism is added additively to `AiInsightCoordinator` and implemented in both `GemmaInsightCoordinator` (real engine) and `StubInsightCoordinator` (deterministic, test-covered).

**Tech Stack:** Kotlin, kotlinx.coroutines (`StateFlow`, `Mutex`), LiteRT-LM (engine, untouched here), JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-09-ai-insight-expansion-design.md`

**Verification note:** This phase is verified by unit tests + prompt review only. The prompt-builder methods and contexts are pure and fully unit-tested. The generalized coordinator logic is tested via `StubInsightCoordinator`. The real Gemma generation path (engine streaming) is wired and compile-checked here; its model-output quality is verified on a physical device in phase 2.

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt` — Progress data + sufficiency + dedup key
- `app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt` — Recovery data + sufficiency + dedup key
- `app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt` — Food data + sufficiency + dedup key
- `app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt` — `InsightKind` enum + `InsightRequest` sealed type
- `app/src/test/java/com/zack/recomptracker/ai/ProgressInsightContextTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightContextTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightContextTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/InsightRequestTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/ProgressInsightPromptTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightPromptTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightPromptTest.kt`
- `app/src/test/java/com/zack/recomptracker/ai/StubInsightCoordinatorInsightKindTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` — add three build methods + band helpers
- `app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt` — add three interface members
- `app/src/main/java/com/zack/recomptracker/ai/StubInsightCoordinator.kt` — implement new members (deterministic)
- `app/src/main/java/com/zack/recomptracker/ai/GemmaInsightCoordinator.kt` — implement new members (real engine wiring)

---

## Task 1: ProgressInsightContext

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/ProgressInsightContextTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightContextTest {

    private fun ctx(
        rangeDays: Int = 28,
        weightTrendKgPerWeek: Double? = -0.2,
        waistTrendCmPerWeek: Double? = -0.3,
        liftTrendKgPerWeek: Double? = 0.5,
        adherencePercent: Double? = 90.0,
        weightPointCount: Int = 10,
        waistPointCount: Int = 8,
    ) = ProgressInsightContext(
        rangeDays, weightTrendKgPerWeek, waistTrendCmPerWeek,
        liftTrendKgPerWeek, adherencePercent, weightPointCount, waistPointCount,
    )

    @Test
    fun `sufficient when at least two weight points`() {
        assertTrue(ctx(weightPointCount = 2, waistPointCount = 0).hasSufficientData)
    }

    @Test
    fun `sufficient when at least two waist points`() {
        assertTrue(ctx(weightPointCount = 0, waistPointCount = 2).hasSufficientData)
    }

    @Test
    fun `insufficient when fewer than two of both`() {
        assertFalse(ctx(weightPointCount = 1, waistPointCount = 1).hasSufficientData)
    }

    @Test
    fun `key changes when weight trend changes meaningfully`() {
        assertTrue(ctx(weightTrendKgPerWeek = -0.2).key() != ctx(weightTrendKgPerWeek = 0.4).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightContextTest"`
Expected: FAIL — `ProgressInsightContext` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

import kotlin.math.roundToInt

data class ProgressInsightContext(
    val rangeDays: Int,
    val weightTrendKgPerWeek: Double?,
    val waistTrendCmPerWeek: Double?,
    val liftTrendKgPerWeek: Double?,
    val adherencePercent: Double?,
    val weightPointCount: Int,
    val waistPointCount: Int,
) {
    val hasSufficientData: Boolean
        get() = weightPointCount >= 2 || waistPointCount >= 2

    fun key(): String {
        val w = weightTrendKgPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val wa = waistTrendCmPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val l = liftTrendKgPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val a = adherencePercent?.roundToInt() ?: -1
        return "$rangeDays|$w|$wa|$l|$a"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightContextTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt app/src/test/java/com/zack/recomptracker/ai/ProgressInsightContextTest.kt
git commit -m "feat(ai): add ProgressInsightContext with sufficiency gate and dedup key"
```

---

## Task 2: RecoveryInsightContext

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightContextTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightContextTest {

    private fun ctx(
        sleepHours: Double? = 7.0,
        energyScore: Int? = 6,
        hungerScore: Int? = 4,
        sorenessScore: Int? = 5,
        trained: Boolean = true,
    ) = RecoveryInsightContext(sleepHours, energyScore, hungerScore, sorenessScore, trained)

    @Test
    fun `sufficient when only sleep logged`() {
        assertTrue(ctx(sleepHours = 6.0, energyScore = null, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `sufficient when only a score logged`() {
        assertTrue(ctx(sleepHours = null, energyScore = 5, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `insufficient when nothing logged`() {
        assertFalse(ctx(sleepHours = null, energyScore = null, hungerScore = null, sorenessScore = null).hasSufficientData)
    }

    @Test
    fun `key changes when soreness changes`() {
        assertTrue(ctx(sorenessScore = 3).key() != ctx(sorenessScore = 8).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightContextTest"`
Expected: FAIL — `RecoveryInsightContext` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

data class RecoveryInsightContext(
    val sleepHours: Double?,
    val energyScore: Int?,
    val hungerScore: Int?,
    val sorenessScore: Int?,
    val trained: Boolean,
) {
    val hasSufficientData: Boolean
        get() = sleepHours != null || energyScore != null ||
            hungerScore != null || sorenessScore != null

    fun key(): String =
        "${sleepHours ?: -1.0}|${energyScore ?: -1}|${hungerScore ?: -1}|${sorenessScore ?: -1}|$trained"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightContextTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightContextTest.kt
git commit -m "feat(ai): add RecoveryInsightContext with sufficiency gate and dedup key"
```

---

## Task 3: RestOfDayInsightContext

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightContextTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightContextTest {

    private fun ctx(
        caloriesConsumed: Int = 1420,
        targetCalories: Int = 2200,
        calorieZoneLowerBound: Int = 2100,
        calorieZoneUpperBound: Int = 2300,
        proteinConsumedG: Double = 102.0,
        proteinTargetG: Int = 165,
        mealsLoggedCount: Int = 2,
    ) = RestOfDayInsightContext(
        caloriesConsumed, targetCalories, calorieZoneLowerBound,
        calorieZoneUpperBound, proteinConsumedG, proteinTargetG, mealsLoggedCount,
    )

    @Test
    fun `sufficient when at least one meal logged`() {
        assertTrue(ctx(mealsLoggedCount = 1).hasSufficientData)
    }

    @Test
    fun `insufficient when no meals logged`() {
        assertFalse(ctx(mealsLoggedCount = 0).hasSufficientData)
    }

    @Test
    fun `key changes when calories consumed changes`() {
        assertTrue(ctx(caloriesConsumed = 1420).key() != ctx(caloriesConsumed = 1800).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightContextTest"`
Expected: FAIL — `RestOfDayInsightContext` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

import kotlin.math.roundToInt

data class RestOfDayInsightContext(
    val caloriesConsumed: Int,
    val targetCalories: Int,
    val calorieZoneLowerBound: Int,
    val calorieZoneUpperBound: Int,
    val proteinConsumedG: Double,
    val proteinTargetG: Int,
    val mealsLoggedCount: Int,
) {
    val hasSufficientData: Boolean
        get() = mealsLoggedCount >= 1

    fun key(): String =
        "$caloriesConsumed|${proteinConsumedG.roundToInt()}|$mealsLoggedCount"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightContextTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightContextTest.kt
git commit -m "feat(ai): add RestOfDayInsightContext with sufficiency gate and dedup key"
```

---

## Task 4: InsightKind + InsightRequest

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/InsightRequestTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightRequestTest {

    private val progressCtx = ProgressInsightContext(
        rangeDays = 28,
        weightTrendKgPerWeek = -0.2,
        waistTrendCmPerWeek = -0.3,
        liftTrendKgPerWeek = 0.5,
        adherencePercent = 90.0,
        weightPointCount = 1,
        waistPointCount = 1,
    )

    private val recoveryCtx = RecoveryInsightContext(
        sleepHours = 7.0, energyScore = 6, hungerScore = 4, sorenessScore = 5, trained = true,
    )

    @Test
    fun `progress request reports its kind`() {
        assertEquals(InsightKind.PROGRESS_TREND, InsightRequest.ProgressTrend(progressCtx).kind)
    }

    @Test
    fun `recovery request reports its kind`() {
        assertEquals(InsightKind.RECOVERY_READINESS, InsightRequest.RecoveryReadiness(recoveryCtx).kind)
    }

    @Test
    fun `request delegates sufficiency to context`() {
        assertFalse(InsightRequest.ProgressTrend(progressCtx).hasSufficientData)
        assertTrue(InsightRequest.RecoveryReadiness(recoveryCtx).hasSufficientData)
    }

    @Test
    fun `request delegates dedup key to context`() {
        assertEquals(progressCtx.key(), InsightRequest.ProgressTrend(progressCtx).dedupKey())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightRequestTest"`
Expected: FAIL — `InsightKind` / `InsightRequest` unresolved reference.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.zack.recomptracker.ai

enum class InsightKind { PROGRESS_TREND, RECOVERY_READINESS, REST_OF_DAY }

sealed interface InsightRequest {
    val kind: InsightKind
    val hasSufficientData: Boolean
    fun dedupKey(): String

    data class ProgressTrend(val context: ProgressInsightContext) : InsightRequest {
        override val kind = InsightKind.PROGRESS_TREND
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class RecoveryReadiness(val context: RecoveryInsightContext) : InsightRequest {
        override val kind = InsightKind.RECOVERY_READINESS
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }

    data class RestOfDay(val context: RestOfDayInsightContext) : InsightRequest {
        override val kind = InsightKind.REST_OF_DAY
        override val hasSufficientData get() = context.hasSufficientData
        override fun dedupKey() = context.key()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightRequestTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt app/src/test/java/com/zack/recomptracker/ai/InsightRequestTest.kt
git commit -m "feat(ai): add InsightKind enum and InsightRequest sealed type"
```

---

## Task 5: Progress Trend prompt builder

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/ProgressInsightPromptTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        rangeDays: Int = 28,
        weightTrendKgPerWeek: Double? = 0.05,
        waistTrendCmPerWeek: Double? = -0.30,
        liftTrendKgPerWeek: Double? = 0.5,
        adherencePercent: Double? = 91.0,
    ) = ProgressInsightContext(
        rangeDays, weightTrendKgPerWeek, waistTrendCmPerWeek,
        liftTrendKgPerWeek, adherencePercent, weightPointCount = 10, waistPointCount = 10,
    )

    @Test
    fun `includes the range window`() {
        assertTrue("28" in builder.buildProgressTrendPrompt(ctx(rangeDays = 28)))
    }

    @Test
    fun `weight trend is qualitative not numeric`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(weightTrendKgPerWeek = 0.05))
        assertTrue("Weight: stable" in prompt)
        assertFalse("0.05" in prompt)
    }

    @Test
    fun `waist trending down is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(waistTrendCmPerWeek = -0.30))
        assertTrue("Waist: trending down" in prompt)
        assertFalse("-0.30" in prompt)
    }

    @Test
    fun `lifts improving is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = 0.5))
        assertTrue("Lifts: improving" in prompt)
    }

    @Test
    fun `null lift trend renders no data`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = null))
        assertTrue("Lifts: no data" in prompt)
    }

    @Test
    fun `adherence is qualitative`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(adherencePercent = 91.0))
        assertTrue("Adherence: high" in prompt)
        assertFalse("91" in prompt)
    }

    @Test
    fun `instructs not to recommend calorie changes`() {
        val prompt = builder.buildProgressTrendPrompt(ctx())
        assertTrue(prompt.contains("not", ignoreCase = true) && prompt.contains("calorie", ignoreCase = true))
    }

    @Test
    fun `requests short output`() {
        val prompt = builder.buildProgressTrendPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
    }

    @Test
    fun `contains few-shot example`() {
        assertTrue("Example output" in builder.buildProgressTrendPrompt(ctx()))
    }

    @Test
    fun `instructs to avoid inventing data`() {
        assertTrue(builder.buildProgressTrendPrompt(ctx()).contains("invent", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightPromptTest"`
Expected: FAIL — `buildProgressTrendPrompt` unresolved reference.

- [ ] **Step 3: Add the method and lift helper to `InsightPromptBuilder`**

Add this method inside the `InsightPromptBuilder` class, immediately after the existing `buildWeeklySummaryPrompt(...)` function (it reuses the existing private `weightLabel`, `waistLabel`, `adherenceLabel`):

```kotlin
    fun buildProgressTrendPrompt(context: ProgressInsightContext): String = buildString {
        appendLine("You are a concise body-recomposition coach interpreting an athlete's progress trends.")
        appendLine("Write exactly 2–3 sentences in plain English explaining what the combination of trends means for body recomposition.")
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere. Interpret the trend only.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"Over the last four weeks your weight held steady while your waist trended down and your lifts kept climbing — that's recomposition, not a stall. Your logging has been consistent, so the trend is trustworthy. Stay the course and let another two weeks confirm it.\"")
        appendLine()
        appendLine("Window: last ${context.rangeDays} days")
        appendLine("Signals:")
        appendLine("- Weight: ${context.weightTrendKgPerWeek?.let { weightLabel(it) } ?: "no data"}")
        appendLine("- Waist: ${context.waistTrendCmPerWeek?.let { waistLabel(it) } ?: "no data"}")
        appendLine("- Lifts: ${liftTrendLabel(context.liftTrendKgPerWeek)}")
        appendLine("- Adherence: ${context.adherencePercent?.let { adherenceLabel(it) } ?: "no data"}")
    }
```

Add this private helper inside the `private companion object`'s enclosing class body — place it alongside the other private label functions (e.g. directly after `adherenceLabel`):

```kotlin
    private fun liftTrendLabel(kgPerWeek: Double?): String = when {
        kgPerWeek == null -> "no data"
        kgPerWeek > 0.1 -> "improving"
        kgPerWeek < -0.1 -> "declining"
        else -> "stable"
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightPromptTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/ProgressInsightPromptTest.kt
git commit -m "feat(ai): add Progress trend-analysis prompt builder"
```

---

## Task 6: Recovery Readiness prompt builder

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightPromptTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        sleepHours: Double? = 5.5,
        energyScore: Int? = 3,
        hungerScore: Int? = 5,
        sorenessScore: Int? = 8,
        trained: Boolean = true,
    ) = RecoveryInsightContext(sleepHours, energyScore, hungerScore, sorenessScore, trained)

    @Test
    fun `sleep below six is poor and not numeric`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 5.5))
        assertTrue("Sleep: poor" in prompt)
        assertFalse("5.5" in prompt)
    }

    @Test
    fun `energy three is low`() {
        assertTrue("Energy: low" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 3)))
    }

    @Test
    fun `soreness eight is high and not numeric`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sorenessScore = 8))
        assertTrue("Soreness: high" in prompt)
        assertFalse("Soreness: 8" in prompt)
    }

    @Test
    fun `hunger five is moderate`() {
        assertTrue("Hunger: moderate" in builder.buildRecoveryReadinessPrompt(ctx(hungerScore = 5)))
    }

    @Test
    fun `omits a signal that was not logged`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = null))
        assertFalse("Sleep:" in prompt)
    }

    @Test
    fun `shows trained status`() {
        assertTrue("Trained today: yes" in builder.buildRecoveryReadinessPrompt(ctx(trained = true)))
    }

    @Test
    fun `instructs no medical advice`() {
        assertTrue(builder.buildRecoveryReadinessPrompt(ctx()).contains("medical", ignoreCase = true))
    }

    @Test
    fun `requests short output and few-shot`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
        assertTrue("Example output" in prompt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightPromptTest"`
Expected: FAIL — `buildRecoveryReadinessPrompt` unresolved reference.

- [ ] **Step 3: Add the method and band helpers to `InsightPromptBuilder`**

Add this method inside the `InsightPromptBuilder` class, after `buildProgressTrendPrompt(...)`:

```kotlin
    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext): String = buildString {
        appendLine("You are a concise training-recovery coach.")
        appendLine("Write exactly 2–3 sentences in plain English about the athlete's training readiness today.")
        appendLine("Give practical training and recovery suggestions only. Do NOT give medical advice or diagnose anything.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"Two short nights with soreness running high and energy low suggests recovery hasn't caught up to your training. Prioritize sleep tonight and keep portions adequate. If soreness holds tomorrow, an easier session would help you bounce back.\"")
        appendLine()
        appendLine("Signals today:")
        context.sleepHours?.let { appendLine("- Sleep: ${sleepLabel(it)}") }
        context.energyScore?.let { appendLine("- Energy: ${scoreLabel(it)}") }
        context.hungerScore?.let { appendLine("- Hunger: ${scoreLabel(it)}") }
        context.sorenessScore?.let { appendLine("- Soreness: ${scoreLabel(it)}") }
        appendLine("- Trained today: ${if (context.trained) "yes" else "no"}")
    }
```

Add these private helpers alongside the other private label functions:

```kotlin
    private fun sleepLabel(hours: Double): String = when {
        hours < 6.0 -> "poor"
        hours < 7.5 -> "acceptable"
        else -> "good"
    }

    private fun scoreLabel(score: Int): String = when {
        score <= 3 -> "low"
        score <= 6 -> "moderate"
        else -> "high"
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightPromptTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightPromptTest.kt
git commit -m "feat(ai): add Recovery readiness prompt builder"
```

---

## Task 7: Rest-of-Day prompt builder

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightPromptTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        caloriesConsumed: Int = 1420,
        targetCalories: Int = 2200,
        calorieZoneLowerBound: Int = 2100,
        calorieZoneUpperBound: Int = 2300,
        proteinConsumedG: Double = 102.0,
        proteinTargetG: Int = 165,
        mealsLoggedCount: Int = 2,
    ) = RestOfDayInsightContext(
        caloriesConsumed, targetCalories, calorieZoneLowerBound,
        calorieZoneUpperBound, proteinConsumedG, proteinTargetG, mealsLoggedCount,
    )

    @Test
    fun `shows concrete calorie numbers`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(caloriesConsumed = 1420, targetCalories = 2200))
        assertTrue("1420" in prompt)
        assertTrue("2200" in prompt)
    }

    @Test
    fun `shows calories remaining`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(caloriesConsumed = 1420, targetCalories = 2200))
        assertTrue("780" in prompt)
    }

    @Test
    fun `shows protein gap`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(proteinConsumedG = 102.0, proteinTargetG = 165))
        assertTrue("63" in prompt)
    }

    @Test
    fun `shows meals logged count`() {
        assertTrue("2" in builder.buildRestOfDayPrompt(ctx(mealsLoggedCount = 2)))
    }

    @Test
    fun `instructs not to invent foods`() {
        assertTrue(builder.buildRestOfDayPrompt(ctx()).contains("invent", ignoreCase = true))
    }

    @Test
    fun `requests short output and few-shot`() {
        val prompt = builder.buildRestOfDayPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
        assertTrue("Example output" in prompt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightPromptTest"`
Expected: FAIL — `buildRestOfDayPrompt` unresolved reference.

- [ ] **Step 3: Add the method to `InsightPromptBuilder`**

Add `import kotlin.math.roundToInt` to the top of `InsightPromptBuilder.kt` (below the existing package/import lines), then add this method inside the class, after `buildRecoveryReadinessPrompt(...)`:

```kotlin
    fun buildRestOfDayPrompt(context: RestOfDayInsightContext): String = buildString {
        appendLine("You are a concise nutrition coach advising an athlete on the rest of their day.")
        appendLine("Write exactly 2–3 sentences in plain English: state where they stand and what to prioritize for the remaining meals.")
        appendLine("Do NOT invent specific foods, brands, or macro numbers beyond what is given. Frame the gap and give general guidance.")
        appendLine("Base everything only on the numbers below.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"You're at 1,420 of your 2,200-calorie target with 38 g of protein still to go. There's room for a solid dinner — make protein the centerpiece to close that gap. You're tracking well for the day.\"")
        appendLine()
        val calRemaining = context.targetCalories - context.caloriesConsumed
        val proteinRemaining = (context.proteinTargetG - context.proteinConsumedG).coerceAtLeast(0.0)
        appendLine("Current intake:")
        appendLine("- Calories: ${context.caloriesConsumed} of ${context.targetCalories} kcal ($calRemaining remaining)")
        appendLine("- Protein: ${context.proteinConsumedG.roundToInt()} of ${context.proteinTargetG} g (${proteinRemaining.roundToInt()} g remaining)")
        appendLine("- Calorie zone: ${context.calorieZoneLowerBound}–${context.calorieZoneUpperBound} kcal")
        appendLine("- Meals logged so far: ${context.mealsLoggedCount}")
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightPromptTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightPromptTest.kt
git commit -m "feat(ai): add Rest-of-Day prompt builder"
```

---

## Task 8: Generalize the coordinator interface + Stub implementation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/StubInsightCoordinator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/StubInsightCoordinatorInsightKindTest.kt`

- [ ] **Step 1: Add the three members to the `AiInsightCoordinator` interface**

Add these to the interface in `AiInsightCoordinator.kt` (keep all existing members). Add the import for `StateFlow` is already present:

```kotlin
    fun generationState(kind: InsightKind): StateFlow<AiInsightState>
    fun onInsightVisible(request: InsightRequest)
    fun retryInsight(request: InsightRequest)
```

- [ ] **Step 2: Implement the members in `StubInsightCoordinator`**

Add these properties and methods inside `StubInsightCoordinator` (keep all existing members):

```kotlin
    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }

    private val lastInsightKeys = mutableMapOf<InsightKind, String>()

    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        insightStates.getValue(kind).asStateFlow()

    override fun onInsightVisible(request: InsightRequest) {
        if (!request.hasSufficientData) return
        val flow = insightStates.getValue(request.kind)
        if (_state.value != AiInsightState.ModelReady) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        scope.launch {
            flow.value = AiInsightState.LoadingModel
            delay(50L)
            if (flow.value !is AiInsightState.LoadingModel) return@launch
            val text = stubInsightText(request)
            flow.value = AiInsightState.Generating(text)
            flow.value = AiInsightState.Ready(text)
        }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private fun stubInsightText(request: InsightRequest): String = when (request) {
        is InsightRequest.ProgressTrend -> "Your trends look stable this period."
        is InsightRequest.RecoveryReadiness -> "Your recovery looks on track today."
        is InsightRequest.RestOfDay -> "You're tracking well for the day."
    }
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StubInsightCoordinatorInsightKindTest {

    private val aiEnabledFlow = MutableStateFlow(false)

    private fun makeScope(parent: kotlin.coroutines.CoroutineContext) =
        CoroutineScope(parent + SupervisorJob())

    private fun sufficientProgress() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = -0.2,
            waistTrendCmPerWeek = -0.3,
            liftTrendKgPerWeek = 0.5,
            adherencePercent = 90.0,
            weightPointCount = 10,
            waistPointCount = 10,
        ),
    )

    private fun insufficientProgress() = InsightRequest.ProgressTrend(
        ProgressInsightContext(
            rangeDays = 28,
            weightTrendKgPerWeek = null,
            waistTrendCmPerWeek = null,
            liftTrendKgPerWeek = null,
            adherencePercent = null,
            weightPointCount = 1,
            waistPointCount = 0,
        ),
    )

    private fun ready(scope: CoroutineScope): StubInsightCoordinator {
        val c = StubInsightCoordinator(aiEnabledFlow, scope)
        aiEnabledFlow.value = true
        return c
    }

    @Test
    fun `insufficient data does not generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(insufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertEquals(AiInsightState.ModelReady, c.generationState(InsightKind.PROGRESS_TREND).value)
    }

    @Test
    fun `sufficient data transitions to Ready`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(sufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
    }

    @Test
    fun `same key does not re-generate`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val req = sufficientProgress()
        c.onInsightVisible(req)
        advanceUntilIdle()
        val first = (c.generationState(InsightKind.PROGRESS_TREND).value as AiInsightState.Ready).text
        c.onInsightVisible(req)
        advanceUntilIdle()
        val second = (c.generationState(InsightKind.PROGRESS_TREND).value as AiInsightState.Ready).text
        cs.cancel()
        assertEquals(first, second)
    }

    @Test
    fun `retry re-runs for cached key`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val req = sufficientProgress()
        c.onInsightVisible(req)
        advanceUntilIdle()
        c.retryInsight(req)
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
    }

    @Test
    fun `kinds are independent`() = runTest {
        aiEnabledFlow.value = false
        val cs = makeScope(coroutineContext)
        val c = ready(cs)
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onInsightVisible(sufficientProgress())
        advanceUntilIdle()
        cs.cancel()
        assertTrue(c.generationState(InsightKind.PROGRESS_TREND).value is AiInsightState.Ready)
        assertEquals(AiInsightState.ModelReady, c.generationState(InsightKind.RECOVERY_READINESS).value)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.StubInsightCoordinatorInsightKindTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt app/src/main/java/com/zack/recomptracker/ai/StubInsightCoordinator.kt app/src/test/java/com/zack/recomptracker/ai/StubInsightCoordinatorInsightKindTest.kt
git commit -m "feat(ai): generalize coordinator with per-kind insight generation (interface + stub)"
```

---

## Task 9: Implement per-kind generation in GemmaInsightCoordinator

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaInsightCoordinator.kt`

This task has no new unit test (the real engine path requires a device). It is verified by: (a) the project compiling, (b) the full existing test suite staying green, and (c) prompt review already done. The logic mirrors the Stub implementation from Task 8, which IS test-covered.

- [ ] **Step 1: Add per-kind state, helpers, and generation to `GemmaInsightCoordinator`**

Add these properties near the existing `lastGeneratedKey` declaration:

```kotlin
    private val insightStates: Map<InsightKind, MutableStateFlow<AiInsightState>> =
        InsightKind.entries.associateWith { MutableStateFlow<AiInsightState>(AiInsightState.ModelReady) }

    private val lastInsightKeys = mutableMapOf<InsightKind, String>()
```

Add these methods (the `promptBuilder` field already exists on the class):

```kotlin
    override fun generationState(kind: InsightKind): StateFlow<AiInsightState> =
        insightStates.getValue(kind).asStateFlow()

    override fun onInsightVisible(request: InsightRequest) {
        if (!request.hasSufficientData) return
        val flow = insightStates.getValue(request.kind)
        if (!isModelUsable()) {
            flow.value = _state.value
            return
        }
        val key = request.dedupKey()
        if (lastInsightKeys[request.kind] == key) return
        lastInsightKeys[request.kind] = key
        scope.launch { generateInsight(request, flow) }
    }

    override fun retryInsight(request: InsightRequest) {
        lastInsightKeys.remove(request.kind)
        insightStates.getValue(request.kind).value = AiInsightState.ModelReady
        onInsightVisible(request)
    }

    private fun isModelUsable(): Boolean = when (_state.value) {
        AiInsightState.Disabled,
        AiInsightState.ModelMissing,
        is AiInsightState.Downloading,
        AiInsightState.DownloadFailed,
        AiInsightState.ModelVerifying -> false
        else -> true
    }

    private suspend fun generateInsight(
        request: InsightRequest,
        flow: MutableStateFlow<AiInsightState>,
    ) {
        flow.value = AiInsightState.LoadingModel
        try {
            val variant = _selectedModel.value
            val modelPath = serviceHolder.modelFileFor(variant).absolutePath
            val service = serviceHolder.getOrCreateService(modelPath)
            service.ensureInitialized()

            if (flow.value !is AiInsightState.LoadingModel) return
            flow.value = AiInsightState.Generating("")
            val prompt = when (request) {
                is InsightRequest.ProgressTrend -> promptBuilder.buildProgressTrendPrompt(request.context)
                is InsightRequest.RecoveryReadiness -> promptBuilder.buildRecoveryReadinessPrompt(request.context)
                is InsightRequest.RestOfDay -> promptBuilder.buildRestOfDayPrompt(request.context)
            }

            val sb = StringBuilder()
            withTimeout(GENERATION_TIMEOUT_MS) {
                service.generateExplanation(prompt).collect { chunk ->
                    sb.append(chunk)
                    flow.value = AiInsightState.Generating(sb.toString())
                }
            }
            if (flow.value is AiInsightState.Generating) {
                val finalText = sb.toString()
                    .trim()
                    .replace(Regex("""[*_`#>]"""), "")
                    .replace(Regex("""\n{2,}"""), " ")
                flow.value = AiInsightState.Ready(finalText)
            }
        } catch (e: TimeoutCancellationException) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Took too long — try again.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (flow.value is AiInsightState.LoadingModel || flow.value is AiInsightState.Generating) {
                flow.value = AiInsightState.Error("Something went wrong — try again.")
            }
        }
    }
```

- [ ] **Step 2: Clear per-kind dedup keys when the model goes away**

In `deleteModel()`, add `lastInsightKeys.clear()` next to the existing `lastGeneratedKey = null` line:

```kotlin
    override fun deleteModel() {
        val variant = _selectedModel.value
        lastGeneratedKey = null
        lastInsightKeys.clear()
        serviceHolder.modelFileFor(variant).delete()
        scope.launch { serviceHolder.release() }
        _state.value = AiInsightState.ModelMissing
    }
```

In `setSelectedModel(...)`, add `lastInsightKeys.clear()` next to the existing `lastGeneratedKey = null` line (inside the `if (_state.value != AiInsightState.Disabled)` block):

```kotlin
            if (_state.value != AiInsightState.Disabled) {
                cancelActiveDownloadSilently()
                lastGeneratedKey = null
                lastInsightKeys.clear()
                serviceHolder.release()
                initializeForCurrentVariant()
            }
```

In the `aiEnabledFlow.collect` block in `init`, add `lastInsightKeys.clear()` in the disable branch:

```kotlin
                if (!enabled) {
                    cancelActiveDownloadSilently()
                    lastInsightKeys.clear()
                    _state.value = AiInsightState.Disabled
                }
```

- [ ] **Step 3: Type-check the whole module**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no unresolved references; `GemmaInsightCoordinator` now satisfies the expanded interface).

- [ ] **Step 4: Run the full unit-test suite for regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all existing tests (`InsightPromptBuilderTest`, `AiInsightStateTest`, `StubInsightCoordinatorTest`, coach tests) plus the new tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/GemmaInsightCoordinator.kt
git commit -m "feat(ai): implement per-kind insight generation in GemmaInsightCoordinator"
```

---

## Self-Review (completed during planning)

**Spec coverage:**
- §2 lifecycle/generation split → Tasks 8 & 9 (additive per-kind state; weekly path untouched). ✓
- §3 shared rules (few-shot, 2–3 sentences, grounding guard, sufficiency gate) → Tasks 1–7 (gates in contexts; prompt rules tested in 5–7). ✓
- §3.1 Progress qualitative + no calorie prescription → Task 5 (tests assert both). ✓
- §3.2 Recovery bands + no medical advice → Task 6 (tests assert both). ✓
- §3.3 Food concrete numbers + no invented foods → Task 7 (tests assert both). ✓
- §4 testing strategy → every builder/context has a dedicated test class; full-suite regression in Task 9. ✓
- §5 out-of-scope (no screen/VM changes) → no UI files touched. ✓
- §6 acceptance criteria → InsightKind (T4), per-kind state + onInsightVisible (T8/T9), three tested contexts (T1–3), three tested prompt methods (T5–7), green suite (T9). ✓

**Placeholder scan:** none — every step has complete code or an exact command.

**Type consistency:** `ProgressInsightContext(rangeDays, weightTrendKgPerWeek, waistTrendCmPerWeek, liftTrendKgPerWeek, adherencePercent, weightPointCount, waistPointCount)` used identically in Tasks 1, 4, 5, 8. `InsightRequest.ProgressTrend/RecoveryReadiness/RestOfDay`, `hasSufficientData`, `dedupKey()`, `generationState(kind)`, `onInsightVisible(request)`, `retryInsight(request)` consistent across Tasks 4, 8, 9. Build-method names (`buildProgressTrendPrompt`, `buildRecoveryReadinessPrompt`, `buildRestOfDayPrompt`) consistent across Tasks 5–7 and 9. Helper names (`liftTrendLabel`, `sleepLabel`, `scoreLabel`) defined once and reused. ✓
