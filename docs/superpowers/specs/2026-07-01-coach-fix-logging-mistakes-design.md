# Coach: fix logging mistakes (delete/edit meals) — design

**Date:** 2026-07-01
**Status:** Approved (brainstorm)
**Branch:** `redesign/ai-coaching`
**Part of:** a 3-feature set (this = #1). The others — coach memory/preferences (#2) and meal
suggestions (#3) — get their own spec → plan → build cycles.

## Goal

Let the AI coach **remove or change a meal the user already logged**, from chat, by name — so
mislogged food can be fixed without opening the app.

## Scope

**In scope**
- `delete_meal` — remove a logged meal entry.
- `edit_meal` — change a logged meal's amount and/or macros.
- Name-based identification with disambiguation when several entries match.
- Optional `date` (defaults to today), like `log_meal`.

**Out of scope (rationale)**
- Metrics (weight/waist/sleep/etc.) — already fixable by re-logging: `log_metric` overwrites the
  day's value. No metric-edit/clear tool.
- Bulk operations, undo history, editing meal *type/slot* (amount + macros only for MVP).

## Existing code this builds on (no schema change)

- `LogRepository.getDay(date): DayLog` → `meals: List<MealEntryEntity>` (each has `id`, `name`,
  `mealType`, `calories`, `proteinG/carbsG/fatG`, `amountGrams`, `basePer100{Calories,ProteinG,
  CarbsG,FatG}`, `planned`).
- `LogRepository.deleteMeal(id)` and `LogRepository.updateMealEntry(entry)` (the latter clamps
  negatives).
- `ai/CoachToolExecutor` dispatch + the existing `esc()` and word-scoring helpers
  (`scoredExerciseMatches`/`scoredFoodMatches`) to mirror for entry name-matching.
- `ai/CoachTools.kt` (`COACH_TOOL_SCHEMAS`/`ROUTINE_TOOL_SCHEMAS`-style lists, `COACH_WRITE_TOOLS`)
  and `ai/CloudCoachCoordinator` (`pendingActionDisplayText`, `toolStatusText`).

## Tool surface (2 new write tools)

Both go in the cloud tool list and in `COACH_WRITE_TOOLS` (confirmation required).

- **`delete_meal(name: String, date?: String)`** → `{success, deleted, calories}` or `{error}` or
  `{needs_disambiguation, matches:[...]}`.
- **`edit_meal(name: String, grams?: Number, calories?: Int, protein_g?: Number, carbs_g?: Number,
  fat_g?: Number, date?: String)`** → `{success, updated, calories}` or `{error}` or
  `{needs_disambiguation, matches:[...]}`.

## Execution

1. Load the target day's entries: `logRepository.getDay(date ?: today).meals`.
2. **Resolve `name`** against those entries with a word-based scorer (same shape as
   `scoredFoodMatches`: exact › startsWith › contains › all-words). Filter to confident matches
   (score ≥ all-words).
   - 0 matches → `{"error":"no logged meal matching '<name>' on <date>"}`.
   - >1 match → `{"needs_disambiguation":true,"matches":[{"name","meal_type","calories","grams"}...]}`
     so the coach asks which. Do NOT act.
   - exactly 1 → proceed.
3. **delete_meal:** `logRepository.deleteMeal(entry.id)` → `{"success":true,"deleted":"<name>","calories":<n>}`.
4. **edit_meal:** build the updated `MealEntryEntity` and call `logRepository.updateMealEntry(...)`:
   - **Rescale by grams:** if `grams` provided:
     - if the entry has `basePer100*` (a library food): new macros = `basePer100X * grams / 100`
       (rounded like the food log), set `amountGrams = grams`.
     - else if `amountGrams` present: proportional rescale `newX = oldX * grams / amountGrams`,
       set `amountGrams = grams`.
     - else (no gram basis): ignore `grams` and require explicit macros, or return an error asking
       for calories/macros.
   - **Explicit macro args override** any rescale (set exactly what's given; leave the rest).
   - Return `{"success":true,"updated":"<name>","calories":<n>}`.

Both tools return a JSON string (success / error / disambiguation), matching the existing
`CoachToolExecutor` contract.

## Confirmation UX

`delete_meal` + `edit_meal` added to `COACH_WRITE_TOOLS`; `pendingActionDisplayText` gets branches:
- delete → *"Delete '2 slices pizza' (520 kcal) from today's log"*
- edit → *"Change 'chicken breast' to 200 g (330 kcal)"* (summarize the fields being changed)

`toolStatusText`: `delete_meal` → "Removing meal…", `edit_meal` → "Updating meal…".

## System-prompt routing (add to `CoachToolsAdapter.COACH_PROMPT_GUIDELINES`)

- "To fix a logged meal, use delete_meal or edit_meal, identifying it by name; pass a past `date`
  if it wasn't today. If the tool returns `needs_disambiguation`, ask the user which one."

## Architecture / boundary

- All logic in `ai/CoachToolExecutor` (already depends on `LogRepository`); schemas in
  `ai/CoachTools.kt`. No new dependency into `ai/local` — `AiCoachBoundaryTest` stays green.
- Deterministic: the executor computes rescaled macros; the LLM only names the meal and the target.

## Testing (`CoachToolExecutorMealEditTest.kt`, fake `LogRepository`)

- delete resolves a name to the entry and calls `deleteMeal(id)`; returns success with calories.
- delete with two matching entries returns `needs_disambiguation` with both, and does NOT delete.
- delete with no match returns an error, no deletion.
- edit by grams on a library-food entry rescales via `basePer100*` (assert new calories/macros).
- edit by grams on a non-library entry rescales proportionally from `amountGrams`.
- edit with explicit macro args overrides/sets those values.
- edit resolves against a past `date` when provided.
- Confirmation display-text for both tools (in the `CloudCoachCoordinator` test).

## Rollout

Subagent-driven TDD in the MAIN checkout on `redesign/ai-coaching` (worktree isolation branches
from the wrong base in this repo — run in main tree with a base sanity check). Review pass, then
on-device verification by the user (log a meal, then "delete it" / "change it to 200g").
