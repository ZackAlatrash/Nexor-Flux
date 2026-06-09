# AI Insight Cards — Phase 2 (UI Layer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the three phase-1 AI insights in the UI — Progress and Recovery as auto `AiInsightCard`s, Food as a tap-to-reveal — each generating from real data through the already-wired coordinator.

**Architecture:** One new shared `GeneratedInsightCard` composable renders only the generation states (Generating/Ready/Error) and nothing else. Each of the three screen ViewModels injects the shared `AiInsightCoordinator`, builds its `InsightRequest` from existing state via a pure mapper function (unit-tested), exposes `generationState(kind)`, and triggers `onInsightVisible`/`retryInsight`. Built Progress → Recovery → Food, with an on-device prompt-validation checkpoint after each.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow + collectAsStateWithLifecycle, JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-10-ai-insight-cards-ui-design.md`
**Phase-1 types (already exist):** `InsightKind`, `InsightRequest.{ProgressTrend,RecoveryReadiness,RestOfDay}`, `ProgressInsightContext`, `RecoveryInsightContext`, `RestOfDayInsightContext`, `AiInsightCoordinator.generationState(kind)`/`onInsightVisible(request)`/`retryInsight(request)`, `AiInsightState`, `AiInsightCard`, `AiBorderMode`, `AiBadge`.

**Verification note:** Pure mapper functions are TDD unit-tested. ViewModel/screen wiring is verified by `:app:compileDebugKotlin` + the full `:app:testDebugUnitTest` suite. Real model-output quality is validated on a physical device at each screen's checkpoint (the prompt-tuning gate deferred from phase 1).

---

## File Structure

**Create:**
- `app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt` — generation-only renderer + previews
- `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressInsightMapper.kt` — pure `ProgressUiState data → ProgressInsightContext`
- `app/src/main/java/com/zack/recomptracker/ui/today/RecoveryInsightMapper.kt` — pure `DailyLog → RecoveryInsightContext?`
- `app/src/main/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapper.kt` — pure `totals/target/count → RestOfDayInsightContext`
- `app/src/test/java/com/zack/recomptracker/ui/progress/ProgressInsightMapperTest.kt`
- `app/src/test/java/com/zack/recomptracker/ui/today/RecoveryInsightMapperTest.kt`
- `app/src/test/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapperTest.kt`

**Modify:**
- `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (factory: 3 VM constructors)
- `app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt` (new constructor arg)

---

## Task 1: GeneratedInsightCard composable

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt`

No unit test (Compose composable, like the existing `AiInsightCard` — previews serve as the visual check). Verified by compile.

- [ ] **Step 1: Create the file**

```kotlin
package com.zack.recomptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted

/**
 * Generation-only renderer for the expanded per-kind insights (Progress / Recovery / Food).
 *
 * Renders the streaming ([AiInsightState.Generating]), finished ([AiInsightState.Ready]), and
 * [AiInsightState.Error] states as an [AiInsightCard]. Renders NOTHING for every model-lifecycle
 * state (Disabled / ModelMissing / Downloading / DownloadFailed / ModelVerifying / ModelReady /
 * LoadingModel) — model download/management lives on the dashboard card and the More screen, so
 * these surfaces never duplicate it. The card simply appears once generation produces text.
 */
