# Reference — Data Model

Complete persistence inventory of the Android app, captured for the iOS port.
**Source of truth is the code**; this document exists so a session can answer schema questions
without re-deriving them. Re-verify before relying on any line-number citation.

**Provenance:** ✅ = verified directly against `develop` @ `d874aa5` on 2026-08-01 ·
📋 = from deep-dive analysis, cited but not independently re-checked.

---

## 1. Room database

**Class:** `data/local/RecompDatabase.kt:44-68` · **File:** `recomp_tracker.db` (`:361`) ·
**Version:** 15 · **`exportSchema = false`** (`:66-67`)

📋 **There is no schema JSON artifact on disk.** No `room.schemaLocation` KSP arg is configured, and
a repo-wide search for `*schema*.json` finds nothing. The entity classes are the only machine-readable
description of the schema.

✅ **Zero `@TypeConverters` in the entire repo.** Every column is a SQLite primitive
(`String`/`Int`/`Long`/`Double`/`Boolean` + nullables). **This is the single biggest portability win
in the data layer** — every entity maps to a flat Swift struct with no custom coding.

No `fallbackToDestructiveMigration`, no `setJournalMode`, no explicit FK pragma (Room enables FKs
by default).

### 1.1 The 19 entities

| # | File | Table | PK | Indices | Foreign keys |
|---|---|---|---|---|---|
| 1 | `DailyLogEntity.kt:9-23` | `daily_logs` | `date: String` (natural, ISO) | — | — |
| 2 | `CatalogFoodEntity.kt:9-27` | `catalog_foods` | `id: Long` auto | `(source, externalId)` UNIQUE, `(name)` | — |
| 3 | `MealEntryEntity.kt:9-38` | `meal_entries` | `id: Long` auto | `(date)` | **none — `slotId` dangles by design** |
| 4 | `SavedFoodEntity.kt:8-19` | `saved_foods` | `id: Long` auto | — | — |
| 5 | `SavedMealEntity.kt:8-18` | `saved_meals` | `id: Long` auto | — | — |
| 6 | `LiftPerformanceEntity.kt:9-22` | `lift_performance` | `id: Long` auto | `(date)`, `(liftName)` | — |
| 7 | `WeeklyReviewEntity.kt:8-18` | `weekly_reviews` | `weekStart: String` | — | — |
| 8 | `MealSlotEntity.kt:9-14` | `meal_slots` | `id: Long` auto | — | — |
| 9 | `RecipeEntity.kt:8-12` | `recipes` | `id: Long` auto | — | — |
| 10 | `RecipeIngredientEntity.kt:10-39` | `recipe_ingredients` | `id: Long` auto | `(recipeId)` | `recipeId → recipes.id` CASCADE |
| 11 | `ExerciseEntity.kt:9-32` | `exercises` | `id: Long` auto | `(source, externalId)` UNIQUE, `(name)` | — |
| 12 | `WorkoutEntity.kt:8-16` | `workouts` | `id: Long` auto | — | — |
| 13 | `WorkoutExerciseEntity.kt:10-33` | `workout_exercises` | `id: Long` auto | `(workoutId)`, `(exerciseId)` | `workoutId→workouts.id` CASCADE; `exerciseId→exercises.id` **NO ACTION** |
| 14 | `PlannedSetEntity.kt:10-28` | `planned_sets` | `id: Long` auto | `(workoutExerciseId)` | `→workout_exercises.id` CASCADE |
| 15 | `WorkoutSessionEntity.kt:10-32` | `workout_sessions` | `id: Long` auto | `(workoutId)`, `(date)`, `(status)` | `workoutId→workouts.id` **SET NULL** |
| 16 | `SessionExerciseEntity.kt:10-34` | `session_exercises` | `id: Long` auto | `(sessionId)`, `(exerciseId)` | `sessionId→workout_sessions.id` CASCADE; `exerciseId→exercises.id` **NO ACTION** |
| 17 | `SessionSetEntity.kt:10-30` | `session_sets` | `id: Long` auto | `(sessionExerciseId)` | `→session_exercises.id` CASCADE |
| 18 | `PlanVersionEntity.kt:9-19` | `plan_versions` | `effectiveFrom: String` (ISO date) | — | — |
| 19 | `UsageEventEntity.kt:14-20` | `usage_events` | `id: Long` auto | **none — see §7** | — |

Tables 11–17 (7 of 19) serve the **Train module only** and are deferred to iOS v1.1 — but build them
in Phase 1 anyway so backup round-trip with Android stays intact.

