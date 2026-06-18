# Workout Tracking — Data Layer Design

**Date:** 2026-06-17
**Branch:** `feat/workout-tracking`
**Scope:** Backend / data logic only. **No UI.**

## Goal

Let users build one or more reusable **workouts** (templates) from an exercise library,
run an **active workout session** in which they add/remove sets and enter reps + weight,
and on completion save the full session. Saved data must support:

1. Showing the user what they did **last time** they performed the same workout.
2. Structured history the in-app **AI can analyze** to track performance/progress over time.

The exercise library is seeded from [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db)
(public domain / Unlicense — safe to bundle and redistribute).

## Decisions (confirmed with user)

- **Active session lifecycle:** persist live + resumable. A session row is created on start with
  `status = ACTIVE`; sets are written to the DB as the user adds them; `status` flips to `COMPLETED`
  (with `completedAt`) when finished. Crash-safe.
- **Effort per set:** each set stores an **optional** `rir` (reps-in-reserve), consistent with the
  existing `lift_performance` model and useful for AI fatigue/progress analysis. Nullable — never blocks logging.
- **Relationship to existing pipeline:** **separate store now, bridge later.** New workout tables are
  independent. `lift_performance`, the weekly review, and the AI adjustment engine are **left untouched**
  in this phase. A clean integration point is documented but not wired.
- **Images:** store image **references only** (raw URLs / relative paths as data). No image binaries
  bundled into the APK in this phase — that is a later UI/asset decision.

## Existing patterns this design follows

Verified in the codebase:

- **Tree with `@Relation`:** `RecipeEntity` + `RecipeIngredientEntity` + `RecipeWithIngredientsDb`,
  with an `abstract` DAO using `@Transaction` and a `replaceIngredients()` helper. Workout
  (template) → exercises → planned sets and Session → exercises → sets reuse this shape.
- **Seeded catalog:** `catalog_foods` uses `source` / `sourceVersion` / `externalId` with a
  `unique(source, externalId)` index and a `name` index. The exercise library mirrors it.
- **Asset seeding:** `assets/knowledge/corpus.json` is read with `kotlinx.serialization` and loaded
  with a `runCatching` fallback so a bad asset never crashes the app (see `AppContainer`).
- **JSON list columns:** list fields are stored as JSON-encoded `String`s via `kotlinx.serialization`.
- **Migrations:** manual `Migration` objects appended to `addMigrations(...)` in
  `RecompDatabase.create`. Currently at **version 9** with no destructive fallback — a real
  `MIGRATION_9_10` is required.
- **Repository style:** `RecipeRepository` maps DB relation POJOs → domain models; `open` methods
  for testability.
- **Units:** the app is kilograms throughout (`bodyWeightKg`, `lift_performance.weight`). Set weight
  is stored as `weightKg`.

## Schema

DB version **9 → 10**. New migration `MIGRATION_9_10` creates all six tables and their indices.
All entities annotated `@Serializable` (consistent with `LiftPerformanceEntity`; keeps future
backup/export trivial).

### A. Exercise library — `exercises` (seeded, read-mostly)

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `source` | `String` | e.g. `"free-exercise-db"` |
| `sourceVersion` | `String` | dataset version; drives re-seed |
| `externalId` | `String` | free-exercise-db slug; **stable identity** for AI tracking |
| `name` | `String` | indexed |
| `category` | `String?` | strength / stretching / cardio / … |
| `force` | `String?` | push / pull / static |
| `level` | `String?` | beginner / intermediate / expert |
| `mechanic` | `String?` | compound / isolation |
| `equipment` | `String?` | barbell / dumbbell / … |
| `primaryMuscles` | `String` | JSON `List<String>` |
| `secondaryMuscles` | `String` | JSON `List<String>` |
| `instructions` | `String` | JSON `List<String>` |
| `images` | `String` | JSON `List<String>` (refs only) |
| `userCreated` | `Boolean` | default `false` — hook for future custom exercises |

Indices: `unique(source, externalId)`, `index(name)`.

**Seeding:** `ExerciseLibraryRepository.seedIfEmpty()` runs **asynchronously** on `appScope`. It reads
`assets/exercises/exercises.json`, parses to DTOs, maps to entities, and `insertAll` in a transaction.
Re-seeds when bundled `sourceVersion` differs from the stored one. Wrapped in `runCatching` — failure
logs a warning and leaves the library empty rather than crashing (same posture as the knowledge corpus).

### B. Workout templates

**`workouts`**

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `name` | `String` | |
| `note` | `String?` | |
| `createdAt` | `String` | ISO datetime |
| `updatedAt` | `String` | ISO datetime |

**`workout_exercises`** — ordered template lines

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `workoutId` | `Long` | FK → `workouts(id)` **ON DELETE CASCADE** |
| `exerciseId` | `Long` | FK → `exercises(id)` |
| `plannedSets` | `Int` | planned number of sets |
| `targetReps` | `Int?` | optional target (AI + pre-fill) |
| `sortOrder` | `Int` | position within workout |
| `note` | `String?` | |

Indices: `index(workoutId)`, `index(exerciseId)`.

### C. Logged sessions

**`workout_sessions`** — one logged instance

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `workoutId` | `Long?` | FK → `workouts(id)` **ON DELETE SET NULL** — history survives template deletion; null = ad-hoc |
| `workoutName` | `String` | denormalized snapshot for stable history/AI reads |
| `date` | `String` | ISO `YYYY-MM-DD` (app convention) |
| `startedAt` | `String` | ISO datetime |
| `completedAt` | `String?` | null while `ACTIVE` |
| `status` | `String` | `ACTIVE` / `COMPLETED` / `ABANDONED` |
| `note` | `String?` | |

