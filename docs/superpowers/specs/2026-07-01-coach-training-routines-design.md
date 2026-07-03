# Coach-managed training routines — design

**Date:** 2026-07-01
**Status:** Approved (brainstorm)
**Branch:** `redesign/ai-coaching`

## Goal

Let the AI coach **create and edit the user's training routines** through chat, using
confirmed write tools, and let it **create custom exercises** (with user-specified muscle
groups) when a requested exercise isn't in the library. Also close a pre-existing gap: the
**manual** "create exercise" UI currently can't set muscles either — add that too.

## Scope

**In scope**
- Coach can build a new routine from a description and edit existing ones (add/remove
  exercises, change sets/reps/target weight, rename).
- Coach can create a custom exercise with a name + muscle group(s).
- Manual "create custom exercise" dialog gains a muscle-group field.

**Out of scope (MVP — future follow-ups)**
- Deleting routines via the coach (stays manual — a destructive action we don't hand the AI).
- Reordering exercises within a routine.
- Per-set target variation (e.g. 8/6/4 across sets) — MVP uses one uniform target per set.
- Supersets / exercise grouping.

## Existing code this builds on (no schema migration needed)

- `data/repository/WorkoutRepository`: `observeAll()`, `getById(id)`, `saveWorkout(name, note,
  lines)`, `updateWorkout(id, name, note, lines)`, `validate(...)` via `WorkoutValidation`.
  Routine = `WorkoutTemplate`; line input = `NewWorkoutLine(exerciseId, plannedSets, note?)`.
- `data/repository/ExerciseLibraryRepository`: `search(query)`, `getById(id)`,
  `addCustomExercise(name)` (currently hardcodes empty muscles).
- `ExerciseEntity` already has `primaryMuscles`/`secondaryMuscles` columns (JSON), the domain
  `Exercise` decodes them, and the UI already displays them — **so muscles are fully supported
  end-to-end except on the creation path.**
- `ai/CoachToolExecutor` (dispatch), `ai/CoachTools.kt` (`COACH_TOOL_SCHEMAS`,
  `CLOUD_COACH_TOOL_SCHEMAS`, `COACH_WRITE_TOOLS`), `ai/CloudCoachCoordinator` (write-tool
  confirmation flow + `pendingActionDisplayText`).
- `ui/train/ExercisePickerScreen` "create custom exercise" `AlertDialog` (name-only) +
  `ExercisePickerViewModel.createCustom(name)`; `MUSCLE_FILTER_CHIPS` = the existing muscle
  taxonomy to reuse for the new muscle selector.

## Tool surface (5 new coach tools) — Approach A, "compact, delta edit"

### Reads (no confirmation)

- **`get_routines()`** → JSON list of the user's routines: each `{name, note, exercises:
  [{name, sets, reps, weight_kg}]}`. Lets the coach see what exists and target edits by name.
- **`search_exercises(query)`** → up to ~8 library matches `{name, primary_muscles, equipment}`.
  The coach uses this to build routines from real exercises and to check existence before
  creating a custom one.

### Writes (each requires user confirmation)

- **`create_routine(name, exercises[])`** — `exercises[]` = `{name, sets:int, reps?:int,
  weight_kg?:number}`. `sets` is the **required set count**; `reps` and `weight_kg` are optional
  per-exercise targets (null = no target, supported by the nullable `PlannedSet` fields).
  Executor resolves each `name` in the library, builds `sets` uniform `PlannedSetDraft`s (each
  carrying the same `reps`/`weight` target, or none), and calls `saveWorkout`.
- **`edit_routine(name, add?[], remove?[], retarget?[], new_name?)`** — applies **deltas** to
  the current routine (fetched via `getById`), then calls `updateWorkout`:
  - `add[]` = `{name, sets, reps, weight_kg?}` (append)
  - `remove[]` = exercise names to drop
  - `retarget[]` = `{name, sets?, reps?, weight_kg?}` (change targets of an existing line)
  - `new_name?` = rename the routine
  Untouched exercises are preserved (delta-safe — no accidental drops).
- **`create_exercise(name, primary_muscles[], secondary_muscles?[], equipment?)`** — creates a
  custom library exercise via the extended `addCustomExercise` (see below). `userCreated=true`.

All three write-tool names are added to `COACH_WRITE_TOOLS` so they route through the existing
confirmation flow, and each gets a `pendingActionDisplayText` summary:
- *"Create routine 'Push Day' — Bench Press 4×8, Overhead Press 3×10, …"*
- *"Edit 'Push Day' — add Cable Fly 3×12; remove Dips; rename to 'Upper A'."*
- *"Create custom exercise 'Cable Y-Raise' (rear delts)."*

Tools are added to `CLOUD_COACH_TOOL_SCHEMAS` (the set the cloud coach receives). Total coach
tools go from 9 → 14.

## Custom exercise + muscles (two write sites, one repo method)

- **Extend the repo:** `addCustomExercise(name, primaryMuscles: List<String> = emptyList(),
  secondaryMuscles: List<String> = emptyList())` — JSON-encode the lists into the existing
  `primaryMuscles`/`secondaryMuscles` columns. Default args keep every current caller compiling.
- **Coach path:** `create_exercise` passes the muscles the user specified.
- **Manual UI path (close the gap):** the `ExercisePickerScreen` create-exercise `AlertDialog`
  gains a muscle-group selector (chips reusing `MUSCLE_FILTER_CHIPS`); `createCustom(name)` →
  `createCustom(name, primaryMuscles)` → the extended repo method. Keep it optional (a user can
  still create name-only). Muscle vocabulary is shared between manual + coach via the same chip
  taxonomy so entries stay consistent.

## Name resolution & guardrails

- **Exercise resolution:** fuzzy-match names via `ExerciseLibraryRepository.search`. Exact/
  starts-with wins; if multiple close matches, the tool returns the candidates so the coach asks
  the user which; if none, the tool returns "not found" so the coach offers `create_exercise`.
- **Routine resolution:** match by name (case-insensitive) against `observeAll()`. Not found →
  clear error the coach relays. Duplicate names → the coach asks which.
- **Validation:** reuse `WorkoutValidation` through the repo (name non-blank, ≥1 exercise, ≥1
  set each). Executor catches `IllegalArgumentException` and returns the reason text to the coach.
- **Bounds:** clamp/validate `sets` and `reps` to sane ranges before building sets; reject
  nonsensical values with a returned error rather than persisting them.
- **Return shape:** every tool returns a JSON string (success payload or `{"error": "..."}`),
  matching the existing `CoachToolExecutor` contract.

## System-prompt routing (add to the coach guidelines)

- "To build a routine: call `search_exercises` to find real exercises, then `create_routine`."
- "To modify a routine: call `get_routines` first, then `edit_routine` with only the changes."
- "Only call `create_exercise` when the user names an exercise not in the library; ask for the
  muscle group(s) if they didn't say."

## Architecture / boundary notes

- All tool logic lives in `ai/CoachToolExecutor` (already depends on repositories) + schemas in
  `ai/CoachTools.kt`. No new dependency into `ai/local` — the `AiCoachBoundaryTest` stays green.
- `CoachToolExecutor` gains constructor params for `WorkoutRepository` and
  `ExerciseLibraryRepository` (nullable defaults, mirroring `workoutSessionRepository`, so tests
  and any non-routine callers are unaffected). Wired in `AppContainer`.
- The deterministic engine is untouched — this is chat-tool surface only.

## Testing

- `CoachToolExecutor` unit tests (fakes for `WorkoutRepository` + `ExerciseLibraryRepository`):
  `create_routine` happy path; `edit_routine` each delta (add / remove / retarget / rename);
  `create_exercise` stores the specified muscles; unresolved-exercise error; validation failure
  (e.g. 0 exercises); routine-not-found; ambiguous-exercise disambiguation payload.
- `CloudCoachCoordinator` display-text tests for the three new write tools' confirm summaries.
- Repo test: extended `addCustomExercise` persists + round-trips muscles.
- Manual UI: `ExercisePickerViewModel.createCustom(name, muscles)` passes muscles through
  (unit); the dialog field itself is verified on-device by the user.
- Full suite green except the known `.env.test` `InsightHarnessTest` network test.

## Rollout

Standard per-task subagent implementation (TDD) on `redesign/ai-coaching`, review pass, then
on-device verification by the user (chat: build/edit a routine; manual dialog: muscle field).