### 1.2 Full column lists

📋 Kotlin types as declared.

**`daily_logs`** (`DailyLogEntity.kt:10-22`)
`date:String` PK · `bodyWeightKg:Double?` · `waistCm:Double?` · `waistSkinfoldMm:Double?` ·
`steps:Int?` · `stepsSource:String?` · `sleepHours:Double?` · `energyScore:Int?` ·
`hungerScore:Int?` · `sorenessScore:Int?` · `trained:Boolean=false` · `notes:String=""`

`stepsSource` is a stringly-typed enum: `StepsSource.MANUAL="manual"` /
`HEALTH_CONNECT="health_connect"` (`StepsSource.kt:10-13`).

**`meal_entries`** (`MealEntryEntity.kt:14-37`)
`id:Long` · `date:String` · `mealType:String` · `name:String` · `calories:Int` ·
`proteinG/carbsG/fatG:Double` · `slotId:Long?` · `amountGrams:Double?` ·
`basePer100Calories:Int?` · `basePer100ProteinG/CarbsG/FatG:Double?` · `entryServingName:String?` ·
`entryServingGrams:Double?` · `loggedByServings:Boolean=false` · `planned:Boolean=false`

**`catalog_foods`** (`CatalogFoodEntity.kt:17-26`)
`id` · `source:String` · `sourceVersion:String` · `externalId:String` · `name:String` ·
`servingName:String` · `calories:Int` · `proteinG/carbsG/fatG:Double`

**`saved_foods`** (`SavedFoodEntity.kt:10-18`)
`id` · `name` · `servingName` · `calories:Int` · 3× macro `Double` · `householdServingName:String?` ·
`householdServingGrams:Double?`

**`saved_meals`** (`SavedMealEntity.kt:10-17`) — `id` · `name` · `mealType` · `calories:Int` · 3× macro

**`lift_performance`** (`LiftPerformanceEntity.kt:14-21`)
`id` · `date:String` · `liftName:String` · `weight:Double` · `reps:Int` · `sets:Int` · `rir:Int?`

**`weekly_reviews`** (`WeeklyReviewEntity.kt:10-17`)
`weekStart:String` PK · `verdict:String` · `recommendedCalorieChange:Int` ·
`reasonCodes:String` (**delimited blob**) · `generatedAt:String` ·
`briefingJson:String?` (**embedded JSON doc**) · `briefingSignature:String?` ·
`briefingGeneratedAt:String?`

**`meal_slots`** (`MealSlotEntity.kt:11-13`)
`id` · `name` · `@ColumnInfo("sort_order") sortOrder:Int` — **the only snake_case column in the
schema.** Every other column is camelCase in SQLite. The backup JSON key is `sortOrder`.

**`recipes`** — `id` · `name`

**`recipe_ingredients`** (`RecipeIngredientEntity.kt:23-38`)
`id` · `recipeId:Long` · `name` · `sortOrder:Int` · `calories:Int` · 3× macro ·
`amountGrams:Double?` · 4× `basePer100*` · `entryServingName:String?` · `entryServingGrams:Double?` ·
`loggedByServings:Boolean`

**`exercises`** (`ExerciseEntity.kt:17-31`)
`id` · `source` · `sourceVersion` · `externalId` · `name` ·
`category/force/level/mechanic/equipment: String?` · `primaryMuscles:String` ·
`secondaryMuscles:String` · `instructions:String` · `images:String` · `userCreated:Boolean`

⚠️ **The four list-shaped columns are JSON-array-in-TEXT**, encoded by
`ExerciseLibraryJson.encodeList/decodeList` (`domain/workout/ExerciseLibraryJson.kt:31-34`).
Modelling them as native `[String]` on iOS breaks byte-compatibility with Android backups.

**`workouts`** — `id` · `name` · `note:String?` · `createdAt:String` (ISO instant) · `updatedAt:String`

**`workout_exercises`** — `id` · `workoutId` · `exerciseId` · `sortOrder:Int` · `note:String?`

**`planned_sets`** — `id` · `workoutExerciseId` · `setNumber:Int` · `targetReps:Int?` · `targetWeightKg:Double?`

**`workout_sessions`** (`WorkoutSessionEntity.kt:23-31`)
`id` · `workoutId:Long?` · `workoutName:String` · `date:String` · `startedAt:String` ·
`completedAt:String?` · `status:String` (`ACTIVE`/`COMPLETED`/`ABANDONED`, **literals hardcoded in
SQL**) · `note:String?` · `durationSeconds:Int?`