Indices: `index(workoutId)`, `index(date)`, `index(status)`.

**`session_exercises`** — per-exercise within a session

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `sessionId` | `Long` | FK → `workout_sessions(id)` **ON DELETE CASCADE** |
| `exerciseId` | `Long` | FK → `exercises(id)` |
| `exerciseName` | `String` | denormalized snapshot |
| `sortOrder` | `Int` | |
| `note` | `String?` | |

Indices: `index(sessionId)`, `index(exerciseId)`.

**`session_sets`** — actual performed sets

| Column | Type | Notes |
|---|---|---|
| `id` | `Long` PK autogen | |
| `sessionExerciseId` | `Long` | FK → `session_exercises(id)` **ON DELETE CASCADE** |
| `setNumber` | `Int` | order within the exercise |
| `reps` | `Int` | |
| `weightKg` | `Double?` | nullable = bodyweight |
| `rir` | `Int?` | optional effort (0–10) |
| `completed` | `Boolean` | default `true` |

Index: `index(sessionExerciseId)`.

### Room read models (relation POJOs)

```kotlin
// Templates — full nested library detail
data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
)
data class WorkoutWithExercisesDb(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithExercise>,
)

// Sessions
data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId") val sets: List<SessionSetEntity>,
)
data class WorkoutSessionWithDetailsDb(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
)
```

## Repositories

Mirror `RecipeRepository` (constructor-injected DAO, `open` methods, DB→domain mapping).

- **`ExerciseLibraryRepository`** — `observeAll()`, `search(query)`, `getById(id)`, `seedIfEmpty()`.
- **`WorkoutRepository`** — `observeAll()`, `getById(id)`, `saveWorkout(name, note, lines)`,
  `updateWorkout(...)`, `deleteWorkout(id)`.
- **`WorkoutSessionRepository`** — `startSession(workoutId)` (snapshots template into session +
  session_exercises), `addSet(...)`, `updateSet(...)`, `removeSet(id)`, `completeSession(id)`,
  `abandonSession(id)`, `observeActiveSession()`, **`getLastCompletedSession(workoutId)`**,
  **`getExerciseHistory(exerciseId)`** (flat dated `reps`/`weightKg`/`rir`), `observeSessionHistory()`.

Wired into `AppContainer` alongside the existing repositories; DAOs exposed from `RecompDatabase`.
`seedIfEmpty()` is launched on `appScope` during container init.

## Domain (pure Kotlin, no Android imports) — the AI-analysis layer

- **`WorkoutValidation`** — name non-blank; ≥1 exercise to save a workout; `plannedSets ≥ 1`;
  `reps ≥ 0`; `weightKg ≥ 0` when present; `rir ∈ 0..10` when present. Pure functions, unit-tested.
- **`WorkoutProgressAnalyzer`** — per-set volume (`reps × weightKg`), session total volume,
  estimated 1RM (Epley: `weight × (1 + reps/30)`), best set per exercise, and per-exercise trend
  points (date → volume / est-1RM) for progress tracking. Pure functions, unit-tested.

## "Last time" and AI queries

- **Last time:** `getLastCompletedSession(workoutId)` →
  `SELECT * FROM workout_sessions WHERE workoutId = :id AND status = 'COMPLETED' ORDER BY date DESC, completedAt DESC LIMIT 1`,
  then load its `WorkoutSessionWithDetailsDb`.
- **AI progress:** `getExerciseHistory(exerciseId)` returns a flat, dated list of completed sets
  (`date`, `reps`, `weightKg`, `rir`) ordered by date; `WorkoutProgressAnalyzer` turns it into
  volume / est-1RM trends. Keyed on the stable `exercises.id` / `externalId`.

## free-exercise-db ingestion

`dist/exercises.json` is an array of objects:
`{ id, name, force?, level, mechanic?, equipment?, primaryMuscles[], secondaryMuscles[], instructions[], category, images[] }`.
A `@Serializable` DTO with nullable fields parses it; `id → externalId`, list fields → JSON columns,
`images` kept as-is (refs). Bundled at `app/src/main/assets/exercises/exercises.json`.

## Testing

- **Pure domain** (`WorkoutValidation`, `WorkoutProgressAnalyzer`): TDD unit tests under
  `:app:testDebugUnitTest` — volume, est-1RM, best set, trend ordering, validation boundaries.
- **DTO mapping:** unit-test free-exercise-db DTO → `ExerciseEntity` (including JSON list encoding
  and null handling).
- **DAO/relation queries:** verified per the project's existing DB test approach (confirm Robolectric
  vs instrumented during planning); at minimum `./gradlew :app:compileDebugKotlin` must pass and the
  migration must be exercised.

## Out of scope (deliberate)

- All UI.
- Bundling image binaries into assets.
- Wiring completed sessions into the existing weekly-review / AI adjustment trend engine
  (documented bridge point only).
- Cardio/duration/time-based set fields.
- Adding the new tables to `BackupRepository` (follow-up).

## Future integration points (not built now)

- **AI bridge:** map completed `session_sets` into `PerformancePoint` so the weekly review and
  adjustment engine can consume workout data; and/or expose a coach tool
  (`get_workout_history`) reading `getExerciseHistory`.
- **Backup:** include the six tables in `BackupRepository` export/import.
- **Custom exercises:** surface `userCreated` exercises in the library UI.
