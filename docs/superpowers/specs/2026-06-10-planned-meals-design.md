# Planned Meals — Design

**Date:** 2026-06-10
**Status:** Implemented

## Problem

Users want to plan meals ahead of time — "save a meal for a specific day" — and
postpone a logged meal to a future day. Today the food log can only target the last
6 days through today (`FoodLogViewModel.selectDate` clamps to `today.minusDays(6)..today`),
and every `meal_entries` row is implicitly "eaten."

## Core concept: meal *status*

A meal entry gains a single boolean dimension, `planned`:

| Status | Meaning | Counts toward eaten totals / adherence / trend? |
|---|---|---|
| `planned = false` (default) | Eaten — a record of reality | **Yes** |
| `planned = true` | An intention placed on a (usually future) day | **No** — until confirmed |

This is the whole feature. Everything else is a consequence.

### Lifecycle

```
   add on future day                confirm ("ate it")
 ─────────────────────►  PLANNED  ──────────────────────►  EATEN
                            │  ▲                              │
              move day      │  └──────── move to future ──────┘
                            ▼
                     (still PLANNED)
```

- **Create:** adding food while a **future** day is selected creates `planned = true`
  entries. Today/past stays `planned = false` (the normal flow, unchanged).
- **Confirm:** a planned entry flips to eaten via a ✓ on the entry, or "Confirm all"
  on the day.
- **Move / postpone:** an entry's date can change; `planned` is recomputed from the new
  date (`date > today ⇒ planned`).
- A meal planned for tomorrow needs **no background job** — when tomorrow becomes today
  it simply appears on the day as an unconfirmed entry (pure date math).

## Integrity rule (the reason the flag must exist)

`planned` entries are excluded from everything that represents reality:

- Day **eaten totals** / remaining calories (`DayLog.totals`).
- **Adherence** and the adjustment engine's usable-days count (`DashboardViewModel`).
- The **week calorie strip** and Progress charts.
- The coach's `get_today_summary` / `get_weekly_trends` numbers.

The dashboard already bounds meals to `…today`, so future plans are mostly excluded
there for free; the explicit filter handles plans placed on today (later today) or
unconfirmed plans that have aged into the past.

`DayLog` exposes the split:

```kotlin
data class DayLog(
    …,
    val totals: MacroTotals,        // eaten only
    val plannedTotals: MacroTotals, // planned only (new)
)
```

## Data model

- `MealEntryEntity` + `val planned: Boolean = false`.
- `MealEntryInput` + `val planned: Boolean = false`.
- Room schema **7 → 8**, `MIGRATION_7_8`:
  `ALTER TABLE meal_entries ADD COLUMN planned INTEGER NOT NULL DEFAULT 0`.
  Backward-safe: every existing row becomes "eaten."

New repository operations (DAO-backed):

| Method | Effect |
|---|---|
| `setMealPlanned(id, planned)` | flip a single entry's status (confirm = `false`) |
| `confirmPlannedForDate(date)` | "Confirm all" — set all planned rows on a day to eaten |
| `moveMealToDate(id, date, planned)` | postpone/move; caller passes recomputed `planned` |

## UX (Food Log screen)

Chosen to fit the existing screen rather than add a new one:

- **Date navigation:** `‹ date ›` chevrons in the header; `selectDate` clamp widened to
  `today.minusDays(30)..today.plusDays(30)`. A "PLANNED" pill shows for future days.
  The week strip stays a recent-7-day glance.
- **Nutrition strip:** eaten calories stay the headline; when `plannedTotals > 0` a
  ghost segment extends the calorie bar and a "+N planned" caption appears.
- **Entry rows:** planned entries render dimmed with a "Planned" chip and a green ✓
  (confirm) button in place of the ✎ (edit) button; delete is unchanged. A "next day"
  ⤍ button postpones any entry.
- **Reconcile banner:** opening a **past** day that still has planned (unconfirmed)
  entries shows "You planned N meals here — mark as eaten?" with **Confirm all**. Until
  reconciled they stay excluded from totals, so an unconfirmed past plan honestly looks
  like an unlogged day.

**Rule:** future date ⇒ planned; today/past ⇒ eaten. No extra toggle in v1 (planning
something for *later today* is deferred).

## Coach

`log_meal` gains an optional `date` arg. A future date plans the meal instead of logging
it eaten (`planned = date > today`); the confirmation sheet says "plan for <date>."

## Out of scope (v1)

Notification reminders, recurring/templated plans, "copy a day's plan," and explicit
plan-for-later-today.
