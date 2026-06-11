# Adherence Redesign — Design

**Date:** 2026-06-11
**Branch:** `adherence-redesign`

## Problem

"Adherence" is defined three inconsistent ways across the codebase, so the dashboard,
the progress chart, and the AI coach can each report a different number for the same week:

1. **`AdherenceCalculator.calculate()`** (Dashboard + Adjustment Engine gate) — a day counts
   only if calories land within a binary ±10% band; score = `adherentDays / expectedDays`.
   - Loses information: 2810 kcal (just over the band) scores identically to 4000 kcal.
   - Conflates "didn't log" with "logged but off-target" — both score 0.
2. **`AdherenceCalculator.dailyAdherencePercent()`** (Progress chart) — a *graded* linear score
   `100 − |cals−target|/target`. A different shape from #1.
3. **`CoachToolExecutor.getWeeklyTrends()`** — `loggedDays / 7 × 100`, which **ignores the target
   entirely**. A day at 4000 kcal against a 2550 target scores as 100% "adherent". This actively
   misleads the coach.

The Adjustment Engine already has a *separate* logging gate (`daysLogged < 14` →
`INSUFFICIENT_DATA`), and the `LOW_ADHERENCE` reason description even mislabels itself as
"logging consistency." So the intent to separate the two signals is half-built.

## Goals (this change)

- **#1 Unify** every consumer onto one adherence definition; delete the ad-hoc `loggedDays/7`.
- **#2 Separate** "calorie adherence" (closeness to target) from "logging consistency" (how often
  you tracked) as two distinct signals.
- **#3 Grade** the daily score instead of a binary band, so 2810 ≠ 4000.

**Out of scope (#4, explicitly declined):** asymmetric / goal-aware tolerance. Keep it simple.

## Design

### One per-day primitive
`dailyAdherencePercent(calories, targetCalories)` is the single source of truth for a day's score:
linear `100 − |calories − target| / target × 100`, clamped to `0..100`, and `0.0` when nothing is
logged (`calories <= 0`). This already exists and is unchanged.

### `AdherenceCalculator` API (rewritten)

```kotlin
data class NutritionDay(val date: LocalDate, val calories: Int)

class AdherenceCalculator {
    // Per-day graded closeness, 0 if not logged. (unchanged)
    fun dailyAdherencePercent(calories: Int, targetCalories: Int): Double

    // ADHERENCE QUALITY: average graded daily score across LOGGED days only.
    // Days with calories <= 0 are excluded from both numerator and denominator.
    // Returns 0.0 if no logged days or target <= 0.
    fun calculate(days: List<NutritionDay>, targetCalories: Int): Double

    // LOGGING CONSISTENCY: fraction of expected days that have any intake logged.
    // Returns 0.0 if expectedDays <= 0.
    fun loggingConsistency(days: List<NutritionDay>, expectedDays: Int): Double
}
```

- `calculate()` **drops** the old `expectedDays` and `tolerancePercent` parameters. Averaging is
  over **logged days only** (decided fork): adherence answers "how close to target *when you
  tracked*", fully decoupled from "how often you tracked."
- `loggingConsistency()` is the separate signal for #2. `distinctBy { date }` guards against
  duplicate-date rows in both functions.

### Consumer changes

| Consumer | Before | After |
|---|---|---|
| `DashboardViewModel` | `calculate(nutritionDays, target, expectedDays = 14)` | `calculate(nutritionDays, target)` (quality). `daysLogged` for the engine stays as the existing separate count. |
| `ProgressViewModel` | `dailyAdherencePercent` per day (chart) | unchanged — already uses the primitive. |
| `CoachToolExecutor.getWeeklyTrends()` | `adherence_percent = loggedDays/7×100` | `adherence_percent = calculate(7-day NutritionDays, target)` (graded quality) **plus** a separate `days_logged` field. |
| `AdjustmentEngine` | gate `adherencePercent < 85` → `LOW_ADHERENCE`, summary/description say "logging consistency" | gate kept; **threshold default lowered 85 → 80**; summary + `LOW_ADHERENCE` description reworded to "too far from target too often" (the `daysLogged < 14` gate remains the logging gate). |

### Threshold

`AdjustmentThresholds.adherenceMinimumPercent` and `PlanPreferences.adherenceMinimumPercent`
default **80.0** (was 85.0). On the graded scale, 80% means avg daily deviation ~20% — a slightly
looser gate that avoids over-blocking adjustments now that the metric is an average rather than a
band-count. Still user-tunable in Plan settings.

### Untouched

- `InsightPromptBuilder.adherenceLabel()` qualitative buckets (≥90 high / ≥75 moderate / else low)
  still read sensibly on the graded scale — unchanged.
- `ProgressViewModel` chart `trendIsGood >= 85f` — unchanged (display-only).
- `docs/ai-coach.md` `get_weekly_trends` table row updated to document the new fields.

## Testing

- **`AdherenceCalculatorTest`** rewritten for the new API:
  - `calculate` averages graded scores over logged days only; unlogged days excluded from the
    denominator; all-on-target → ~100; empty / no-logged → 0; target ≤ 0 → 0; duplicate dates
    de-duped.
  - `loggingConsistency` = loggedDays/expectedDays; missing days lower it; expectedDays ≤ 0 → 0.
  - `dailyAdherencePercent` graded shape (exact, off-by-X%, not-logged → 0) — keep/extend.
- **`AdjustmentEngineTest`** — `LOW_ADHERENCE` fires below the new 80 default; updated summary.
- **`CoachToolExecutorTest`** — `get_weekly_trends` returns graded `adherence_percent` and the new
  `days_logged`; a logged-but-over-target day no longer yields 100%.

## Risks

- Behavior change: dashboard/coach adherence numbers shift for existing users (graded vs band).
  Acceptable — the old numbers were inconsistent and the coach number was misleading.
- The 85→80 default only affects users on the default; anyone who customized the threshold keeps
  their value.
