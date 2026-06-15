# De-bucket Insight Prompt Signals (Option A) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the AI insight cards specific by feeding the model the real numbers it already has, instead of pre-bucketed words like "trending down" / "poor" / "high".

**Architecture:** Single production file change — `ai/InsightPromptBuilder.kt`. Every context object already carries the raw numbers; the builder currently discards their precision at the last step. We add a `signed(value, decimals)` formatter, render each numeric signal as `<number> <unit> (<existing label>)`, add a "lead with a number" instruction plus a "do not do your own math" hallucination guard, and upgrade the few-shot examples to cite numbers. Deterministic labels stay as the guardrail. Tests are pure prompt-string assertions — no model needed.

**Tech Stack:** Kotlin, JUnit4, Gradle (`:app:testDebugUnitTest`).

**Spec:** `docs/superpowers/specs/2026-06-15-insight-cards-debucket-design.md`

---

## Design notes (read before starting)

- **Parenthetical hint = the existing label function, verbatim.** The spec's illustrative "(down)" maps to the real label text "(trending down)". Reusing `weightLabel()`/`waistLabel()`/`adherenceLabel()`/`liftTrendLabel()`/`sleepLabel()`/`scoreLabel()` means **zero new label functions** — only the numeric formatter is new. Adherence's low label is `"low (below target)"`, so the low line reads `Adherence: 60% (low (below target))` — this nested paren is intentional and acceptable in a prompt.
- **Units stay kg/cm** (the app shows kg/cm everywhere today; `useMetricUnits` is dormant).
- **Performance & Recovery on the weekly card stay labels** — they arrive as enums, not numbers.
- **Null trends keep `"no data"`** exactly as today.
- **Two new instruction strings** (asserted by tests, so they must match exactly):
  - `Lead with the most decisive number from the signals below.` (Rest-of-day uses `...from the numbers below.`)
  - `Use only the figures given; do not do any math of your own.`

### Commands

- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.<ClassName>"`
- Whole module: `./gradlew :app:testDebugUnitTest`
- Type-check only: `./gradlew :app:compileDebugKotlin`

---

## Task 1: Add the `signed` number formatter

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (companion object + imports)
- Test: `app/src/test/java/com/zack/recomptracker/ai/SignedFormatTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/SignedFormatTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SignedFormatTest {

    @Test
    fun `positive value gets an explicit plus sign`() {
        assertEquals("+0.05", InsightPromptBuilder.signed(0.05, 2))
    }

    @Test
    fun `negative value keeps its minus sign`() {
        assertEquals("-0.35", InsightPromptBuilder.signed(-0.35, 2))
    }

    @Test
    fun `one decimal place rounds and signs`() {
        assertEquals("+0.4", InsightPromptBuilder.signed(0.4, 1))
    }

    @Test
    fun `near-zero renders as unsigned zero`() {
        assertEquals("0.0", InsightPromptBuilder.signed(0.02, 1))
    }

    @Test
    fun `negative-near-zero is normalized to plain zero (no minus)`() {
        assertEquals("0.00", InsightPromptBuilder.signed(-0.001, 2))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.SignedFormatTest"`
Expected: FAIL — `signed` is unresolved (compile error).

- [ ] **Step 3: Add the formatter and imports**

In `InsightPromptBuilder.kt`, add these imports under the existing `import kotlin.math.roundToInt` (line 6):

```kotlin
import kotlin.math.pow
import java.util.Locale
```

Then, inside the `companion object` (starts at line 166), directly **above** the existing `fun limitToSentences(...)`, add:

```kotlin
        /**
         * Formats [value] to [decimals] places with an explicit leading sign:
         * positives get "+", negatives keep "-", and anything that rounds to zero
         * renders unsigned (no "+0.00" or "-0.00"). US locale so the decimal is always a dot.
         */
        internal fun signed(value: Double, decimals: Int): String {
            val factor = 10.0.pow(decimals)
            val rounded = (value * factor).roundToInt() / factor
            val norm = if (rounded == 0.0) 0.0 else rounded
            val body = String.format(Locale.US, "%.${decimals}f", norm)
            return if (norm > 0.0) "+$body" else body
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.SignedFormatTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/SignedFormatTest.kt
git commit -m "feat(ai-insight): add signed number formatter for insight prompts"
```

---

