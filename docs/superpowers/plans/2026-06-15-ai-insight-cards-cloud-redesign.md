# AI Insight Cards — Cloud Redesign & Iteration Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fast, no-emulator iteration harness that renders each insight card's real cloud prompt against realistic data and scores it with an LLM-judge, then redesign the card prompts (and their input data) to the output doctrine.

**Architecture:** A self-skipping JUnit test in `app/src/test` reuses the production `InsightPromptBuilder` + `OpenAiCompatClient` + `CloudConfig`, reads creds from a git-ignored `.env.test`, generates each card, judges it, and prints a compact report. Then the prompt builders and context objects are extended to the doctrine (baseline comparison, name-the-driver, stay-quiet gate) and three new cards are added, validated through the same harness.

**Tech Stack:** Kotlin, JUnit4, kotlinx-coroutines (`runBlocking`), kotlinx.serialization JSON, OkHttp (via existing `OpenAiCompatClient`), Gradle.

**Reference docs:** [output doctrine](../../ai-insight-cards/insight-output-doctrine.md) · [spec](../specs/2026-06-15-ai-insight-cards-cloud-redesign-design.md) · [ideas backlog](../../ai-insight-cards/ai-ideas-backlog.md)

---

## File Structure

**Phase 1 — Harness (test-only, ships nothing to the app):**
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnv.kt` — reads `.env.test`, returns creds or null.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenarios.kt` — the realistic fixture data.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudge.kt` — judge prompt + JSON score parsing.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnvTest.kt` — offline tests for the loader.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudgeTest.kt` — offline tests for the parser.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenariosTest.kt` — offline fixture-validity test.
- Create `app/src/test/java/com/zack/recomptracker/ai/harness/InsightHarnessTest.kt` — the network harness (self-skips).
- Create `.env.test.example` (repo root) — documents the keys.
- Modify `.gitignore` (repo root) — ignore `.env.test`.

**Phase 2 — Prompt + context redesign (production code):**
- Modify `app/src/main/java/com/zack/recomptracker/ai/InsightContext.kt` — additive baseline fields.
- Modify `app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt` — additive prior-window field.
- Modify `app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt` — additive personal-average fields.
- Modify `app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt` — additive pace field.
- Modify `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` — rewrite prompts to doctrine.
- Create `app/src/main/java/com/zack/recomptracker/ai/InsightGate.kt` — the stay-quiet `shouldFire` gate.
- Test files alongside, mirroring existing `*PromptTest.kt` conventions.

**Phase 3 — New cards (production code, follows output validation):**
- Modify `app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt` — new kinds + request variants.
- Create new context types + builder methods for target-change, noise-defuser, cross-metric cards.
- Modify `app/src/main/java/com/zack/recomptracker/ai/CloudInsightCoordinator.kt` — dispatch new requests.

---

## Conventions (read once)

- Run a single test: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.HarnessEnvTest"`
- Run the network harness: `./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"` (verbose: add `-DinsightVerbose=true`)
- Type-check only: `./gradlew :app:compileDebugKotlin`
- Tests are JUnit4: `import org.junit.Test`, `import org.junit.Assert.*`, `import org.junit.Assume.assumeTrue`.
- Commit after every green step. We are on branch `feat/ai-insight-cards-cloud-redesign`.

---

## Phase 1 — The Iteration Harness

### Task 1: `.env.test` plumbing

**Files:**
- Create: `.env.test.example`
- Modify: `.gitignore`

- [ ] **Step 1: Create the example env file**

Create `.env.test.example`:
```
# Copy to .env.test (git-ignored) and fill in. The harness self-skips if absent.
# Any OpenAI-compatible endpoint. baseUrl has NO trailing slash and includes the version path.
INSIGHT_BASE_URL=https://api.openai.com/v1
INSIGHT_API_KEY=sk-replace-me
INSIGHT_MODEL=gpt-4o-mini
# Optional. Defaults to INSIGHT_MODEL if omitted.
INSIGHT_JUDGE_MODEL=gpt-4o-mini
```

- [ ] **Step 2: Ignore the real env file**

Append to `.gitignore`:
```
# Local-only cloud creds for the insight harness
.env.test
```

- [ ] **Step 3: Verify it's ignored**

Run: `touch .env.test && git status --porcelain .env.test && rm .env.test`
Expected: no output (the file is ignored, so `git status` lists nothing).

- [ ] **Step 4: Commit**

```bash
git add .env.test.example .gitignore
git commit -m "chore(harness): add .env.test.example and ignore .env.test"
```

---

### Task 2: Env loader (`HarnessEnv`)

**Files:**
- Create: `app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnv.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnvTest.kt`

- [ ] **Step 1: Write the failing test**

Create `HarnessEnvTest.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarnessEnvTest {

    @Test
    fun `parses keys and defaults judge model to model`() {
        val text = """
            # a comment
            INSIGHT_BASE_URL=https://api.x.com/v1
            INSIGHT_API_KEY=sk-abc

            INSIGHT_MODEL=model-a
        """.trimIndent()
        val env = HarnessEnv.parse(text)!!
        assertEquals("https://api.x.com/v1", env.baseUrl)
        assertEquals("sk-abc", env.apiKey)
        assertEquals("model-a", env.model)
        assertEquals("model-a", env.judgeModel)
    }

    @Test
    fun `explicit judge model overrides default`() {
        val text = """
            INSIGHT_BASE_URL=https://api.x.com/v1
            INSIGHT_API_KEY=sk-abc
            INSIGHT_MODEL=model-a
            INSIGHT_JUDGE_MODEL=judge-b
        """.trimIndent()
        assertEquals("judge-b", HarnessEnv.parse(text)!!.judgeModel)
    }

    @Test
    fun `missing required key yields null`() {
        val text = "INSIGHT_BASE_URL=https://x\nINSIGHT_MODEL=m"
        assertNull(HarnessEnv.parse(text))
    }

    @Test
    fun `quoted values are unwrapped`() {
        val text = """
            INSIGHT_BASE_URL="https://api.x.com/v1"
            INSIGHT_API_KEY='sk-abc'
            INSIGHT_MODEL=model-a
        """.trimIndent()
        val env = HarnessEnv.parse(text)!!
        assertEquals("https://api.x.com/v1", env.baseUrl)
        assertEquals("sk-abc", env.apiKey)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.HarnessEnvTest"`
