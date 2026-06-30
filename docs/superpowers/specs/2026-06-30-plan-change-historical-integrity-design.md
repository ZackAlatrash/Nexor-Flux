# Plan Change Historical Integrity — Design

**Date:** 2026-06-30
**Branch:** `fix/plan-change-historical-integrity`
**Status:** Approved design, pending implementation plan

## Problem

Plan targets (calories, macros, calorie zone) live only in DataStore (`PlanPreferences`)
as a **single, overwritten value with no history and no timestamps**. Every feature that
judges a day reads the *live* current plan for *all* days. So when the user lowers their
target, the entire logged history is retroactively re-judged against the new (lower) plan:
previously-good days flip to "Over"/"Missed", adherence collapses, and calorie streaks
break retroactively.

A plan change should apply to **today and forward only**. Every day already logged must
keep the plan that was in effect on that day.

### Affected consumers (all read the live plan for historical days)

| Area | Where | Symptom on plan change |
|---|---|---|
| Daily over/under status | `FoodScreen.calorieStatus()` | past days flip to Over/Missed |
| Calorie zone bar / "remaining" | `CalorieZoneBar`, `WeekCalorieStrip` | wrong remaining/over on past days |
| Past-day food log | `FoodLogViewModel` (`target = prefs`) | every past date uses today's plan |
| Adherence chart (28d) | `ProgressViewModel` | historical adherence recomputed wrong |
| Dashboard adherence (14d) | `DashboardViewModel` → adjustment engine | corrupts auto-adjust signal |
| Weekly review input | `AppContainer` | past weeks judged on current plan |
| Calorie streak | `StreakRepository` (zone bounds) | retroactively breaks past streaks |
| Weekly briefings | `WeeklyReviewComputer` (signature uses current target) | spurious regeneration + "new insight" nudges |
| Coach weekly trends | `CoachToolExecutor.getWeeklyTrends` | reports wrong historical adherence |

Correctly scoped already: **today** and **rest-of-day** insight (they *should* use the
current plan).

### Hard constraint (be honest)

True historical plans were never stored — they are already lost. We cannot retroactively
*correct* days already logged. What we can do: **freeze the current plan as the baseline
now**, so every day up to today is anchored to it, and the user's *next* change only
affects today forward. That is the real, achievable fix.

## Decisions (locked)

1. **Storage:** effective-dated plan-history table (single source of truth `planOn(date)`).
2. **Effective date:** a plan change applies from **today forward**; prior days keep their
   historical plan. Today immediately adopts the new plan.
3. **UI scope:** invisible correctness fix — no new screens. Plan history is internal only.

## Section 1 — Data model & resolution semantics

New Room entity `PlanVersionEntity` (table `plan_versions`). Schema bump **v12 → v13**.

| Field | Type | Notes |
|---|---|---|
| `effectiveFrom` | String (ISO date) | **PK**. Version applies to all days ≥ this date |
| `targetCalories` | Int | |
| `targetProteinG` | Int | |
| `targetCarbsG` | Int | |
| `targetFatG` | Int | |
| `calorieZoneLowerBound` | Int | drives status + streak |
| `calorieZoneUpperBound` | Int | drives status + streak |
| `createdAt` | String | audit timestamp (ISO instant) |

Only fields used to *judge a day* live here. Settings (thresholds, `reviewCadenceDays`,
`maintenancePhaseStartDate`, `useMetricUnits`, `healthConnectEnabled`,
`weightTrendThresholdKgPerWeek`, `waistIncreaseThresholdCm`, `adherenceMinimumPercent`)
stay in DataStore — they are current-state, not per-day judgment targets.

**Resolution** (pure helper, `domain/` — no Android imports):

- `planOn(date): PlanTargets` returns the version with the greatest `effectiveFrom ≤ date`.
- If `date` precedes the earliest version, **clamp** to the earliest version.
- If there are no versions at all (should not happen post-backfill), fall back to the
  current DataStore targets.

New domain model `PlanTargets` (pure Kotlin): `calories`, `proteinG`, `carbsG`, `fatG`,
`zoneLowerBound`, `zoneUpperBound`.

Batch helper `targetsByDate(dates): Map<LocalDate, PlanTargets>` — loads all versions once
and resolves in-memory (charts/ranges must not query per-day).

**Invariant:** `planOn(today)` always equals the current DataStore targets. The write path
keeps them in sync, so existing "current plan" reads are unaffected.

## Section 2 — Write path & backfill

**Single choke point:** `PlanRepository.save()` becomes the one place history is recorded.
After persisting DataStore it diffs the *target-relevant* fields (calories, macros, zone
bounds) against the current plan; **only if they changed** it upserts a `plan_versions` row
with `effectiveFrom = today`.

Consequences (all 8 existing writers are covered automatically because they go through
`save()`):

- Plan editor, reset-defaults, onboarding finish, weekly-review "accept recommendation"
  (`AppContainer.saveCalorieTarget`), coach `update_calorie_target`, backup restore, backup
  reset → record a version when targets change.
- **Health Connect toggle does NOT create a version** (targets unchanged) — correct.
- **Multiple changes in one day upsert** today's row (PK = `effectiveFrom = today`) — only
  the final value sticks; past days untouched.