## Task 2: De-bucket the weekly card

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (`buildWeeklySummaryPrompt`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderTest.kt`

- [ ] **Step 1: Update the failing tests**

In `InsightPromptBuilderTest.kt`, **replace** the entire "Qualitative weight signal", "Qualitative waist signal", and "Qualitative adherence signal" test blocks (the methods from `weight trending down is qualitative` through `adherence low is qualitative`, lines 115–213) with:

```kotlin
    // --- Numeric weight signal ---

    @Test
    fun `weight trend shows the signed number and label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = -0.35))
        assertTrue("Expected numeric weight line", "Weight: -0.35 kg/wk (trending down)" in prompt)
    }

    @Test
    fun `weight near zero shows signed number and stable label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = 0.05))
        assertTrue("Expected numeric stable weight line", "Weight: +0.05 kg/wk (stable)" in prompt)
    }

    @Test
    fun `weight trending up shows positive number`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = 0.35))
        assertTrue("Expected numeric up weight line", "Weight: +0.35 kg/wk (trending up)" in prompt)
    }

    // --- Numeric waist signal ---

    @Test
    fun `waist near zero shows zero and stable label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(waistTrend = 0.02))
        assertTrue("Expected numeric stable waist line", "Waist: 0.0 cm/wk (stable)" in prompt)
    }

    @Test
    fun `waist trending up shows positive number`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(waistTrend = 0.30))
        assertTrue("Expected numeric up waist line", "Waist: +0.3 cm/wk (trending up)" in prompt)
    }

    // --- Qualitative performance signal (unchanged: enum, no number) ---

    @Test
    fun `performance DOWN maps to declining`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.DOWN))
        assertTrue("Expected 'declining'", "Performance: declining" in prompt)
        assertFalse("Raw enum label must not appear", "Performance: DOWN" in prompt)
    }

    @Test
    fun `performance UP maps to improving`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.UP))
        assertTrue("Expected 'improving'", "Performance: improving" in prompt)
        assertFalse("Raw enum label must not appear", "Performance: UP" in prompt)
    }

    @Test
    fun `performance UNKNOWN maps to no data`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.UNKNOWN))
        assertTrue("Expected 'no data'", "Performance: no data" in prompt)
    }

    // --- Qualitative recovery signal (unchanged: enum, no number) ---

    @Test
    fun `recovery POOR maps to poor`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(recoveryTrend = RecoveryTrend.POOR))
        assertTrue("Expected 'poor'", "Recovery: poor" in prompt)
        assertFalse("Raw enum label must not appear", "Recovery: POOR" in prompt)
    }

    @Test
    fun `recovery GOOD maps to good`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(recoveryTrend = RecoveryTrend.GOOD))
        assertTrue("Expected 'good'", "Recovery: good" in prompt)
        assertFalse("Raw enum label must not appear", "Recovery: GOOD" in prompt)
    }

    // --- Numeric adherence signal ---

    @Test
    fun `adherence shows integer percent and high label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 91.0))
        assertTrue("Expected numeric adherence line", "Adherence: 91% (high)" in prompt)
    }

    @Test
    fun `adherence moderate shows percent and label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 78.0))
        assertTrue("Expected numeric moderate adherence", "Adherence: 78% (moderate)" in prompt)
    }

    @Test
    fun `adherence low shows percent and below-target label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 60.0))
        assertTrue("Expected numeric low adherence", "Adherence: 60% (low (below target))" in prompt)
    }

    // --- New instruction guards ---

    @Test
    fun `prompt instructs to lead with a number`() {
        val prompt = builder.buildWeeklySummaryPrompt(context())
        assertTrue("Expected lead-with-number instruction", "Lead with the most decisive number" in prompt)
    }

    @Test
    fun `prompt forbids the model doing its own math`() {
        val prompt = builder.buildWeeklySummaryPrompt(context())
        assertTrue("Expected no-math guard", "do not do any math" in prompt)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderTest"`
Expected: FAIL — the numeric/instruction tests fail (builder still emits bucketed labels and lacks the new instruction lines).

- [ ] **Step 3: Rewrite `buildWeeklySummaryPrompt`**

In `InsightPromptBuilder.kt`, replace the whole `buildWeeklySummaryPrompt` method (lines 10–37) with:

```kotlin
    fun buildWeeklySummaryPrompt(context: InsightContext): String = buildString {
        appendLine("You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 1–2 short sentences. Lead with the decisive signal, then state the verdict — do not change it. No preamble or filler.")
        appendLine("Be specific about which signals drove the decision. Keep the tone calm and direct.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output for a Hold verdict:")
        appendLine("\"Weight is down 0.30 kg/wk with waist flat and adherence at 88% — fat loss is on track, hold calories.\"")
        appendLine()
        appendLine("Verdict: ${verdictLabel(context.result.verdict)}")
        appendLine("Context: ${context.result.summary}")
        appendLine()
        appendLine("Reasons:")
        context.result.reasonCodes.forEach { code ->
            appendLine("- ${reasonDescription(code)}")
        }
        appendLine()
        appendLine("Signals this week:")
        appendLine("- Weight: ${signed(context.input.weightTrendKgPerWeek, 2)} kg/wk (${weightLabel(context.input.weightTrendKgPerWeek)})")
        appendLine("- Waist: ${signed(context.input.waistTrendCmPerWeek, 1)} cm/wk (${waistLabel(context.input.waistTrendCmPerWeek)})")
        appendLine("- Performance: ${performanceLabel(context.input.performanceTrend)}")
        appendLine("- Recovery: ${recoveryLabel(context.input.recoveryTrend)}")
        appendLine("- Adherence: ${context.input.adherencePercent.roundToInt()}% (${adherenceLabel(context.input.adherencePercent)})")
        if (context.input.weeksSincePhaseStart != DEFAULT_WEEKS_FALLBACK) {
            appendLine("- Weeks in current phase: ${context.input.weeksSincePhaseStart}")
        }
        appendLine()
        appendLine("Calorie target: ${context.targetCalories} kcal | Protein target: ${context.targetProteinG}g")
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderTest"`
Expected: PASS (all tests in the class, including the unchanged verdict/reason-code/weeks tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderTest.kt
git commit -m "feat(ai-insight): show real numbers in weekly insight prompt"
```

---

## Task 3: De-bucket the progress card

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (`buildProgressTrendPrompt`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/ProgressInsightPromptTest.kt`

- [ ] **Step 1: Update the failing tests**

In `ProgressInsightPromptTest.kt`, replace the four methods `weight trend is qualitative not numeric`, `waist trending down is qualitative`, `lifts improving is qualitative`, and `adherence is qualitative` (lines 27–58) with:

```kotlin
    @Test
    fun `weight trend shows the signed number and label`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(weightTrendKgPerWeek = 0.05))
        assertTrue("Weight: +0.05 kg/wk (stable)" in prompt)
    }

    @Test
    fun `waist trending down shows the signed number`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(waistTrendCmPerWeek = -0.30))
        assertTrue("Waist: -0.3 cm/wk (trending down)" in prompt)
    }

    @Test
    fun `lifts improving shows the signed e1RM number`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = 0.5))
        assertTrue("Lifts: +0.5 kg/wk e1RM (improving)" in prompt)
    }

    @Test
    fun `adherence shows integer percent and label`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(adherencePercent = 91.0))
        assertTrue("Adherence: 91% (high)" in prompt)
    }
```

Then replace the `lifts declining is qualitative` method (lines 82–86) with:

```kotlin
    @Test
    fun `lifts declining shows the negative number`() {
        val prompt = builder.buildProgressTrendPrompt(ctx(liftTrendKgPerWeek = -0.5))
        assertTrue("Lifts: -0.5 kg/wk e1RM (declining)" in prompt)
    }
```

Then add these two methods at the end of the class (before the final closing brace):

```kotlin
    @Test
    fun `instructs to lead with a number`() {
        assertTrue("Lead with the most decisive number" in builder.buildProgressTrendPrompt(ctx()))
    }

    @Test
    fun `forbids the model doing its own math`() {
        assertTrue("do not do any math" in builder.buildProgressTrendPrompt(ctx()))
    }
```

(The `null lift trend renders no data`, `null weight trend renders no data`, `null adherence renders no data`, range, short-output, few-shot, "not invent", and "not recommend calories" tests are unchanged and must keep passing.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightPromptTest"`
Expected: FAIL — numeric/instruction tests fail.

- [ ] **Step 3: Rewrite `buildProgressTrendPrompt`**

Replace the whole `buildProgressTrendPrompt` method (lines 39–58) with:

```kotlin
    fun buildProgressTrendPrompt(context: ProgressInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a body-recomposition coach interpreting an athlete's progress trends.")
        if (rich) {
            appendLine("Write a thorough, cross-signal interpretation (4–6 sentences) of what the combination of trends means for body recomposition. Connect the signals to each other; call out tension or agreement between weight, waist, lifts, and adherence.")
        } else {
            appendLine("Write exactly 1–2 short sentences in plain English: name the key signals and what they mean together for recomposition. No preamble or filler.")
        }
        appendLine("Do NOT recommend changing calories or macros — that decision is made elsewhere. Interpret the trend only.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"Weight is flat at -0.05 kg/wk while waist is down 0.3 cm/wk and lifts are up 0.4 kg/wk — textbook recomposition, stay the course.\"")
        appendLine()
        appendLine("Window: last ${context.rangeDays} days")
        appendLine("Signals:")
        appendLine("- Weight: ${context.weightTrendKgPerWeek?.let { "${signed(it, 2)} kg/wk (${weightLabel(it)})" } ?: "no data"}")
        appendLine("- Waist: ${context.waistTrendCmPerWeek?.let { "${signed(it, 1)} cm/wk (${waistLabel(it)})" } ?: "no data"}")
        appendLine("- Lifts: ${context.liftTrendKgPerWeek?.let { "${signed(it, 1)} kg/wk e1RM (${liftTrendLabel(it)})" } ?: "no data"}")
        appendLine("- Adherence: ${context.adherencePercent?.let { "${it.roundToInt()}% (${adherenceLabel(it)})" } ?: "no data"}")
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.ProgressInsightPromptTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/ProgressInsightPromptTest.kt
git commit -m "feat(ai-insight): show real numbers in progress trend prompt"
```

---

## Task 4: De-bucket the recovery card

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (`buildRecoveryReadinessPrompt`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightPromptTest.kt`

- [ ] **Step 1: Update the failing tests**

In `RecoveryInsightPromptTest.kt`, replace the methods `sleep below six is poor and not numeric`, `energy three is low`, `soreness eight is high and not numeric`, and `hunger five is moderate` (lines 19–41) with:

```kotlin
    @Test
    fun `sleep shows hours and poor label`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 5.5))
        assertTrue("Sleep: 5.5 h (poor)" in prompt)
    }

    @Test
    fun `energy shows score out of ten and low label`() {
        assertTrue("Energy: 3/10 (low)" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 3)))
    }

    @Test
    fun `soreness shows score out of ten and high label`() {
        val prompt = builder.buildRecoveryReadinessPrompt(ctx(sorenessScore = 8))
        assertTrue("Soreness: 8/10 (high)" in prompt)
    }

    @Test
    fun `hunger shows score out of ten and moderate label`() {
        assertTrue("Hunger: 5/10 (moderate)" in builder.buildRecoveryReadinessPrompt(ctx(hungerScore = 5)))
    }