Expected: FAIL — `HarnessEnv` unresolved.

- [ ] **Step 3: Write the implementation**

Create `HarnessEnv.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.data.remote.CloudConfig
import java.io.File

/**
 * Local-only cloud creds for the insight harness. Loaded from `.env.test` (git-ignored)
 * or process env vars. Pure parsing lives in [parse] so it is unit-testable offline.
 */
data class HarnessEnv(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val judgeModel: String,
) {
    fun generationConfig() = CloudConfig(baseUrl = baseUrl, apiKey = apiKey, model = model)
    fun judgeConfig() = CloudConfig(baseUrl = baseUrl, apiKey = apiKey, model = judgeModel)

    companion object {
        /** Parses dotenv-style text. Returns null if any required key is missing/blank. */
        fun parse(text: String): HarnessEnv? {
            val map = HashMap<String, String>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val idx = line.indexOf('=')
                if (idx <= 0) return@forEach
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim().trim('"', '\'')
                if (value.isNotEmpty()) map[key] = value
            }
            val baseUrl = map["INSIGHT_BASE_URL"] ?: return null
            val apiKey = map["INSIGHT_API_KEY"] ?: return null
            val model = map["INSIGHT_MODEL"] ?: return null
            val judge = map["INSIGHT_JUDGE_MODEL"] ?: model
            return HarnessEnv(baseUrl, apiKey, model, judge)
        }

        /**
         * Resolves creds for a live run: process env vars first, else the nearest `.env.test`
         * found by walking up from the working directory (Gradle runs tests with cwd = module dir,
         * so the repo-root file is one or two levels up). Returns null when nothing is configured.
         */
        fun load(): HarnessEnv? {
            System.getenv("INSIGHT_API_KEY")?.let { key ->
                val base = System.getenv("INSIGHT_BASE_URL")
                val model = System.getenv("INSIGHT_MODEL")
                if (base != null && model != null) {
                    return HarnessEnv(base, key, model, System.getenv("INSIGHT_JUDGE_MODEL") ?: model)
                }
            }
            var dir: File? = File(System.getProperty("user.dir"))
            repeat(4) {
                val candidate = dir?.resolve(".env.test")
                if (candidate != null && candidate.isFile) return parse(candidate.readText())
                dir = dir?.parentFile
            }
            return null
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.HarnessEnvTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnv.kt app/src/test/java/com/zack/recomptracker/ai/harness/HarnessEnvTest.kt
git commit -m "feat(harness): env loader for .env.test"
```

---

### Task 3: Judge prompt + score parser (`InsightJudge`)

