# Initial Plan Generator — Design

**Date:** 2026-06-13
**Status:** Approved (pending implementation plan)

## Summary

Add a one-time "generate my diet plan" feature: from the user's profile and
current bodyweight, compute an initial calorie target and macro split using a
TDEE calculation, and write it into `PlanPreferences`. This replaces the
hardcoded default targets (2550 kcal / 165P / 320C / 68F) with numbers derived
from the user's own data.

The ongoing weekly adjustment loop **already exists** (`domain/adjustment/AdjustmentEngine`,
which recommends ±calorie changes from weight/waist/performance trends, plus the
AI coach's `update_calorie_target` tool). This feature only fills the gap for the
**initial** plan. The adjustment engine is not modified.

## Goals

- Generate calories + macros from profile data on demand.
- Be transparent: show the user how the numbers were derived (BMR → TDEE →
  goal-adjusted calories → macros).
- Nothing is persisted without explicit user confirmation.

## Non-Goals (YAGNI)

- No forced onboarding wizard — generation is an on-demand action.
- No new persisted bodyweight field — weight is read from the daily log.
- No changes to `AdjustmentEngine` or the weekly loop.
- No body-fat % collection (rules out Katch-McArdle).

## Architecture

A new **pure-Kotlin** domain unit `domain/plan/`, sibling to `adjustment/` and
`trend/`, with **no Android imports** (consistent with the rest of `domain/`).

- `PlanCalculator.kt` — pure functions implementing the full formula.
- `PlanCalculatorModels.kt` — `PlanCalculatorInput` (input) and `GeneratedPlan`
  (output, including intermediate values for the preview).

`PlanCalculator` produces numbers only. Writing them into `PlanPreferences`
happens in the ViewModel/UI layer. The calculator never touches
`AdjustmentEngine`.

## The Calculation

**Input (`PlanCalculatorInput`):** `heightCm`, `ageYears`, `sex`, `activityLevel`,
`goal`, `weightKg`.

1. **BMR — Mifflin-St Jeor:**
   `10·kg + 6.25·cm − 5·age + (sex offset)`
   where the sex offset is `+5` for male, `−161` for female.

2. **TDEE:** `BMR × activityFactor`
   - `SEDENTARY` → 1.2
   - `LIGHTLY_ACTIVE` → 1.375
   - `MODERATELY_ACTIVE` → 1.55
   - `VERY_ACTIVE` → 1.725

   Activity factor is derived from `activityLevel` only. `weeklyGymSessions` and
   `plannedTrainingDays` are deliberately **not** used here, to avoid
   double-counting training the user already reflected in their activity level.

3. **Target calories:** `TDEE × (1 + goalDelta)`, goalDelta by goal:

   | Goal | Δ vs TDEE |
   |---|---|
   | `AGGRESSIVE_CUT` | −25% |
   | `MODERATE_CUT` | −18% |
   | `MINI_CUT` | −22% |
   | `RECOMP` | −5% |
   | `LEAN_BULK` | +8% |
   | `MODERATE_BULK` | +12% |
   | `AGGRESSIVE_BULK` | +18% |

   Result rounded to the nearest 10 kcal, then clamped to the range 1000–6000.

4. **Protein:** `round(proteinPerKg × kg)`
   - `2.2 g/kg` for any cut goal (`AGGRESSIVE_CUT`, `MODERATE_CUT`, `MINI_CUT`)
   - `2.0 g/kg` for all other goals

5. **Fat:** `round(0.25 × targetCalories / 9)` — 25% of calories, 9 kcal/g.

6. **Carbs:** remainder.
   `carbsKcal = targetCalories − proteinG·4 − fatG·9`, then
   `carbsG = round(carbsKcal / 4)`, clamped to ≥ 0.

7. **Calorie zone:** `lower = target − 100`, `upper = target + 100`.

**Output (`GeneratedPlan`):** `bmr`, `tdee`, `activityFactor`, `goalDeltaPercent`,
`weightKgUsed`, `targetCalories`, `proteinG`, `carbsG`, `fatG`,
`zoneLower`, `zoneUpper`. The intermediate values exist so the preview dialog can
show its work.

## Data Flow

`PlanViewModel` gains read access to `UserProfilePreferencesStore` (profile) and
`LogRepository` (latest `DailyLog.bodyWeightKg`). New action
`generateFromProfile()`:

1. Read profile + most recent logged bodyweight.
2. Validate required profile fields are present (see Error Handling).
3. Build `PlanCalculatorInput` → `PlanCalculator` → `GeneratedPlan`.
4. Put `GeneratedPlan` into UI state to drive the preview dialog.

On **Apply**, the generated calorie/macro/zone values populate the existing
editable fields on the Plan screen (marked dirty). The user persists with the
existing **Save** button. Only the calorie, macro, and zone fields of
`PlanPreferences` are written — thresholds, cadence, units, and phase dates are
untouched. No new DataStore keys are introduced.

## UI

- **Plan screen:** a "Generate from profile" card/button at the top of the
  existing screen where targets are shown and edited.
- **Preview dialog:** shows the breakdown (BMR → TDEE → goal-adjusted calories →
  P/C/F) along with the weight and assumptions used, with **Apply** / **Cancel**
  actions.

## Error Handling

- **Missing required profile field** (any of: height, age, sex, activity level,
  goal): do not open the preview. Show a message naming what is missing and
  pointing the user to Settings to complete their profile.
- **No logged bodyweight:** the preview dialog asks for weight inline first, then
  computes (the "latest logged weight, fallback to ask" decision).
- **Carbs would be negative** (extreme low-calorie edge case): clamp carbs to 0.

## Testing

- **`PlanCalculator` unit tests** (pure, fast, no Android): BMR for each sex,
  TDEE for each activity level, each goal delta, protein cut-bump vs base, fat at
  25%, carb remainder and ≥0 clamp, rounding to nearest 10, calorie clamp bounds,
  zone bounds.
- **`PlanViewModel` tests:** missing-profile-field path, weight-fallback path,
  Apply populates the editable fields correctly.

## Decisions Log

| Decision | Choice |
|---|---|
| Weight source | Latest logged `bodyWeightKg`; prompt inline if none |
| BMR formula | Mifflin-St Jeor |
| Activity factor | From `activityLevel` only (no training bump) |
| Goal deltas | Percentage of TDEE, per table above |
| Macro split | Protein g/kg (2.2 cut / 2.0 else), fat 25% kcal, carbs remainder |
| Placement | Plan screen, "Generate from profile" button |
| Apply flow | Preview dialog → Apply populates fields → existing Save persists |
| Calorie zone | target ±100 kcal |