```

Then replace `sleep between six and seven point five is acceptable`, `sleep at or above seven point five is good`, and `score seven is high` (lines 66–79) with:

```kotlin
    @Test
    fun `sleep between six and seven point five is acceptable`() {
        assertTrue("Sleep: 7.0 h (acceptable)" in builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 7.0)))
    }

    @Test
    fun `sleep at or above seven point five is good`() {
        assertTrue("Sleep: 8.0 h (good)" in builder.buildRecoveryReadinessPrompt(ctx(sleepHours = 8.0)))
    }

    @Test
    fun `energy seven is high`() {
        assertTrue("Energy: 7/10 (high)" in builder.buildRecoveryReadinessPrompt(ctx(energyScore = 7)))
    }
```

Then add at the end of the class (before the final closing brace):

```kotlin
    @Test
    fun `instructs to lead with a number`() {
        assertTrue("Lead with the most decisive number" in builder.buildRecoveryReadinessPrompt(ctx()))
    }

    @Test
    fun `forbids the model doing its own math`() {
        assertTrue("do not do any math" in builder.buildRecoveryReadinessPrompt(ctx()))
    }
```

(The `omits a signal that was not logged`, `shows trained status`, `shows not trained status`, `instructs no medical advice`, and `requests short output and few-shot` tests are unchanged and must keep passing.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightPromptTest"`
Expected: FAIL — numeric/instruction tests fail.