**`session_exercises`** — `id` · `sessionId` · `exerciseId` · `exerciseName:String` (denormalised) ·
`sortOrder:Int` · `note:String?`

**`session_sets`** — `id` · `sessionExerciseId` · `setNumber:Int` · `reps:Int` · `weightKg:Double?` ·
`rir:Int?` · `completed:Boolean=true`

**`plan_versions`** (`PlanVersionEntity.kt:11-18`)
`effectiveFrom:String` PK · `targetCalories/ProteinG/CarbsG/FatG:Int` ·
`calorieZoneLowerBound/UpperBound:Int` · `createdAt:String`

**`usage_events`** — `id` · `timestampEpochMs:Long` · `type:String` · `label:String?`

### 1.3 Relation projections (not tables)

`RecipeWithIngredientsDb.kt:6-13` · `WorkoutWithExercisesDb.kt:6-22` (`@Embedded` + 1:1 exercise +
1:N plannedSets) · `WorkoutSessionWithDetailsDb.kt:6-20` (**two-level nested `@Relation`**:
session → exercises → sets) · flat projection `ExerciseHistoryRow` (`:23-28`).

### 1.4 The 14 migrations

All registered at `RecompDatabase.kt:363`. None destructive.

| Migration | Line | Effect |
|---|---|---|
| 1→2 | `:86-100` | CREATE `meal_slots`; ADD `meal_entries.slotId`; seed 3 slots |
| 2→3 | `:314-337` | CREATE `catalog_foods` + 2 indices |
| 3→4 | `:102-114` | +2 cols `saved_foods`, +7 cols `meal_entries` |
| 4→5 | `:116-120` | ADD `daily_logs.waistSkinfoldMm` |
| 5→6 | `:122-126` | ADD `meal_entries.loggedByServings` |
| 6→7 | `:128-160` | CREATE `recipes`, `recipe_ingredients` + index |
| 7→8 | `:162-168` | ADD `meal_entries.planned` |
| 8→9 | `:170-176` | +3 briefing cols on `weekly_reviews` |
| 9→10 | `:178-241` | CREATE the entire training graph (6 tables + 10 indices) |
| 10→11 | `:243-269` | CREATE `planned_sets`; **table-rebuild** of `workout_exercises` |
| 11→12 | `:271-275` | ADD `workout_sessions.durationSeconds` |
| 12→13 | `:277-291` | CREATE `plan_versions` |
| 13→14 | `:293-299` | ADD `daily_logs.stepsSource` |
| 14→15 | `:301-312` | CREATE `usage_events` |

**On iOS you do not replay these** — a fresh install starts at the final shape. Keep them as
semantically-numbered GRDB migrations anyway so both platforms' histories stay legible, and because
the list is the authoritative record of which columns are nullable-because-added-later.

**Fresh-install seed:** `SEED_DEFAULT_SLOTS` callback (`:346-356`) inserts
`DEFAULT_MEAL_SLOTS` = Meal 1 / Lunch / Dinner (`MealSlotEntity.kt:21-25`). Plus an idempotent
app-start self-heal `MealSlotInitializer.seedIfEmpty()` (`data/repository/MealSlotInitializer.kt:15-18`).

---

## 2. DAOs

📋 14 DAOs in `data/local/dao/`. Annotation census:

| DAO | `@Query` | `@Insert` | `@Update` | `@Upsert` | `@Delete` | `@Transaction` |
|---|---:|---:|---:|---:|---:|---:|
| `CatalogFoodDao` | 4 | 1 | 0 | 0 | 0 | 0 |
| `DailyLogDao` | 13 | 1 | 0 | 1 | 0 | 1 |
| `ExerciseDao` | 9 | 2 | 1 | 0 | 0 | 2 |
| `MealEntryDao` | 17 | 2 | 1 | 0 | 1 | 0 |
| `MealSlotDao` | 4 | 1 | 1 | 0 | 0 | 0 |
| `PerformanceDao` | 4 | 2 | 0 | 0 | 0 | 0 |
| `PlanVersionDao` | 4 | 0 | 0 | 1 | 0 | 0 |
| `RecipeDao` | 5 | 2 | 1 | 0 | 0 | 4 |
| `SavedFoodDao` | 4 | 2 | 1 | 0 | 0 | 0 |
| `SavedMealDao` | 4 | 2 | 1 | 0 | 0 | 0 |
| `UsageEventDao` | 3 | 1 | 0 | 0 | 0 | 0 |
| `WeeklyReviewDao` | 4 | 1 | 0 | 1 | 0 | 0 |
| `WorkoutDao` | 8 | 3 | 1 | 0 | 0 | 3 |
| `WorkoutSessionDao` | 26 | 3 | 2 | 0 | 0 | 7 |
| **Total** | **109** | **23** | **9** | **4** | **1** | **19** |

