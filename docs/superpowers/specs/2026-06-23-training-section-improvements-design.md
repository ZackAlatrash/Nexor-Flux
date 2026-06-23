# Training Section Improvements — Design

**Date:** 2026-06-23
**Branch:** feat/training-stats-screen
**Status:** Approved

Five focused changes to the live workout (Active Session) flow and its summary.

---

## 1. Prefill sets from the previous session

**Problem:** When a session starts, set cells are seeded from the routine's *plan targets*.
The user wants the actual numbers from the last time they did each exercise.

**Change:** `WorkoutSessionRepository.startSession(template)`:

- Fetch `getLastCompletedSession(template.id)` once at the start of session creation.
- Build a lookup `exerciseId → List<(reps, weightKg)>` ordered by set index, from that
  previous session's exercises.
- When seeding each new set, take KG/REPS from the matching exercise + set index.
- **No match → blank** (`reps = 0`, `weightKg = null`). Routine plan targets are no longer
  used for prefill at all.
- The number of sets per exercise still comes from `template` (`plannedSets.size`).
- Prefilled values are editable and **not** pre-ticked (`completed = false`).

**Notes / edge cases:**
- An exercise appearing twice in a routine matches the same previous-exercise set list for both
  instances. Acceptable.
- The PREV column (driven by `ActiveSessionViewModel.prevMap`) is unchanged. Cells will simply
  start matching the PREV hint.
- `getLastCompletedSession` already filters to `status = 'COMPLETED'`.

**Files:** `data/repository/WorkoutSessionRepository.kt`

---

## 2. Lock ticked sets

**Problem:** A completed (ticked) set's KG/REPS/RIR can still be edited, risking accidental changes.

**Change:** In `SessionSetGrid` (`ui/train/component/SetGrid.kt`), when `row.completed`:

- KG and REPS `SetInputCell`s become read-only: non-focusable, no edit, no focus border.
  Achieved by a `locked`/`readOnly` flag on `SetInputCell` that disables the `BasicTextField`
  and skips the focus-driven border/background.
- The RIR +/− steppers are disabled (clicks no-op) when the row is completed.
- The row can still be tapped to *reveal* the RIR value for viewing; values just can't change.
- Unticking via the check circle restores full editing.

**Files:** `ui/train/component/SetGrid.kt`

---

## 3. Fix the "+ Exercise" button

**Problem:** The button renders an `Add` icon **and** a literal "+ " in its label ("+ Exercise"),
producing a double-plus, and a manual `Spacer(6.dp)` fights the button's built-in
`Arrangement.spacedBy(8.dp)`, giving uneven spacing.

**Change:** In `ActiveSessionScreen`'s add-exercise button item:
- Remove the manual `Spacer`.
- Change the label to **"Add Exercise"** (no leading "+").
- Result: `[+ icon] Add Exercise`, evenly centered. Accent fill unchanged.

**Files:** `ui/train/ActiveSessionScreen.kt`

---

## 4. Sticky header + clearer timer

**Problem:** The header (minimize, title, timer, Finish, overflow) is the first `LazyColumn`
item, so it scrolls away. The timer is small and easy to miss.

**Change:** Restructure `ActiveSessionScreen` from a single `LazyColumn` into a `Column`:

- A **pinned top header** that does not scroll, with a subtle bottom divider so it reads as
  fixed. Contents unchanged (minimize · title · timer · Finish · overflow menu).
- The timer moves into an **accent pill**: `accent.tintedSurface` background, `accent.tintedBorder`,
  Timer icon + larger time text (~16sp), placed with the title.
- A scrolling `LazyColumn` with `Modifier.weight(1f)` beneath, holding the session notes,
  exercise cards (with drag-reorder), and the Add Exercise button.
- `ElapsedTimerText`'s isolated recomposition scope is preserved — only the time text recomposes
  on each per-second tick.

**Files:** `ui/train/ActiveSessionScreen.kt`

---

## 5. Don't save the workout until Save/Discard on the summary

**Problem:** `ActiveSessionViewModel.finish()` calls `completeSession()` immediately, so the
session is already COMPLETED (saved) before the summary is shown. Pressing the phone back/home
button on the summary leaves it saved, with no way to back out.

**Change:**

- `ActiveSessionViewModel.finish()` stops calling `completeSession()`. It computes the elapsed
  duration and records it via `updateSessionDuration(id, duration)`, keeping the session **ACTIVE**,
  then returns the id for navigation to the summary.
- `SessionSummaryViewModel.save()` now calls `completeSession(sessionId, state.durationSeconds)`
  (plus `setSessionNote`). This is the **only** place the session flips ACTIVE → COMPLETED.
- `SessionSummaryViewModel.discard()` is unchanged (`abandonSession`).
- `SessionSummaryViewModel.load()` duration display already falls back to `durationSeconds`, which
  `finish()` now writes — so duration shows correctly while the session is still ACTIVE.

**Result:** Pressing back/home on the summary leaves the workout ACTIVE, so it remains the
in-progress workout on Train Home. Only **Save** (complete) or **Discard** (abandon) removes it
from the active state.

**Unaffected:** PR detection in the summary reads `getExerciseHistory`, which filters to
`status = 'COMPLETED'`, so a still-active session is never double-counted.

**Files:** `ui/train/ActiveSessionViewModel.kt`, `ui/train/SessionSummaryViewModel.kt`

---

## Out of scope

- No DB schema changes.
- No changes to the routine builder's plan-target editing (only how a *session* seeds its sets).
- No changes to navigation routes; the existing `onFinish`/`onDone` wiring is reused.