- [ ] **Step 3: Rewrite `buildRecoveryReadinessPrompt`**

Replace the whole `buildRecoveryReadinessPrompt` method (lines 60–79) with:

```kotlin
    fun buildRecoveryReadinessPrompt(context: RecoveryInsightContext, rich: Boolean = false): String = buildString {
        appendLine("You are a training-recovery coach.")
        if (rich) {
            appendLine("Write a thorough, cross-signal readiness assessment (4–6 sentences) for the athlete today. Relate sleep, energy, hunger, and soreness to each other and to whether they trained.")
        } else {
            appendLine("Write exactly 1–2 short sentences: assess readiness today and give one concrete suggestion. No preamble or filler.")
        }
        appendLine("Give practical training and recovery suggestions only. Do NOT give medical advice or diagnose anything.")
        appendLine("Base everything only on the signals below. Do not invent data.")
        appendLine("Lead with the most decisive number from the signals below.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output:")
        appendLine("\"On 5 hours of sleep with soreness at 8/10, recovery is behind — keep today light and prioritize sleep tonight.\"")
        appendLine()
        appendLine("Signals today:")
        context.sleepHours?.let { appendLine("- Sleep: ${String.format(Locale.US, "%.1f", it)} h (${sleepLabel(it)})") }
        context.energyScore?.let { appendLine("- Energy: $it/10 (${scoreLabel(it)})") }
        context.hungerScore?.let { appendLine("- Hunger: $it/10 (${scoreLabel(it)})") }
        context.sorenessScore?.let { appendLine("- Soreness: $it/10 (${scoreLabel(it)})") }
        appendLine("- Trained today: ${if (context.trained) "yes" else "no"}")
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RecoveryInsightPromptTest"`
Expected: PASS. Also run the rich-mode test to confirm it's unaffected:
`./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderRichModeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/RecoveryInsightPromptTest.kt
git commit -m "feat(ai-insight): show real numbers in recovery readiness prompt"
```