### 2.1 Exotic-SQL audit — the headline finding

📋 Repo-wide search for `@RawQuery`, `Fts3`/`Fts4`, `strftime`, `julianday`, `OVER (`,
`WITH RECURSIVE`, `COLLATE`: **zero hits.**

The only non-CRUD constructs in the entire database are one 3-table JOIN, one `GROUP BY` aggregate,
one `COALESCE(MAX(...), -1) + 1`, a handful of `COUNT(*)`, and one `LIKE '%' || :q || '%'`.

**Everything analytical — trends, adherence, e1RM, 28-day aggregation, weekly rollups — is computed
in Kotlin in `domain/`, not in SQL.** There is essentially no query logic to port.

### 2.2 The queries worth knowing

**The only multi-table JOIN** — `WorkoutSessionDao.kt:153-161`
```sql
SELECT s.date AS date, st.reps AS reps, st.weightKg AS weightKg, st.rir AS rir
FROM session_sets st
JOIN session_exercises se ON st.sessionExerciseId = se.id
JOIN workout_sessions s ON se.sessionId = s.id
WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND st.completed = 1
ORDER BY s.date, s.startedAt
```

**The load-bearing date assumption** — `WorkoutSessionDao.kt:118-130`. The KDoc states it
explicitly: *"`date` is stored as an ISO `YYYY-MM-DD` string, so a lexicographic `>=` compare is a
correct date-range filter."* See §5.

**The only GROUP BY** — `UsageEventDao.kt:25-28`
```sql
SELECT type, label, COUNT(*) AS count FROM usage_events
WHERE timestampEpochMs >= :sinceEpochMs GROUP BY type, label
```
Groups on a nullable column; Room maps the NULL key to `label: String?`.

**Sort-order allocator** — `WorkoutSessionDao.kt:132`
```sql
SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM session_exercises WHERE sessionId = :sessionId
```

**Row materialiser backing the partial-update pattern** — `DailyLogDao.kt:44`
```sql
INSERT OR IGNORE INTO daily_logs (date, trained, notes) VALUES (:date, 0, '')
```

**Unbounded upper bound via sentinel string** — `LogRepository.kt:110` calls
`observeBetween(startDate.toString(), "9999-12-31")`.

### 2.3 Transaction bodies — each encodes a fixed bug

📋 These are the real porting targets. Reproduce the semantics *and* the ordering.

| Transaction | Why it exists |
|---|---|
| `DailyLogDao.upsertMetric` (`:72-82`) | Dispatches on metric name to a **single-column UPDATE** so a coach write can't clobber a concurrent check-in save (P2-7) |
| `ExerciseDao.insertCustomOrGetExisting` (`:51-53`) | Lookup-or-insert, never REPLACE — REPLACE deletes the conflicting row and trips the NO-ACTION FK (P1-20) |
| `ExerciseDao.upsertLibrary` (`:66-73`) | **Id-preserving** re-seed + version stamp; delete-then-insert crashed every user with a routine (P1-19) |
| `WorkoutSessionDao.abandonActiveAndInsertSession` (`:40-44`) | Guarantees exactly one ACTIVE session (P1-16) |
| `WorkoutSessionDao.insertNextSet` (`:93-97`) | Count-then-insert so `setNumber` is race-free |
| `WorkoutDao.replaceExercises` (`:56-65`) / `RecipeDao.replaceIngredients` (`:44-50`) | Delete children, re-insert with recomputed ordering |

⚠️ **The per-column UPDATE pattern is load-bearing, not an optimisation.** `DailyLogDao.kt:38-63`
(six single-column updates) and `WorkoutSessionDao.kt:61-73` (four single-column set updates) exist
*specifically* because whole-row read-modify-write lost concurrent field writes (P1-18, P2-7).

---

## 3. DataStore preferences

✅ **10 stores, not 11.** `CLAUDE.md` says 11; `grep -c "preferencesDataStore(name"` returns 10.
Doc drift — fix on the Android side.