**Files:**
- Create: `app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudge.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudgeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `InsightJudgeTest.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightJudgeTest {

    @Test
    fun `judge prompt embeds the data, the output, and the rubric axes`() {
        val prompt = InsightJudge.buildPrompt(
            cardLabel = "Weekly Summary",
            dataPrompt = "Weight: -0.30 kg/wk",
            output = "Down 0.30/wk — hold calories.",
        )
        assertTrue("data echoed", "Weight: -0.30 kg/wk" in prompt)
        assertTrue("output echoed", "hold calories" in prompt)
        assertTrue("accuracy axis", prompt.contains("accuracy", ignoreCase = true))
        assertTrue("shouldFire axis", prompt.contains("shouldFire"))
        assertTrue("asks for strict JSON", prompt.contains("JSON"))
    }

    @Test
    fun `parses a clean JSON score object`() {
        val json = """
            {"accuracy":5,"actionability":4,"proactivity":4,"tone":5,"brevity":5,
             "shouldFire":true,"notes":"good"}
        """.trimIndent()
        val s = InsightJudge.parse(json)!!
        assertEquals(5, s.accuracy)
        assertEquals(4, s.actionability)
        assertEquals(true, s.shouldFire)
        assertEquals("good", s.notes)
    }

    @Test
    fun `parses JSON wrapped in markdown fences and prose`() {
        val raw = "Here you go:\n```json\n{\"accuracy\":3,\"actionability\":3," +
            "\"proactivity\":3,\"tone\":3,\"brevity\":3,\"shouldFire\":false,\"notes\":\"meh\"}\n```"
        val s = InsightJudge.parse(raw)!!
        assertEquals(3, s.accuracy)
        assertEquals(false, s.shouldFire)
    }

    @Test
    fun `returns null on unparseable text`() {
        assertNull(InsightJudge.parse("the model refused"))
    }

    @Test
    fun `passes is true only when every axis is at least 4`() {
        val good = JudgeScores(5, 4, 4, 4, 4, true, "")
        val bad = JudgeScores(5, 3, 5, 5, 5, true, "")
        assertTrue(good.passes())
        assertTrue(!bad.passes())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.InsightJudgeTest"`
Expected: FAIL — `InsightJudge`/`JudgeScores` unresolved.

- [ ] **Step 3: Write the implementation**

Create `InsightJudge.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One judge verdict for a single generated card. */
@Serializable
data class JudgeScores(
    val accuracy: Int,
    val actionability: Int,
    val proactivity: Int,
    val tone: Int,
    val brevity: Int,
    val shouldFire: Boolean,
    val notes: String = "",
) {
    /** A card passes an iteration when every 1-5 axis is >= 4. */
    fun passes(): Boolean =
        accuracy >= 4 && actionability >= 4 && proactivity >= 4 && tone >= 4 && brevity >= 4

    fun compactLine(): String =
        "acc=$accuracy act=$actionability pro=$proactivity tone=$tone brev=$brevity " +
            "fire=${if (shouldFire) "yes" else "no"}"
}

/** Builds the judge prompt (rubric from the output doctrine) and parses its JSON reply. */
object InsightJudge {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildPrompt(cardLabel: String, dataPrompt: String, output: String): String = """
        You are a strict reviewer of AI coaching "insight cards" for a body-recomposition app.
        Score the CARD OUTPUT against the DATA it was given. Reply with ONLY a JSON object.

        Rubric (each 1-5, 5 best):
        - accuracy: uses ONLY numbers present in the data; invents nothing.
        - actionability: gives exactly one concrete, small next step.
        - proactivity: compares to a baseline, names the driver, non-obvious synthesis.
        - tone: warm, autonomy-supporting, zero shame.
        - brevity: <= 2 sentences, no preamble or filler.
        Plus:
        - shouldFire (boolean): given this data, SHOULD a card have spoken at all?
          false when the user is on-track and flat and the card is just filler.
        - notes: one short sentence on the biggest weakness.

        Return exactly: {"accuracy":n,"actionability":n,"proactivity":n,"tone":n,"brevity":n,"shouldFire":bool,"notes":"..."}

        CARD: $cardLabel
        DATA GIVEN TO THE CARD:
        $dataPrompt

        CARD OUTPUT:
        $output
    """.trimIndent()

    /** Extracts the first JSON object from [raw] (tolerating fences/prose) and parses it. */
    fun parse(raw: String): JudgeScores? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString(JudgeScores.serializer(), raw.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.InsightJudgeTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudge.kt app/src/test/java/com/zack/recomptracker/ai/harness/InsightJudgeTest.kt
git commit -m "feat(harness): LLM-judge prompt and score parser"
```

---

### Task 4: Scenario fixtures (`InsightScenarios`)

**Files:**
- Create: `app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenarios.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenariosTest.kt`

- [ ] **Step 1: Write the failing test**

Create `InsightScenariosTest.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import org.junit.Assert.assertTrue
import org.junit.Test

class InsightScenariosTest {

    @Test
    fun `has at least eight named scenarios`() {
        assertTrue("expected >= 8 scenarios", InsightScenarios.ALL.size >= 8)
    }

    @Test
    fun `every scenario has a non-blank name and at least one card`() {
        InsightScenarios.ALL.forEach { s ->
            assertTrue("blank name", s.name.isNotBlank())
            assertTrue("scenario ${s.name} has no cards", s.cards().isNotEmpty())
        }
    }

    @Test
    fun `every card builds a non-empty prompt`() {
        InsightScenarios.ALL.forEach { s ->
            s.cards().forEach { (label, prompt) ->
                assertTrue("empty prompt for ${s.name}/$label", prompt.isNotBlank())
            }
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.InsightScenariosTest"`
Expected: FAIL — `InsightScenarios` unresolved.

- [ ] **Step 3: Write the implementation**

Create `InsightScenarios.kt`. (Each scenario carries the typed contexts that apply; `cards()`
turns the present contexts into `(label, prompt)` pairs via the real `InsightPromptBuilder`.)
```kotlin
package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.ai.InsightPromptBuilder
import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.insight.InsightFact
import com.zack.recomptracker.domain.insight.InsightFactType

/** A realistic persona's data for one or more cards. Nulls mean "this card doesn't apply". */
data class InsightScenario(
    val name: String,
    val weekly: InsightContext? = null,
    val progress: ProgressInsightContext? = null,
    val recovery: RecoveryInsightContext? = null,
    val restOfDay: RestOfDayInsightContext? = null,
    val pattern: PatternInsightContext? = null,
) {
    /** (cardLabel, renderedPrompt) for each present card. */
    fun cards(): List<Pair<String, String>> {
        val b = InsightPromptBuilder()
        val out = ArrayList<Pair<String, String>>()
        weekly?.let { out += "Weekly Summary" to b.buildWeeklySummaryPrompt(it) }
        progress?.let { out += "Progress Trend" to b.buildProgressTrendPrompt(it) }
        recovery?.let { out += "Recovery Readiness" to b.buildRecoveryReadinessPrompt(it) }
        restOfDay?.let { out += "Rest of Day" to b.buildRestOfDayPrompt(it) }
        pattern?.let { out += "Weekly Pattern" to b.buildPatternInsightPrompt(it) }
        return out
    }
}

object InsightScenarios {

    private fun weekly(
        verdict: AdjustmentVerdict,
        codes: List<String>,
        weight: Double,
        waist: Double,
        perf: PerformanceTrend,
        recov: RecoveryTrend,
        adherence: Double,
        weeks: Int,
        targetCals: Int,
        summary: String,
    ) = InsightContext(
        result = AdjustmentResult(verdict, 0, codes, summary),
        input = AdjustmentInput(14, adherence, weeks, weight, waist, perf, recov),
        targetCalories = targetCals,
        targetProteinG = 165,
    )

    val ALL: List<InsightScenario> = listOf(
        InsightScenario(
            name = "On-track fat loss (stay-quiet test)",
            weekly = weekly(
                AdjustmentVerdict.HOLD, listOf("MAINTENANCE_TREND"),
                weight = -0.38, waist = -0.10, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.GOOD, adherence = 92.0, weeks = 3,
                targetCals = 2550, summary = "On plan, no change.",
            ),
            progress = ProgressInsightContext(28, -0.38, -0.20, 0.4, 92.0, 6, 6),
        ),
        InsightScenario(
            name = "Plateau (flat 3 weeks, high adherence)",
            weekly = weekly(
                AdjustmentVerdict.REDUCE_CALORIES, listOf("NO_CLEAR_CHANGE_SIGNAL"),
                weight = -0.02, waist = 0.0, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.OK, adherence = 90.0, weeks = 4,
                targetCals = 2400, summary = "Loss has stalled at high adherence.",
            ),
        ),
        InsightScenario(
            name = "Fast loss -> expenditure change",
            weekly = weekly(
                AdjustmentVerdict.INCREASE_CALORIES, listOf("LOSING_WITH_POOR_RECOVERY"),
                weight = -0.85, waist = -0.4, perf = PerformanceTrend.DOWN,
                recov = RecoveryTrend.POOR, adherence = 95.0, weeks = 2,
                targetCals = 2300, summary = "Losing faster than target with recovery dropping.",
            ),
        ),
        InsightScenario(
            name = "Surplus + waist creeping up",
            weekly = weekly(
                AdjustmentVerdict.REDUCE_CALORIES, listOf("GAINING_WITH_WAIST_INCREASE"),
                weight = 0.35, waist = 0.4, perf = PerformanceTrend.STABLE,
                recov = RecoveryTrend.OK, adherence = 80.0, weeks = 5,
                targetCals = 2800, summary = "Weight and waist both rising.",
            ),
        ),
        InsightScenario(
            name = "Lean-mass gain (weight up, waist stable, lifts up)",
            weekly = weekly(
                AdjustmentVerdict.HOLD, listOf("WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP"),
                weight = 0.25, waist = 0.0, perf = PerformanceTrend.UP,
                recov = RecoveryTrend.GOOD, adherence = 88.0, weeks = 6,
                targetCals = 2900, summary = "Likely lean mass.",
            ),
            progress = ProgressInsightContext(28, 0.25, 0.0, 0.6, 88.0, 6, 6),
        ),
        InsightScenario(
            name = "Poor recovery + trained today",
            recovery = RecoveryInsightContext(
                sleepHours = 5.0, energyScore = 3, hungerScore = 7, sorenessScore = 8, trained = true,
            ),
        ),
        InsightScenario(
            name = "Good recovery, rest day",
            recovery = RecoveryInsightContext(
                sleepHours = 8.2, energyScore = 8, hungerScore = 4, sorenessScore = 2, trained = false,
            ),
        ),
        InsightScenario(
            name = "Mid-day protein deficit, dinner to go",
            restOfDay = RestOfDayInsightContext(
                caloriesConsumed = 1420, targetCalories = 2200,
                calorieZoneLowerBound = 2050, calorieZoneUpperBound = 2350,
                proteinConsumedG = 72.0, proteinTargetG = 165, mealsLoggedCount = 2,
            ),
        ),
        InsightScenario(
            name = "Over calories already, evening",
            restOfDay = RestOfDayInsightContext(
                caloriesConsumed = 2380, targetCalories = 2200,
                calorieZoneLowerBound = 2050, calorieZoneUpperBound = 2350,
                proteinConsumedG = 150.0, proteinTargetG = 165, mealsLoggedCount = 4,
            ),
        ),
        InsightScenario(
            name = "Weekend derailment pattern",
            pattern = PatternInsightContext(
                InsightFact(
                    type = InsightFactType.WEEKDAY_WEEKEND,
                    priority = 2,
                    statement = "You average 2,150 kcal on weekdays but 3,050 kcal on weekends.",
                ),
            ),
        ),
    )
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.InsightScenariosTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenarios.kt app/src/test/java/com/zack/recomptracker/ai/harness/InsightScenariosTest.kt
git commit -m "feat(harness): realistic scenario fixtures for every card"
```

---

### Task 5: The network harness (`InsightHarnessTest`)

**Files:**
- Create: `app/src/test/java/com/zack/recomptracker/ai/harness/InsightHarnessTest.kt`

- [ ] **Step 1: Write the harness**

This is a runnable report, not an assertion test. It self-skips when `.env.test` is absent.
Create `InsightHarnessTest.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightPromptBuilder
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live iteration harness for the cloud insight cards. NOT part of normal CI: it self-skips
 * unless `.env.test` (or INSIGHT_* env vars) is present. Run explicitly:
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"        (compact)
 *   ./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*" -DinsightVerbose=true
 */
class InsightHarnessTest {

    private val verbose = System.getProperty("insightVerbose") == "true"

    @Test
    fun runInsightHarness() {
        val env = HarnessEnv.load()
        assumeTrue("No .env.test / INSIGHT_* env — skipping live harness.", env != null)
        env!!

        val client = OpenAiCompatClient()
        val genConfig = env.generationConfig()
        val judgeConfig = env.judgeConfig()
        val systemPrompt = "You are a precise, supportive body-recomposition coach. " +
            "Answer only from the data you are given."

        val passes = ArrayList<Boolean>()
        println("\n==================== INSIGHT HARNESS (model=${env.model}) ====================")

        for (scenario in InsightScenarios.ALL) {
            println("\n### ${scenario.name}")
            for ((label, dataPrompt) in scenario.cards()) {
                if (verbose) println("--- prompt [$label] ---\n$dataPrompt")

                val output = runBlocking {
                    val sb = StringBuilder()
                    client.streamCompletion(genConfig, systemPrompt, dataPrompt)
                        .collect { sb.append(it) }
                    sb.toString().trim()
                        .replace(Regex("""[*_`#>]"""), "")
                        .replace(Regex("""\n{2,}"""), " ")
                        .let { InsightPromptBuilder.limitToSentences(it, 2) }
                }

                val judgeRaw = runBlocking {
                    client.completion(
                        config = judgeConfig,
                        messages = listOf(
                            ChatRequestMessage(
                                role = "user",
                                content = InsightJudge.buildPrompt(label, dataPrompt, output),
                            ),
                        ),
                        toolSchemasJson = emptyList(),
                    ).text
                }
                val scores = InsightJudge.parse(judgeRaw)

                println("• $label: $output")
                if (scores != null) {
                    println("    ${scores.compactLine()}  | ${scores.notes}")
                    passes += scores.passes()
                } else {
                    println("    [judge parse failed] raw=${judgeRaw.take(120)}")
                    passes += false
                }
            }
        }

        val passed = passes.count { it }
        println("\n==================== SUMMARY: $passed/${passes.size} cards passed (>=4 all axes) ====================\n")
    }
}
```

- [ ] **Step 2: Verify it compiles and self-skips without creds**

Run (no `.env.test` present): `./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"`
Expected: PASS, reported as **skipped/ignored** (assumeTrue short-circuits). No network call.

