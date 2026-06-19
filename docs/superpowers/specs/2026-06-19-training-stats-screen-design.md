# Training Stats Screen — Design

**Date:** 2026-06-19
**Branch:** `feat/training-stats-screen`
**Status:** Approved — ready for implementation planning

## Summary

Add a third sub-section, **Stats**, to the Training screen (alongside Routines and
History). The Stats screen shows two interactive body figures (front and back) and a
list of broad muscle categories. Selecting a category highlights its muscles on the
bodies and reveals the exercises the user has actually logged for that muscle group.
Tapping an exercise opens a full-screen detail view with a progressive-overload chart
and supporting stats.

## Goals

- Let the user browse their training history *by muscle group*, anchored on a visual
  body map that reuses the app's existing muscle artwork.
- Surface progressive-overload progress per exercise (is the weight going up over time?).
- Reuse existing components and visual language; add only what's genuinely missing.

## Non-Goals

- No muscle "heatmap" coloring by volume/frequency — bodies show muscles unhighlighted
  at rest; highlight is driven only by selection/interaction.
- No editing of history from this screen (read-only analytics).
- No cross-exercise or whole-body aggregate charts in this iteration.

## User-Facing Behavior

### Stats sub-section (entry screen)

1. **Tab bar** — `TrainTab` gains a `STATS` value; the screen renders Routines / History
   / Stats as the existing pill-style segmented control.
2. **Two body figures** — front and back, side by side, prominently sized. All muscles
   are drawn faintly; none highlighted at rest.
3. **Muscle category list** — broad, gym-style groups: **Chest, Back, Shoulders, Arms,
   Legs, Core.** Each row is an expandable accordion showing a count of logged exercises.
   - **Chest** → chest
   - **Back** → lats/upper-back, trapezius, lower-back
   - **Shoulders** → deltoids
   - **Arms** → biceps, triceps, forearms
   - **Legs** → quadriceps, hamstrings, glutes, calves, adductors
   - **Core** → abs/obliques
4. **Two-way interaction:**
   - Selecting/expanding a category **highlights that category's muscles** on both body
     figures (Arms → biceps on front, triceps on back, etc.).
   - Tapping a muscle **on a body figure** opens (and scrolls to) its category.
5. **Per-category exercise list** — when expanded, lists the distinct exercises the user
   has logged in completed sessions that target that muscle group. Tapping one opens the
   exercise detail screen.
6. **Empty categories** — a category with no logged exercises shows a muted "no exercises
   logged yet" state rather than being hidden (keeps the body map and list complete).

### Exercise detail (full screen)

Navigated to as its own screen (not an inline expand).

- **Header:** exercise name + muscle group badge (e.g. "ARMS · BICEPS").
- **Quick stats:** best Est. 1RM, heaviest set (weight × reps), training frequency
  (avg sessions/week), last performed.
- **Progress chart:** line chart over time with three metric toggles — **Est. 1RM
  (default)**, Top-set weight, Total volume. Includes a short trend caption
  (e.g. "+26% over 7 weeks").
- **Personal records:** max weight, most reps, best volume in a single day.
- **Recent sessions:** dated list with the sets performed (reps × weight).

## Architecture

Follows the app's existing layering: UI → ViewModel → Repository → Room.

### Body map component (new)

A new composable — working name **`BodyMap`** — built on the **existing `MuscleArt`
data** (`assets/muscles/body_front.json`, `body_back.json`; parsed to Compose `Path`s).
It is distinct from the existing `MuscleGroupIcon` because the Stats screen needs three
behaviors `MuscleGroupIcon` does not provide:

1. **No auto-crop** — render the full body silhouette, not a zoom to the highlighted
   muscle's bounding box.
2. **Multi-muscle highlight** — highlight a *set* of slugs (a whole category) at once,
   rather than one exercise's primary muscle.
3. **Tap hit-testing** — point-in-path detection mapping a tap to the muscle slug it
   landed on, to drive body → category interaction.