| # | Store | Declared at | Keys |
|---|---|---|---|
| 1 | `plan_preferences` | `data/preferences/AppPreferences.kt:14` | `target_calories`, `target_protein_g`, `target_carbs_g`, `target_fat_g`, `maintenance_phase_start_date`, `weight_trend_threshold_kg_per_week`, `waist_increase_threshold_cm`, `adherence_minimum_percent`, `review_cadence_days`, `use_metric_units`, `calorie_zone_lower_bound`, `calorie_zone_upper_bound`, `health_connect_enabled`, `health_connect_last_sync_epoch_ms` (`:69-86`) |
| 2 | `ui_preferences` | `AppPreferences.kt:91` | `selected_font`, `ai_insights_enabled`, `onboarding_complete`, `accent_theme`, `theme_mode`, `cloud_base_url`, `cloud_model_id`, `last_seen_briefing_signature` (`:170-179`) |
| 3 | `user_profile_preferences` | `UserProfilePreferencesStore.kt:21` | `name`, `profile_photo_uri`, `height_cm`, `birth_date`, `biological_sex`, `activity_level`, `weekly_gym_sessions`, `goal`, `daily_step_goal`, + legacy read-only `age_years` (`:35-48`) |
| 4 | `coach_inbox` | `data/coach/CoachInboxRepository.kt:14` | `current_signal` (JSON), `seen_ledger` (JSON map), `last_run_date` |
| 5 | `coach_journey` | `CoachJourneyStore.kt:14` | `fired_history` (JSON list), `weekly_verdicts` (JSON list) |
| 6 | `coach_memory` | `CoachMemoryStore.kt:16` | `entries` (JSON list, cap 50) |
| 7 | `coach_notification_prefs` | `NotificationPreferences.kt:17` | `weekly_check_in_push_enabled` (default true), `ambient_nudges_enabled` (default false), `quiet_hours_start` (22), `quiet_hours_end` (7), `last_pushed_weekly_signature` |
| 8 | `coach_push_history` | `PushHistoryStore.kt:13` | `push_events` (JSON list) |
| 9 | `coach_experiment` | `CoachExperimentStore.kt:13` | `active_experiment` (JSON) |
| 10 | `rebalance` | `data/rebalance/RebalanceStore.kt:16` | `state` (JSON), `last_evaluated` (ISO date) |

### 3.1 JSON-blob-in-a-preference — 8 keys

DataStore is used as a key-value bucket for serialised aggregates. All are kotlinx.serialization
`data class`es with camelCase keys → directly Swift `Codable`-compatible.

| Blob | Shape | Retention |
|---|---|---|
| `CoachSignal` (`domain/coach/CoachSignal.kt:15-38`) | `kind` (18-value enum), `tier`, `category`, `severity:Int 0..100`, `facts`, `verdict`, `action` (sealed), `rationale`, `dedupKey`, `surface`, `fallbackText` | current only |
| Seen ledger | `Map<dedupKey, ISO date>` | pruned at 30 days (`CoachInboxSerialization.kt:76-85`) |
| `FiredSignalRecord` | `kind`, `dedupKey`, `weekSignature`, `dateIso` | cap 24 |
| `WeeklyVerdictRecord` | `weekSignature`, `weekEndDateIso`, `verdict` | cap 8 |
| `CoachMemoryEntry` | `id`, `text`, `createdAtIso` | cap 50 |
| `PushEvent` list | — | 14 days / 14 events |
| `CoachExperiment` | `correlationId`, `hypothesis`, `trackedMetric`, `baselineValue`, `startDateIso` | one |
| `RebalanceState` | hand-rolled `buildJsonObject` codec (`RebalanceSerialization.kt:51-143`), 18 fields on `RebalancePlan` | history capped |

⚠️ `CoachSignal` has `init` `require`s — **a decoded-but-invalid payload throws from the
constructor**. `CoachInboxRepository.kt:67-74` catches and logs. Preserve that boundary.

### 3.2 Two deliberate failure-tolerance doctrines — do not merge them

**Tolerant (the norm):** `RebalanceSerialization.decode` returns the empty default on
blank/invalid/unknown-enum (`:41-47`, doctrine documented at `:30-32`); `CoachMemoryStore.decode`
→ `emptyList()`; `CoachExperimentSerialization.decode` → null.