- [ ] **Step 3: Do a live smoke run (manual, needs creds)**

Copy `.env.test.example` to `.env.test`, fill in real creds, then:
`./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*" --info`
Expected: a printed report with one line + scores per scenario × card, and a SUMMARY line.
(If your CI/agent has no key, skip this step — it's the interactive loop's entry point.)

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/zack/recomptracker/ai/harness/InsightHarnessTest.kt
git commit -m "feat(harness): live insight harness with compact judge report"
```

---

### Task 6: Full Phase-1 green check

- [ ] **Step 1: Run the whole harness package offline**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.*"`
Expected: all offline tests PASS; the live harness is skipped (no creds in CI).

- [ ] **Step 2: Run the existing AI test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.*"`
Expected: PASS (existing tests unaffected — Phase 1 is test-only).

---

## Phase 2 — Redesign the prompts to the doctrine

> The harness from Phase 1 is now the feedback loop. Each task below adds structure + a concrete
> first-draft prompt; **the exact wording is then tuned interactively by running the harness and
> reading the judge scores.** The unit tests assert *structure and instructions present*, not exact
> phrasing, so tuning the copy won't churn the tests.

### Task 7: Add baseline fields to contexts (additive, defaulted)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightContext.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/harness/ContextBaselineFieldsTest.kt` (create)

