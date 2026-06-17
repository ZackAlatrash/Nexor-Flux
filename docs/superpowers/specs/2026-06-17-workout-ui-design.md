# Workout Tracking — UI Design

**Date:** 2026-06-17
**Branch:** `feat/workout-tracking`
**Depends on:** the workout data layer (already built) + 3 additions listed below.
**Mockups:** `docs/superpowers/specs/2026-06-17-workout-ui-mockups.html` (open in a browser; built live during design).

## Goal

A page-by-page workout UI modelled on the **Liftoff** app's flow (which the user likes), rebuilt in
this app's own dark glass theme. Users browse routines, build/edit them, run a resumable logging
session (the Liftoff `SET·PREV·KG·REPS·✓` grid), review and save with an editable duration, and see
history + per-exercise progress.

## Design system rules (must follow)

- **Theme:** dark-first, painted over the themed background. The default accent is violet `#8B5CF6`,
  but **every accent-coloured element must read from `LocalAppAccent`** (buttons, active states,
  completed-set tint + check, nav highlight, progress line, PR badges) — never a hardcoded colour.
  The app has 11 accent themes; the whole workout UI must follow whichever is active.
- **Cards — readability first.** Use **`FrostedCard`** (real M3 blur + solid `glassOverlay` veil) for
  all primary workout content cards (routine cards, exercise cards, session grid container, summary
  tiles, history cards). **Do NOT use `NeutralCard`** (`cardSurface` 0.04 alpha) for primary content —
  it's too transparent to read against the busy themed background. `NeutralCard` is acceptable only
  for minor nested rows where contrast isn't a concern.
  - `FrostedCard(surfaceTint = accent.tintedSurface)` paints the **completed-set / selected-row**
    accent tint without washing out the text on top — use it for those states.
  - `TintedCard` stays reserved for AI surfaces only.
- **Pills / buttons / nav:** liquid-glass pill (`CornerPill`) for the bottom nav, primary CTAs
  (Start, Save, Add N), and the active session's finish button. Accent fill with `accent.onAccent`
  ink (white on saturated accents, dark ink on pale ones like Amber/Lime/Silver — reuse the existing
  `onAccent` luminance rule).
- **Corners:** `CornerCard` (16dp) for cards, `CornerSmall` (10dp) for grid input cells, `CornerChip`
  (20dp) for chips/badges, `CornerPill` for pills.
