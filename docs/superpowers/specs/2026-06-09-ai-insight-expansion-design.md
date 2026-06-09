# AI Insight Expansion — Phase 1 (AI Layer) Design

**Date:** 2026-06-09
**Status:** Approved for implementation planning
**Scope:** Phase 1 of a multi-phase feature. This phase builds the AI layer only — correct, tested prompt generation for three new insight types. The on-screen UI cards are a later phase.

---

## 1. Goal

Extend the proven dashboard insight-card pattern ("structured data → natural-language explanation") to three more places in the app, so the on-device Gemma model explains the user's data and gives bounded advice:

| Insight | Screen (phase 2) | What it explains |
|---|---|---|
| **Trend Analysis** | Progress | What the multi-metric trends mean for body recomposition |
| **Recovery Readiness** | Body & Recovery | Training readiness from sleep + subjective scores |
| **Rest-of-Day** | Food | Where today's intake stands and what to prioritize |

This phase delivers the **AI generation layer** for all three: context data classes, prompt builders, the generalized coordinator, and full unit-test coverage. It does **not** build the screen cards or ViewModels.

### Verification method (decided)

**Unit tests + prompt review only.** On-device inference is out of scope for this phase, so model output quality is not measured here. Correctness is guaranteed two ways:
1. **Prompt review** — the prompt design itself (reviewed and approved in brainstorming).
2. **Unit tests** — the existing `InsightPromptBuilderTest` assertion pattern, run in CI via `:app:testDebugUnitTest`.

Real-model output tuning happens in phase 2 when the cards exist and the model can be exercised on a physical device.

---

## 2. Architecture

### Problem with the current design

`GemmaInsightCoordinator` conflates two concerns in a single `_state: StateFlow<AiInsightState>`:
- **Model lifecycle** (`Disabled / ModelMissing / Downloading / ModelVerifying / ModelReady`) — global, shared by every insight.
- **One insight's generation** (`LoadingModel / Generating / Ready / Error`) — currently hardwired to the weekly verdict via `onAiCardVisible(InsightContext)` and a single `lastGeneratedKey`.

Adding three more insights to this single state would be impossible — they each need independent generation state while sharing one model and one lifecycle.

### The split

- **Model lifecycle stays singular and shared.** One source of truth for download/verify/ready. Behaviorally unchanged. (Download, integrity check, storage check, delete, model-variant switching all stay exactly as they are.)
- **Generation becomes per-insight.** Introduce an `InsightKind` enum:
  ```
  WEEKLY_VERDICT | PROGRESS_TREND | RECOVERY_READINESS | REST_OF_DAY
  ```
  > **Implementation note:** `WEEKLY_VERDICT` was intentionally dropped from the `InsightKind` enum — the weekly verdict retains its existing dedicated `state` / `onAiCardVisible` path, so an enum member for it would never be routed. `InsightKind` therefore holds only the three new kinds.

  The coordinator exposes a generation state **per kind** and an `onInsightVisible(kind, context)` entry point. Each kind keeps its own `lastGeneratedKey` for dedup. All generation serializes naturally behind the existing `inferenceLock` (one engine, one inference at a time) — so two insights never run concurrently, but each retains its own latest result.
- **One `InsightPromptBuilder` class, extended.** Add one build method per new insight and **reuse the existing qualitative-label helpers** (`weightLabel`, `waistLabel`, `adherenceLabel`). No churn on the weekly path.

### Why this shape

Each insight is a small, independently-testable unit: a context (data in) → a prompt builder method (pure function) → a generation state (result out). Phase-2 screen ViewModels will consume this by observing `generationState(kind)` and calling `onInsightVisible(kind, context)` — the same contract the dashboard uses today, just keyed by kind.

---

## 3. The three insights

### Shared design rules (all three)

- **Few-shot example** in every prompt (one sample output).
- **"2–3 sentences, plain English"** length instruction.
- **Grounding guard:** "Base everything only on the data given; invent nothing."
- **Data-sufficiency gate:** each context exposes a pure `hasSufficientData: Boolean`. The coordinator suppresses generation when false — mirroring how `WAIT_FOR_DATA` suppresses the weekly card today.

### Qualitative vs. concrete-numbers split (approved)