All new fields are nullable with `= null` defaults so existing construction sites (ViewModels,
mappers, the fixtures, existing tests) keep compiling unchanged.

- [ ] **Step 1: Write the failing test**

Create `ContextBaselineFieldsTest.kt`:
```kotlin
package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextBaselineFieldsTest {

    @Test
    fun `weekly context carries desired rate and prior target`() {
        val c = InsightContext(
            result = AdjustmentResult(AdjustmentVerdict.HOLD, 0, emptyList(), "s"),
            input = AdjustmentInput(14, 90.0, 3, -0.3, 0.0, PerformanceTrend.STABLE, RecoveryTrend.GOOD),
            targetCalories = 2500,
            targetProteinG = 165,
            desiredWeeklyRateKg = -0.4,
            priorTargetCalories = 2600,
        )
        assertEquals(-0.4, c.desiredWeeklyRateKg!!, 0.0001)
        assertEquals(2600, c.priorTargetCalories)
    }

    @Test
    fun `progress context carries prior-window trends`() {
        val c = ProgressInsightContext(28, -0.3, 0.0, 0.4, 90.0, 6, 6, priorWeightTrendKgPerWeek = -0.1)
        assertEquals(-0.1, c.priorWeightTrendKgPerWeek!!, 0.0001)
    }

    @Test
    fun `recovery context carries personal averages`() {
        val c = RecoveryInsightContext(5.0, 3, 7, 8, true, avgSleepHours = 7.5, avgEnergyScore = 6)
        assertEquals(7.5, c.avgSleepHours!!, 0.0001)
        assertEquals(6, c.avgEnergyScore)
    }

    @Test
    fun `rest-of-day context carries fraction-of-day-elapsed`() {
        val c = RestOfDayInsightContext(1420, 2200, 2050, 2350, 72.0, 165, 2, fractionOfDayElapsed = 0.6)
        assertEquals(0.6, c.fractionOfDayElapsed!!, 0.0001)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.ContextBaselineFieldsTest"`
Expected: FAIL — unknown named args.

- [ ] **Step 3: Add the fields**

In `InsightContext.kt`, extend the data class:
```kotlin
data class InsightContext(
    val result: AdjustmentResult,
    val input: AdjustmentInput,
    val targetCalories: Int,
    val targetProteinG: Int,
    /** Goal pace, signed kg/week (negative = loss). For the target-change rationale. */
    val desiredWeeklyRateKg: Double? = null,
    /** The calorie target before this week's verdict, if it changed. */
    val priorTargetCalories: Int? = null,
)
```

In `ProgressInsightContext.kt`, add to the constructor (after `waistPointCount`):
```kotlin
    val waistPointCount: Int,
    /** Same trend over the PRIOR window of equal length — lets the card say "accelerating". */
    val priorWeightTrendKgPerWeek: Double? = null,
```

In `RecoveryInsightContext.kt`, add (after `trained`):
```kotlin
    val trained: Boolean,
    /** The user's recent personal averages, so today reads as a deviation, not an absolute. */
    val avgSleepHours: Double? = null,
    val avgEnergyScore: Int? = null,
```

In `RestOfDayInsightContext.kt`, add (after `mealsLoggedCount`):
```kotlin
    val mealsLoggedCount: Int,
    /** 0.0-1.0 of the eating day elapsed, so the card can judge pace. */
    val fractionOfDayElapsed: Double? = null,
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.harness.ContextBaselineFieldsTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Confirm nothing else broke**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (defaults keep all existing call sites valid).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightContext.kt app/src/main/java/com/zack/recomptracker/ai/ProgressInsightContext.kt app/src/main/java/com/zack/recomptracker/ai/RecoveryInsightContext.kt app/src/main/java/com/zack/recomptracker/ai/RestOfDayInsightContext.kt app/src/test/java/com/zack/recomptracker/ai/harness/ContextBaselineFieldsTest.kt
git commit -m "feat(insight): add baseline/trend fields to insight contexts"
```

---

