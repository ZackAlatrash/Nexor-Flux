# AI Insight Cards — De-bucket the Prompt Signals (Option A)

**Date:** 2026-06-15
**Status:** Approved design, pending implementation plan
**Scope:** Single-file change to `ai/InsightPromptBuilder.kt` (plus its unit tests)

## Problem

The on-device AI insight cards feel generic — they "read out the information and give obvious
advice." Root cause is structural, not model size: `InsightPromptBuilder` converts every numeric
signal into a coarse word **before** it reaches the model (e.g. weight `-0.35 kg/wk` → `"trending
down"`, adherence `92%` → `"high"`, sleep `5.5h` → `"poor"`). The model can only re-state the
adjective it was handed, so output is paraphrase, not analysis.

Critically, **every context object already carries the raw numbers** — `ProgressInsightContext`
holds `weightTrendKgPerWeek: Double`, `RecoveryInsightContext` holds `sleepHours`/scores, the weekly
`InsightContext` holds the raw `AdjustmentInput`. The precision is thrown away at the last step.

## Goal

Let the model see and cite the real figures it is already given, so insights become specific
("Weight is flat at -0.05 kg/wk while waist is down 0.3 cm/wk") instead of vague ("weight is
trending up"). Keep the deterministic labels as a guardrail and keep the 1–2 sentence ceiling.

## Non-goals (explicitly out of scope)

- **No new data/signals.** No per-day streaks ("protein under target 5 of 7 days"), no
  `loggingConsistency`/`inZoneDays` plumbing. That is Option B.
- **No unit conversion.** Numbers stay in **kg/cm**, matching every other screen in the app today.
  The `useMetricUnits` toggle is currently dormant app-wide (no kg↔lb/cm↔in conversion code exists,
  all weight UI hardcodes "kg"); making the app respect imperial units is a separate future feature.
- **No engine/sampling changes.** No temperature, no `GenerationConfig`, no touching the "rich" mode.
- **No UI changes.** Cards, states, lengths, triggers unchanged.

## Affected files

- `app/src/main/java/com/zack/recomptracker/ai/InsightPromptBuilder.kt` — the only production change.
- `app/src/test/.../InsightPromptBuilderTest.kt` (and any sibling prompt tests) — updated expectations.

No changes to context classes, mappers, ViewModels, coordinators, DAOs, or DataStore.

## Design

### 1. Signal lines gain their real figure

Each bucketed signal line becomes `<number> <unit> (<existing label>)`, with an explicit sign so
direction is unambiguous. The existing label functions (`weightLabel`, `waistLabel`,
`adherenceLabel`, `liftTrendLabel`, `sleepLabel`, `scoreLabel`, `performanceLabel`, `recoveryLabel`)
are **kept** and reused as the parenthetical hint.

| Card | Before | After |
|---|---|---|
| Weekly | `Weight: trending down` | `Weight: -0.35 kg/wk (down)` |
| Weekly | `Waist: trending down` | `Waist: -0.3 cm/wk (down)` |
| Weekly | `Adherence: high` | `Adherence: 92% (high)` |
| Progress | `Weight: stable` | `Weight: -0.05 kg/wk (stable)` |
| Progress | `Lifts: improving` | `Lifts: +0.4 kg/wk e1RM (improving)` |
| Recovery | `Sleep: poor` | `Sleep: 5.5 h (poor)` |
| Recovery | `Energy: low` | `Energy: 3/10 (low)` |
| Rest of day | *(already numeric)* | unchanged wording |

**Stays a label (no number available):** the weekly card's `Performance` and `Recovery` lines —
they arrive as enums (`PerformanceTrend`, `RecoveryTrend`), not numbers, so there is nothing to
de-bucket. **Null trends** keep `"no data"` exactly as today (Progress weight/waist/lift/adherence
can be null; Recovery sleep/energy/hunger/soreness can be null and their lines are omitted as today).

### 2. Instruction-block edits (each card's prompt)

1. Add: *"Lead with the most decisive number from the data below."*
2. Add hallucination guard: *"Use only the figures given. Do not calculate or invent new numbers."*
   (Essential now that the model sees raw numbers — a 2B model must not do its own arithmetic.)
3. Keep the existing hard "exactly 1–2 short sentences … no preamble or filler" limit.

The existing per-card framing constraints stay (Progress: "do NOT recommend changing calories";
Recovery: "no medical advice"; Rest of day: "do not invent specific foods/brands").

### 3. Few-shot examples upgraded to cite numbers

Replace the vague example outputs so the 2B model anchors on specificity rather than the bare
"X, Y, Z — verdict" cadence. Examples:

- Weekly: `"Weight is up 0.4 kg/wk while waist held flat and lifts climbed — likely lean mass, hold calories."`
- Progress: `"Weight is flat at -0.05 kg/wk while waist is down 0.3 cm/wk and lifts are up 0.4 kg/wk — textbook recomposition, stay the course."`
- Recovery: `"On 5.5 h sleep with soreness at 8/10, recovery is behind — keep today light and prioritize sleep tonight."`
- Rest of day: keep the existing numeric example (`"You're at 1,420 of 2,200 kcal with 38 g protein left…"`).

### 4. Formatting helpers (private, same file)

Signed, rounded formatting so figures read cleanly and direction is explicit:

- Weight trend: 2 decimals, signed — `+0.20`, `-0.35` kg/wk
- Waist trend: 1 decimal, signed — `-0.3` cm/wk
- Lift trend: 1 decimal, signed — `+0.4` kg/wk e1RM
- Adherence: integer percent — `92%`
- Sleep: 1 decimal — `5.5 h`
- Scores (energy/hunger/soreness): `N/10` integer

A small `signed(value, decimals)` helper handles the sign + rounding; existing label functions
provide the parenthetical.

## Testing

`InsightPromptBuilderTest.kt` already asserts prompt construction (not model output), so the change
is fully unit-testable without the model. Update/add cases for:

- Each card emits `<number> <unit> (<label>)` for numeric signals, with correct sign and rounding.
- Null/`no data` paths unchanged (Progress null trends, Recovery omitted lines, weekly enum
  Performance/Recovery still render as labels).
- New instruction lines present ("Lead with the most decisive number", "Use only the figures
  given…").
- `limitToSentences` behavior unchanged.

## Risks & mitigations

- **2B model does bad arithmetic on the new numbers** → mitigated by the explicit "do not calculate"
  instruction and by every number being pre-computed. The deterministic verdict/labels remain the
  quality floor, so output cannot drift below today's.
- **Few-shot examples leak specific numbers into output** → examples use plainly illustrative values
  and each prompt already instructs "base everything only on the signals/numbers below."

## Out-of-scope follow-ups (noted, not built here)

- Option B: computed insight layer (streaks, weekend divergence, derailment-day finder,
  back-calculated expenditure, goal projection).
- App-wide imperial unit support (conversion utilities + wiring `useMetricUnits` through all weight
  surfaces).
- Option C: actionable cards (Adjust-target button, "Ask the coach about this" bridge, severity
  color on the rim).
