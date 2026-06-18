# Workout Readability + Summary Muscle Group — Design

**Date:** 2026-06-18
**Branch:** `feat/workout-tracking`
**Status:** Approved

## Goals

1. **Active-workout readability** — too much of the set grid is rendered in
   `textMuted` (dark theme ≈ 28% white), which is hard to read. Reclassify text
   into three legibility tiers.
2. **Summary muscle group** — on the end-of-workout summary, show the trained
   muscle group for each exercise (Option A: leading muscle icon + group name
   prefixing the subtitle).

## Part 1 — Readability tiers

The app currently has only `textPrimary` (white) and `textMuted`
(`Color(0x47FFFFFF)` ≈ 28% white) — no middle tier, so anything de-emphasized is
barely legible.

**New token:** add `textSecondary` to `AppColors`:
- Dark: `Color(0xB3FFFFFF)` (70% white).
- Light: `Color(0xB3141019)` (70% black).

Applied **only in the active-workout card** (`SessionSetGrid`) and the active
session screen's own labels. `PlanSetGrid` (routine builder) and `ReadonlySetGrid`
(session detail) are unchanged. Other screens are untouched.

**Reassignments in `SessionSetGrid` (`SetGrid.kt`):**

| Element | Current | New |
|---|---|---|
| PREV "last time" value | `textMuted` | `textPrimary` (white) |
| Column labels SET / PREV / KG / REPS | `textMuted` | `textSecondary` |
| Set number (incomplete row) | `textMuted` | `textSecondary` |
| RIR label | `textMuted` | `textSecondary` |
| Entered KG / REPS value | `textPrimary` | unchanged |
| Completed-row values / set number | `accent.inkLighter` | unchanged |
| Empty-cell "–" placeholder | `textMuted` | unchanged (faint = correct) |

**`ActiveSessionScreen.kt`:** the "SESSION NOTES" section label
`textMuted → textSecondary`; the note-field placeholder stays `textMuted`.

## Part 2 — Summary muscle group (Option A)

- **`SessionSummaryViewModel`** gains `exerciseLibraryRepository: ExerciseLibraryRepository`
  (constructor; wired in `AppContainer`). `ExerciseRecap` gains
  `primaryMuscles: List<String>`, resolved per `exerciseId` via
  `exerciseLibraryRepository.getById(id)` during `load()` (cache lookups to avoid
  refetching the same id).
- **Recap row** (`SessionSummaryScreen.kt`, the `FrostedCard` at the exercise
  recap `itemsIndexed`): prepend a **40dp `MuscleGroupIcon`** (reused;
  `tint = accent.accentLighter`) before the name/subtitle `Column`. The subtitle
  becomes an `AnnotatedString`: the **group label in `accent.inkLight`** (e.g.
  "Chest"), then `" · "` + the existing `buildRecapSubtitle(recap)` text in the
  current subtitle color. PR badge placement unchanged.
- **New helper** `muscleGroupLabel(primaryMuscles: List<String>): String?` in
  `MuscleGroup.kt`. Uses the first muscle (trim/lowercase). Mapping:
  - chest→Chest, shoulders→Shoulders, biceps→Biceps, triceps→Triceps,
    forearms→Forearms, abdominals→Abs, glutes→Glutes, traps→Traps, neck→Neck.
  - lats / middle back / lower back → **Back**.
  - quadriceps / hamstrings / calves / adductors / abductors → **Legs**.
  - empty / unknown → `null` (row shows the `MuscleGroupIcon` dumbbell fallback and
    the plain subtitle with no group prefix).

## Edge Cases

- Exercise not in library → `primaryMuscles = emptyList()` → no label, dumbbell
  fallback icon.
- Light theme: `textSecondary` token is theme-aware; accent label colors are
  mode-aware (`inkLight`/`accentLighter`).

## Testing

- **Unit:** `muscleGroupLabel` — sample muscles per group, the Back/Legs groupings,
  unknown/empty → null.
- **Manual (user, in-app):** active-workout card reads clearly with hierarchy
  intact; summary rows show the correct muscle icon + group name.

## Out of Scope

- Changing the global `textMuted` value (it stays; we add a tier and reassign).
- Readability changes outside the active-workout card / session screen.
- Routine builder and session-detail grids.