### Task 8: Stay-quiet gate (`InsightGate`)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/InsightGate.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/InsightGateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `InsightGateTest.kt`:
```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightGateTest {

    private fun weekly(verdict: AdjustmentVerdict, weight: Double, waist: Double, adherence: Double) =
        InsightContext(
            result = AdjustmentResult(verdict, 0, emptyList(), "s"),
            input = AdjustmentInput(14, adherence, 3, weight, waist, PerformanceTrend.STABLE, RecoveryTrend.GOOD),
            targetCalories = 2500, targetProteinG = 165,
        )

    @Test
    fun `holds and stays quiet when on-plan and flat`() {
        // HOLD verdict, weight moving as desired, no waist drift, high adherence -> no need to speak.
        assertFalse(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.HOLD, -0.38, 0.0, 92.0)))
    }

    @Test
    fun `fires when the verdict changes calories`() {
        assertTrue(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.REDUCE_CALORIES, 0.3, 0.4, 80.0)))
    }

    @Test
    fun `fires on a hold when adherence is low (worth addressing)`() {
        assertTrue(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.HOLD, -0.1, 0.0, 60.0)))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightGateTest"`
Expected: FAIL — `InsightGate` unresolved.

- [ ] **Step 3: Write the implementation**

Create `InsightGate.kt`:
```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlin.math.abs

/**
 * Decides whether a card has anything worth saying (doctrine rule 7: "provide nothing").
 * Pure and deterministic — runs BEFORE any cloud call, so silence costs zero tokens.
 */
object InsightGate {

    private const val LOW_ADHERENCE = 75.0

    /**
     * Weekly summary fires when there is a real decision or problem to surface:
     * any non-HOLD verdict, low adherence, or a meaningful waist drift. A HOLD with good
     * adherence and a stable waist is "on track" — stay quiet.
     */
    fun shouldFireWeekly(context: InsightContext): Boolean {
        if (context.result.verdict != AdjustmentVerdict.HOLD) return true
        if (context.input.adherencePercent < LOW_ADHERENCE) return true
        if (abs(context.input.waistTrendCmPerWeek) >= 0.25) return true
        return false
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightGateTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightGate.kt app/src/test/java/com/zack/recomptracker/ai/InsightGateTest.kt
git commit -m "feat(insight): stay-quiet gate for on-track weeks"
```

> Wiring the gate into `CloudInsightCoordinator.onAiCardVisible` (early-return when
> `!InsightGate.shouldFireWeekly(context)`) happens in Task 11 once output is validated.

---

### Task 9: Rewrite the Weekly Summary prompt to the doctrine

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` (`buildWeeklySummaryPrompt`)
- Test: `app/src/test/java/com/zack/recomptracker/ai/WeeklySummaryDoctrineTest.kt` (create)

The existing `InsightPromptBuilderTest` asserts several substrings (e.g. `"Lead with the most
decisive number"`, `"do not do any math"`, `"Example output"`, the signal lines). **Keep those
substrings present** so that suite stays green; add doctrine elements around them.

- [ ] **Step 1: Write the failing test (new doctrine assertions)**

Create `WeeklySummaryDoctrineTest.kt`:
```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySummaryDoctrineTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(desiredRate: Double? = null, priorTarget: Int? = null) = InsightContext(
        result = AdjustmentResult(AdjustmentVerdict.INCREASE_CALORIES, 0, listOf("LOSING_WITH_POOR_RECOVERY"), "s"),
        input = AdjustmentInput(14, 95.0, 2, -0.85, -0.4, PerformanceTrend.DOWN, RecoveryTrend.POOR),
        targetCalories = 2400, targetProteinG = 165,
        desiredWeeklyRateKg = desiredRate, priorTargetCalories = priorTarget,
    )

    @Test
    fun `instructs observation then why then action ordering`() {
        val p = builder.buildWeeklySummaryPrompt(ctx())
        assertTrue("expected structure guidance", p.contains("then", ignoreCase = true) &&
            p.contains("action", ignoreCase = true))
    }

    @Test
    fun `instructs naming the driver`() {
        val p = builder.buildWeeklySummaryPrompt(ctx())
        assertTrue("expected driver instruction", p.contains("driver", ignoreCase = true) ||
            p.contains("what is driving", ignoreCase = true))
    }

    @Test
    fun `forbids shame and red-number framing`() {
        val p = builder.buildWeeklySummaryPrompt(ctx())
        assertTrue("expected no-shame guidance", p.contains("shame", ignoreCase = true) ||
            p.contains("blame", ignoreCase = true))
    }

    @Test
    fun `surfaces desired weekly rate when provided`() {
        val p = builder.buildWeeklySummaryPrompt(ctx(desiredRate = -0.4))
        assertTrue("expected goal-rate line", p.contains("Goal rate") || p.contains("goal rate"))
    }

    @Test
    fun `surfaces prior target for change rationale when provided`() {
        val p = builder.buildWeeklySummaryPrompt(ctx(priorTarget = 2300))
        assertTrue("expected previous target line", p.contains("2300") || p.contains("Previous target"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklySummaryDoctrineTest"`
Expected: FAIL on the new assertions.

- [ ] **Step 3: Rewrite `buildWeeklySummaryPrompt`**