`MuscleGroupIcon` is left unchanged. The muscle-slug ↔ category mapping (existing
`MuscleGroup` / `muscleTargetFor` logic) is the shared source of truth for both
highlighting and grouping; it may need a category-level grouping helper layered on top.

### Data layer (new query)

History "by exercise" already exists: `WorkoutSessionDao.getExerciseHistory(exerciseId)`
returning `ExerciseHistoryRow`. What's missing is grouping by muscle:

- A new DAO query to return the **distinct exercises that have logged completed sets**,
  joinable to their `primaryMuscles`, so the Stats screen can bucket them into the six
  categories. (Exercise → category derived from `ExerciseEntity.primaryMuscles` via the
  shared mapping.)
- Existing `getExerciseHistory(exerciseId)` feeds the detail screen.

### Domain / analytics (mostly existing)

`WorkoutProgressAnalyzer` already produces per-day `ExerciseTrendPoint`
(date, totalVolume, bestEstimatedOneRepMax). This covers the **Est. 1RM** and **Volume**
chart series and the volume PR directly. Additional derivations needed:

- **Top-set weight** per day (max weight lifted that day).
- **Quick stats / PRs:** best Est. 1RM, heaviest set, max reps, frequency (sessions per
  week over the logged span), last-performed date — all computable from the history rows.

### Chart rendering

Use **Vico** (already a declared dependency, currently unused) for the progressive-
overload line chart. Fall back to a custom Canvas chart only if Vico can't match the
desired look. The existing custom charts (`CalorieProgressBar`, `MacroRingChart`) remain
the reference for styling conventions (`ChartDefaults`).

### ViewModel / state

- A `StatsViewModel` (or extension of `TrainViewModel`) exposes: the category list with
  per-category exercise counts/lists, the currently selected category (for highlight),
  and loading/empty states.
- An exercise-detail state holder exposes the history-derived stats, chart series, PRs,
  and recent sessions for the selected exercise.

### Reused design-system components

- `FrostedCard` for stat/chart cards; `SectionLabel` for section headers; `VioletBadge`
  for the muscle-group badge; Material3 expandable/`AnimatedVisibility` for the accordion;
  `LocalAppAccent` / `LocalAppColors` for theming and highlight color.

## Data Flow

```
Stats screen
  ├─ BodyMap (front + back)  ←─ highlight slugs ── selected category
  │     └─ tap muscle ──→ select category (two-way)
  └─ Category accordion list ←── DAO: distinct logged exercises grouped by category
        └─ tap exercise ──→ Exercise Detail screen
                               ├─ DAO.getExerciseHistory(exerciseId)
                               ├─ WorkoutProgressAnalyzer.trendPoints() → chart + PRs
                               └─ derived quick stats / top-set / frequency
```

## Error & Empty States

- **No history at all:** Stats screen shows the body map with an empty-state message under
  the category list ("Log a workout to see your stats").
- **Empty category:** muted "no exercises logged yet" row.
- **Single data point** for an exercise: chart shows the point without a misleading trend
  caption; trend caption only appears with ≥2 points.

## Testing

- **Domain (pure Kotlin, unit tests):** top-set-per-day derivation, frequency calc, PR
  extraction, and category bucketing from `primaryMuscles` — including edge cases (single
  session, multi-muscle exercises, unmapped muscle slugs).
- **DAO:** the new "distinct logged exercises by muscle" query against an in-memory Room
  DB.
- **BodyMap hit-testing:** point-in-path mapping for representative taps (unit-testable
  geometry helper kept separate from the composable).
- Manual on-device verification of layout, highlight, two-way interaction, and chart per
  the project's "verify each screen in the app" workflow.

## Open Implementation Notes

- Confirm the exact slug names per category against the actual `body_front.json` /
  `body_back.json` slugs during implementation (mapping must use real slugs).
- Decide final navigation route for the exercise detail screen (new `Routes` entry vs.
  nested within Train).
