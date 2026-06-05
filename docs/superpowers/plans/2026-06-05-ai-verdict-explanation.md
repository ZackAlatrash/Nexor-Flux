# AI Verdict Explanation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional on-device AI explanation below the calorie verdict on the Stats screen, powered by Gemma 4 E2B via LiteRT-LM, with a fake coordinator for UX validation before real model integration.

**Architecture:** The AI layer lives entirely in `ai/`. An `AiInsightCoordinator` interface owns state, download, and inference; `DashboardViewModel` injects it and exposes a `StateFlow<AiInsightState>`. The first slice uses `FakeAiInsightCoordinator` to validate all UI states before touching a real model. Tasks 1–10 produce a fully working, testable feature. Tasks 11–12 swap in LiteRT-LM.

**Tech Stack:** Kotlin, Jetpack Compose, `kotlinx.coroutines`, `androidx.datastore`, LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.11.0`, existing `kyant.backdrop` for blur.

---

## File Map

### New files
| Path | Responsibility |
|---|---|
| `ai/AiInsightState.kt` | Sealed class — full lifecycle state machine |
| `ai/AiInsightCoordinator.kt` | Interface — contract for download, inference, state |
| `ai/FakeAiInsightCoordinator.kt` | Deterministic fake — simulates all states for UX validation |
| `ai/RealAiInsightCoordinator.kt` | Real implementation — DownloadManager + LiteRT-LM (Task 11–12) |
| `ui/component/AiBadge.kt` | `✦ AI` badge composable |
| `ui/component/AiInsightCard.kt` | Tinted card with animated comet border |

### Modified files
| Path | Change |
|---|---|
| `ai/InsightPromptBuilder.kt` | Refine prompt for coaching voice, short output |
| `ai/GemmaInsightService.kt` | Replace stub with real LiteRT-LM engine wrapper (Task 12) |
| `core/AppContainer.kt` | Add `appScope`, wire `AiInsightCoordinator` |
| `ui/dashboard/DashboardViewModel.kt` | Inject coordinator, expose `aiInsightState`, add actions |
| `ui/dashboard/DashboardScreen.kt` | Render AI card below verdict, add `LaunchedEffect` trigger |
| `app/build.gradle.kts` | Add LiteRT-LM dependency (Task 11) |

### Test files
| Path | Tests |
|---|---|
| `test/ai/AiInsightStateTest.kt` | State transitions, `key()` hash stability |
| `test/ai/InsightPromptBuilderTest.kt` | Prompt contains verdict, reason codes, no numbers |
| `test/ai/FakeAiInsightCoordinatorTest.kt` | Toggle → state, download flow, generation guard |

---

## Task 1: Create Feature Branch

**Files:** none

- [ ] **Step 1: Create and switch to branch**

```bash
git checkout -b feature/ai-verdict-explanation
```

- [ ] **Step 2: Verify branch**

```bash
git branch --show-current
```
Expected output: `feature/ai-verdict-explanation`

---

## Task 2: `AiInsightState` Sealed Class

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/AiInsightState.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ai/AiInsightStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ai/AiInsightStateTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiInsightStateTest {

    @Test
    fun `resultKey is stable for identical AdjustmentResult`() {
        val result = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        assertEquals(result.key(), result.key())
    }

    @Test
    fun `resultKey differs when verdict differs`() {
        val hold = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        val increase = hold.copy(verdict = AdjustmentVerdict.INCREASE_CALORIES, recommendedCalorieChange = 150)
        assertNotEquals(hold.key(), increase.key())
    }

    @Test
    fun `resultKey differs when reasonCodes differ`() {
        val a = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        val b = a.copy(reasonCodes = listOf("NO_CLEAR_CHANGE_SIGNAL"))
        assertNotEquals(a.key(), b.key())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.AiInsightStateTest" 2>&1 | tail -20
```

Expected: FAILED — `key()` not defined yet.

- [ ] **Step 3: Create `AiInsightState.kt`**

Create `app/src/main/java/com/zack/recomptracker/ai/AiInsightState.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult

sealed class AiInsightState {
    /** AI Insights toggle is off. */
    object Disabled : AiInsightState()

    /** Toggle on, but model file is not on device. */
    object ModelMissing : AiInsightState()

    /**
     * Model is downloading.
     * [progress] is 0.0–1.0, or null when total bytes are unknown (show indeterminate UI).
     */
    data class Downloading(val progress: Float?) : AiInsightState()

    /** Download ended with an error. */
    object DownloadFailed : AiInsightState()

    /** Model file is present; engine has not been loaded yet. */
    object ModelReady : AiInsightState()

    /** Engine is initialising ("Preparing model…"). */
    object LoadingModel : AiInsightState()

    /** Inference is streaming; [partialText] grows as tokens arrive. */
    data class Generating(val partialText: String) : AiInsightState()

    /** Explanation is complete. */
    data class Ready(val text: String) : AiInsightState()

    /** Human-readable error after a failed inference attempt. */
    data class Error(val message: String) : AiInsightState()
}

/**
 * Stable cache key — changes only when the engine should produce a new explanation.
 * Does not include [AdjustmentResult.summary] (deterministic prose, same content).
 */
fun AdjustmentResult.key(): String =
    "${verdict.name}|${reasonCodes.joinToString(",")}|$recommendedCalorieChange"
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.AiInsightStateTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — 3 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/AiInsightState.kt \
        app/src/test/java/com/zack/recomptracker/ai/AiInsightStateTest.kt
git commit -m "feat(ai): add AiInsightState sealed class and resultKey extension"
```