Replace the body of `buildWeeklySummaryPrompt` in `InsightPromptBuilder.kt` with (keeps the
substrings the existing suite checks, adds doctrine):
```kotlin
    fun buildWeeklySummaryPrompt(context: InsightContext): String = buildString {
        appendLine("You are a concise body-recomposition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 1–2 short sentences. Structure: observation (the decisive number) → why it matters → the verdict/action. Do not change the verdict.")
        appendLine("Lead with the most decisive number from the signals below, compared to the goal where relevant.")
        appendLine("Name what is driving the decision (the single dominant signal). No preamble or filler.")
        appendLine("Tone: calm, direct, supportive. Never use shame, blame, or red-flag language; attribute changes to the data, not the athlete's willpower.")
        appendLine("Use only the figures given; do not do any math of your own.")
        appendLine()
        appendLine("Example output for a Hold verdict:")
        appendLine("\"Weight is down 0.30 kg/wk with waist flat and adherence at 88% — fat loss is on track, hold calories.\"")
        appendLine()
        appendLine("Verdict: ${verdictLabel(context.result.verdict)}")
        appendLine("Context: ${context.result.summary}")
        appendLine()
        appendLine("Reasons:")
        context.result.reasonCodes.forEach { code -> appendLine("- ${reasonDescription(code)}") }
        appendLine()
        appendLine("Signals this week:")
        appendLine("- Weight: ${signed(context.input.weightTrendKgPerWeek, 2)} kg/wk (${weightLabel(context.input.weightTrendKgPerWeek)})")
        appendLine("- Waist: ${signed(context.input.waistTrendCmPerWeek, 1)} cm/wk (${waistLabel(context.input.waistTrendCmPerWeek)})")
        appendLine("- Performance: ${performanceLabel(context.input.performanceTrend)}")
        appendLine("- Recovery: ${recoveryLabel(context.input.recoveryTrend)}")
        appendLine("- Adherence: ${context.input.adherencePercent.roundToInt()}% (${adherenceLabel(context.input.adherencePercent)})")
        context.desiredWeeklyRateKg?.let { appendLine("- Goal rate: ${signed(it, 2)} kg/wk") }
        if (context.input.weeksSincePhaseStart != DEFAULT_WEEKS_FALLBACK) {
            appendLine("- Weeks in current phase: ${context.input.weeksSincePhaseStart}")
        }
        context.priorTargetCalories?.let { appendLine("- Previous target: $it kcal") }
        appendLine()
        appendLine("Calorie target: ${context.targetCalories} kcal | Protein target: ${context.targetProteinG}g")
    }
```

