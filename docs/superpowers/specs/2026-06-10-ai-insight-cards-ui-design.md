# AI Insight Cards — Phase 2 (UI Layer) Design

**Date:** 2026-06-10
**Status:** Approved for implementation planning
**Scope:** Phase 2 of the AI insight expansion. Phase 1 built the AI generation layer (contexts, prompt builders, generalized coordinator) — all wired to the real on-device model. This phase surfaces those three insights in the UI, on the Progress, Recovery, and Food screens.

**Prior spec:** `docs/superpowers/specs/2026-06-09-ai-insight-expansion-design.md`

---

## 1. Goal

Show the three already-wired AI insights to the user, each presented in a way that fits the screen it lives on:

| Screen | ViewModel | Insight (`InsightKind`) | Treatment |
|---|---|---|---|
| Progress | `ProgressViewModel` | `PROGRESS_TREND` | Full `AiInsightCard`, top of screen, auto |
| Recovery | `TodayViewModel` | `RECOVERY_READINESS` | Full `AiInsightCard`, under metrics hero, auto |
| Food | `FoodLogViewModel` | `REST_OF_DAY` | Tap-to-reveal (sparkle button → expands), on demand |

This phase delivers: ViewModel context-building + coordinator wiring for each screen, one new shared "generation-only" renderer composable, the three placements, unit tests for context-building, and Compose previews. Real model-output quality is validated and tuned on-device, per screen, at checkpoints.

---

## 2. The generation-only rule (cross-cutting)

The existing dashboard card renders the **full** model lifecycle — "Download Model ~2.6 GB", download progress, "Verifying…", etc. Those states are **global** (one shared model). Replicating them on three more screens would show the same "Download Model" prompt in four places.

Therefore the new cards are **generation-only**. A new shared composable, `GeneratedInsightCard(state)`, renders **only**:
- `Generating(partialText)` → streaming text, `AiBorderMode.Generating`
- `Ready(text)` → final text + a retry/refresh affordance, `AiBorderMode.Ready`
- `Error(message)` → a compact inline message + "Try again" (calls `retryInsight`)

For **every other state** (`Disabled`, `ModelMissing`, `Downloading`, `DownloadFailed`, `ModelVerifying`, `ModelReady`, `LoadingModel`-before-text), it renders **nothing** (the card is absent). Model download/management stays in its two existing homes: the dashboard insight card and the More screen. The new screens never duplicate model management.

`LoadingModel` renders nothing (not a "preparing" card) to keep the new surfaces quiet until there is actual content — the card appears only when streaming begins.

---

## 3. Architecture

Mirrors the dashboard's proven pattern (coordinator injected into VM → exposed as `StateFlow` → collected with `collectAsStateWithLifecycle` → `LaunchedEffect` trigger → render).

### 3.1 Shared composable: `GeneratedInsightCard`

A new stateless composable (new file under `ui/component`):