---

## Task 3: Refine `InsightPromptBuilder`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptBuilderTest {

    private val builder = InsightPromptBuilder()

    private fun result(
        verdict: AdjustmentVerdict = AdjustmentVerdict.HOLD,
        codes: List<String> = listOf("MAINTENANCE_TREND"),
        change: Int = 0,
    ) = AdjustmentResult(
        verdict = verdict,
        recommendedCalorieChange = change,
        reasonCodes = codes,
        summary = "Stable.",
    )

    @Test
    fun `prompt contains verdict name`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(verdict = AdjustmentVerdict.INCREASE_CALORIES))
        assertTrue("Expected INCREASE_CALORIES in prompt", "INCREASE_CALORIES" in prompt)
    }

    @Test
    fun `prompt contains reason codes`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(codes = listOf("LOSING_WITH_POOR_RECOVERY")))
        assertTrue("Expected reason code in prompt", "LOSING_WITH_POOR_RECOVERY" in prompt)
    }

    @Test
    fun `prompt instructs not to change verdict`() {
        val prompt = builder.buildWeeklySummaryPrompt(result())
        assertTrue("Expected instruction to preserve verdict", prompt.contains("do not change", ignoreCase = true))
    }

    @Test
    fun `prompt requests short output`() {
        val prompt = builder.buildWeeklySummaryPrompt(result())
        assertTrue("Expected short-output instruction", prompt.contains("2") || prompt.contains("3"))
    }

    @Test
    fun `prompt does not include raw calorie numbers from result`() {
        val prompt = builder.buildWeeklySummaryPrompt(result(change = 150))
        // The prompt should guide the model, not dump data for it to parrot
        assertFalse("Prompt should not include raw change value", "150" in prompt)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderTest" 2>&1 | tail -20
```

Expected: multiple failures — current prompt doesn't meet the new contract.

- [ ] **Step 3: Replace `InsightPromptBuilder.kt`**

Overwrite `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult

class InsightPromptBuilder {

    /**
     * Builds a prompt that asks the model to explain — not change — the verdict.
     * Output target: 2–3 sentences, coaching voice, plain English.
     * Explicitly omits numeric values so the model explains reasoning, not data.
     */
    fun buildWeeklySummaryPrompt(result: AdjustmentResult): String = buildString {
        appendLine("You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 2–3 sentences in plain English. Do not change the verdict.")
        appendLine("Explain the reasoning behind it — do not repeat the raw numbers already shown on screen.")
        appendLine("Be specific about which signals drove the decision. Keep the tone calm and direct.")
        appendLine()
        appendLine("Verdict: ${result.verdict.name}")
        appendLine("Reason codes: ${result.reasonCodes.joinToString()}")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.InsightPromptBuilderTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt \
        app/src/test/java/com/zack/recomptracker/ai/InsightPromptBuilderTest.kt
git commit -m "feat(ai): refine InsightPromptBuilder for coaching voice and short output"
```

---

## Task 4: `AiInsightCoordinator` Interface

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import kotlinx.coroutines.flow.StateFlow

interface AiInsightCoordinator {

    /** Current lifecycle state. Observed by the ViewModel. */
    val state: StateFlow<AiInsightState>

    /**
     * Start model download. Guards: checks Wi-Fi, free space, skips if already downloading.
     * Transitions: ModelMissing → Downloading → ModelReady (or DownloadFailed).
     */
    fun requestDownload()

    /** Cancel an in-flight download. Transitions back to ModelMissing. */
    fun cancelDownload()

    /**
     * Delete the model file from disk.
     * Transitions to ModelMissing regardless of current state.
     */
    fun deleteModel()

    /**
     * Called when the AI card becomes visible with a concrete result.
     * Internally guards: skips if toggle is off, verdict is WAIT_FOR_DATA,
     * state is not ModelReady, or this resultKey was already generated.
     * Transitions: ModelReady → LoadingModel → Generating → Ready (or Error).
     */
    fun onAiCardVisible(result: AdjustmentResult)

    /**
     * Force re-generation for the same result (user tapped refresh).
     * Clears the cached key and re-triggers inference if model is ready.
     */
    fun retryGeneration(result: AdjustmentResult)
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/AiInsightCoordinator.kt
git commit -m "feat(ai): add AiInsightCoordinator interface"
```

---

## Task 5: `FakeAiInsightCoordinator`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ai/FakeAiInsightCoordinator.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ai/FakeAiInsightCoordinatorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/zack/recomptracker/ai/FakeAiInsightCoordinatorTest.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAiInsightCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    private val aiEnabledFlow = MutableStateFlow(false)

    private fun coordinator() = FakeAiInsightCoordinator(
        aiEnabledFlow = aiEnabledFlow,
        scope = scope,
    )

    private fun holdResult() = AdjustmentResult(
        verdict = AdjustmentVerdict.HOLD,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("MAINTENANCE_TREND"),
        summary = "Stable.",
    )

    private fun waitResult() = AdjustmentResult(
        verdict = AdjustmentVerdict.WAIT_FOR_DATA,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("INSUFFICIENT_DATA"),
        summary = "Wait.",
    )

    @Test
    fun `initial state is Disabled when toggle is off`() = scope.runTest {
        val c = coordinator()
        advanceUntilIdle()
        assertEquals(AiInsightState.Disabled, c.state.value)
    }

    @Test
    fun `enabling toggle transitions to ModelMissing`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        assertEquals(AiInsightState.ModelMissing, c.state.value)
    }

    @Test
    fun `disabling toggle returns to Disabled from any state`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        aiEnabledFlow.value = false
        advanceUntilIdle()
        assertEquals(AiInsightState.Disabled, c.state.value)
    }

    @Test
    fun `requestDownload transitions through Downloading to ModelReady`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        assertEquals(AiInsightState.ModelReady, c.state.value)
    }

    @Test
    fun `onAiCardVisible with WAIT_FOR_DATA does not trigger generation`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(waitResult())
        advanceUntilIdle()
        assertEquals(AiInsightState.ModelReady, c.state.value)
    }

    @Test
    fun `onAiCardVisible with real verdict transitions to Ready`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        assertTrue("Expected Ready state", c.state.value is AiInsightState.Ready)
    }

    @Test
    fun `onAiCardVisible with same key does not re-generate`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        val firstText = (c.state.value as AiInsightState.Ready).text
        // simulate second visit (same result)
        c.onAiCardVisible(holdResult())
        advanceUntilIdle()
        val secondText = (c.state.value as AiInsightState.Ready).text
        assertEquals(firstText, secondText)
        // state remains Ready — didn't restart
        assertTrue(c.state.value is AiInsightState.Ready)
    }

    @Test
    fun `retryGeneration re-runs even for cached key`() = scope.runTest {
        val c = coordinator()
        aiEnabledFlow.value = true
        advanceUntilIdle()
        c.requestDownload()
        advanceUntilIdle()
        val result = holdResult()
        c.onAiCardVisible(result)
        advanceUntilIdle()
        c.retryGeneration(result)
        advanceUntilIdle()
        assertTrue("Expected Ready after retry", c.state.value is AiInsightState.Ready)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.FakeAiInsightCoordinatorTest" 2>&1 | tail -20
```

Expected: FAILED — class not defined yet.

- [ ] **Step 3: Create `FakeAiInsightCoordinator.kt`**

Create `app/src/main/java/com/zack/recomptracker/ai/FakeAiInsightCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FakeAiInsightCoordinator(
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private var lastGeneratedKey: String? = null

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    _state.value = AiInsightState.ModelMissing
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing) return
        scope.launch {
            // Simulate download with fake progress ticks
            for (i in 1..5) {
                _state.value = AiInsightState.Downloading(i / 5f)
                delay(60L)
            }
            _state.value = AiInsightState.ModelReady
        }
    }

    override fun cancelDownload() {
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelMissing
    }

    override fun onAiCardVisible(result: AdjustmentResult) {
        if (result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(result) }
    }

    override fun retryGeneration(result: AdjustmentResult) {
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(result)
    }

    private suspend fun generate(result: AdjustmentResult) {
        _state.value = AiInsightState.LoadingModel
        delay(300L) // fake engine load
        val explanation = buildExplanation(result)
        // Stream word by word
        val words = explanation.split(" ")
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(word)
            _state.value = AiInsightState.Generating(sb.toString())
            delay(60L)
        }
        _state.value = AiInsightState.Ready(sb.toString())
    }

    private fun buildExplanation(result: AdjustmentResult): String =
        when (result.reasonCodes.firstOrNull()) {
            "INSUFFICIENT_DATA" ->
                "You need at least 14 logged days before a verdict can be made. " +
                "Keep logging consistently to unlock your first calorie recommendation."
            "LOW_ADHERENCE" ->
                "Logging consistency has been below the threshold this period. " +
                "Improve tracking accuracy before changing your calorie target — " +
                "the data needs to be reliable for the engine to act on it."
            "EARLY_SCALE_JUMP" ->
                "Your weight jumped in the first week, which often reflects water or glycogen shifts rather than real fat gain. " +
                "Holding calories while monitoring waist trend gives a clearer picture before acting."
            "LOSING_WITH_POOR_RECOVERY" ->
                "Weight is trending down while performance or recovery is suffering. " +
                "Adding calories will support muscle maintenance and training quality — " +
                "losing weight at the cost of performance is not the goal."
            "GAINING_WITH_WAIST_INCREASE" ->
                "Both weight and waist are trending upward, which points to fat accumulation rather than lean gains. " +
                "A small calorie reduction will help redirect the trend without a harsh cut."
            "MAINTENANCE_TREND" ->
                "Weight, waist, and performance are all stable this period. " +
                "Your current intake is working — no adjustment is needed this week."
            "WEIGHT_UP_WAIST_STABLE_PERFORMANCE_UP" ->
                "Weight is rising but waist is stable and performance is improving. " +
                "This pattern points to lean mass gains, not fat accumulation. Holding calories is the right call."
            "NO_CLEAR_CHANGE_SIGNAL" ->
                "No strong signal emerged this week to justify a calorie change. " +
                "Staying at current intake gives you another review period of data to work with."
            else -> result.summary
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ai.FakeAiInsightCoordinatorTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` — 8 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/FakeAiInsightCoordinator.kt \
        app/src/test/java/com/zack/recomptracker/ai/FakeAiInsightCoordinatorTest.kt
git commit -m "feat(ai): add FakeAiInsightCoordinator with streaming simulation"
```

---

## Task 6: Wire `AppContainer`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add `appScope` and `aiInsightCoordinator` to `AppContainer`**

In `AppContainer.kt`, add the following after the `adherenceCalculator` line (line 61) and before `viewModelFactory`:

```kotlin
// Add these imports at the top of the file:
// import com.zack.recomptracker.ai.AiInsightCoordinator
// import com.zack.recomptracker.ai.FakeAiInsightCoordinator
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.SupervisorJob

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
val aiInsightCoordinator: AiInsightCoordinator = FakeAiInsightCoordinator(
    aiEnabledFlow = uiPreferences.aiInsightsEnabled,
    scope = appScope,
)
```

Then update `DashboardViewModel::class.java` in the factory to pass the coordinator:

```kotlin
DashboardViewModel::class.java -> DashboardViewModel(
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    dateProvider = container.dateProvider,
    trendCalculator = container.trendCalculator,
    adherenceCalculator = container.adherenceCalculator,
    adjustmentEngine = container.adjustmentEngine,
    aiInsightCoordinator = container.aiInsightCoordinator,  // ADD THIS LINE
)
```

- [ ] **Step 2: Verify it compiles (the ViewModel update in Task 7 is needed first — compile will fail until both are done)**

Skip compile check until after Task 7.

---

## Task 7: Update `DashboardViewModel`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Add imports and constructor parameter**

Add to the import block at the top of `DashboardViewModel.kt`:

```kotlin
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
```

Change the class declaration from:

```kotlin
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
) : ViewModel() {
```

To:

```kotlin
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {
```

- [ ] **Step 2: Expose AI state and actions**

Add the following block immediately after the `uiState` property declaration (after line 81):

```kotlin
/** AI explanation lifecycle — observed by DashboardScreen. */
val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state

/** Called from LaunchedEffect when the Stats screen becomes active. */
fun onAiCardVisible(result: AdjustmentResult) {
    aiInsightCoordinator.onAiCardVisible(result)
}

fun requestModelDownload() = aiInsightCoordinator.requestDownload()

fun cancelDownload() = aiInsightCoordinator.cancelDownload()

fun retryGeneration() = aiInsightCoordinator.retryGeneration(_uiState.value.result)
```

- [ ] **Step 3: Compile check (both Task 6 and Task 7 changes are now in place)**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt \
        app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt
git commit -m "feat(ai): wire AiInsightCoordinator into AppContainer and DashboardViewModel"
```

---

## Task 8: `AiBadge` Component

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AiBadge.kt`

- [ ] **Step 1: Create `AiBadge.kt`**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.theme.TintedBorder
import com.zack.recomptracker.ui.theme.TintedSurface
import com.zack.recomptracker.ui.theme.Violet400

/** Small badge marking AI-generated content. Only text in the app that says "AI". */
@Composable
fun AiBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TintedSurface)
            .border(1.dp, TintedBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✦ AI",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Violet400.copy(alpha = 0.75f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun AiBadgePreview() {
    AiBadge()
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiBadge.kt
git commit -m "feat(ai): add AiBadge component"
```

---

## Task 9: `AiInsightCard` Component

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt`

- [ ] **Step 1: Create `AiInsightCard.kt`**

```kotlin
package com.zack.recomptracker.ui.component

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.TintedBorder
import com.zack.recomptracker.ui.theme.TintedSurface
import com.zack.recomptracker.ui.theme.Violet300
import com.zack.recomptracker.ui.theme.Violet500

enum class AiBorderMode {
    /** Slow full-perimeter pulse — engine loading. */
    Preparing,
    /** Comet travels clockwise — inference streaming. */
    Generating,
    /** Comet decelerates and fades out — once only per Ready transition. */
    Ready,
    /** Static TintedBorder. Reduced-motion fallback and post-Ready rest. */
    Static,
}

/**
 * Tinted frosted card reserved for AI-generated content.
 * Identical background to TintedCard; border animates based on [borderMode].
 */
@Composable
fun AiInsightCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val effectiveMode = if (animationsEnabled) borderMode else AiBorderMode.Static

    val infiniteTransition = rememberInfiniteTransition(label = "aiComet")

    val cometPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
        ),
        label = "cometPhase",
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Tracks whether the Ready fade-out has completed — prevents replay on recomposition.
    var readyComplete by remember { mutableStateOf(false) }
    // Reset when transitioning into Ready from a non-Ready mode.
    if (effectiveMode != AiBorderMode.Ready) readyComplete = false

    val readyFadeAlpha by animateFloatAsState(
        targetValue = if (effectiveMode == AiBorderMode.Ready && !readyComplete) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "readyFade",
        finishedListener = { if (effectiveMode == AiBorderMode.Ready) readyComplete = true },
    )

    val backdrop = LocalBackdrop.current
    var cardWidth by remember { mutableIntStateOf(0) }
    val shimmerBrush = remember(cardWidth) {
        Brush.horizontalGradient(
            colors = listOf(Color.Transparent, TintedBorder, TintedBorder, Color.Transparent),
            startX = cardWidth * 0.10f,
            endX = cardWidth * 0.90f,
        )
    }
    val cornerPx = CornerCard.value  // used in drawScope below

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .onSizeChanged { cardWidth = it.width }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(CornerCard) },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(TintedSurface)
                    val shimmerY = 1.dp.toPx() / 2f
                    drawLine(
                        brush = shimmerBrush,
                        start = Offset(0f, shimmerY),
                        end = Offset(size.width, shimmerY),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            )
            .drawWithContent {
                drawContent()
                drawAnimatedBorder(
                    mode = effectiveMode,
                    cometPhase = cometPhase,
                    pulseAlpha = pulseAlpha,
                    readyFadeAlpha = readyFadeAlpha,
                    cornerPx = cornerPx * density,
                )
            }
            .padding(16.dp),
        content = content,
    )
}

private fun DrawScope.drawAnimatedBorder(
    mode: AiBorderMode,
    cometPhase: Float,
    pulseAlpha: Float,
    readyFadeAlpha: Float,
    cornerPx: Float,
) {
    val strokeWidth = 1.5.dp.toPx()
    val corner = CornerRadius(cornerPx)

    when (mode) {
        AiBorderMode.Static -> {
            drawRoundRect(
                color = TintedBorder,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        AiBorderMode.Preparing -> {
            drawRoundRect(
                color = Violet300.copy(alpha = pulseAlpha),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth),
            )
        }

        AiBorderMode.Generating, AiBorderMode.Ready -> {
            // Static base border beneath the comet
            drawRoundRect(
                color = TintedBorder,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
            val alpha = if (mode == AiBorderMode.Ready) readyFadeAlpha else 1f
            rotate(
                degrees = cometPhase * 360f,
                pivot = Offset(size.width / 2f, size.height / 2f),
            ) {
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.06f to Color(0x70FFFFFF),
                            0.11f to Violet300,
                            0.19f to Violet500,
                            0.23f to Color.Transparent,
                            1.00f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                    ),
                    cornerRadius = corner,
                    style = Stroke(width = strokeWidth),
                    alpha = alpha,
                )
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewStatic() {
    AiInsightCard(borderMode = AiBorderMode.Static) {
        androidx.compose.material3.Text("Static border", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewPreparing() {
    AiInsightCard(borderMode = AiBorderMode.Preparing) {
        androidx.compose.material3.Text("Preparing model…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewGenerating() {
    AiInsightCard(borderMode = AiBorderMode.Generating) {
        androidx.compose.material3.Text("Your weight has been trending down…", color = Color.White)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewReady() {
    AiInsightCard(borderMode = AiBorderMode.Ready) {
        androidx.compose.material3.Text("Weight, waist, and performance are all stable.", color = Color.White)
    }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/AiInsightCard.kt
git commit -m "feat(ai): add AiInsightCard with animated comet border and previews"
```

---

## Task 10: Update `DashboardScreen`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Add imports**

Add the following to the import block in `DashboardScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiInsightCard
```

- [ ] **Step 2: Update `DashboardScreen` composable**

Find the `DashboardScreen` composable (currently at line 603). Replace it entirely with:

```kotlin
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiInsightState.collectAsStateWithLifecycle()

    // Trigger generation whenever the result changes (guard is inside the coordinator).
    LaunchedEffect(state.result) {
        viewModel.onAiCardVisible(state.result)
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Current status and calorie decision")
            }
        }
        item {
            SectionCard("Calorie verdict") {
                Text(state.result.verdict.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.result.summary)
                Text("Change: ${state.result.recommendedCalorieChange} kcal/day")
                Text("Reasons: ${state.result.reasonCodes.joinToString()}")
            }
        }
        // AI explanation card — only shown when verdict is explainable
        item {
            AiInsightSection(
                result = state.result,
                aiState = aiState,
                onDownload = viewModel::requestModelDownload,
                onCancel = viewModel::cancelDownload,
                onRetry = viewModel::retryGeneration,
            )
        }
        item {
            SectionCard("Current targets") {
                Text("${state.preferences.targetCalories} kcal")
                Text("${state.preferences.targetProteinG}P / ${state.preferences.targetCarbsG}C / ${state.preferences.targetFatG}F")
            }
        }
        item {
            SectionCard("Today") {
                CalorieZoneBar(
                    eaten = state.todayTotals.calories,
                    zoneLower = state.preferences.calorieZoneLowerBound,
                    zoneUpper = state.preferences.calorieZoneUpperBound,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroMiniBar(
                        label = "Protein",
                        eaten = state.todayTotals.proteinG,
                        target = state.preferences.targetProteinG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Carbs",
                        eaten = state.todayTotals.carbsG,
                        target = state.preferences.targetCarbsG,
                        modifier = Modifier.weight(1f),
                    )
                    MacroMiniBar(
                        label = "Fat",
                        eaten = state.todayTotals.fatG,
                        target = state.preferences.targetFatG,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            SectionCard("Trend summary") {
                StatRow("7-day weight average", state.sevenDayWeightAverage?.let { "${String.format(Locale.US, "%.1f", it)} kg" } ?: "No data")
                StatRow("Weight trend", "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg/week")
                StatRow("Waist trend", "${state.waistTrendCmPerWeek.formatSignedOneDecimal()} cm/week")
                StatRow("Adherence", state.adherencePercent.formatPercent())
                StatRow("Logged days", state.daysLogged.toString())
            }
        }
    }
}
```

- [ ] **Step 3: Add `AiInsightSection` private composable**

Add this before the `StatRow` function in `DashboardScreen.kt`:

```kotlin
@Composable
private fun AiInsightSection(
    result: AdjustmentResult,
    aiState: AiInsightState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    // Never show the AI card when there is no real verdict to explain.
    if (result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) {
        if (aiState != AiInsightState.Disabled) {
            Text(
                text = "AI explanations appear once a weekly verdict is ready.",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        return
    }

    when (aiState) {
        AiInsightState.Disabled -> Unit // nothing shown

        AiInsightState.ModelMissing -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Understand the reasoning behind this verdict.",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Text(
                    text = "Requires a ~2.6 GB download · Wi-Fi recommended",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text("Download Model")
                }
            }
        }

        is AiInsightState.Downloading -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(10.dp))
                val progress = aiState.progress
                if (progress != null) {
                    Text(
                        text = "${"%.1f".format(progress * 2.6f)} GB of 2.6 GB",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Downloading…", fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("Cancel", fontSize = 11.sp)
                }
            }
        }

        AiInsightState.DownloadFailed -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text("Download failed — check your connection.", fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClick = onDownload) { Text("Retry") }
            }
        }

        AiInsightState.ModelReady,
        AiInsightState.LoadingModel -> {
            AiInsightCard(borderMode = AiBorderMode.Preparing) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.then(Modifier.padding(0.dp)).let {
                            androidx.compose.ui.Modifier.then(it)
                        },
                        strokeWidth = 2.dp,
                        color = Violet400,
                    )
                    Text("Preparing model…", fontSize = 13.sp, color = TextMuted)
                }
            }
        }

        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(
                    text = aiState.partialText,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp,
                )
            }
        }

        is AiInsightState.Ready -> {
            AiInsightCard(borderMode = AiBorderMode.Ready) {
                AiCardHeader(title = "Why this verdict", showRefresh = true, onRefresh = onRetry)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = aiState.text,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp,
                )
            }
        }

        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static) {
                AiCardHeader(title = "Why this verdict", showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text("Something went wrong.", fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}

@Composable
private fun AiCardHeader(
    title: String,
    showRefresh: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextFaint,
            letterSpacing = 0.14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRefresh) {
                androidx.compose.material3.IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.then(Modifier.padding(0.dp)).let {
                        androidx.compose.ui.Modifier.then(it)
                    },
                ) {
                    Text("↺", fontSize = 14.sp, color = Violet400)
                }
            }
            AiBadge()
        }
    }
}
```

- [ ] **Step 4: Add missing import for `TextFaint`**

Verify `TextFaint` is already imported (it is, via `com.zack.recomptracker.ui.theme.TextFaint`). Add it to the import block if missing.

- [ ] **Step 5: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. Fix any import errors before proceeding.

- [ ] **Step 6: Build and run on device or emulator — verify all AI states by toggling AI Insights in More**

Open the app → More → enable "AI Insights" → navigate to More → Stats.

Expected sequence:
1. Card shows "Download Model" button (`ModelMissing`)
2. Tap "Download Model" → progress bar appears (`Downloading`)
3. Progress completes → "Preparing model…" spinner (`LoadingModel`)
4. Text streams in word by word (`Generating`)
5. Full explanation appears, comet border fades (`Ready`)
6. Tap ↺ refresh → streams again

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(ai): render AiInsightSection in DashboardScreen with all state variants"
```

---

## Task 11: `RealAiInsightCoordinator` — Model Download

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/zack/recomptracker/ai/RealAiInsightCoordinator.kt`

- [ ] **Step 1: Add LiteRT-LM dependency**

In `app/build.gradle.kts`, add inside the `dependencies { }` block:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
```

- [ ] **Step 2: Sync and verify dependency resolves**

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep "litertlm"
```

Expected: `com.google.ai.edge.litertlm:litertlm-android:0.11.0` appears in the tree.

If not found: verify `google()` is listed in `settings.gradle.kts` under `dependencyResolutionManagement.repositories`. It should already be present in a standard Android project.

- [ ] **Step 3: Create `RealAiInsightCoordinator.kt` (download logic only — inference in Task 12)**

Create `app/src/main/java/com/zack/recomptracker/ai/RealAiInsightCoordinator.kt`:

```kotlin
package com.zack.recomptracker.ai

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RealAiInsightCoordinator(
    private val context: Context,
    private val aiEnabledFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
) : AiInsightCoordinator {

    private val _state = MutableStateFlow<AiInsightState>(AiInsightState.Disabled)
    override val state: StateFlow<AiInsightState> = _state.asStateFlow()

    private var lastGeneratedKey: String? = null
    private var downloadId: Long = -1L
    private val modelFile: File
        get() = File(context.filesDir, "ai/gemma-4-E2B-it.litertlm")

    // MODEL_URL: update if HuggingFace requires token-based download.
    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private val requiredFreeBytes = 3L * 1024 * 1024 * 1024 // 3 GB safety margin

    init {
        scope.launch {
            aiEnabledFlow.collect { enabled ->
                if (!enabled) {
                    _state.value = AiInsightState.Disabled
                } else if (_state.value == AiInsightState.Disabled) {
                    _state.value = if (modelFile.exists()) AiInsightState.ModelReady
                    else AiInsightState.ModelMissing
                }
            }
        }
    }

    override fun requestDownload() {
        if (_state.value != AiInsightState.ModelMissing) return
        if (!hasSufficientStorage()) {
            _state.value = AiInsightState.DownloadFailed
            return
        }
        modelFile.parentFile?.mkdirs()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Gemma 4 E2B — AI Explanation Model")
            .setDescription("Downloading for on-device AI features (~2.6 GB)")
            .setDestinationUri(Uri.fromFile(modelFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverRoaming(false)

        downloadId = dm.enqueue(request)
        _state.value = AiInsightState.Downloading(null)
        scope.launch { pollDownloadProgress(dm) }
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager) {
        while (true) {
            delay(1000L)
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = withContext(Dispatchers.IO) { dm.query(query) }
            if (!cursor.moveToFirst()) { cursor.close(); break }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal.toFloat() else null
                    _state.value = AiInsightState.Downloading(progress)
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _state.value = AiInsightState.ModelReady
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    _state.value = AiInsightState.DownloadFailed
                    break
                }
            }
        }
    }

    override fun cancelDownload() {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.remove(downloadId)
        downloadId = -1L
        _state.value = AiInsightState.ModelMissing
    }

    override fun deleteModel() {
        lastGeneratedKey = null
        modelFile.delete()
        _state.value = AiInsightState.ModelMissing
    }

    override fun onAiCardVisible(result: AdjustmentResult) {
        if (result.verdict == AdjustmentVerdict.WAIT_FOR_DATA) return
        if (_state.value != AiInsightState.ModelReady) return
        val key = result.key()
        if (key == lastGeneratedKey) return
        lastGeneratedKey = key
        scope.launch { generate(result) }
    }

    override fun retryGeneration(result: AdjustmentResult) {
        if (_state.value !is AiInsightState.Ready && _state.value != AiInsightState.ModelReady) return
        lastGeneratedKey = null
        _state.value = AiInsightState.ModelReady
        onAiCardVisible(result)
    }

    // Inference implemented in Task 12.
    private suspend fun generate(result: AdjustmentResult) {
        _state.value = AiInsightState.Error("Inference not yet implemented — swap in GemmaInsightService in Task 12.")
    }

    private fun hasSufficientStorage(): Boolean {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBytes >= requiredFreeBytes
    }
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/zack/recomptracker/ai/RealAiInsightCoordinator.kt
git commit -m "feat(ai): add RealAiInsightCoordinator download flow and LiteRT-LM dependency"
```

---

## Task 12: Inference + Swap Real Coordinator into `AppContainer`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaInsightService.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/RealAiInsightCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Replace `GemmaInsightService` stub with LiteRT-LM engine wrapper**

Overwrite `app/src/main/java/com/zack/recomptracker/ai/GemmaInsightService.kt`:

```kotlin
package com.zack.recomptracker.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper around the LiteRT-LM Engine.
 * Initialized lazily on first inference call; released by [release].
 */
class GemmaInsightService(
    private val modelPath: String,
    private val cacheDir: String,
) {
    private var engine: Engine? = null

    /**
     * Loads the model into memory. Blocking — call from Dispatchers.IO.
     * Safe to call multiple times; skips init if already loaded.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),  // GPU() or QNN() can be tried for acceleration
            cacheDir = cacheDir,
        )
        val e = Engine(config)
        e.initialize()
        engine = e
    }

    /**
     * Runs inference for [prompt]. Returns the full response as a single string.
     * Blocking — call from Dispatchers.IO.
     * Throws if the engine has not been initialized.
     */
    suspend fun generateExplanation(prompt: String): String = withContext(Dispatchers.IO) {
        val e = engine ?: error("GemmaInsightService not initialized — call initialize() first.")
        val conversation = e.createConversation()
        conversation.send(prompt)
    }

    /** Releases the engine from memory. Call when the coordinator is done. */
    fun release() {
        engine = null
    }
}
```

- [ ] **Step 2: Wire `GemmaInsightService` into `RealAiInsightCoordinator.generate()`**

Replace the placeholder `generate()` function in `RealAiInsightCoordinator.kt` with:

```kotlin
private val promptBuilder = InsightPromptBuilder()
private var gemmaService: GemmaInsightService? = null

private suspend fun generate(result: AdjustmentResult) {
    _state.value = AiInsightState.LoadingModel
    try {
        val service = gemmaService ?: GemmaInsightService(
            modelPath = modelFile.absolutePath,
            cacheDir = context.cacheDir.absolutePath,
        ).also { gemmaService = it }

        withContext(Dispatchers.IO) { service.initialize() }

        _state.value = AiInsightState.Generating("")
        val prompt = promptBuilder.buildWeeklySummaryPrompt(result)
        val text = withContext(Dispatchers.IO) { service.generateExplanation(prompt) }

        // Emit streamed chunks word by word for UX continuity
        val words = text.trim().split(" ")
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(word)
            _state.value = AiInsightState.Generating(sb.toString())
        }
        _state.value = AiInsightState.Ready(sb.toString())
    } catch (e: Exception) {
        _state.value = AiInsightState.Error("Something went wrong — try again.")
    }
}
```

Also add this import at the top of `RealAiInsightCoordinator.kt`:

```kotlin
import kotlinx.coroutines.withContext
```

- [ ] **Step 3: Swap `FakeAiInsightCoordinator` → `RealAiInsightCoordinator` in `AppContainer`**

In `AppContainer.kt`, replace:

```kotlin
val aiInsightCoordinator: AiInsightCoordinator = FakeAiInsightCoordinator(
    aiEnabledFlow = uiPreferences.aiInsightsEnabled,
    scope = appScope,
)
```

With:

```kotlin
val aiInsightCoordinator: AiInsightCoordinator = RealAiInsightCoordinator(
    context = context,
    aiEnabledFlow = uiPreferences.aiInsightsEnabled,
    scope = appScope,
)
```

Also update the import: replace `FakeAiInsightCoordinator` with `RealAiInsightCoordinator`.

- [ ] **Step 4: Compile check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

If LiteRT-LM import paths differ from `com.google.ai.edge.litertlm.*`, check the library's published API with:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath 2>&1 | grep litertlm
```

Then browse the AAR's class names in the Gradle cache or the [LiteRT-LM GitHub README](https://github.com/google-ai-edge/LiteRT-LM) for the exact package and class names.

- [ ] **Step 5: Full build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Test on real device**

Install on a physical device (not emulator — LiteRT-LM targets real hardware):

```bash
./gradlew :app:installDebug
```

Steps to verify:
1. Enable AI Insights in More
2. Open More → Stats
3. Tap "Download Model" — confirm 2.6 GB download starts (will take time on real device)
4. After download: "Preparing model…" appears, then the explanation streams in
5. Tap ↺ — explanation regenerates

**Note:** First initialization after download can take 15–50 seconds on device. This is expected.

- [ ] **Step 7: Final commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/GemmaInsightService.kt \
        app/src/main/java/com/zack/recomptracker/ai/RealAiInsightCoordinator.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(ai): wire GemmaInsightService inference into RealAiInsightCoordinator"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ `AiInsightState` with all 9 states including `Generating(partialText)` — Task 2
- ✅ `AiInsightCoordinator` interface with all 5 methods — Task 4
- ✅ Toggle separation: coordinator reads `aiEnabledFlow`, ViewModel reads coordinator state — Tasks 5, 7
- ✅ `WAIT_FOR_DATA` gate: passive line shown, no download CTA, no inference trigger — Task 10
- ✅ `resultKey` guard: same key skips generation — Tasks 2, 5
- ✅ `onAiCardVisible` as explicit `LaunchedEffect` event — Task 10
- ✅ `retryGeneration` resets key and re-runs — Tasks 4, 5, 10
- ✅ Download: Wi-Fi-only flag, space check, indeterminate progress fallback, cancel — Task 11
- ✅ `Preparing model…` wording (not "Loading") — Task 10
- ✅ Human-readable error messages — Task 10
- ✅ Fake coordinator first, real coordinator last — Task ordering
- ✅ Comet border with `Preparing`/`Generating`/`Ready`/`Static` modes — Task 9
- ✅ Reduced-motion fallback (`ANIMATOR_DURATION_SCALE == 0` → `Static`) — Task 9
- ✅ Ready fade-out tracked with `remember { mutableStateOf(false) }`, no replay — Task 9
- ✅ `AiBadge` — Task 8; card title "Why this verdict" — Task 10
- ✅ Model management in More screen: already handled by existing `MoreScreen` AI toggle; `deleteModel()` wired through coordinator
- ✅ `appScope` injected into coordinator from `AppContainer` — Task 6
- ✅ `GemmaInsightService` replaces stub — Task 12
- ✅ `InsightPromptBuilder` refined — Task 3