- [ ] **Step 4: Run the new and the existing weekly tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.WeeklySummaryDoctrineTest" --tests "com.zack.recomptracker.ai.InsightPromptBuilderTest"`
Expected: PASS (both suites — the existing substrings are preserved).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/WeeklySummaryDoctrineTest.kt
git commit -m "feat(insight): weekly summary prompt rewritten to output doctrine"
```

---

### Task 10: Rewrite Progress / Recovery / Rest-of-Day prompts to the doctrine

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Test: extend `app/src/test/java/com/zack/recomptracker/ai/WeeklySummaryDoctrineTest.kt` or add per-card tests.

Apply the same doctrine treatment, and render the new baseline fields when present:
- **Progress:** when `priorWeightTrendKgPerWeek != null`, add a line
  `"- Prior-window weight: ${signed(it,2)} kg/wk"` and instruct "say whether the trend is
  accelerating, steady, or slowing vs. the prior window."
- **Recovery:** when `avgSleepHours`/`avgEnergyScore` present, render today's value *as a deviation*
  (e.g. `"- Sleep: 5.0 h (your recent average is 7.5 h)"`); instruct "frame each signal against the
  athlete's own average; hedge single-day readings."
- **Rest of Day:** when `fractionOfDayElapsed != null`, add `"- Day elapsed: ${(it*100).roundToInt()}%"`
  and instruct "judge pace — are they ahead of or behind where they should be by now?".

- [ ] **Step 1: Write per-card doctrine tests**

Add to a new file `app/src/test/java/com/zack/recomptracker/ai/PerCardDoctrineTest.kt`:
```kotlin
package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PerCardDoctrineTest {

    private val b = InsightPromptBuilder()

    @Test
    fun `progress renders prior-window comparison when present`() {
        val p = b.buildProgressTrendPrompt(
            ProgressInsightContext(28, -0.3, 0.0, 0.4, 90.0, 6, 6, priorWeightTrendKgPerWeek = -0.1),
        )
        assertTrue("expected prior-window line", p.contains("Prior-window", ignoreCase = true))
    }

    @Test
    fun `recovery frames sleep against personal average when present`() {
        val p = b.buildRecoveryReadinessPrompt(
            RecoveryInsightContext(5.0, 3, 7, 8, true, avgSleepHours = 7.5),
        )
        assertTrue("expected personal-average framing", p.contains("average", ignoreCase = true))
    }

    @Test
    fun `rest of day renders day-elapsed pace when present`() {
        val p = b.buildRestOfDayPrompt(
            RestOfDayInsightContext(1420, 2200, 2050, 2350, 72.0, 165, 2, fractionOfDayElapsed = 0.6),
        )
        assertTrue("expected day-elapsed line", p.contains("elapsed", ignoreCase = true))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.PerCardDoctrineTest"`
Expected: FAIL.

- [ ] **Step 3: Edit the three builders**

In `buildProgressTrendPrompt`, after the Adherence line append:
```kotlin
        context.priorWeightTrendKgPerWeek?.let {
            appendLine("- Prior-window weight: ${signed(it, 2)} kg/wk")
        }
```
and add to the instruction block: `appendLine("If a prior-window figure is given, say whether the trend is accelerating, steady, or slowing.")`

In `buildRecoveryReadinessPrompt`, change the sleep line to fold in the average when present:
```kotlin
        context.sleepHours?.let {
            val avg = context.avgSleepHours?.let { a -> " (your recent average is ${String.format(Locale.US, "%.1f", a)} h)" } ?: ""
            appendLine("- Sleep: ${String.format(Locale.US, "%.1f", it)} h (${sleepLabel(it)})$avg")
        }
```
and add the instruction: `appendLine("Frame each signal against the athlete's own recent average when given; hedge single-day readings.")`

In `buildRestOfDayPrompt`, after the meals line append:
```kotlin
        context.fractionOfDayElapsed?.let {
            appendLine("- Day elapsed: ${(it * 100).roundToInt()}%")
        }
```
and add the instruction: `appendLine("If day-elapsed is given, judge pace — are they ahead of or behind where they should be by now?")`

- [ ] **Step 4: Run per-card + existing card tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.PerCardDoctrineTest" --tests "com.zack.recomptracker.ai.RestOfDayInsightPromptTest" --tests "com.zack.recomptracker.ai.InsightPromptBuilderRichModeTest"`
Expected: PASS (new + existing).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt app/src/test/java/com/zack/recomptracker/ai/PerCardDoctrineTest.kt
git commit -m "feat(insight): progress/recovery/rest-of-day prompts render baselines per doctrine"
```

- [ ] **Step 6: ITERATE with the harness (interactive)**

With `.env.test` in place, run `./gradlew :app:testDebugUnitTest --tests "*InsightHarnessTest*"`,
read the judge scores, and refine the prompt wording (Tasks 9–10) until every card scores ≥ 4 on
all axes and `shouldFire` is correct for the on-track scenario. Re-run after each edit. Commit each
improvement with `git commit -m "tune(insight): <what changed> (harness)"`.

---

## Phase 3 — New cards (after output is validated)

> These add the three new cards from the doctrine §5.6. Build the prompt + context + harness
> coverage first (so output can be tuned), then wire into the coordinator and UI.

### Task 11: Target-change explainer card

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightRequest.kt` — add `TARGET_CHANGE` to
  `InsightKind` and a `TargetChange` request variant.
- Create: `app/src/main/java/com/zack/recomptracker/ai/TargetChangeContext.kt` — `oldTarget`,
  `newTarget`, `avgIntake`, `actualRateKg`, `desiredRateKg`, `estimatedExpenditure`.
- Modify: `InsightPromptBuilder.kt` — add `buildTargetChangePrompt(context)` using the
  target-change template from the doctrine (§4).
- Modify: `CloudInsightCoordinator.kt` — dispatch the new request in `onInsightVisible`.
- Modify: `InsightScenarios.kt` — add the "Fast loss -> expenditure change" scenario's target-change card.
- Test: `app/src/test/java/com/zack/recomptracker/ai/TargetChangePromptTest.kt` — assert the prompt
  states old→new target, the causal chain (intake → actual vs. desired rate → expenditure), and the
  no-blame instruction.

Follow the same TDD rhythm: failing test → implement → run → commit. The prompt's exact copy is then
tuned via the harness. Wire the stay-quiet gate from Task 8 into `onAiCardVisible` in this task
(early-return when `!InsightGate.shouldFireWeekly(context)`), with a coordinator test asserting no
generation starts for the on-track scenario.

### Task 12: Noise-defuser and cross-metric cards

**Files:** mirror Task 11 for two more kinds:
- **Noise-defuser** (`NOISE_DEFUSER`): context = `todayWeightDeltaKg`, `smoothedTrendKgPerWeek`,
  `likelyCause` (enum: SODIUM_CARBS / HYDRATION / UNKNOWN). Prompt uses the noise-defuser template.
  Fires only when `sign(todayDelta) != sign(trend)` and `abs(todayDelta)` is large — encode that as a
  `shouldFire` in `InsightGate`.
- **Cross-metric** (`CROSS_METRIC`): context = two linked series summarized (e.g. protein-hit-rate +
  avg hunger score) with a detected `direction`. Prompt uses the long-trend pattern template and MUST
  hedge ("you tend to…"), never claim causation.

Each: failing prompt test (asserts template structure + hedging language) → implement → harness-tune
→ commit. Add the new `InsightKind`s to any `when` over kinds (the compiler will flag exhaustive
`when`s to update — search for `InsightKind.` and `when (request)`).

**UI wiring — follow [ai-glass-cards.md](../../ai-glass-cards.md):** render every new card through the
existing `GeneratedInsightCard(title, state, onRetry, …)` engine — do NOT write new composables
(reuse-existing-components rule). Pick the variant by importance: `HERO` for a single headline
insight (the target-change explainer on the dashboard, with `evidence` + `confidence`), `STANDARD`
for per-screen cards, `PILL` for lightweight number-tied nudges (noise-defuser). Drive it from an
`AiInsightState` (`Generating` → loading pill, `Ready` → collapsible card, model-lifecycle states →
hidden). Opt into actions only via the existing hooks (`onTellMeMore` to seed the coach, `onFeedback`)
— don't add controls outside `InsightActions`. The cross-metric card is a natural `onTellMeMore`
candidate (open the coach seeded with the correlation).

---

## Self-Review

**Spec coverage:**
- Harness (env, fixtures, judge, runner, compact report, self-skip) → Tasks 1–6. ✓
- `.env.test` + `.gitignore` + example → Task 1. ✓
- Rewrite 5 prompts to doctrine → Tasks 9 (weekly) + 10 (progress/recovery/rest-of-day); pattern
  prompt already matches the doctrine (number-first, non-judgmental) and is exercised by the harness
  for tuning — no structural change needed, tune via Task 10 Step 6 if the judge flags it. ✓
- Richer input data → Task 7 (additive baseline fields). ✓
- LLM-judge rubric (same model) → Task 3. ✓
- 3 new cards → Tasks 11–12. ✓
- Stay-quiet gate → Task 8, wired in Task 11. ✓
- Docs (doctrine, ideas, spec) → already written/committed before this plan. ✓

**Placeholder scan:** Tasks 1–10 contain complete code. Tasks 11–12 are deliberately specified at
the structural level (file list + field list + test assertions + TDD rhythm) because their prompt
copy is tuned interactively through the harness; the engineer has exact files, types, and the
doctrine templates to write against. No "TBD/handle edge cases" placeholders.

**Type consistency:** New fields are additive with `= null` defaults; method names
(`shouldFireWeekly`, `buildWeeklySummaryPrompt`, `cards()`, `parse`, `passes`, `compactLine`) are
used consistently across tasks. `CloudConfig`, `OpenAiCompatClient`, `ChatRequestMessage`,
`InsightPromptBuilder.limitToSentences` match the real signatures read from source.

`InsightFact` confirmed against `domain/insight/InsightModels.kt`: `type` is the `InsightFactType`
enum (Task 4 fixture uses `InsightFactType.WEEKDAY_WEEKEND`), `priority: Int`, `statement: String`.