```
GeneratedInsightCard(
    state: AiInsightState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Wraps the existing `AiInsightCard` internally.
- Renders only the three generation states above; emits nothing otherwise.
- Single responsibility: "turn a per-kind `AiInsightState` into a generation-only card." Distinct from the dashboard's full-lifecycle `when` block (unchanged).
- Compose previews for Generating / Ready / Error.

### 3.2 Per-ViewModel additions

Each of the three VMs (`ProgressViewModel`, `TodayViewModel`, `FoodLogViewModel`) gains:
- Constructor injection of the shared `aiInsightCoordinator: AiInsightCoordinator` (wired in `AppViewModelFactory`, same instance as the dashboard).
- `val insightState: StateFlow<AiInsightState> = aiInsightCoordinator.generationState(<KIND>)`.
- A pure `buildInsightContext(...)` (or equivalent) that assembles the kind's context from current state, and a trigger method `onInsightVisible()` that calls `aiInsightCoordinator.onInsightVisible(InsightRequest.<Kind>(context))`.
- A `retryInsight()` that calls `aiInsightCoordinator.retryInsight(request)`.

### 3.3 Per-screen wiring

- **Progress / Recovery (auto):** `LaunchedEffect(<dedupKey>)` calls the VM trigger when `hasSufficientData`; collect `insightState`; render `GeneratedInsightCard` at the placement (Progress: top; Recovery: under the metrics hero).
- **Food (on demand):** a sparkle / "Rest of day?" button near the macro summary calls the VM trigger on tap; the reveal area renders `GeneratedInsightCard`. The button is shown only when there is ≥1 meal logged. If the model is not ready, tapping produces no visible card (consistent with the generation-only rule) — no download UI is surfaced here.

---

## 4. Data assembly (per screen)

### 4.1 Progress → `ProgressInsightContext`
`ProgressViewModel` already computes `weightTrend` / `waistTrend` via its private `trendPerWeek`. Expose the numeric values plus:
- `liftTrendKgPerWeek` = `trendPerWeek(liftValues)`
- `weightPointCount` = `weightValues.size`, `waistPointCount` = `waistValues.size`
- `adherencePercent` = `adherenceLast`
- `rangeDays` = current range

Sufficiency gate (from phase 1): ≥2 weight or waist points.

### 4.2 Recovery → `RecoveryInsightContext`
**Build from the persisted `DailyLog` (`day.dailyLog`), NOT the editable form fields.** The form defaults `energyScore`/`hungerScore`/`sorenessScore` to 5 and holds `sleepHours` as a String; the context's nullable fields must reflect what was actually logged so that `hasSufficientData` and "no data" omission are honest. Map: `sleepHours`, `energyScore`, `hungerScore`, `sorenessScore`, `trained` from the persisted log (null when absent).

Sufficiency gate: at least one of sleep / energy / hunger / soreness logged.

### 4.3 Food → `RestOfDayInsightContext`
From `FoodLogViewModel` state: `caloriesConsumed` = `totals.calories`, `proteinConsumedG` = `totals.proteinG`; `targetCalories`, `proteinTargetG`, `calorieZoneLowerBound`, `calorieZoneUpperBound` from the plan target; `mealsLoggedCount` = number of logged meal entries today.

Sufficiency gate: ≥1 meal logged.

---

## 5. Build sequence & verification

**Order: Progress → Recovery → Food**, one spec, implemented sequentially with a device checkpoint.

1. **Progress (full vertical slice):** VM context-building + `GeneratedInsightCard` + wiring + top-of-screen card. **Checkpoint:** this is the first on-device sighting of real model output — validate the Gemma response and tune the Progress prompt (in phase-1's `InsightPromptBuilder`) if needed before continuing.
2. **Recovery:** reuse `GeneratedInsightCard`; build context from the persisted log; card under the metrics hero. Device-validate.
3. **Food:** reuse `GeneratedInsightCard` via the tap-to-reveal button. Device-validate.

### Verification
- **VM context-building → unit tests:** given a UI-state / persisted-log fixture, assert the built context's fields, `dedupKey`, and `hasSufficientData`. Same rigor as phase 1.
- **`GeneratedInsightCard` → Compose previews** for Generating / Ready / Error (and confirm nothing renders for a lifecycle state).
- **Real model output → on-device manual validation**, per screen, at each checkpoint. This is the prompt-tuning gate deferred from phase 1.

---

## 6. Out of scope

- Any change to the weekly dashboard card, the model download/verify flow, or the More screen.
- New AI insight types beyond the three from phase 1.
- Changing the phase-1 contexts/coordinator (only the prompt *text* may be tuned at a checkpoint if a real response demands it).
- The AI-enabled toggle (the existing global gate already covers the new cards).

---

## 7. Acceptance criteria

- `GeneratedInsightCard` exists, renders only the three generation states, nothing otherwise; has previews.
- All three VMs inject the shared coordinator, expose `generationState(kind)`, and build their context from existing state (Recovery from the persisted log). Context-building is unit-tested.
- Progress shows a top card (auto), Recovery shows a card under the metrics hero (auto), Food shows a tap-to-reveal insight — each appearing only when generating/ready, hidden otherwise.
- No duplicate model-download UI on the three screens.
- `:app:testDebugUnitTest` green (existing + new VM context tests); each screen device-validated at its checkpoint.