- **Progress & Recovery → qualitative** ("trending down", "soreness high"). The 2B model reasons better on words, and tests assert raw numbers do not leak — consistent with the weekly verdict.
- **Food → concrete numbers** ("38 g protein to go", "780 kcal of room"). The numbers *are* the action here; stripping them would make the advice useless.

---

### 3.1 Progress → Trend Analysis

- **Context (`ProgressInsightContext`):** range days (7/14/28), weight trend (kg/wk), waist trend (cm/wk), lift e1RM direction, adherence band. All already computed in `ProgressViewModel`.
- **Prompt approach:** qualitative trend directions via existing label helpers; framed as *interpreting what the trend combination means for recomposition*.
- **Hard rule:** **must not prescribe calorie changes.** Calorie verdicts are owned by the deterministic `AdjustmentEngine` and shown on the dashboard. This insight interprets the body-composition trend only, preventing the AI from contradicting the engine.
- **Example output:**
  > "Over the last 28 days your weight held steady while your waist trended down and your lifts kept climbing — that's recomposition, not a stall. Your logging's been consistent, so the trend is trustworthy. Stay the course and let another two weeks confirm it."
- **Sufficiency gate:** ≥2 weight or waist data points within the range.

### 3.2 Recovery → Readiness

- **Context (`RecoveryInsightContext`):** sleep hours, energy score (1–10), hunger score (1–10), soreness score (1–10), trained-today flag. From `DailyLogEntity` for today.
- **Prompt approach:** score → band helpers (energy/hunger/soreness: low / moderate / high; sleep: poor / ok / good). Qualitative only.
- **Hard rule:** **training-readiness language only — no medical claims.**
- **Example output:**
  > "Two short nights with soreness running high and energy low suggests recovery hasn't caught up to your training. Prioritize sleep tonight and keep portions adequate. If soreness holds tomorrow, an easier session would help you bounce back."
- **Sufficiency gate:** at least one of sleep / energy / hunger / soreness logged today.

### 3.3 Food → Rest-of-Day

- **Context (`RestOfDayInsightContext`):** calories consumed vs. target (+ zone bounds), protein consumed vs. target, meals-logged count. From `TodayUiState` (`totals`, `target`).
- **Prompt approach:** concrete numbers — frame the calorie room and protein gap, then a bounded suggestion.
- **Hard rule:** **invents no specific foods or macros** beyond what is logged; frames the gap, does not fabricate a meal plan.
- **Example output:**
  > "You're at 1,420 of your 2,200-calorie target with 38 g of protein still to go. Room for a solid dinner — make protein the centerpiece to close that gap. Tracking well for the day."
- **Sufficiency gate:** ≥1 meal logged.

---

## 4. Testing strategy

Each new prompt builder method gets a dedicated test class mirroring `InsightPromptBuilderTest`:

- Correct qualitative labels / concrete numbers appear for representative inputs.
- Raw enum names never leak (Progress/Recovery).
- The per-insight hard rule is present in the prompt:
  - Progress: no-calorie-prescription instruction.
  - Recovery: no-medical-advice instruction.
  - Food: no-invented-foods instruction.
- Few-shot example present; "2–3 sentences" instruction present; grounding guard present.
- `hasSufficientData` returns correct boolean across boundary inputs per context.

All tests run in CI: `./gradlew :app:testDebugUnitTest`. No device or model file required.

---

## 5. Out of scope (this phase)

- Screen cards on Progress / Recovery / Food.
- Per-screen ViewModels wiring `onInsightVisible` / `generationState`.
- On-device model output tuning and quality measurement.
- Any change to the weekly verdict behavior, download flow, or model-variant logic.

---

## 6. Acceptance criteria

- `InsightKind` introduced; coordinator exposes per-kind generation state + `onInsightVisible(kind, context)`; weekly verdict still works unchanged.
- Three context classes with tested `hasSufficientData`.
- Three prompt builder methods on `InsightPromptBuilder`, reusing existing label helpers.
- Full unit-test coverage per the strategy above; `:app:testDebugUnitTest` green.
- No regression in existing AI tests (`InsightPromptBuilderTest`, `AiInsightStateTest`, coach tests).