- **Destructive** actions use `ErrorRed`.
- **Reuse** the existing glass component library; only add a new composable when nothing fits.
- **No gamification** (Liftoff's rank/streak/XP/levels are dropped — not part of this app).

## Navigation

Add a **5th bottom-nav destination, "Train"** (`ti-barbell`-style icon), alongside Home · Body ·
Coach · More → Home · Body · **Train** · Coach · More. Train is the root of the workout flow.

```
Train tab → Train Home ──┬─ + / New routine ─→ Routine Builder ─→ Exercise Picker ─→ (Detail 3b)
                         ├─ Start ───────────→ Active Session ─→ Session Summary ─→ (save)
                         ├─ Resume banner ───→ Active Session
                         └─ History toggle ──→ Workout History ─→ Session Detail (6b)
```

## Backend additions required (fold into the implementation plan)

1. **Per-set planned targets.** Replace `WorkoutExerciseEntity.plannedSets`/`targetReps` with a
   `planned_sets` child table: `(id, workoutExerciseId, setNumber, targetReps Int?, targetWeightKg
   Double?)` — **both targets nullable** (a routine may define a bare set with no targets). Starting a
   session pre-fills each `session_set`'s KG/REPS from these planned targets. Migration bumps the DB
   version; mirror the `session_sets`/`recipe_ingredients` child-table pattern.
2. **Mid-session edits** on `WorkoutSessionRepository`: `addExerciseToSession`,
   `removeSessionExercise`, `reorderSessionExercises`, `updateSessionExerciseNote`,
   `updateSessionNote`. (Sessions currently snapshot exercises only at start.)
3. **Editable duration.** Let the summary override the computed time — store an explicit
   `durationSeconds Int?` on `WorkoutSessionEntity` (null = derive from `startedAt`/`completedAt`);
   `completeSession(durationSeconds: Int? = null)`.

## Screens

### 1 — Train Home
- **Purpose:** Train tab root. View routines, start/resume, reach history.
- **Sections:** header ("Train" + `+`) · **Routines / History** segmented toggle · **resume banner**
  (only when an ACTIVE session exists) · routine cards.
- **Routine card (FrostedCard):** name, "N exercises · M sets", first 3 exercises (thumbnail +
  planned-set count), "and X more", **Start** (accent pill), ⋮ (Edit · Duplicate · Delete).
- **Actions:** `+`/New routine → Builder (empty). Start → `startSession` → Active Session (if another
  session is active, confirm first). Card body → Builder (edit). Resume banner → Active Session.
  History toggle → screen 6.
- **Empty state:** centered FrostedCard — dumbbell icon, "No routines yet", "Build your first workout
  from 870+ exercises", **Create routine**.
- **Edge cases:** routine with 0 exercises → Start disabled with hint. Active session exists → resume
  banner shown.

### 2 — Routine Builder
- **Purpose:** Create/edit a routine template.
- **Sections:** ✕ cancel · title · **Save** (accent) · name field (required) · optional note ·
  exercise cards · **Add exercise**.
- **Exercise card (FrostedCard):** drag handle (≡, reorder), thumbnail, name + "muscle · equipment",
  ⋮ (Replace · Remove · Notes), and a **per-set target grid**: `SET · KG · REPS` rows where **KG and
  REPS are optional** (blank = bare set slot), + **Add set** (swipe-left to remove a set row).
- **Actions:** Save → `saveWorkout`/`updateWorkout` (disabled until name + ≥1 exercise). Add exercise
  → Exercise Picker, returns appended. Drag → reorder (`sortOrder`). ⋮ Remove → swipe/`ErrorRed`.
- **Empty state:** "Add exercises to build this routine" above Add exercise.
- **Edge cases:** editing a routine with past sessions is allowed (history preserved via snapshots);
  duplicate exercises allowed; removing the last exercise disables Save.

### 3 — Exercise Picker
- **Purpose:** Multi-select exercises to add (from Builder's Add exercise, or the session's
  + Exercise).
- **Sections:** ✕ · "Add exercises" · search field · **muscle/equipment filter chips** (horizontal
  scroll, "All" active) · list · sticky **Add N** (accent pill, live count) · **Create custom
  exercise** entry (uses `userCreated`).
- **List row (FrostedCard):** thumbnail (exercise image), name, "muscle · equipment", right-side
  select circle. Selected rows use `surfaceTint = accent` + accent check.
- **Actions:** row tap → toggle select; **thumbnail tap → Exercise Detail (3b)** (images, step
  instructions, primary/secondary muscles) without losing selection; Add N → returns to caller.
- **Empty/no-results:** "No exercises match" + clear-filters action.

### 4 — Active Session (the core; Liftoff-faithful)
- **Purpose:** Run and log a workout; resumable.
- **Sections:** top bar — collapse chevron (minimise to resume banner), stopwatch, **elapsed timer**
  (center), **finish** button (accent pill, top-right) · session-notes field · stacked exercise cards
  · collapsed next exercises · **+ Exercise** (accent).
- **Exercise card (FrostedCard):** thumbnail, name, collapse chevron, ⋮ (Reorder · Notes · Replace ·
  Remove; superset deferred) · grid header `SET · PREV · KG · REPS · ✓` · set rows.
  - **Set row:** number · PREV (last time, e.g. "10×15", from `getLastCompletedSession`) · **KG cell**
    · **REPS cell** · **complete check**. Editing pre-filled from planned targets.
  - **Completed row:** `FrostedCard surfaceTint = accent.tintedSurface` (accent tint) + accent-filled
    check (`onAccent` ink). Pending rows show editable cells + hollow check. (No green — accent only.)
  - **Optional RIR:** grid stays `KG·REPS·✓`; **tapping a set row reveals an RIR stepper** (optional).
  - **+ Add set** per exercise → `insertNextSet` (pre-fills from previous set).
- **No rest timer** (elapsed-only, per decision). **No rank band.**
- **Actions:** check a set → mark `completed` (requires reps; weight optional for bodyweight).
  + Exercise → Picker → `addExerciseToSession`. ⋮ → reorder/replace/remove/notes. Finish → Summary.
  Collapse → minimise (session stays ACTIVE; resume banner on Train Home).
- **Edge cases:** leaving the app keeps the session ACTIVE (already persisted live); bodyweight
  exercise → blank KG allowed; removing all sets of an exercise keeps the exercise with 0 logged sets.

### 5 — Session Summary
- **Purpose:** Review and save the finished workout.
- **Sections:** completion header (accent check + routine + date) · stat tiles — **Duration
  (editable)**, Total volume, Sets, New PRs · per-exercise recap (sets, top set, volume, **PR badge**)
  · session note · **Save workout** (accent) · **Discard** (`ErrorRed`).
- **Editable duration:** tap the pencil → time picker; persists via `durationSeconds` override.
- **Actions:** Save → `completeSession(durationSeconds?)` (writes COMPLETED) → back to Train Home.
  Discard → confirm → abandon/delete session.
- **PRs:** computed by `WorkoutProgressAnalyzer` (best est-1RM / volume vs prior history).
- **Edge cases:** a session with no completed sets → warn before saving (or auto-discard).

### 6 — Workout History
- **Purpose:** Content of the History toggle: browse past sessions.
- **Sections:** Routines/History toggle (History active) · month group headers · session cards.
- **Session card (FrostedCard):** routine name, date, **PR badge** (if any), duration · sets · volume.
- **Actions:** tap → Session Detail (6b).
- **Empty state:** "No workouts yet — start one from Routines."

### 6b — Session Detail + Exercise Progress
- **Purpose:** Read-only breakdown of one past session + per-exercise progress.
- **Sections:** back + header (routine, date · duration · volume) · per-exercise **read-only grid**
  (`SET · KG · REPS`, PR badge) · a **per-exercise est-1RM progress sparkline** over time
  (`getExerciseHistory` + analyzer) with a delta label (e.g. "+12% · 90d").
- **Actions:** read-only (optionally: "repeat this workout" → start a session from it).
- **Edge cases:** exercise with one data point → show the point, no trend line.

## Implementation workflow (user directive)

- **Build one screen at a time.** After each screen, **run the app and verify the screen visually**
  (emulator screenshot) against this spec / the mockups before starting the next screen. Do not batch
  all screens then check at the end.
- Reuse `FrostedCard` (solid, readable) for content; never the faint `NeutralCard`/`cardSurface` for
  primary content. Reuse existing pills/buttons/chips from the glass component library.
- Suggested screen order = build order: 1 Train Home → 2 Routine Builder → 3 Exercise Picker →
  4 Active Session → 5 Session Summary → 6 History → 6b Detail, with the 3 backend additions landed
  before the screens that need them (per-set targets before Builder/Session; mid-session edits before
  Session; duration override before Summary).

## Out of scope

- Supersets (Liftoff has them; not modelled — future).
- Rest timer / between-sets countdown (elapsed-only).
- Gamification (ranks, streaks, XP).
- Backup/restore of workout tables, and wiring sessions into the existing weekly-review/AI trend
  engine (documented future bridges from the data-layer spec).
