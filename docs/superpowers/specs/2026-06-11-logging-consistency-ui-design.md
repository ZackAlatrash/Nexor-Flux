# Logging Consistency UI — Design

**Date:** 2026-06-11
**Branch:** `adherence-redesign`
**Builds on:** `docs/superpowers/specs/2026-06-11-adherence-redesign-design.md`

## Problem

The adherence redesign produced two distinct signals — **adherence quality** (how close to
target on the days you logged) and **logging consistency** (how often you logged) — but only the
quality signal is shown to the user. The logging signal exists in the domain
(`AdherenceCalculator.loggingConsistency`) and in the coach tool (`days_logged`), yet no screen
surfaces it. Worse, the Dashboard already shows a *loose* "Days logged" count
(`loggedDates.size`, no fixed window, counts any entry incl. body-only days), which neither
matches the adherence window nor reflects the new metric.

## Goal

Surface logging consistency on the Dashboard and the Progress screen, paired with adherence, and
reconcile the existing loose count to the real windowed metric.

## The metric

A day is "logged" iff food was logged that day (`calories > 0`) — the same notion the adherence
window already uses via its `nutritionDays` list. Logging consistency is measured over the **last
14 days** on the Dashboard (matching adherence) and over the selected range (7/14/28) on Progress.

- Domain: `AdherenceCalculator.loggingConsistency(days, expectedDays)` — already implemented and
  unit-tested. Returns a percentage.
- The per-day food-logged data is already computed in both ViewModels (`nutritionDays` /
  `calValues`), so no new data plumbing is required.

## Dashboard changes

**`DashboardViewModel` / `DashboardUiState`**
- Add `val loggedDaysInWindow: Int = 0` to `DashboardUiState` = `nutritionDays.count { it.calories > 0 }` (range 0–14).
- Populate it where the state is built (the same block that computes `adherence` from
  `nutritionDays`, ~line 183/235).
- The existing `daysLogged` field (computed as `loggedDates.size`, line 236) is **replaced** in its
  UI role by `loggedDaysInWindow`. If `daysLogged` has no remaining consumer after the screen edits
  below, remove the field; if it is still read elsewhere, leave it but stop using it for display.

**`DashboardScreen`**
- Line ~485 (`ChartStat` "Days logged", value `"${state.daysLogged}"`) → label stays "Days logged",
  value becomes `"${state.loggedDaysInWindow} / 14"`.
- Line ~702 (`StatRow("Logged days", state.daysLogged.toString())`) → `StatRow("Logged days", "${state.loggedDaysInWindow} / 14")`.
- No new components; reuse `ChartStat` and `StatRow` as-is.

**Untouched (intentional):** `AdjustmentInput.daysLogged` (line ~199) remains the engine's
"14 usable days" data-sufficiency gate — a separate concern from the user-facing logging metric.
Add a one-line comment at that site noting the two counts are intentionally different (engine gate
counts any logged day incl. body-only; the UI metric counts food-logged days).

## Progress changes

**`ProgressViewModel` / `ProgressUiState`**
- Add `val logging: ChartSeries = ChartSeries("Logging", "%", emptyList())` to `ProgressUiState`.
- Build it from the already-computed `calValues`:
  - `values = calValues.map { if (it > 0f) 100f else 0f }` (per-day logged/not-logged over the range).
  - `pct = values.count { it > 0f }.toFloat() / values.size * 100f` (guard `values.isNotEmpty()`).
    This equals `loggingConsistency` by definition; computing it inline avoids rebuilding a
    `NutritionDay` list when `calValues` already encodes logged/not-logged.
  - `currentValue = pct`; `trendLabel = "${pct.roundToInt()}%"`; `trendIsGood = pct >= 80f`;
    `unit = "%"`.

**`ProgressScreen`**
- Move the adherence chart out of the *Performance* section and into *Nutrition*, paired with the
  new logging chart: replace the Nutrition tail with an added
  `item { MiniChartPair(state.adherence, state.logging) }` (placed after the fat chart).
- In *Performance*, the existing `MiniChartPair(state.adherence, state.lifts)` becomes a single
  lifts card: `item { ShortChartCard(state.lifts) }` (reuse the existing `ShortChartCard` used for
  fat). Net layout: Nutrition gains an adherence+logging pair; Performance shows lifts alone.

## Testing

- `loggingConsistency` domain behavior: already covered by `AdherenceCalculatorTest`.
- **Dashboard:** extend the existing `DashboardViewModel` test harness
  (`DashboardViewModelMessagesTest.kt` shows the construction pattern) with a test asserting
  `loggedDaysInWindow` counts only food-logged (`calories > 0`) days within the last 14, excluding
  body-only days and days outside the window.
- **Progress:** the logging-series mapping (`cal > 0 → 100f`) is trivial and the percentage derives
  from already-tested logic; covered by `compileDebugKotlin` + the domain test. No new
  ProgressViewModel harness is introduced for this (documented decision, not an omission).

## Risks

- Semantic change to the Dashboard "Days logged" number: it now means *food-logged days over the
  last 14* (capped at 14) rather than an unbounded any-entry count. This is the intended
  reconciliation and aligns the figure with adherence.
- Progress layout reshuffle moves adherence from Performance to Nutrition; purely presentational.