**Backfill (critical detail):** Room migrations cannot read DataStore, so `MIGRATION_12_13`
only *creates the empty table*. A one-time, idempotent `PlanHistoryInitializer` runs at app
start (wired in `AppContainer`): if `plan_versions` is empty, it seeds **one baseline row
from the current DataStore plan** with `effectiveFrom` = an early sentinel
(`PlanHistory.BASELINE_DATE`, e.g. `1970-01-01`). This freezes today's plan as the
historical baseline; every already-logged day resolves to it (no behavioral regression),
and the next change correctly affects today forward only. Idempotent: only seeds when the
table is empty.

**Backup format** gains a `planVersions` array so export/import round-trips history;
`resetEverything()` clears `plan_versions` and re-seeds the default baseline.

## Section 3 — Consumer migration (tighten the whole system)

Every historical/range judgment switches from the live plan to `planOn(date)` /
`targetsByDate(range)`:

- **`FoodLogViewModel`** → combine the viewed date with `observePlanOn(date)` instead of
  live `planRepository.preferences`. `FoodScreen.calorieStatus` then reads the date-correct
  `state.target` — minimal change in `FoodScreen` itself.
- **`TodayViewModel`** → continues to use the current plan (= `planOn(today)`); minimal /
  no change.
- **`WeekCalorieStrip`** → API changes from a scalar target to **per-day targets** (each of
  the 7 bars uses its own day's plan). Caller supplies per-day targets.
- **`AdherenceCalculator`** (central domain refactor) → accepts **per-day targets** instead
  of one global target. This single change fixes `ProgressViewModel` (28d),
  `DashboardViewModel` (14d, which feeds the adjustment engine), and the weekly-review input
  in `AppContainer`. The day model the calculator consumes carries (or is paired with) its
  resolved `PlanTargets`.
- **`StreakRepository`** → per-day zone bounds via `planOn(date)` instead of current
  `prefs.calorieZone*`.
- **`WeeklyReviewComputer`** → judge each week against *that week's* plan; change the cache
  **signature** to incorporate the historical target (e.g. the week-end plan) so changing
  the current plan no longer spuriously regenerates briefings or fires "new insight" nudges.
- **`CoachToolExecutor.getWeeklyTrends`** → per-day plan for the 7-day window.
- **Today / rest-of-day insight** → unchanged (correctly current plan).

## Section 4 — Components

**New:**
- `data/db/entity/PlanVersionEntity.kt`
- `data/db/dao/PlanVersionDao.kt` (upsert, observeAll, getAll, deleteAll, isEmpty/count)
- `domain/plan/PlanTargets.kt` (pure model)
- `domain/plan/PlanHistory.kt` (pure resolver: `planOn`, `resolve(dates)`, `BASELINE_DATE`)
- `data/repository/PlanHistoryInitializer.kt` (idempotent backfill, run from AppContainer)
- `RecompDatabase` `MIGRATION_12_13`

**Modified:**
- `PlanRepository` — record history on `save()` (inject `PlanVersionDao`); expose
  `planOn(date)`, `observePlanOn(date)`, `targetsByDate(range)`.
- `domain/adherence/AdherenceCalculator` — per-day targets.
- `StreakRepository`, `ProgressViewModel`, `DashboardViewModel`, `core/AppContainer`,
  `FoodLogViewModel`, `WeekCalorieStrip` (+ caller), `WeeklyReviewComputer`,
  `CoachToolExecutor`, `BackupRepository` (export/import `plan_versions`).
- `RecompDatabase` (version 13; register entity + DAO + migration).

## Section 5 — Testing (TDD)

- `PlanHistory.planOn` boundary cases: before-earliest clamp, exact boundary date, between
  two versions, same-day upsert resolves to final value, empty-history fallback.
- `PlanRepository.save()`: records a version only when target fields change; skips when only
  Health Connect toggle changes; same-day change upserts (one row for today).
- `PlanHistoryInitializer`: seeds one baseline from DataStore when empty; idempotent on
  re-run; does nothing when table already populated.
- `AdherenceCalculator` with per-day targets (day A under old plan stays adherent after a
  later plan change).
- `StreakRepository` across a mid-history zone change (past in-zone days stay in-zone).
- `WeeklyReviewComputer` signature stability: changing the *current* plan does not change a
  past week's signature.
- Migration 12→13 creates `plan_versions` (Room migration test).
- Backup round-trip includes `planVersions`.

## Build order

1. **Core (sequential, lands first):** entity → DAO → migration (v13) → `PlanTargets` →
   `PlanHistory.planOn` → `PlanRepository` write-path + read helpers → `PlanHistoryInitializer`
   → AppContainer wiring. All with tests.
2. **Consumers (parallel, fan out by area) — depend only on the stable `planOn` interface:**
   - food log + `WeekCalorieStrip`
   - `AdherenceCalculator` + `ProgressViewModel` + `DashboardViewModel` + `AppContainer`
   - `StreakRepository`
   - `WeeklyReviewComputer` + `CoachToolExecutor`
   - `BackupRepository` export/import

## Out of scope (YAGNI)

- Any UI surfacing of plan history (markers, history view).
- User-chosen / backdated effective dates.
- Reconstructing truly-historical (pre-baseline) plans — impossible; data was never stored.