---

## Task 5: Add the instruction guards to the rest-of-day card

The rest-of-day card already shows raw numbers, so only the two instruction lines are added (for consistency and the no-math guard).

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (`buildRestOfDayPrompt`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightPromptTest.kt`

- [ ] **Step 1: Add the failing tests**

In `RestOfDayInsightPromptTest.kt`, add at the end of the class (before the final closing brace):

```kotlin
    @Test
    fun `instructs to lead with a number`() {
        assertTrue("Lead with the most decisive number" in builder.buildRestOfDayPrompt(ctx()))
    }

    @Test
    fun `forbids the model doing its own math`() {
        assertTrue("do not do any math" in builder.buildRestOfDayPrompt(ctx()))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightPromptTest"`
Expected: FAIL — the two new instruction tests fail.

- [ ] **Step 3: Insert the instruction lines**

In `buildRestOfDayPrompt`, find this line (line 89):

```kotlin
        appendLine("Base everything only on the numbers below.")
```

and insert directly **after** it:

```kotlin
        appendLine("Lead with the most decisive number from the numbers below.")
        appendLine("Use only the figures given; do not do any math of your own.")
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.RestOfDayInsightPromptTest"`
Expected: PASS (including the unchanged numeric/clamp/few-shot tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/RestOfDayInsightPromptTest.kt
git commit -m "feat(ai-insight): add no-math guard to rest-of-day prompt"
```

---

## Task 6: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the whole unit-test module**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — all tests green. Pay attention to any other suite that asserts on insight prompt text (e.g. `CloudInsightCoordinatorTest`, `StubInsightCoordinatorTest`); these assert coordinator behavior, not bucketed wording, and should be unaffected. If any fail because they pinned old bucketed text, update the expected string to the new `<number> <unit> (<label>)` form and re-run.

- [ ] **Step 2: Type-check the app**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Final commit (if Step 1 required any incidental test fixes)**

```bash
git add -A
git commit -m "test(ai-insight): update remaining prompt assertions for numeric signals"
```

If no changes were needed in Steps 1–2, skip this commit.

---

## Self-review checklist (completed by plan author)

- **Spec coverage:** Weekly (Task 2), Progress (Task 3), Recovery (Task 4), Rest-of-day (Task 5) all de-bucketed/guarded; `signed` formatter (Task 1); instruction guards + upgraded examples in each card; kg/cm retained; no contexts/mappers/ViewModels touched; tests updated. ✅
- **Placeholder scan:** No TBD/TODO; every step has concrete code and exact expected output. ✅
- **Type consistency:** `signed(value: Double, decimals: Int)` defined in Task 1, used identically in Tasks 2–4. `weightLabel`/`waistLabel`/`adherenceLabel`/`liftTrendLabel`/`sleepLabel`/`scoreLabel` are existing private members, reused unchanged. `roundToInt` already imported; `pow` and `Locale` added in Task 1. ✅
- **Instruction strings** asserted by tests (`Lead with the most decisive number`, `do not do any math`) match the strings emitted by the builder exactly. ✅