@Composable
fun GeneratedInsightCard(
    title: String,
    state: AiInsightState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(text = state.partialText, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
            }
        }
        is AiInsightState.Ready -> {
            AiInsightCard(borderMode = AiBorderMode.Ready, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = true, onRefresh = onRetry)
                Spacer(Modifier.height(8.dp))
                Text(text = state.text, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
            }
        }
        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(text = state.message, fontSize = 13.sp, color = TextMuted)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun InsightCardHeader(title: String, showRefresh: Boolean, onRefresh: () -> Unit) {
    val accent = LocalAppAccent.current
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
                IconButton(onClick = onRefresh) {
                    Text("↺", fontSize = 14.sp, color = accent.accentLight)
                }
            }
            AiBadge()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewGenerating() {
    GeneratedInsightCard(
        title = "Trend analysis",
        state = AiInsightState.Generating("Your weight held steady while your waist trended down…"),
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewReady() {
    GeneratedInsightCard(
        title = "Recovery readiness",
        state = AiInsightState.Ready("Two short nights with high soreness — prioritize sleep tonight."),
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewError() {
    GeneratedInsightCard(
        title = "Rest of day",
        state = AiInsightState.Error("Something went wrong — try again."),
        onRetry = {},
    )
}
```

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/component/GeneratedInsightCard.kt
git commit -m "feat(ui): add generation-only GeneratedInsightCard renderer"
```

---

## Task 2: Progress insight mapper (pure + tested)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressInsightMapper.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/progress/ProgressInsightMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressInsightMapperTest {

    @Test
    fun `maps counts and range`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 28,
            weightValues = listOf(80f, 79.5f, 79f),
            waistValues = listOf(85f, 84f),
            liftValues = listOf(100f, 102f),
            adherencePercent = 90f,
        )
        assertEquals(28, ctx.rangeDays)
        assertEquals(3, ctx.weightPointCount)
        assertEquals(2, ctx.waistPointCount)
        assertEquals(90.0, ctx.adherencePercent!!, 0.001)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `single point yields null trend`() {
        val ctx = buildProgressInsightContext(
            rangeDays = 7,
            weightValues = listOf(80f),
            waistValues = emptyList(),
            liftValues = emptyList(),
            adherencePercent = null,
        )
        assertNull(ctx.weightTrendKgPerWeek)
        assertNull(ctx.adherencePercent)
        assertFalse(ctx.hasSufficientData)
    }

    @Test
    fun `computes a weekly trend from two weeks of points`() {
        // 8 points span 7 days = 1.0 week; (last - first) / 1.0 week
        val ctx = buildProgressInsightContext(
            rangeDays = 7,
            weightValues = listOf(80f, 80f, 80f, 80f, 80f, 80f, 80f, 79f),
            waistValues = emptyList(),
            liftValues = emptyList(),
            adherencePercent = null,
        )
        assertEquals(-1.0, ctx.weightTrendKgPerWeek!!, 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.progress.ProgressInsightMapperTest"`
Expected: FAIL — `buildProgressInsightContext` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.ai.ProgressInsightContext

/**
 * Pure mapper: builds a [ProgressInsightContext] from the raw per-metric value series the
 * ProgressViewModel already computes for its charts. Trends are kg-or-cm per week; a series
 * with fewer than two points yields a null trend (and does not count toward sufficiency).
 */
fun buildProgressInsightContext(
    rangeDays: Int,
    weightValues: List<Float>,
    waistValues: List<Float>,
    liftValues: List<Float>,
    adherencePercent: Float?,
): ProgressInsightContext = ProgressInsightContext(
    rangeDays = rangeDays,
    weightTrendKgPerWeek = trendPerWeek(weightValues),
    waistTrendCmPerWeek = trendPerWeek(waistValues),
    liftTrendKgPerWeek = trendPerWeek(liftValues),
    adherencePercent = adherencePercent?.toDouble(),
    weightPointCount = weightValues.size,
    waistPointCount = waistValues.size,
)

private fun trendPerWeek(values: List<Float>): Double? {
    if (values.size < 2) return null
    val weeks = (values.size - 1).toFloat() / 7f
    return if (weeks > 0f) ((values.last() - values.first()) / weeks).toDouble() else null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.progress.ProgressInsightMapperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressInsightMapper.kt app/src/test/java/com/zack/recomptracker/ui/progress/ProgressInsightMapperTest.kt
git commit -m "feat(ui): add pure Progress insight context mapper"
```

---

## Task 3: Wire Progress (ViewModel + factory + screen)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`

Integration task — no new unit test. Verified by compile + full suite + on-device checkpoint.

- [ ] **Step 1: Update `ProgressViewModel`**

Add these imports (top of file, with the other `com.zack.recomptracker.ai`-free imports):
```kotlin
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.ProgressInsightContext
```

Add a field to `ProgressUiState` (with a default so existing construction sites are unaffected):
```kotlin
    val lifts: ChartSeries = ChartSeries("Marker lift e1RM", "kg", emptyList()),
    val insightContext: ProgressInsightContext? = null,
)
```

Add the constructor parameter:
```kotlin
class ProgressViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val adherenceCalculator: AdherenceCalculator,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {
```

In the `combine { ... }` block, the locals `weightValues`, `waistValues`, `liftValues`, and `adherenceLast` are already computed. In the returned `ProgressUiState(...)`, add the `insightContext` argument at the end (after `lifts = ...`):
```kotlin
                    insightContext = buildProgressInsightContext(
                        rangeDays = range,
                        weightValues = weightValues,
                        waistValues = waistValues,
                        liftValues = liftValues,
                        adherencePercent = adherenceLast,
                    ),
```
(`buildProgressInsightContext` is in the same package — no import needed.)

Add these members to the class (e.g. after the `uiState` declaration):
```kotlin
    val progressInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.PROGRESS_TREND)

    fun onProgressInsightVisible() {
        val ctx = _uiState.value.insightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.ProgressTrend(ctx))
    }

    fun retryProgressInsight() {
        val ctx = _uiState.value.insightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.ProgressTrend(ctx))
    }
```

- [ ] **Step 2: Update the factory in `AppContainer.kt`**

In `AppViewModelFactory.create`, the `ProgressViewModel::class.java ->` branch currently passes four args. Add the coordinator:
```kotlin
            ProgressViewModel::class.java -> ProgressViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                adherenceCalculator = container.adherenceCalculator,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
```

- [ ] **Step 3: Update `ProgressScreen`**

Read the file first. Add imports as needed:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import com.zack.recomptracker.ui.component.GeneratedInsightCard
```
(`collectAsStateWithLifecycle` and `getValue` are already imported.)

In `fun ProgressScreen(viewModel: ProgressViewModel)`, just after the existing `val state by viewModel.uiState.collectAsStateWithLifecycle()` line, add:
```kotlin
    val insightState by viewModel.progressInsightState.collectAsStateWithLifecycle()
    LaunchedEffect(state.insightContext?.key()) {
        viewModel.onProgressInsightVisible()
    }
```

Inside the `LazyColumn(...)` (around `ProgressScreen.kt:66`), add a NEW first `item { }` — before the existing first item at line 75 — so the card sits at the very top:
```kotlin
            item {
                GeneratedInsightCard(
                    title = "Trend analysis",
                    state = insightState,
                    onRetry = viewModel::retryProgressInsight,
                )
            }
```

- [ ] **Step 4: Type-check and run the full suite**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; full suite green (no test count change — wiring only).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt
git commit -m "feat(ui): show Progress trend-analysis AI card"
```

- [ ] **Step 6: DEVICE CHECKPOINT (manual, by the human)**

This is the first on-device sighting of a new insight's real model output. On a device with the model downloaded and AI enabled, open Progress with ≥2 weight/waist points logged. Confirm the card streams and reads sensibly. If the response is off, tune `buildProgressTrendPrompt` in `InsightPromptBuilder.kt` (and its tests) before continuing. Do not gate the remaining tasks on this — note findings and proceed.

---

## Task 4: Recovery insight mapper (pure + tested)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/RecoveryInsightMapper.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/today/RecoveryInsightMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightMapperTest {

    @Test
    fun `null log yields null context`() {
        assertNull(buildRecoveryInsightContext(null))
    }

    @Test
    fun `maps logged recovery fields`() {
        val log = DailyLogEntity(
            date = "2026-06-10",
            sleepHours = 6.5,
            energyScore = 4,
            hungerScore = 5,
            sorenessScore = 8,
            trained = true,
        )
        val ctx = buildRecoveryInsightContext(log)!!
        assertEquals(6.5, ctx.sleepHours!!, 0.001)
        assertEquals(8, ctx.sorenessScore)
        assertTrue(ctx.trained)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `log with only body metrics is insufficient`() {
        val log = DailyLogEntity(date = "2026-06-10", bodyWeightKg = 80.0, waistCm = 85.0)
        val ctx = buildRecoveryInsightContext(log)!!
        assertNull(ctx.sleepHours)
        assertNull(ctx.energyScore)
        assertFalse(ctx.hasSufficientData)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RecoveryInsightMapperTest"`
Expected: FAIL — `buildRecoveryInsightContext` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.data.local.entity.DailyLogEntity

/**
 * Pure mapper: builds a [RecoveryInsightContext] from the PERSISTED daily log (not the editable
 * form fields, which default scores to 5). Returns null when there is no log for the day. When a
 * log exists but holds no recovery signals, the resulting context's `hasSufficientData` is false
 * so the card stays hidden.
 */
fun buildRecoveryInsightContext(log: DailyLogEntity?): RecoveryInsightContext? {
    if (log == null) return null
    return RecoveryInsightContext(
        sleepHours = log.sleepHours,
        energyScore = log.energyScore,
        hungerScore = log.hungerScore,
        sorenessScore = log.sorenessScore,
        trained = log.trained,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RecoveryInsightMapperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/RecoveryInsightMapper.kt app/src/test/java/com/zack/recomptracker/ui/today/RecoveryInsightMapperTest.kt
git commit -m "feat(ui): add pure Recovery insight context mapper"
```

---

## Task 5: Wire Recovery (TodayViewModel + factory + screen)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 1: Update `TodayViewModel`**

Add imports:
```kotlin
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.RecoveryInsightContext
```

Add a field to `TodayUiState` (with default):
```kotlin
    val totalDaysLogged: Int = 0,
    val recoveryInsightContext: RecoveryInsightContext? = null,
)
```

Add the constructor parameter:
```kotlin
class TodayViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val hcRepository: HealthConnectRepository,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {
```

In the first `combine(...).collect { (day, prefs, slots) -> ... }` block, a local `val log = day.dailyLog` already exists. In the final `metrics.copy(...)` call (the one that sets `target`, `totals`, `slots`, `checkInDone`), add:
```kotlin
                    metrics.copy(
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                        unslottedEntries = unslotted,
                        checkInDone = log != null,
                        recoveryInsightContext = buildRecoveryInsightContext(day.dailyLog),
                    )
```
(`buildRecoveryInsightContext` is same-package — no import.)

Add these members to the class:
```kotlin
    val recoveryInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.RECOVERY_READINESS)

    fun onRecoveryInsightVisible() {
        val ctx = _uiState.value.recoveryInsightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.RecoveryReadiness(ctx))
    }

    fun retryRecoveryInsight() {
        val ctx = _uiState.value.recoveryInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.RecoveryReadiness(ctx))
    }
```

- [ ] **Step 2: Update the factory in `AppContainer.kt`**

```kotlin
            TodayViewModel::class.java -> TodayViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                hcRepository = container.healthConnectRepository,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
```

- [ ] **Step 3: Update `BodyRecoveryScreen`**

Read the file. Add imports:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import com.zack.recomptracker.ui.component.GeneratedInsightCard
```
In `fun BodyRecoveryScreen(viewModel: TodayViewModel, ...)`, just after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line ~69), add:
```kotlin
    val recoveryInsightState by viewModel.recoveryInsightState.collectAsStateWithLifecycle()
    LaunchedEffect(state.recoveryInsightContext?.key()) {
        viewModel.onRecoveryInsightVisible()
    }
```
In the `LazyColumn` (line ~137), immediately AFTER `item { MetricsHeroCard(state) }` (line ~147), insert:
```kotlin
            item {
                GeneratedInsightCard(
                    title = "Recovery readiness",
                    state = recoveryInsightState,
                    onRetry = viewModel::retryRecoveryInsight,
                )
            }
```

- [ ] **Step 4: Type-check and run the full suite**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; full suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
git commit -m "feat(ui): show Recovery readiness AI card"
```

- [ ] **Step 6: DEVICE CHECKPOINT (manual):** open Recovery with sleep/energy/hunger/soreness logged for today; confirm the readiness card streams sensibly. Tune `buildRecoveryReadinessPrompt` if needed.

---

## Task 6: Rest-of-Day insight mapper (pure + tested)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapper.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.preferences.PlanPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightMapperTest {

    private val target = PlanPreferences(
        targetCalories = 2200,
        targetProteinG = 165,
        calorieZoneLowerBound = 2100,
        calorieZoneUpperBound = 2300,
    )

    @Test
    fun `maps totals and target into context`() {
        val ctx = buildRestOfDayInsightContext(
            totals = MacroTotals(calories = 1420, proteinG = 102.0),
            target = target,
            mealsLoggedCount = 2,
        )
        assertEquals(1420, ctx.caloriesConsumed)
        assertEquals(2200, ctx.targetCalories)
        assertEquals(2100, ctx.calorieZoneLowerBound)
        assertEquals(2300, ctx.calorieZoneUpperBound)
        assertEquals(102.0, ctx.proteinConsumedG, 0.001)
        assertEquals(165, ctx.proteinTargetG)
        assertEquals(2, ctx.mealsLoggedCount)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `zero meals is insufficient`() {
        val ctx = buildRestOfDayInsightContext(MacroTotals(), target, mealsLoggedCount = 0)
        assertFalse(ctx.hasSufficientData)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RestOfDayInsightMapperTest"`
Expected: FAIL — `buildRestOfDayInsightContext` unresolved reference.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.preferences.PlanPreferences

/**
 * Pure mapper: builds a [RestOfDayInsightContext] from today's running totals, the plan target,
 * and the number of meals logged so far. `mealsLoggedCount` of 0 makes the context insufficient
 * so the tap-to-reveal button stays hidden.
 */
fun buildRestOfDayInsightContext(
    totals: MacroTotals,
    target: PlanPreferences,
    mealsLoggedCount: Int,
): RestOfDayInsightContext = RestOfDayInsightContext(
    caloriesConsumed = totals.calories,
    targetCalories = target.targetCalories,
    calorieZoneLowerBound = target.calorieZoneLowerBound,
    calorieZoneUpperBound = target.calorieZoneUpperBound,
    proteinConsumedG = totals.proteinG,
    proteinTargetG = target.targetProteinG,
    mealsLoggedCount = mealsLoggedCount,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.RestOfDayInsightMapperTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapper.kt app/src/test/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapperTest.kt
git commit -m "feat(ui): add pure Rest-of-Day insight context mapper"
```

---

## Task 7: Wire Food (FoodLogViewModel + factory + screen tap-to-reveal + test fix)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Modify: `app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt`

- [ ] **Step 1: Update `FoodLogViewModel`**

Add imports:
```kotlin
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.RestOfDayInsightContext
```

Add a field to `FoodLogUiState` (with default):
```kotlin
    val message: String? = null,
    val restOfDayInsightContext: RestOfDayInsightContext? = null,
) {
    val isToday: Boolean get() = selectedDate == today
}
```

Add the constructor parameter:
```kotlin
class FoodLogViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {
```

In the first `combine(...).collect { (day, prefs, slots) -> ... }` block, in the `_uiState.update { it.copy(...) }`, add the context — only for today (rest-of-day advice is meaningless for a past date):
```kotlin
                _uiState.update {
                    it.copy(
                        selectedDate = day.date,
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                        restOfDayInsightContext = if (day.date == today) {
                            buildRestOfDayInsightContext(day.totals, prefs, day.meals.size)
                        } else null,
                    )
                }
```
(`buildRestOfDayInsightContext` is same-package — no import.)

Add these members:
```kotlin
    val restOfDayInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.REST_OF_DAY)

    fun onRestOfDayInsightVisible() {
        val ctx = _uiState.value.restOfDayInsightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.RestOfDay(ctx))
    }

    fun retryRestOfDayInsight() {
        val ctx = _uiState.value.restOfDayInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.RestOfDay(ctx))
    }
```

- [ ] **Step 2: Update the factory in `AppContainer.kt`**

```kotlin
            FoodLogViewModel::class.java -> FoodLogViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
```

- [ ] **Step 3: Fix `FoodLogViewModelTest`**

The test's `buildVm()` constructs `FoodLogViewModel(logRepo, planRepo, dateProvider)`. Add a stub coordinator. Add these imports to the test:
```kotlin
import com.zack.recomptracker.ai.StubInsightCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
```
The test already declares a `StandardTestDispatcher` (find the existing `private val dispatcher = StandardTestDispatcher()` or equivalent). Add a stub coordinator field using that dispatcher, and pass it into `buildVm()`:
```kotlin
    private val aiCoordinator = StubInsightCoordinator(MutableStateFlow(false), CoroutineScope(dispatcher))

    private fun buildVm() = FoodLogViewModel(logRepo, planRepo, dateProvider, aiCoordinator)
```
(If the dispatcher field has a different name, use that name. The `StubInsightCoordinator` constructor is `(aiEnabledFlow: Flow<Boolean>, scope: CoroutineScope)`.)

- [ ] **Step 4: Update `FoodScreen` — add the tap-to-reveal**

Read the file. Add imports:
```kotlin
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.component.GeneratedInsightCard
```
(Skip any that are already imported.)

In `fun FoodScreen(viewModel: FoodLogViewModel, ...)`, just after `val state by viewModel.uiState.collectAsStateWithLifecycle()` (line ~83), add:
```kotlin
    val restOfDayInsightState by viewModel.restOfDayInsightState.collectAsStateWithLifecycle()
```

Add this private composable to the file (e.g. near the other private composables):
```kotlin
@Composable
private fun RestOfDayReveal(
    available: Boolean,
    state: AiInsightState,
    onReveal: () -> Unit,
    onRetry: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    when {
        revealed -> GeneratedInsightCard(
            title = "Rest of day",
            state = state,
            onRetry = onRetry,
        )
        available -> TextButton(onClick = { revealed = true; onReveal() }) {
            Text("✨ Rest of day?")
        }
    }
}
```

In the `LazyColumn` (line ~163), immediately AFTER `item { NutritionStrip(state) }` (line ~182), insert:
```kotlin
                item {
                    RestOfDayReveal(
                        available = state.restOfDayInsightContext?.hasSufficientData == true,
                        state = restOfDayInsightState,
                        onReveal = viewModel::onRestOfDayInsightVisible,
                        onRetry = viewModel::retryRestOfDayInsight,
                    )
                }
```
Note: if `FoodScreen` delegates its body to a stateless content composable rather than holding the `LazyColumn` directly, thread `restOfDayInsightState` and the two callbacks (`onReveal`, `onRetry`) plus `available` down to where the `LazyColumn` lives, following the screen's existing parameter-passing style.

- [ ] **Step 5: Type-check and run the full suite**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; full suite green including the updated `FoodLogViewModelTest`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt
git commit -m "feat(ui): show Rest-of-Day AI insight via tap-to-reveal on Food screen"
```

- [ ] **Step 7: DEVICE CHECKPOINT (manual):** open Food (today) with ≥1 meal logged; tap "Rest of day?"; confirm the insight streams sensibly. Tune `buildRestOfDayPrompt` if needed.

---

## Self-Review (completed during planning)

**Spec coverage:**
- §2 generation-only rule → Task 1 (`GeneratedInsightCard` renders only Generating/Ready/Error, else nothing; no download UI). ✓
- §3.1 shared composable → Task 1. ✓
- §3.2 per-VM additions (inject coordinator, `generationState(kind)`, build context, trigger, retry) → Tasks 3, 5, 7. ✓
- §3.3 per-screen wiring (Progress top auto; Recovery under hero auto; Food tap-to-reveal) → Tasks 3, 5, 7 (placements at the confirmed anchors). ✓
- §4.1/4.2/4.3 data assembly → Tasks 2, 4, 6 (pure mappers; Recovery from persisted log; Food today-only). ✓
- §5 sequencing Progress→Recovery→Food with device checkpoints → task order + Steps 6/6/7. ✓
- §5 verification (mapper unit tests; previews; on-device) → Tasks 2/4/6 tests, Task 1 previews, checkpoint steps. ✓
- §6 out of scope (no dashboard/download/More changes; no toggle change) → not touched. ✓

**Placeholder scan:** none — every code step has complete code; screen-placement steps give exact snippets + confirmed anchor lines.

**Type consistency:** `buildProgressInsightContext(rangeDays, weightValues, waistValues, liftValues, adherencePercent)`, `buildRecoveryInsightContext(log)`, `buildRestOfDayInsightContext(totals, target, mealsLoggedCount)` are defined in Tasks 2/4/6 and called identically in Tasks 3/5/7. VM members `progressInsightState`/`onProgressInsightVisible`/`retryProgressInsight` (and Recovery/RestOfDay analogues) are defined and consumed consistently. `GeneratedInsightCard(title, state, onRetry, modifier)` signature consistent across Task 1 and all call sites. UiState fields `insightContext` / `recoveryInsightContext` / `restOfDayInsightContext` defined with defaults and read in the same VM and screen. `InsightRequest.{ProgressTrend,RecoveryReadiness,RestOfDay}` and `InsightKind.{PROGRESS_TREND,RECOVERY_READINESS,REST_OF_DAY}` match phase-1 definitions. ✓