**Intolerant (a known bug — fix, don't port):** `UserProfilePreferencesStore` throws *inside the
flow's `map`* on an unrecognised enum, crashing every collector. This is review **P2-9**.

⚠️ **One deliberate asymmetry to preserve exactly** — `RebalanceSerialization.kt:114-120`: every
required field is `?: return null`, but a missing `intensity` **defaults to `STANDARD`** so a
pre-feature backup still decodes.

⚠️ **`UiPreferences.cloudModelId` is a read-time remap, not stored data** (`AppPreferences.kt:106-117`):
reading `openai/gpt-oss-20b:free` returns `nvidia/nemotron-nano-9b-v2:free`. A naive "just read the
key" port silently reinstates a tool-broken model.

⚠️ **`ui_preferences` holds the live `selected_font` / `ai_insights_enabled`;** the same key names
also exist as dead declarations in `plan_preferences` (`AppPreferences.kt:84-85`). And
`selected_font` is **stored, shown in a picker, and never applied** — no `res/font` directory
exists. Don't port dead wiring.

---

## 4. Secrets — `SecureKeyStore`

`data/preferences/SecureKeyStore.kt`. Store file `secure_ai_prefs` (`:67`), exactly two values:
`cloud_api_key` (`:68`) and `web_search_api_key` (`:69`).

`MasterKey.Builder(...).setKeyScheme(AES256_GCM)` (`:74-76`) +
`EncryptedSharedPreferences.create(..., AES256_SIV, AES256_GCM)` (`:77-83`). Exposes
`hasKey`/`hasWebSearchKey` as `StateFlow<Boolean>` (`:31-35`), seeded eagerly in `init`.

Corruption recovery `openEncryptedPrefsWithRecovery` (`:95-107`) deletes and rebuilds once on
`GeneralSecurityException`/`IOException` — the P1-4 fix for a device-restore crash-loop. Backed up
by `<exclude domain="sharedpref" path="secure_ai_prefs.xml"/>` in both `data_extraction_rules.xml`
and `backup_rules.xml`.

**iOS:** two Keychain items (`kSecClassGenericPassword`). Use
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` — the digest may need the key while locked.
**The entire recovery + backup-exclusion complex collapses to one accessibility attribute.**
Keep the reactive `hasKey` pattern and the **key-provider lambda** (`CloudConfig.apiKey: () -> String`,
the P1-5 rotation fix), not a captured string.

> ℹ️ Unrelated to iOS: `androidx.security:security-crypto` APIs were **all deprecated** in 1.1.0
> (2025-07-30) with guidance to use Android Keystore directly.

---

## 5. Dates — the highest silent-corruption risk

### 5.1 Three storage conventions, all TEXT/INTEGER, never a date type

1. **ISO local date `YYYY-MM-DD` as TEXT** — `daily_logs.date` (PK), `meal_entries.date`,
   `lift_performance.date`, `weekly_reviews.weekStart` (PK), `workout_sessions.date`,
   `plan_versions.effectiveFrom` (PK). Written via `LocalDate.toString()`, read via `LocalDate.parse`.
2. **ISO instant as TEXT** — `workouts.createdAt/updatedAt`, `workout_sessions.startedAt/completedAt`,
   `plan_versions.createdAt`, `weekly_reviews.generatedAt/briefingGeneratedAt`.
3. **Epoch millis as INTEGER** — only `usage_events.timestampEpochMs` and the
   `health_connect_last_sync_epoch_ms` preference.

### 5.2 The rule

> **Store dates as `YYYY-MM-DD` strings on iOS. Do not use `Date`.**

Every range predicate (`BETWEEN`, `>=`, `< :date`) depends on lexicographic TEXT ordering, including
the `"9999-12-31"` sentinel. Switching to `Date` (a `Double` instant) changes every predicate's
meaning and makes `Calendar.startOfDay` timezone conversion silently shift rows across day
boundaries. Expose a computed date in Swift; persist the string.

**And:** use `Locale(identifier: "en_US_POSIX")` on every formatter. Android's `LocalDate.toString()`
is locale-independent; `DateFormatter` is not, and a non-Gregorian-calendar device would corrupt the
TEXT primary keys of three tables.

### 5.3 Timezone posture

Everything date-scoped is **device-local, implicitly** — `LocalDate` carries no zone, and nothing
records the zone a row was written in. The only explicit zone use is Health Connect
(`ZoneId.systemDefault()` at `HealthConnectRepository.kt:77,123`). A user crossing timezones
re-buckets "today"; there is no way to repair a mixed-zone dataset after the fact. Match this
behaviour rather than trying to improve it — changing it would re-judge existing data.

### 5.4 `DateProvider`

`core/time/DateProvider.kt` — `today()` + `todayFlow()`. The flow is a real midnight ticker
(`:40-46`): emit → `delay(millisUntilNextDay + 1s)` → repeat, `distinctUntilChanged()`. Injected
into the coach stores and coordinators so "today" is deterministic in tests.

**iOS:** a protocol with a `Clock`-injected impl. `todayFlow()` → `NSCalendarDayChangedNotification`,
which is *better* than the manual delay loop.

📋 Known open bug (P1-10): six tab ViewModels capture "today" once at construction and freeze across
midnight. `todayFlow()` is the prescribed fix and exists but isn't wired everywhere. **Wire it
correctly from the start on iOS.**

---

## 6. File formats

All three are plain JSON with camelCase keys → Swift `Codable` 1:1.

### 6.1 Full backup

**Schema:** `domain/export/BackupModels.kt` · **Versioned envelope:** `CURRENT_BACKUP_VERSION = 2`
(`:34`), field `version: Int` (`:38`).

21 top-level fields (`:37-69`): `version`, `exportedAt`, `preferences:PlanPreferences`, `dailyLogs`,
`mealEntries`, `savedFoods`, `savedMeals`, `liftPerformances`, `weeklyReviews`, `mealSlots`,
`recipes:List<RecipeBackup>`, `planVersions`, `rebalanceState:RebalanceState?`, then the v2
additions: `exercises`, `catalogFoods`, `workouts`, `workoutExercises`, `plannedSets`,
`workoutSessions`, `sessionExercises`, `sessionSets`.

**Every element is a Room entity serialised verbatim**, so JSON keys = Kotlin property names =
SQLite column names (except `sort_order`, where `@ColumnInfo` decouples them — the JSON key is
`sortOrder`). `UsageEventEntity` is the only entity not `@Serializable`.

**Codec:** `BackupRepository.kt:23-27` — `Json { prettyPrint = true; ignoreUnknownKeys = true;
encodeDefaults = true }`, both directions on `Dispatchers.Default`. Plain `.json`, unzipped,
unencrypted, written via SAF as `recomp-tracker-${LocalDate.now()}.json`.

**Restore algorithm** (`:60-116`) — ✅ verified current:
1. `require(payload.version <= CURRENT_BACKUP_VERSION)` (`:62-65`)
2. In `database.withTransaction`: `clearAllTables()` (`:70`)
3. Slots: seed 3 defaults if empty, **else re-insert with original ids** (`:79`) — the P0-2 fix
4. Training + catalog **parents before children, id-for-id** (`:85-92`) — the P0-1 fix
5. Flat tables via `insertAll` (`:93-98`)
6. **Recipes are the one exception** — fresh ids, ingredients remapped (`:100-107`)
7. `planVersionDao().deleteAll()` then upsert (`:109-110`)
8. **Outside the transaction:** prefs + rebalance (`:112,115`)
9. `usage_events` deliberately cleared and never restored (`:67-69`)

⚠️ **The id-for-id restore is a hard constraint and the reason SwiftData is unusable.**
`PersistentIdentifier` cannot be assigned. Under SwiftData you'd need an old→new map for six tables
and rewrite every referring column — exactly the class of remap that caused P0-2.

📋 **Still open (fix on iOS, don't port):** prefs/rebalance save outside the transaction leaves a
hybrid state on failure (P2-18); `resetEverything` (`:118-133`) leaves profile, coach stores, UI
prefs, and API keys untouched despite saying "permanently delete everything" (P2-18);
`EffectiveTargets.kt:61,87-88` uses strict `LocalDate.parse` on persisted dates, so a corrupt backup
crash-loops the dashboard (P2-10).

### 6.2 Personal foods

`domain/foodimport/PersonalFoodsJsonCodec.kt` — `{version:Int=1, exportedAt, foods:[{name,
servingName, calories, proteinG, carbsG, fatG}]}` (`:66-70`).
⚠️ **Strict `==` version check** (`:42-44`), unlike the backup's `<=`. Don't unify silently.

### 6.3 `.rtroutine` — the cross-platform bridge

`domain/share/RoutineShareModels.kt` + `RoutineShareSerializer.kt`.
`APP_ID = "recomptracker"` (`:9`), `CURRENT_SHARE_VERSION = 1` (`:16`).

Payload (`:20-26`): `{version, app, name, note?, exercises:[SharedExercise]}`.
`SharedExercise` (`:34-40`): `{source, externalId, name, note?, sets:[SharedSet]}` —
**deliberately carries portable identity `(source, externalId, name)` and NOT the local
`exerciseId`** (KDoc at `:28-32`). `SharedSet`: `{setNumber, targetReps?, targetWeightKg?}`, always kg.

Decode order matters (`RoutineShareSerializer.kt:9-11,30-37`): parse leniently → `app != APP_ID` →
`NotARoutineFile`; `version > CURRENT` → `UnsupportedVersion`; blank name / no exercises / any
set-less exercise → `Damaged`. Typed result enum, never throws.

**This format was designed for portability. iOS can read and write Android `.rtroutine` files
byte-compatibly.** Declare a UTI conforming to `public.data` with extension `rtroutine`.

---

## 7. Bundled assets and data volume

| Asset | Size | Records | Seeded into DB? |
|---|---|---|---|
| `assets/exercises/exercises.json` | **978 KB** | 873 exercises | **Yes** → `exercises`, version-gated on `source="free-exercise-db"` / `version="2026-06-17"` (`ExerciseLibraryRepository.kt:59-74`), id-preserving upsert |
| `assets/knowledge/corpus.json` | **113 KB** | 77 chunks | No — in-memory RAG only |
| `assets/muscles/body_front.json` | 24 KB | 89 SVG paths | No — parsed to Compose `Path` |
| `assets/muscles/body_back.json` | 21 KB | — | No |

**NEVO is NOT bundled** — it's a user-supplied CSV import (`FoodCatalogRepository.replaceNevoCatalog`),
`NEVO_SOURCE="NEVO"`, `SUPPORTED_NEVO_VERSION="2025/9.0"`. The parser handles **two distinct CSV
layouts** (long/tidy pipe-delimited vs wide) plus BOM stripping and delimiter detection.
**Deferred to iOS v1.1** — Android backup import carries `catalogFoods` in v2, which covers anyone
switching platforms.

### 7.1 Volume and known perf debt

📋 **No pagination anywhere.** Zero `PagingSource`/`Pager`. Every list surface reads the whole table:
`MealEntryDao.observeAll()`, `DailyLogDao.observeAll()`, `WorkoutSessionDao.observeCompletedSessions()`
(**every completed session with all nested sets, unbounded**), `CatalogFoodDao.observeAll()`.

| Table | Realistic magnitude |
|---|---|
| `exercises` | ~873 + custom |
| `catalog_foods` | thousands (NEVO) |
| `meal_entries` | **thousands to low tens of thousands** — largest user table, driven by the 365-day import |
| `daily_logs` | ~365/yr |
| `session_sets` | thousands/yr |
| `usage_events` | **fastest-growing, unbounded** |

📋 Open issues worth fixing *on the way over* rather than porting:
- **`usage_events` has no index on `timestampEpochMs`** despite both queries filtering on it, and no
  retention sweep (P2-24).
- Food-library search filters the full NEVO catalog **on the main thread per keystroke**
  (`FoodLibraryViewModel.kt:372-377`).
- 6 of 7 core ViewModels hold `combine` pipelines for their whole lifetime; only `StreakViewModel`
  uses `stateIn(WhileSubscribed)` (P2-21).

---

## 8. Portability summary

**Green — port as-is:** all 19 entity shapes (no type converters), ~95 of 109 queries, all 10
preference stores, all 8 JSON blob codecs, all three file formats, all four bundled assets, the
`DateProvider` abstraction, the two-secret store.

**Yellow — reimplement with care:** the 19 transaction bodies (each encodes a fixed bug), the
`NevoCsvParser` (v1.1), the muscle-path renderer, the tolerant-vs-intolerant codec split.

**Red — a naive translation actively breaks correctness:**

| # | Trap | Consequence |
|---|---|---|
| 1 | Dates as `Date` instead of `YYYY-MM-DD` strings | Every range predicate changes meaning; rows shift across days |
| 2 | Whole-object saves instead of per-column UPDATEs | Reintroduces P1-18 and P2-7 (lost concurrent writes) |
| 3 | SwiftData | Id-for-id restore is unimplementable; bulk-insert cliff |
| 4 | Modelling `meal_entries.slotId` as a relationship | A real FK rejects the legitimately-dangling ids this schema depends on |
| 5 | `exercises.*Muscles/instructions/images` as native `[String]` | Breaks byte-compatibility with Android backups (JSON-in-TEXT) |
| 6 | Default-locale `DateFormatter` | Corrupts the TEXT primary keys of three tables |
