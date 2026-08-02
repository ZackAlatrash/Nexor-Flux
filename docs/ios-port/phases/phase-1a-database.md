# iOS Port Phase 1a — GRDB Database Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the iOS persistence layer in GRDB — 19 tables at Room schema v15, every record
type, the non-trivial queries, and the seven transaction bodies — with the schema proven equivalent
to Android's byte for byte.

**Architecture:** A single `DatabaseQueue` opened from the app container, with a `DatabaseMigrator`
that creates the v15 schema in one `v15-initial` migration (see §Decisions). Each table gets a Swift
`struct` conforming to `Codable`, `FetchableRecord` and `MutablePersistableRecord`. **Dates persist
as `YYYY-MM-DD` strings** (D6) and every range query compares lexicographically. Reads that the UI
will observe use `ValueObservation`.

**Tech Stack:** Swift 6.3.2, Xcode 26.5, iOS 26 deployment target, **GRDB 7.11.1** via SPM,
Swift Testing (`@Test`/`#expect`).

**Repo:** `~/Desktop/RecompTracker-IOS/` (sibling of the Android repo — see D11). The Xcode project
is at `RecompTracker/RecompTracker.xcodeproj`.

**Ends with:** a schema-equivalence test asserting GRDB's `sqlite_master` matches Room's v15 DDL,
plus round-trip coverage of every table and all seven transactions. It does **not** import a backup
— that is Phase 1b.

---

## ⚠️ Amendment — applied 2026-08-02 after Tasks 1–4 were built

**Primary keys are `Int64?`, not `Int64`.** The plan originally mirrored Android's `id = 0`
sentinel literally. In Swift that fights GRDB: a record with `id = 0` inserts *rowid 0* rather than
auto-assigning, and nulling it out needs an `encode(to:)` override that cannot cleanly call through
to the Codable default.

`Int64?` expresses the same idea natively — **`nil` means "assign me one", a value means "use
exactly this"** — and GRDB encodes `nil` to SQL NULL, which is precisely what `nullif(?, 0)`
produced. Behaviour matches Android with no encoder override.

Consequences when you write code from this plan:
- Every record's `id` is `Int64?`. **Foreign-key fields stay non-optional** (`recipeId: Int64`,
  `sessionId: Int64`, …) — only the primary key changes.
- Android's `.copy(id = 0)` idiom becomes `copy.id = nil`.
- Reading an id back after insert gives `Int64?`; unwrap with `!` where a subsequent insert needs
  it as a parent key. The compiler will point at every site.

Tasks 1–4 are **already implemented and green** (13 tests). `IDPreservation.swift` as shipped is
below; it supersedes the version in Task 4.

```swift
protocol IDPreservingRecord: MutablePersistableRecord {
    var id: Int64? { get set }
}

extension IDPreservingRecord {
    mutating func didInsert(_ inserted: InsertionSuccess) { id = inserted.rowID }
    mutating func insertPreservingID(_ db: Database) throws { try insert(db) }
}
```

**🔴 `error: circular reference` on a record declaration — read this before writing any Swift type
that conforms to `IDPreservingRecord`.** The app target builds with
`SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`. A MainActor-isolated struct conforming to a
*same-module* protocol that refines `MutablePersistableRecord` sends the compiler into
`error: circular reference` — with no notes, pointing at the struct itself. It does **not** happen
in the test target (which gets the protocol cross-module via `@testable import`), which is why
Tasks 1–4 never saw it.

**Already fixed at the root:** `IDPreservingRecord` is declared `nonisolated`. You need no
per-struct annotation and no extension-based conformance — declare it inline like any other
protocol. If you see the error anyway, check that `nonisolated` is still on the protocol rather
than working around it locally.

Also settled while building Tasks 1–4, so do not re-derive:
- The test target was created for **macOS**; it is now retargeted to iOS with `TEST_HOST` and a
  dependency on the app target.
- A **shared** scheme (`xcshareddata/xcschemes/RecompTracker.xcscheme`) provides the test action.
  Xcode's auto-generated scheme lives in gitignored `xcuserdata` and would break every fresh clone.
- Deployment target is **iOS 26.0** across all four configs (was 26.5).
- `PRAGMA table_info` reports `notnull = 1` for `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`,
  so schema assertions must expect `"INTEGER NOT NULL"` for id columns.
- **`#expect` cannot be used inside a throwing closure** — `try db.reader.read { #expect(…) }`
  fails with "errors thrown from here are not handled", because the macro expands into a
  non-throwing context. Write `#expect(try db.reader.read { … } == expected)` instead.
- Tests live flat in `RecompTrackerTests/`, not the `RecompTrackerTests/Persistence/` subfolder the
  File-structure table suggests. Follow the existing files.
- **Tasks 5–8 are complete and merged** (29 tests green): all 18 record types plus
  `RecipeWithIngredients`. `SessionExerciseWithSets` and `SessionWithDetails` are deliberately
  *not* written yet — Task 10 defines them alongside `TrainingQueries`.

---

## Context you need before starting

Read, in order:
1. `docs/ios-port/STATUS.md` (Android repo) — where the port is
2. `docs/ios-port/decisions.md` — **D3** (GRDB not SwiftData), **D6** (dates as strings), **D11**
   (two-repo layout), **D13** (this split) are binding
3. `docs/ios-port/reference/data-model.md` — the full column-by-column inventory

Everything below was verified against Android `develop` @ `e54e880` on 2026-08-02.

### Five facts that shape every task

**1. 🔴 `nullif(?, 0)` on every auto-id insert — the single most important mechanism here.**
Room generates every `@PrimaryKey(autoGenerate = true)` insert as
`INSERT INTO t (id, …) VALUES (nullif(?, 0), …)`. So:
- `id = 0` → binds NULL → SQLite assigns the next rowid
- `id = 4711` → **that exact rowid is used**

Backup restore depends on the second (ids must survive), and the `.copy(id = 0)` re-insert idiom
in three transactions depends on the first. **A GRDB port that always binds the id literally
inserts `id = 0` and collides on the second row; one that always omits it destroys every foreign
key in a restored backup.** Task 7 builds and tests this explicitly.

**2. All 16 auto-id tables are `AUTOINCREMENT`**, not plain `INTEGER PRIMARY KEY`. That gives a
never-reuse guarantee. It matters because `meal_entries.slotId` has **no foreign key** (finding D6
below), so a reused id could silently re-point a dangling reference at the wrong slot.

**3. 🔴 Android's migrated and fresh-install databases genuinely differ.** Migrations declare
`DEFAULT 0` / `DEFAULT 1` on five Boolean columns; no entity declares `@ColumnInfo(defaultValue=…)`,
so Room's fresh-install DDL omits them. Same schema version, different DDL. **We adopt the `DEFAULT`
form** — see §Decisions.

**4. Column order differs between the two Android forms too** (`ALTER TABLE` appends; `CREATE TABLE`
follows constructor order). Harmless for name-based access, fatal for ordinal access. **Never use
`SELECT *` with positional indexing and never `INSERT INTO t VALUES (…)` without a column list.**

**5. There is no test fixture.** No backup JSON, `.rtroutine`, or golden file exists anywhere in the
Android repo. Phase 1b needs a real device export; 1a does not.

---

## Decisions this plan makes

**Dec-1: One `v15-initial` migration, not fourteen replayed ones.**
The roadmap suggested mirroring Android's 14 migrations. Don't. No iOS device has ever held a v1–v14
database, so replaying them creates a table only to rebuild it (10→11) and drops data that was never
there. Instead: one migration named `v15-initial` that creates the final schema, with Android's
migration history preserved as a comment block for provenance. **Future migrations are numbered from
`v16` in lockstep with Android**, so the two histories stay aligned from here on.

**Dec-2: Adopt the `DEFAULT` clauses (the migrated-device form).**
Fresh Android installs lack them; upgraded devices have them. Neither is "more correct", but
`DEFAULT` is strictly safer — a raw insert omitting the column succeeds instead of failing NOT NULL —
and it matches the majority of real devices. Record it in the schema file so the divergence is
deliberate, not accidental.

**Dec-3: `eraseDatabaseOnSchemaChange` is DEBUG-only and never committed as `true`.**
GRDB's convenience for schema iteration silently wipes the database. Guard it behind `#if DEBUG` and
a launch argument, never a bare `true`.

**Dec-4: Records are `MutablePersistableRecord`, not `PersistableRecord`.**
Auto-id tables need `didInsert` to write the assigned rowid back, which requires the mutable variant.

---

## File structure

All paths relative to `~/Desktop/RecompTracker-IOS/RecompTracker/RecompTracker/`.

| Path | Responsibility |
|---|---|
| `Persistence/AppDatabase.swift` | Opens the `DatabaseQueue`, owns the migrator, exposes `read`/`write` |
| `Persistence/Schema/Migrations.swift` | The `v15-initial` migration — all 19 `CREATE TABLE` + 17 `CREATE INDEX` |
| `Persistence/Records/NutritionRecords.swift` | `DailyLog`, `MealEntry`, `MealSlot`, `SavedFood`, `SavedMeal`, `CatalogFood` |
| `Persistence/Records/PlanRecords.swift` | `PlanVersion`, `WeeklyReview`, `LiftPerformance`, `UsageEvent` |
| `Persistence/Records/RecipeRecords.swift` | `Recipe`, `RecipeIngredient`, `RecipeWithIngredients` |
| `Persistence/Records/TrainingRecords.swift` | `Exercise`, `Workout`, `WorkoutExercise`, `PlannedSet`, `WorkoutSession`, `SessionExercise`, `SessionSet` + the three projections |
| `Persistence/Queries/DailyLogQueries.swift` | Date-range reads, the `INSERT OR IGNORE` materializer, the six per-column updates |
| `Persistence/Queries/MealEntryQueries.swift` | Date-range reads, the `9999-12-31` sentinel, planned-meal state writes |
| `Persistence/Queries/TrainingQueries.swift` | The 3-table JOIN, the relation graphs, the allocators, the four per-column set updates |
| `Persistence/Queries/UsageQueries.swift` | The `GROUP BY` aggregate |
| `Persistence/Transactions/` | The seven imperative transaction bodies, one file per DAO origin |
| `Persistence/IDPreservation.swift` | The `nullif(?, 0)` equivalent — insert-preserving-id vs insert-assigning-id |

Tests mirror this under `RecompTrackerTests/Persistence/`.

---

## Task 1: Add GRDB and open a database

**Files:** Modify the Xcode project (SPM dependency); create
`Persistence/AppDatabase.swift`, `RecompTrackerTests/Persistence/AppDatabaseTests.swift`

- [ ] **Step 1: Add the SPM dependency**

Xcode → File → Add Package Dependencies → `https://github.com/groue/GRDB.swift` → **Up to Next Major
7.11.1** → add `GRDB` to the `RecompTracker` target.

Verify: `xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: **BUILD SUCCEEDED**.

- [ ] **Step 2: Write the failing test**

Create `RecompTrackerTests/Persistence/AppDatabaseTests.swift`:
```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct AppDatabaseTests {
    @Test func opensInMemoryAndAppliesMigrations() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let applied = try db.reader.read { try AppDatabase.migrator.appliedIdentifiers($0) }
        #expect(applied.contains("v15-initial"))
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`
Expected: **FAIL** — "cannot find 'AppDatabase' in scope".

- [ ] **Step 4: Implement `AppDatabase`**

Create `Persistence/AppDatabase.swift`:
```swift
import Foundation
import GRDB

/// Owns the single SQLite connection. Mirrors Android's Room database (schema v15).
///
/// Dates are stored as `YYYY-MM-DD` strings and compared lexicographically — see decision D6.
/// Never store a `Date` in a date column; every range predicate in this layer depends on the
/// string ordering, including the `"9999-12-31"` open-ended sentinel.
struct AppDatabase {
    let writer: any DatabaseWriter
    var reader: any DatabaseReader { writer }

    init(_ writer: any DatabaseWriter) throws {
        self.writer = writer
        try Self.migrator.migrate(writer)
    }

    /// On-disk database in Application Support, matching Android's `recomp_tracker.db` name.
    static func onDisk() throws -> AppDatabase {
        let folder = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true
        )
        let url = folder.appendingPathComponent("recomp_tracker.db")
        var config = Configuration()
        config.foreignKeysEnabled = true   // Room enables FKs at open; several ON DELETE CASCADE
                                           // chains and two NO ACTION guards depend on it.
        return try AppDatabase(DatabaseQueue(path: url.path, configuration: config))
    }

    static func inMemoryForTesting() throws -> AppDatabase {
        var config = Configuration()
        config.foreignKeysEnabled = true
        return try AppDatabase(DatabaseQueue(configuration: config))
    }
}
```

- [ ] **Step 5: Add the empty migrator**

Create `Persistence/Schema/Migrations.swift`:
```swift
import GRDB

extension AppDatabase {
    static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        // Decision Dec-3: NEVER commit this as an unguarded `true` — it wipes the database.
        #if DEBUG
        if CommandLine.arguments.contains("--reset-database-on-schema-change") {
            migrator.eraseDatabaseOnSchemaChange = true
        }
        #endif
        migrator.registerMigration("v15-initial") { db in
            // Filled in by Task 2.
        }
        return migrator
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run the `xcodebuild test` command from Step 3. Expected: **PASS**.

- [ ] **Step 7: Commit**

```bash
cd ~/Desktop/RecompTracker-IOS
git add -A
git commit -m "feat(db): add GRDB and open a migrated database"
```

---

## Task 2: The v15 schema

The DDL below is Room's own fresh-install output (from the KSP-generated `RecompDatabase_Impl.kt`),
**plus** the `DEFAULT` clauses per Dec-2. Type it exactly — this is the contract Task 3 asserts against.

**Files:** Modify `Persistence/Schema/Migrations.swift`

- [ ] **Step 1: Write the 19 tables and 17 indexes**

Replace the empty `registerMigration("v15-initial")` body with:

```swift
migrator.registerMigration("v15-initial") { db in
    // Room schema v15, reached on Android through 14 incremental migrations (v1→v15).
    // iOS has no v1–v14 databases in the wild, so we create the final shape directly
    // (decision Dec-1). Future migrations are numbered from v16 in lockstep with Android.
    //
    // DEFAULT clauses follow the *migrated-device* form (decision Dec-2): Android's
    // migrations declare them but its fresh-install DDL does not, so the two diverge.
    // We adopt DEFAULT because an insert omitting the column then succeeds rather than
    // failing NOT NULL.

    try db.execute(sql: """
        CREATE TABLE daily_logs (
            date TEXT NOT NULL, bodyWeightKg REAL, waistCm REAL, waistSkinfoldMm REAL,
            steps INTEGER, stepsSource TEXT, sleepHours REAL, energyScore INTEGER,
            hungerScore INTEGER, sorenessScore INTEGER, trained INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '', PRIMARY KEY(date))
        """)

    try db.execute(sql: """
        CREATE TABLE catalog_foods (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL,
            sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, name TEXT NOT NULL,
            servingName TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL,
            carbsG REAL NOT NULL, fatG REAL NOT NULL)
        """)
    try db.execute(sql: "CREATE UNIQUE INDEX index_catalog_foods_source_externalId ON catalog_foods (source, externalId)")
    try db.execute(sql: "CREATE INDEX index_catalog_foods_name ON catalog_foods (name)")

    try db.execute(sql: """
        CREATE TABLE meal_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL,
            mealType TEXT NOT NULL, name TEXT NOT NULL, calories INTEGER NOT NULL,
            proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL,
            slotId INTEGER, amountGrams REAL, basePer100Calories INTEGER,
            basePer100ProteinG REAL, basePer100CarbsG REAL, basePer100FatG REAL,
            entryServingName TEXT, entryServingGrams REAL,
            loggedByServings INTEGER NOT NULL DEFAULT 0, planned INTEGER NOT NULL DEFAULT 0)
        """)
    try db.execute(sql: "CREATE INDEX index_meal_entries_date ON meal_entries (date)")

    try db.execute(sql: """
        CREATE TABLE saved_foods (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
            servingName TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL,
            carbsG REAL NOT NULL, fatG REAL NOT NULL, householdServingName TEXT,
            householdServingGrams REAL)
        """)

    try db.execute(sql: """
        CREATE TABLE saved_meals (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
            mealType TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL,
            carbsG REAL NOT NULL, fatG REAL NOT NULL)
        """)

    try db.execute(sql: """
        CREATE TABLE lift_performance (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL,
            liftName TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL,
            sets INTEGER NOT NULL, rir INTEGER)
        """)
    try db.execute(sql: "CREATE INDEX index_lift_performance_date ON lift_performance (date)")
    try db.execute(sql: "CREATE INDEX index_lift_performance_liftName ON lift_performance (liftName)")

    try db.execute(sql: """
        CREATE TABLE weekly_reviews (
            weekStart TEXT NOT NULL, verdict TEXT NOT NULL,
            recommendedCalorieChange INTEGER NOT NULL, reasonCodes TEXT NOT NULL,
            generatedAt TEXT NOT NULL, briefingJson TEXT, briefingSignature TEXT,
            briefingGeneratedAt TEXT, PRIMARY KEY(weekStart))
        """)

    // NOTE: sort_order is the ONLY snake_case column in the schema. Its backup JSON key is
    // `sortOrder` (kotlinx.serialization uses the Kotlin property name) — column name and
    // JSON key differ for this one field. Phase 1b's codec must handle that.
    try db.execute(sql: """
        CREATE TABLE meal_slots (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL,
            sort_order INTEGER NOT NULL)
        """)

    try db.execute(sql: "CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")

    try db.execute(sql: """
        CREATE TABLE recipe_ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, recipeId INTEGER NOT NULL,
            name TEXT NOT NULL, sortOrder INTEGER NOT NULL, calories INTEGER NOT NULL,
            proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL,
            amountGrams REAL, basePer100Calories INTEGER, basePer100ProteinG REAL,
            basePer100CarbsG REAL, basePer100FatG REAL, entryServingName TEXT,
            entryServingGrams REAL, loggedByServings INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """)
    try db.execute(sql: "CREATE INDEX index_recipe_ingredients_recipeId ON recipe_ingredients (recipeId)")

    // primaryMuscles / secondaryMuscles / instructions / images are JSON-array-in-TEXT,
    // NOT NULL. Do not model them as [String] — the backup carries them as JSON strings.
    try db.execute(sql: """
        CREATE TABLE exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL,
            sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, name TEXT NOT NULL,
            category TEXT, force TEXT, level TEXT, mechanic TEXT, equipment TEXT,
            primaryMuscles TEXT NOT NULL, secondaryMuscles TEXT NOT NULL,
            instructions TEXT NOT NULL, images TEXT NOT NULL,
            userCreated INTEGER NOT NULL DEFAULT 0)
        """)
    try db.execute(sql: "CREATE UNIQUE INDEX index_exercises_source_externalId ON exercises (source, externalId)")
    try db.execute(sql: "CREATE INDEX index_exercises_name ON exercises (name)")

    try db.execute(sql: """
        CREATE TABLE workouts (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, note TEXT,
            createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)
        """)

    // exerciseId is ON DELETE NO ACTION deliberately: two production crashes (P1-19, P1-20)
    // came from delete-then-insert re-seeds of the exercise library. Keep NO ACTION.
    try db.execute(sql: """
        CREATE TABLE workout_exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL,
            exerciseId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, note TEXT,
            FOREIGN KEY(workoutId) REFERENCES workouts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """)
    try db.execute(sql: "CREATE INDEX index_workout_exercises_workoutId ON workout_exercises (workoutId)")
    try db.execute(sql: "CREATE INDEX index_workout_exercises_exerciseId ON workout_exercises (exerciseId)")

    try db.execute(sql: """
        CREATE TABLE planned_sets (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutExerciseId INTEGER NOT NULL,
            setNumber INTEGER NOT NULL, targetReps INTEGER, targetWeightKg REAL,
            FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """)
    try db.execute(sql: "CREATE INDEX index_planned_sets_workoutExerciseId ON planned_sets (workoutExerciseId)")

    try db.execute(sql: """
        CREATE TABLE workout_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER,
            workoutName TEXT NOT NULL, date TEXT NOT NULL, startedAt TEXT NOT NULL,
            completedAt TEXT, status TEXT NOT NULL, note TEXT, durationSeconds INTEGER,
            FOREIGN KEY(workoutId) REFERENCES workouts(id) ON UPDATE NO ACTION ON DELETE SET NULL)
        """)
    try db.execute(sql: "CREATE INDEX index_workout_sessions_workoutId ON workout_sessions (workoutId)")
    try db.execute(sql: "CREATE INDEX index_workout_sessions_date ON workout_sessions (date)")
    try db.execute(sql: "CREATE INDEX index_workout_sessions_status ON workout_sessions (status)")

    try db.execute(sql: """
        CREATE TABLE session_exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId INTEGER NOT NULL,
            exerciseId INTEGER NOT NULL, exerciseName TEXT NOT NULL, sortOrder INTEGER NOT NULL,
            note TEXT,
            FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE NO ACTION)
        """)
    try db.execute(sql: "CREATE INDEX index_session_exercises_sessionId ON session_exercises (sessionId)")
    try db.execute(sql: "CREATE INDEX index_session_exercises_exerciseId ON session_exercises (exerciseId)")

    // completed is the ONLY Boolean in the schema defaulting to 1.
    try db.execute(sql: """
        CREATE TABLE session_sets (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionExerciseId INTEGER NOT NULL,
            setNumber INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL, rir INTEGER,
            completed INTEGER NOT NULL DEFAULT 1,
            FOREIGN KEY(sessionExerciseId) REFERENCES session_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)
        """)
    try db.execute(sql: "CREATE INDEX index_session_sets_sessionExerciseId ON session_sets (sessionExerciseId)")

    try db.execute(sql: """
        CREATE TABLE plan_versions (
            effectiveFrom TEXT NOT NULL, targetCalories INTEGER NOT NULL,
            targetProteinG INTEGER NOT NULL, targetCarbsG INTEGER NOT NULL,
            targetFatG INTEGER NOT NULL, calorieZoneLowerBound INTEGER NOT NULL,
            calorieZoneUpperBound INTEGER NOT NULL, createdAt TEXT NOT NULL,
            PRIMARY KEY(effectiveFrom))
        """)

    // usage_events is the only table with no @Serializable entity — it can never appear in a
    // backup, and restore deliberately leaves it empty.
    try db.execute(sql: """
        CREATE TABLE usage_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampEpochMs INTEGER NOT NULL,
            type TEXT NOT NULL, label TEXT)
        """)

    // ⚠️ MISSING ON ANDROID, ADDED HERE: usage_events grows unboundedly and both of its
    // queries filter on timestampEpochMs with no index (Android review P2-24). Adding it now
    // costs nothing and is a divergence in our favour; note it in the parity ledger.
    try db.execute(sql: "CREATE INDEX index_usage_events_timestampEpochMs ON usage_events (timestampEpochMs)")
}
```

- [ ] **Step 2: Build**

Run the `xcodebuild build` command from Task 1 Step 1. Expected: **BUILD SUCCEEDED**.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat(db): create the v15 schema — 19 tables, 18 indexes"
```

---

## Task 3: Prove the schema matches Android

This is Phase 1a's acceptance test. Any drift between the two schemas surfaces here rather than as a
mysterious decode failure in Phase 1b.

**Files:** Create `RecompTrackerTests/Persistence/SchemaEquivalenceTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

/// Pins the schema against Android's Room v15. Reference:
/// docs/ios-port/reference/data-model.md in the Android repo.
@Suite struct SchemaEquivalenceTests {

    private func columns(_ db: AppDatabase, _ table: String) throws -> [String: String] {
        try db.reader.read { d in
            try Row.fetchAll(d, sql: "PRAGMA table_info(\(table))")
                .reduce(into: [String: String]()) { acc, row in
                    let name: String = row["name"]
                    let type: String = row["type"]
                    let notNull: Int = row["notnull"]
                    acc[name] = "\(type)\(notNull == 1 ? " NOT NULL" : "")"
                }
        }
    }

    @Test func hasExactlyTheNineteenTables() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let tables = try db.reader.read { d in
            try String.fetchSet(d, sql: """
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
                  AND name NOT LIKE 'grdb_%'
                """)
        }
        #expect(tables == [
            "daily_logs", "catalog_foods", "meal_entries", "saved_foods", "saved_meals",
            "lift_performance", "weekly_reviews", "meal_slots", "recipes", "recipe_ingredients",
            "exercises", "workouts", "workout_exercises", "planned_sets", "workout_sessions",
            "session_exercises", "session_sets", "plan_versions", "usage_events",
        ])
    }

    @Test func dailyLogsMatchesRoom() throws {
        let db = try AppDatabase.inMemoryForTesting()
        #expect(try columns(db, "daily_logs") == [
            "date": "TEXT NOT NULL", "bodyWeightKg": "REAL", "waistCm": "REAL",
            "waistSkinfoldMm": "REAL", "steps": "INTEGER", "stepsSource": "TEXT",
            "sleepHours": "REAL", "energyScore": "INTEGER", "hungerScore": "INTEGER",
            "sorenessScore": "INTEGER", "trained": "INTEGER NOT NULL", "notes": "TEXT NOT NULL",
        ])
    }

    @Test func mealEntriesMatchesRoom() throws {
        let db = try AppDatabase.inMemoryForTesting()
        #expect(try columns(db, "meal_entries") == [
            "id": "INTEGER", "date": "TEXT NOT NULL", "mealType": "TEXT NOT NULL",
            "name": "TEXT NOT NULL", "calories": "INTEGER NOT NULL", "proteinG": "REAL NOT NULL",
            "carbsG": "REAL NOT NULL", "fatG": "REAL NOT NULL", "slotId": "INTEGER",
            "amountGrams": "REAL", "basePer100Calories": "INTEGER",
            "basePer100ProteinG": "REAL", "basePer100CarbsG": "REAL", "basePer100FatG": "REAL",
            "entryServingName": "TEXT", "entryServingGrams": "REAL",
            "loggedByServings": "INTEGER NOT NULL", "planned": "INTEGER NOT NULL",
        ])
    }

    @Test func hasEveryExpectedIndex() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let indexes = try db.reader.read { d in
            try String.fetchSet(d, sql: """
                SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%'
                """)
        }
        #expect(indexes == [
            "index_catalog_foods_source_externalId", "index_catalog_foods_name",
            "index_meal_entries_date", "index_lift_performance_date",
            "index_lift_performance_liftName", "index_recipe_ingredients_recipeId",
            "index_exercises_source_externalId", "index_exercises_name",
            "index_workout_exercises_workoutId", "index_workout_exercises_exerciseId",
            "index_planned_sets_workoutExerciseId", "index_workout_sessions_workoutId",
            "index_workout_sessions_date", "index_workout_sessions_status",
            "index_session_exercises_sessionId", "index_session_exercises_exerciseId",
            "index_session_sets_sessionExerciseId",
            "index_usage_events_timestampEpochMs",   // ours, not Android's — see Task 2
        ])
    }

    @Test func everyAutoIdTableIsAutoincrement() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let sql = try db.reader.read { d in
            try String.fetchAll(d, sql: """
                SELECT sql FROM sqlite_master WHERE type = 'table' AND sql LIKE '%AUTOINCREMENT%'
                """)
        }
        // 16 auto-id tables; daily_logs, weekly_reviews and plan_versions have TEXT PKs.
        #expect(sql.count == 16)
    }

    @Test func foreignKeysAreEnforced() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let enabled = try db.reader.read { try Bool.fetchOne($0, sql: "PRAGMA foreign_keys") }
        #expect(enabled == true)
    }
}
```

- [ ] **Step 2: Run it**

Run: `xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker -destination 'platform=iOS Simulator,name=iPhone 17 Pro'`
Expected: **all six PASS.** If a column set mismatches, the schema in Task 2 is wrong — fix the
schema, not the test. The expected values come from Room's generated DDL.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test(db): pin the schema against Room v15"
```

---

## Task 4: The id-preservation mechanism

🔴 Build this before any record type, because every record's insert path depends on it.

**Files:** Create `Persistence/IDPreservation.swift`,
`RecompTrackerTests/Persistence/IDPreservationTests.swift`

- [ ] **Step 1: Write the failing test**

The test defines its **own** record against the `recipes` table rather than using one from a later
task — the protocol and the records would otherwise depend on each other and neither could be
written first.

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct IDPreservationTests {

    /// Test-local record over the simplest auto-id table. Deliberately not one of the production
    /// record types — those arrive in Tasks 5–8 and conform to this protocol, so using one here
    /// would make the two tasks circular.
    private struct Probe: Codable, FetchableRecord, IDPreservingRecord {
        static let databaseTableName = "recipes"
        var id: Int64?
        var name: String
    }

    @Test func insertWithZeroIdAssignsANewRowid() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let saved = try db.writer.write { d -> Probe in
            var p = Probe(id: nil, name: "auto")
            try p.insertPreservingID(d)
            return p
        }
        #expect(saved.id > 0)
    }

    @Test func insertWithExplicitIdKeepsThatExactId() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var p = Probe(id: 4711, name: "explicit")
            try p.insertPreservingID(d)
        }
        #expect(try db.reader.read { try Probe.fetchOne($0, key: 4711) }?.name == "explicit")
    }

    /// The behaviour a restored backup depends on: rows with explicit, non-contiguous ids land on
    /// exactly those ids, and a later auto-id row does not collide with them.
    @Test func explicitIdsCoexistWithLaterAutoIds() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for id in [10, 200] {
                var p = Probe(id: Int64(id), name: "row\(id)")
                try p.insertPreservingID(d)
            }
            var fresh = Probe(id: nil, name: "fresh")
            try fresh.insertPreservingID(d)
            #expect(fresh.id == 201)   // AUTOINCREMENT continues above the high-water mark
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — "cannot find type 'IDPreservingRecord'". The `Probe` record makes this task
self-contained, so the failure should be about the protocol only.

- [ ] **Step 3: Implement**

Create `Persistence/IDPreservation.swift`:
```swift
import GRDB

/// Reproduces Room's `nullif(?, 0)` insert semantics.
///
/// Room generates every autoGenerate insert as `INSERT INTO t (id, …) VALUES (nullif(?, 0), …)`:
/// an id of 0 binds NULL and SQLite assigns the next rowid, while any non-zero id is used
/// verbatim. Both halves are load-bearing —
///   • backup restore inserts rows with their original ids so foreign keys survive (P0-1/P0-2)
///   • three transactions re-insert with `id = 0` to force fresh ids
/// A port that always binds the id inserts `id = 0` and collides on the second row; one that
/// always omits it destroys every foreign key in a restored backup.
protocol IDPreservingRecord: MutablePersistableRecord {
    var id: Int64 { get set }
}

extension IDPreservingRecord {
    /// Inserts, honouring a non-zero `id` and assigning a fresh one when `id == 0`.
    mutating func insertPreservingID(_ db: Database) throws {
        if id == 0 {
            var copy = self
            try copy.insert(db)          // GRDB writes the assigned rowid back via didInsert
            self.id = copy.id
        } else {
            try insert(db)               // explicit id is written as-is
        }
    }

    mutating func didInsert(_ inserted: InsertionSuccess) {
        if id == 0 { id = inserted.rowID }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Expected: **PASS** — all three, with no dependency on any later task.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): reproduce Room's nullif(?,0) id-preservation semantics"
```

---

## Task 5: Nutrition record types

**Files:** Create `Persistence/Records/NutritionRecords.swift`,
`RecompTrackerTests/Persistence/NutritionRecordsTests.swift`

- [ ] **Step 1: Write the failing round-trip test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct NutritionRecordsTests {

    @Test func dailyLogRoundTripsWithAllNullablesNil() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let log = DailyLog(date: "2026-08-02", bodyWeightKg: nil, waistCm: nil,
                           waistSkinfoldMm: nil, steps: nil, stepsSource: nil, sleepHours: nil,
                           energyScore: nil, hungerScore: nil, sorenessScore: nil,
                           trained: false, notes: "")
        try db.writer.write { try log.insert($0) }
        #expect(try db.reader.read { try DailyLog.fetchOne($0, key: "2026-08-02") } == log)
    }

    @Test func dailyLogRoundTripsFullyPopulated() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let log = DailyLog(date: "2026-08-02", bodyWeightKg: 82.4, waistCm: 84.0,
                           waistSkinfoldMm: 12.5, steps: 9123, stepsSource: "health_connect",
                           sleepHours: 7.25, energyScore: 8, hungerScore: 4, sorenessScore: 3,
                           trained: true, notes: "felt good")
        try db.writer.write { try log.insert($0) }
        #expect(try db.reader.read { try DailyLog.fetchOne($0, key: "2026-08-02") } == log)
    }

    @Test func mealEntryPreservesADanglingSlotId() throws {
        // meal_entries.slotId has NO foreign key on Android — coach-logged meals carry nil, and
        // historical entries can reference a deleted slot. Modelling it as a relationship would
        // reject data this schema legitimately contains.
        let db = try AppDatabase.inMemoryForTesting()
        var entry = MealEntry(id: nil, date: "2026-08-02", mealType: "LUNCH", name: "Rice",
                              calories: 300, proteinG: 6, carbsG: 65, fatG: 1,
                              slotId: 99999, amountGrams: 200, basePer100Calories: 150,
                              basePer100ProteinG: 3, basePer100CarbsG: 32.5, basePer100FatG: 0.5,
                              entryServingName: nil, entryServingGrams: nil,
                              loggedByServings: false, planned: false)
        try db.writer.write { try entry.insertPreservingID($0) }
        #expect(try db.reader.read { try MealEntry.fetchOne($0, key: entry.id) }?.slotId == 99999)
    }

    @Test func booleansStoreAsIntegerZeroOne() throws {
        let db = try AppDatabase.inMemoryForTesting()
        var entry = MealEntry(id: nil, date: "2026-08-02", mealType: "LUNCH", name: "x",
                              calories: 1, proteinG: 0, carbsG: 0, fatG: 0, slotId: nil,
                              amountGrams: nil, basePer100Calories: nil, basePer100ProteinG: nil,
                              basePer100CarbsG: nil, basePer100FatG: nil, entryServingName: nil,
                              entryServingGrams: nil, loggedByServings: true, planned: true)
        try db.writer.write { try entry.insertPreservingID($0) }
        let raw = try db.reader.read {
            try Row.fetchOne($0, sql: "SELECT planned, loggedByServings FROM meal_entries")
        }
        #expect(raw?["planned"] == 1)
        #expect(raw?["loggedByServings"] == 1)
    }

    @Test func mealSlotMapsSnakeCaseSortOrder() throws {
        let db = try AppDatabase.inMemoryForTesting()
        var slot = MealSlot(id: nil, name: "Dinner", sortOrder: 2)
        try db.writer.write { try slot.insertPreservingID($0) }
        let raw = try db.reader.read { try Row.fetchOne($0, sql: "SELECT sort_order FROM meal_slots") }
        #expect(raw?["sort_order"] == 2)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — types not found.

- [ ] **Step 3: Implement the records**

Create `Persistence/Records/NutritionRecords.swift`:
```swift
import GRDB

/// `date` is a `YYYY-MM-DD` string (D6). Nine of twelve columns are nullable.
struct DailyLog: Codable, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "daily_logs"
    var date: String
    var bodyWeightKg: Double?
    var waistCm: Double?
    var waistSkinfoldMm: Double?
    var steps: Int?
    /// "manual" | "health_connect" | nil (legacy). Free text on Android — keep it a String.
    var stepsSource: String?
    var sleepHours: Double?
    var energyScore: Int?
    var hungerScore: Int?
    var sorenessScore: Int?
    var trained: Bool
    var notes: String
}

struct MealEntry: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "meal_entries"
    var id: Int64?
    var date: String
    /// Free text; "FOOD_LIBRARY" is used as a discriminator on this shared table.
    var mealType: String
    var name: String
    var calories: Int
    var proteinG: Double
    var carbsG: Double
    var fatG: Double
    /// No foreign key by design — may reference a deleted slot, or be nil for coach-logged meals.
    var slotId: Int64?
    var amountGrams: Double?
    var basePer100Calories: Int?
    var basePer100ProteinG: Double?
    var basePer100CarbsG: Double?
    var basePer100FatG: Double?
    var entryServingName: String?
    var entryServingGrams: Double?
    var loggedByServings: Bool
    /// Planned (not-yet-eaten) entries are excluded from eaten totals, adherence and trend.
    var planned: Bool
}

struct MealSlot: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "meal_slots"
    var id: Int64?
    var name: String
    var sortOrder: Int

    /// The only snake_case column in the schema. Note the backup JSON key is `sortOrder`,
    /// so Phase 1b's codec must NOT reuse these coding keys.
    enum CodingKeys: String, CodingKey {
        case id, name
        case sortOrder = "sort_order"
    }
}

struct SavedFood: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "saved_foods"
    var id: Int64?
    var name: String
    var servingName: String
    var calories: Int
    var proteinG: Double
    var carbsG: Double
    var fatG: Double
    var householdServingName: String?
    var householdServingGrams: Double?
}

struct SavedMeal: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "saved_meals"
    var id: Int64?
    var name: String
    var mealType: String
    var calories: Int
    var proteinG: Double
    var carbsG: Double
    var fatG: Double
}

/// The NEVO catalog. Unique on (source, externalId).
struct CatalogFood: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "catalog_foods"
    var id: Int64?
    var source: String
    var sourceVersion: String
    var externalId: String
    var name: String
    var servingName: String
    var calories: Int
    var proteinG: Double
    var carbsG: Double
    var fatG: Double
}
```

- [ ] **Step 4: Run both this task's and Task 4's tests**

Expected: **all PASS**, including the three `IDPreservationTests` that were red.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): nutrition record types"
```

---

## Task 6: Plan, review and usage record types

**Files:** Create `Persistence/Records/PlanRecords.swift`,
`RecompTrackerTests/Persistence/PlanRecordsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct PlanRecordsTests {

    @Test func planVersionRoundTripsOnItsTextPrimaryKey() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let v = PlanVersion(effectiveFrom: "1970-01-01", targetCalories: 2550,
                            targetProteinG: 165, targetCarbsG: 320, targetFatG: 68,
                            calorieZoneLowerBound: 2400, calorieZoneUpperBound: 2600,
                            createdAt: "2026-08-02T10:00:00Z")
        try db.writer.write { try v.insert($0) }
        #expect(try db.reader.read { try PlanVersion.fetchOne($0, key: "1970-01-01") } == v)
    }

    @Test func planVersionsSortLexicographicallyByDate() throws {
        // PlanHistory.planOn() picks the greatest effectiveFrom <= date. That is only correct
        // because these are zero-padded YYYY-MM-DD strings.
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for date in ["2026-01-05", "1970-01-01", "2026-11-20", "2026-02-01"] {
                try PlanVersion(effectiveFrom: date, targetCalories: 2000, targetProteinG: 1,
                                targetCarbsG: 1, targetFatG: 1, calorieZoneLowerBound: 1,
                                calorieZoneUpperBound: 1, createdAt: "x").insert(d)
            }
        }
        let ordered = try db.reader.read {
            try String.fetchAll($0, sql: "SELECT effectiveFrom FROM plan_versions ORDER BY effectiveFrom ASC")
        }
        #expect(ordered == ["1970-01-01", "2026-01-05", "2026-02-01", "2026-11-20"])
    }

    @Test func weeklyReviewRoundTripsWithNilBriefing() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let r = WeeklyReview(weekStart: "2026-07-27", verdict: "HOLD",
                             recommendedCalorieChange: 0, reasonCodes: "A,B",
                             generatedAt: "2026-08-02T10:00:00Z", briefingJson: nil,
                             briefingSignature: nil, briefingGeneratedAt: nil)
        try db.writer.write { try r.insert($0) }
        #expect(try db.reader.read { try WeeklyReview.fetchOne($0, key: "2026-07-27") } == r)
    }

    @Test func usageEventAllowsANilLabel() throws {
        let db = try AppDatabase.inMemoryForTesting()
        var e = UsageEvent(id: nil, timestampEpochMs: 1_754_100_000_000, type: "tab_view", label: nil)
        try db.writer.write { try e.insertPreservingID($0) }
        #expect(try db.reader.read { try UsageEvent.fetchOne($0, key: e.id) }?.label == nil)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — types not found.

- [ ] **Step 3: Implement**

Create `Persistence/Records/PlanRecords.swift`:
```swift
import GRDB

/// The plan-version ledger. `effectiveFrom` is the primary key, a YYYY-MM-DD string; the baseline
/// sentinel is "1970-01-01". A plan change never re-judges already-logged days.
struct PlanVersion: Codable, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "plan_versions"
    var effectiveFrom: String
    var targetCalories: Int
    var targetProteinG: Int
    var targetCarbsG: Int
    var targetFatG: Int
    var calorieZoneLowerBound: Int
    var calorieZoneUpperBound: Int
    /// ISO-8601 instant, not a date. Different format, same TEXT affinity.
    var createdAt: String
}

struct WeeklyReview: Codable, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "weekly_reviews"
    var weekStart: String
    var verdict: String
    var recommendedCalorieChange: Int
    /// Delimited blob, not JSON.
    var reasonCodes: String
    var generatedAt: String
    /// An embedded JSON document, stored as TEXT.
    var briefingJson: String?
    var briefingSignature: String?
    var briefingGeneratedAt: String?
}

struct LiftPerformance: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "lift_performance"
    var id: Int64?
    var date: String
    var liftName: String
    var weight: Double
    var reps: Int
    var sets: Int
    var rir: Int?
}

/// The only table with no serializable Android entity — it can never appear in a backup, and
/// restore deliberately leaves it empty.
struct UsageEvent: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "usage_events"
    var id: Int64?
    /// Epoch millis — one of only two INTEGER timestamps in the schema.
    var timestampEpochMs: Int64
    var type: String
    var label: String?
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): plan, review, performance and usage record types"
```

---

## Task 7: Recipe records and the ingredient cascade

**Files:** Create `Persistence/Records/RecipeRecords.swift`,
`RecompTrackerTests/Persistence/RecipeRecordsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct RecipeRecordsTests {

    @Test func deletingARecipeCascadesToItsIngredients() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var recipe = Recipe(id: nil, name: "Chili")
            try recipe.insertPreservingID(d)
            var ing = RecipeIngredient(id: nil, recipeId: recipe.id, name: "Beans", sortOrder: 0,
                                       calories: 120, proteinG: 8, carbsG: 20, fatG: 1,
                                       amountGrams: 100, basePer100Calories: 120,
                                       basePer100ProteinG: 8, basePer100CarbsG: 20,
                                       basePer100FatG: 1, entryServingName: nil,
                                       entryServingGrams: nil, loggedByServings: false)
            try ing.insertPreservingID(d)
            try recipe.delete(d)
        }
        #expect(try db.reader.read { try RecipeIngredient.fetchCount($0) } == 0)
    }

    @Test func fetchesARecipeWithItsIngredientsOrderedById() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let recipeId = try db.writer.write { d -> Int64 in
            var recipe = Recipe(id: nil, name: "Bowl")
            try recipe.insertPreservingID(d)
            for (i, name) in ["Rice", "Chicken", "Broccoli"].enumerated() {
                var ing = RecipeIngredient(id: nil, recipeId: recipe.id, name: name,
                                           sortOrder: i, calories: 100, proteinG: 1, carbsG: 1,
                                           fatG: 1, amountGrams: nil, basePer100Calories: nil,
                                           basePer100ProteinG: nil, basePer100CarbsG: nil,
                                           basePer100FatG: nil, entryServingName: nil,
                                           entryServingGrams: nil, loggedByServings: false)
                try ing.insertPreservingID(d)
            }
            return recipe.id
        }
        let loaded = try db.reader.read { try RecipeWithIngredients.fetchOne($0, recipeId: recipeId) }
        #expect(loaded?.ingredients.map(\.name) == ["Rice", "Chicken", "Broccoli"])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — types not found.

- [ ] **Step 3: Implement**

Create `Persistence/Records/RecipeRecords.swift`:
```swift
import GRDB

struct Recipe: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "recipes"
    static let ingredients = hasMany(RecipeIngredient.self)
    var id: Int64?
    var name: String
}

struct RecipeIngredient: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "recipe_ingredients"
    var id: Int64?
    var recipeId: Int64
    var name: String
    var sortOrder: Int
    var calories: Int
    var proteinG: Double
    var carbsG: Double
    var fatG: Double
    var amountGrams: Double?
    var basePer100Calories: Int?
    var basePer100ProteinG: Double?
    var basePer100CarbsG: Double?
    var basePer100FatG: Double?
    var entryServingName: String?
    var entryServingGrams: Double?
    var loggedByServings: Bool
}

/// Mirrors Android's `RecipeWithIngredientsDb` @Relation projection.
/// Android orders ingredients by `id ASC`, not `sortOrder` — match that exactly.
struct RecipeWithIngredients: Equatable, FetchableRecord, Decodable {
    var recipe: Recipe
    var ingredients: [RecipeIngredient]

    static func fetchOne(_ db: Database, recipeId: Int64) throws -> RecipeWithIngredients? {
        guard let recipe = try Recipe.fetchOne(db, key: recipeId) else { return nil }
        let ingredients = try RecipeIngredient
            .filter(Column("recipeId") == recipeId)
            .order(Column("id").asc)
            .fetchAll(db)
        return RecipeWithIngredients(recipe: recipe, ingredients: ingredients)
    }

    static func fetchAll(_ db: Database) throws -> [RecipeWithIngredients] {
        try Recipe.order(Column("id").asc).fetchAll(db).map { recipe in
            RecipeWithIngredients(
                recipe: recipe,
                ingredients: try RecipeIngredient
                    .filter(Column("recipeId") == recipe.id)
                    .order(Column("id").asc)
                    .fetchAll(db)
            )
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): recipe records with cascade and relation projection"
```

---

## Task 8: Training record types

**Files:** Create `Persistence/Records/TrainingRecords.swift`,
`RecompTrackerTests/Persistence/TrainingRecordsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct TrainingRecordsTests {

    private func seedExercise(_ d: Database, externalId: String = "e1") throws -> Int64 {
        var e = Exercise(id: nil, source: "free-exercise-db", sourceVersion: "2026-06-17",
                         externalId: externalId, name: "Squat", category: "strength",
                         force: "push", level: "intermediate", mechanic: "compound",
                         equipment: "barbell", primaryMuscles: #"["quadriceps"]"#,
                         secondaryMuscles: #"["glutes"]"#, instructions: #"["Stand up."]"#,
                         images: "[]", userCreated: false)
        try e.insertPreservingID(d)
        return e.id
    }

    @Test func muscleColumnsStayJSONStringsNotArrays() throws {
        // On Android these are JSON-array-in-TEXT. Modelling them as [String] would break
        // byte-compatibility with a restored backup.
        let db = try AppDatabase.inMemoryForTesting()
        let id = try db.writer.write { try seedExercise($0) }
        let raw = try db.reader.read {
            try String.fetchOne($0, sql: "SELECT primaryMuscles FROM exercises WHERE id = ?", arguments: [id])
        }
        #expect(raw == #"["quadriceps"]"#)
    }

    @Test func deletingAnExerciseInUseIsRejected() throws {
        // workout_exercises.exerciseId is ON DELETE NO ACTION. Two production crashes came from
        // delete-then-insert re-seeds (P1-19, P1-20); this guard is why the upsert must preserve ids.
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            let exId = try seedExercise(d)
            var w = Workout(id: nil, name: "Leg day", note: nil,
                            createdAt: "2026-08-02T10:00:00Z", updatedAt: "2026-08-02T10:00:00Z")
            try w.insertPreservingID(d)
            var line = WorkoutExercise(id: nil, workoutId: w.id, exerciseId: exId,
                                       sortOrder: 0, note: nil)
            try line.insertPreservingID(d)
        }
        #expect(throws: DatabaseError.self) {
            try db.writer.write { try $0.execute(sql: "DELETE FROM exercises") }
        }
    }

    @Test func deletingAWorkoutCascadesLinesAndPlannedSets() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            let exId = try seedExercise(d)
            var w = Workout(id: nil, name: "Push", note: nil, createdAt: "t", updatedAt: "t")
            try w.insertPreservingID(d)
            var line = WorkoutExercise(id: nil, workoutId: w.id, exerciseId: exId, sortOrder: 0, note: nil)
            try line.insertPreservingID(d)
            var ps = PlannedSet(id: nil, workoutExerciseId: line.id, setNumber: 1,
                                targetReps: 8, targetWeightKg: 100)
            try ps.insertPreservingID(d)
            try w.delete(d)
        }
        try db.reader.read { d in
            #expect(try WorkoutExercise.fetchCount(d) == 0)
            #expect(try PlannedSet.fetchCount(d) == 0)
        }
    }

    @Test func deletingAWorkoutNullsItsSessionsWorkoutId() throws {
        // workout_sessions.workoutId is ON DELETE SET NULL — history survives routine deletion.
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var w = Workout(id: nil, name: "Pull", note: nil, createdAt: "t", updatedAt: "t")
            try w.insertPreservingID(d)
            var s = WorkoutSession(id: nil, workoutId: w.id, workoutName: "Pull", date: "2026-08-02",
                                   startedAt: "2026-08-02T10:00:00Z", completedAt: nil,
                                   status: "COMPLETED", note: nil, durationSeconds: nil)
            try s.insertPreservingID(d)
            try w.delete(d)
        }
        #expect(try db.reader.read { try WorkoutSession.fetchOne($0)?.workoutId } == nil)
    }

    @Test func sessionSetCompletedDefaultsToTrue() throws {
        // The only Boolean in the schema defaulting to 1.
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            let exId = try seedExercise(d)
            var s = WorkoutSession(id: nil, workoutId: nil, workoutName: "Ad hoc", date: "2026-08-02",
                                   startedAt: "t", completedAt: nil, status: "ACTIVE",
                                   note: nil, durationSeconds: nil)
            try s.insertPreservingID(d)
            var se = SessionExercise(id: nil, sessionId: s.id, exerciseId: exId,
                                     exerciseName: "Squat", sortOrder: 0, note: nil)
            try se.insertPreservingID(d)
            try d.execute(sql: """
                INSERT INTO session_sets (sessionExerciseId, setNumber, reps) VALUES (?, 1, 5)
                """, arguments: [se.id])
        }
        #expect(try db.reader.read { try SessionSet.fetchOne($0)?.completed } == true)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — types not found.

- [ ] **Step 3: Implement**

Create `Persistence/Records/TrainingRecords.swift`:
```swift
import GRDB

/// `primaryMuscles`, `secondaryMuscles`, `instructions` and `images` are JSON arrays stored as
/// TEXT — NOT Swift arrays. Keep them as `String` so a restored Android backup is byte-compatible.
struct Exercise: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "exercises"
    var id: Int64?
    var source: String
    var sourceVersion: String
    var externalId: String
    var name: String
    var category: String?
    var force: String?
    var level: String?
    var mechanic: String?
    var equipment: String?
    var primaryMuscles: String
    var secondaryMuscles: String
    var instructions: String
    var images: String
    var userCreated: Bool
}

struct Workout: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "workouts"
    var id: Int64?
    var name: String
    var note: String?
    /// ISO-8601 instants.
    var createdAt: String
    var updatedAt: String
}

struct WorkoutExercise: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "workout_exercises"
    var id: Int64?
    var workoutId: Int64
    var exerciseId: Int64
    var sortOrder: Int
    var note: String?
}

struct PlannedSet: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "planned_sets"
    var id: Int64?
    var workoutExerciseId: Int64
    /// 1-based and re-densified on every replace.
    var setNumber: Int
    var targetReps: Int?
    var targetWeightKg: Double?
}

struct WorkoutSession: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "workout_sessions"
    var id: Int64?
    /// Nullable — ON DELETE SET NULL keeps history when a routine is deleted.
    var workoutId: Int64?
    /// Denormalised so history survives the routine.
    var workoutName: String
    var date: String
    var startedAt: String
    var completedAt: String?
    /// "ACTIVE" | "COMPLETED" | "ABANDONED" — compared as string literals in SQL.
    var status: String
    var note: String?
    var durationSeconds: Int?
}

struct SessionExercise: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "session_exercises"
    var id: Int64?
    var sessionId: Int64
    var exerciseId: Int64
    /// Denormalised.
    var exerciseName: String
    var sortOrder: Int
    var note: String?
}

struct SessionSet: Codable, Equatable, FetchableRecord, IDPreservingRecord {
    static let databaseTableName = "session_sets"
    var id: Int64?
    var sessionExerciseId: Int64
    var setNumber: Int
    var reps: Int
    var weightKg: Double?
    var rir: Int?
    var completed: Bool
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): training record types with FK behaviour pinned"
```

---

## Task 9: Date-range queries and the sentinel

**Files:** Create `Persistence/Queries/DailyLogQueries.swift`,
`Persistence/Queries/MealEntryQueries.swift`,
`RecompTrackerTests/Persistence/DateRangeQueryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct DateRangeQueryTests {

    private func seed(_ db: AppDatabase, dates: [String]) throws {
        try db.writer.write { d in
            for date in dates {
                try DailyLog(date: date, bodyWeightKg: nil, waistCm: nil, waistSkinfoldMm: nil,
                             steps: nil, stepsSource: nil, sleepHours: nil, energyScore: nil,
                             hungerScore: nil, sorenessScore: nil, trained: false, notes: "")
                    .insert(d)
                var m = MealEntry(id: nil, date: date, mealType: "LUNCH", name: "x", calories: 1,
                                  proteinG: 0, carbsG: 0, fatG: 0, slotId: nil, amountGrams: nil,
                                  basePer100Calories: nil, basePer100ProteinG: nil,
                                  basePer100CarbsG: nil, basePer100FatG: nil,
                                  entryServingName: nil, entryServingGrams: nil,
                                  loggedByServings: false, planned: false)
                try m.insertPreservingID(d)
            }
        }
    }

    @Test func dailyLogsBetweenIsInclusiveOnBothEnds() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seed(db, dates: ["2026-07-30", "2026-07-31", "2026-08-01", "2026-08-02"])
        let got = try db.reader.read {
            try DailyLogQueries.between(d: $0, start: "2026-07-31", end: "2026-08-01")
        }
        #expect(got.map(\.date) == ["2026-07-31", "2026-08-01"])
    }

    /// The open-ended read is expressed as `BETWEEN start AND '9999-12-31'`. That is only correct
    /// because dates are zero-padded YYYY-MM-DD strings compared lexicographically.
    @Test func sentinelUpperBoundReturnsEverythingFromTheStartDate() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try seed(db, dates: ["2026-07-30", "2026-08-02", "2099-01-01"])
        let got = try db.reader.read { try MealEntryQueries.since(d: $0, start: "2026-08-01") }
        #expect(got.map(\.date) == ["2026-08-02", "2099-01-01"])
    }

    @Test func stalePlannedCountUsesAHalfOpenWindow() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for (date, planned) in [("2026-07-28", true), ("2026-07-31", true), ("2026-08-02", true)] {
                var m = MealEntry(id: nil, date: date, mealType: "LUNCH", name: "x", calories: 1,
                                  proteinG: 0, carbsG: 0, fatG: 0, slotId: nil, amountGrams: nil,
                                  basePer100Calories: nil, basePer100ProteinG: nil,
                                  basePer100CarbsG: nil, basePer100FatG: nil,
                                  entryServingName: nil, entryServingGrams: nil,
                                  loggedByServings: false, planned: planned)
                try m.insertPreservingID(d)
            }
        }
        // floor inclusive, date exclusive
        let n = try db.reader.read {
            try MealEntryQueries.stalePlannedCount(d: $0, floor: "2026-07-28", before: "2026-08-02")
        }
        #expect(n == 2)
    }

    @Test func insertOrIgnoreMaterialisesARowWithoutClobbering() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: "2026-08-02")
            try DailyLogQueries.updateBodyWeight(d: d, date: "2026-08-02", value: 82.4)
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: "2026-08-02")   // must be a no-op
        }
        #expect(try db.reader.read { try DailyLog.fetchOne($0, key: "2026-08-02") }?.bodyWeightKg == 82.4)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — query types not found.

- [ ] **Step 3: Implement**

Create `Persistence/Queries/DailyLogQueries.swift`:
```swift
import GRDB

/// Reads and per-column writes for `daily_logs`.
///
/// The single-column updates are deliberate, not an optimisation: on Android a whole-row
/// read-modify-write lost concurrent field writes when the coach and a check-in saved at the
/// same time (review P1-18 / P2-7). Never replace these with a whole-record save.
enum DailyLogQueries {

    static func between(d: Database, start: String, end: String) throws -> [DailyLog] {
        try DailyLog
            .filter(sql: "date BETWEEN ? AND ?", arguments: [start, end])
            .order(Column("date"))
            .fetchAll(d)
    }

    /// Materialises an empty row so a subsequent single-column UPDATE has something to write to.
    /// `trained` and `notes` are supplied explicitly because Android's fresh-install DDL has no
    /// defaults for them (we added defaults, but keeping the explicit values matches Android).
    static func insertEmptyIfAbsent(d: Database, date: String) throws {
        try d.execute(
            sql: "INSERT OR IGNORE INTO daily_logs (date, trained, notes) VALUES (?, 0, '')",
            arguments: [date])
    }

    static func updateBodyWeight(d: Database, date: String, value: Double?) throws {
        try d.execute(sql: "UPDATE daily_logs SET bodyWeightKg = ? WHERE date = ?",
                      arguments: [value, date])
    }
    static func updateWaist(d: Database, date: String, value: Double?) throws {
        try d.execute(sql: "UPDATE daily_logs SET waistCm = ? WHERE date = ?", arguments: [value, date])
    }
    static func updateSleepHours(d: Database, date: String, value: Double?) throws {
        try d.execute(sql: "UPDATE daily_logs SET sleepHours = ? WHERE date = ?", arguments: [value, date])
    }
    static func updateEnergyScore(d: Database, date: String, value: Int?) throws {
        try d.execute(sql: "UPDATE daily_logs SET energyScore = ? WHERE date = ?", arguments: [value, date])
    }
    static func updateHungerScore(d: Database, date: String, value: Int?) throws {
        try d.execute(sql: "UPDATE daily_logs SET hungerScore = ? WHERE date = ?", arguments: [value, date])
    }
    static func updateSorenessScore(d: Database, date: String, value: Int?) throws {
        try d.execute(sql: "UPDATE daily_logs SET sorenessScore = ? WHERE date = ?", arguments: [value, date])
    }
}
```

Create `Persistence/Queries/MealEntryQueries.swift`:
```swift
import GRDB

enum MealEntryQueries {
    /// Android's open-ended read: `BETWEEN start AND '9999-12-31'`. Correct only because dates
    /// are zero-padded YYYY-MM-DD strings (D6).
    static let openEndedUpperBound = "9999-12-31"

    static func between(d: Database, start: String, end: String) throws -> [MealEntry] {
        try MealEntry
            .filter(sql: "date BETWEEN ? AND ?", arguments: [start, end])
            .order(Column("date"), Column("id"))
            .fetchAll(d)
    }

    static func since(d: Database, start: String) throws -> [MealEntry] {
        try between(d: d, start: start, end: openEndedUpperBound)
    }

    /// Planned entries older than `before`, from `floor` inclusive. Half-open on purpose.
    static func stalePlannedCount(d: Database, floor: String, before: String) throws -> Int {
        try Int.fetchOne(d, sql: """
            SELECT COUNT(*) FROM meal_entries WHERE planned = 1 AND date >= ? AND date < ?
            """, arguments: [floor, before]) ?? 0
    }

    static func confirmAllPlanned(d: Database, date: String) throws {
        try d.execute(sql: "UPDATE meal_entries SET planned = 0 WHERE date = ? AND planned = 1",
                      arguments: [date])
    }

    static func clearSlotId(d: Database, slotId: Int64) throws {
        try d.execute(sql: "UPDATE meal_entries SET slotId = NULL WHERE slotId = ?", arguments: [slotId])
    }

    /// "FOOD_LIBRARY" is used as a discriminator on this shared table.
    static func foodLibraryEntries(d: Database) throws -> [MealEntry] {
        try MealEntry.filter(Column("mealType") == "FOOD_LIBRARY").order(Column("id").desc).fetchAll(d)
    }
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): date-range queries, sentinel read and per-column daily-log updates"
```

---

## Task 10: The training queries — JOIN, allocators, relation graphs

**Files:** Create `Persistence/Queries/TrainingQueries.swift`,
`RecompTrackerTests/Persistence/TrainingQueryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct TrainingQueryTests {

    /// Builds one COMPLETED session with two sets, plus an ABANDONED session that must be excluded.
    private func seedHistory(_ db: AppDatabase) throws -> Int64 {
        try db.writer.write { d -> Int64 in
            var e = Exercise(id: nil, source: "s", sourceVersion: "v", externalId: "x", name: "Squat",
                             category: nil, force: nil, level: nil, mechanic: nil, equipment: nil,
                             primaryMuscles: "[]", secondaryMuscles: "[]", instructions: "[]",
                             images: "[]", userCreated: false)
            try e.insertPreservingID(d)

            for (status, reps) in [("COMPLETED", 5), ("ABANDONED", 99)] {
                var s = WorkoutSession(id: nil, workoutId: nil, workoutName: "W", date: "2026-08-01",
                                       startedAt: "2026-08-01T10:00:00Z", completedAt: nil,
                                       status: status, note: nil, durationSeconds: nil)
                try s.insertPreservingID(d)
                var se = SessionExercise(id: nil, sessionId: s.id, exerciseId: e.id,
                                         exerciseName: "Squat", sortOrder: 0, note: nil)
                try se.insertPreservingID(d)
                var set = SessionSet(id: nil, sessionExerciseId: se.id, setNumber: 1, reps: reps,
                                     weightKg: 100, rir: 2, completed: true)
                try set.insertPreservingID(d)
            }
            return e.id
        }
    }

    @Test func exerciseHistoryExcludesNonCompletedSessions() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let exId = try seedHistory(db)
        let rows = try db.reader.read { try TrainingQueries.exerciseHistory(d: $0, exerciseId: exId) }
        #expect(rows.map(\.reps) == [5])
    }

    @Test func exerciseHistoryExcludesIncompleteSets() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let exId = try seedHistory(db)
        try db.writer.write { try $0.execute(sql: "UPDATE session_sets SET completed = 0") }
        #expect(try db.reader.read { try TrainingQueries.exerciseHistory(d: $0, exerciseId: exId) }.isEmpty)
    }

    @Test func nextSortOrderStartsAtZeroThenIncrements() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var s = WorkoutSession(id: nil, workoutId: nil, workoutName: "W", date: "2026-08-01",
                                   startedAt: "t", completedAt: nil, status: "ACTIVE",
                                   note: nil, durationSeconds: nil)
            try s.insertPreservingID(d)
            #expect(try TrainingQueries.nextSortOrder(d: d, sessionId: s.id) == 0)

            var e = Exercise(id: nil, source: "s", sourceVersion: "v", externalId: "x", name: "N",
                             category: nil, force: nil, level: nil, mechanic: nil, equipment: nil,
                             primaryMuscles: "[]", secondaryMuscles: "[]", instructions: "[]",
                             images: "[]", userCreated: false)
            try e.insertPreservingID(d)
            var se = SessionExercise(id: nil, sessionId: s.id, exerciseId: e.id, exerciseName: "N",
                                     sortOrder: 0, note: nil)
            try se.insertPreservingID(d)
            #expect(try TrainingQueries.nextSortOrder(d: d, sessionId: s.id) == 1)
        }
    }

    @Test func completedSessionsSinceUsesLexicographicDateCompare() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for date in ["2026-07-05", "2026-07-28", "2026-08-01"] {
                var s = WorkoutSession(id: nil, workoutId: nil, workoutName: "W", date: date,
                                       startedAt: "t", completedAt: "t", status: "COMPLETED",
                                       note: nil, durationSeconds: nil)
                try s.insertPreservingID(d)
            }
        }
        let got = try db.reader.read { try TrainingQueries.completedSessionsSince(d: $0, start: "2026-07-28") }
        #expect(got.map(\.session.date) == ["2026-08-01", "2026-07-28"])   // date DESC
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — `TrainingQueries` not found.

- [ ] **Step 3: Implement**

Create `Persistence/Queries/TrainingQueries.swift`:
```swift
import GRDB

/// Flat projection of the 3-table history JOIN. Mirrors Android's `ExerciseHistoryRow`.
struct ExerciseHistoryPoint: Codable, Equatable, FetchableRecord {
    var date: String
    var reps: Int
    var weightKg: Double?
    var rir: Int?
}

/// Mirrors Android's `SessionExerciseWithSets`.
struct SessionExerciseWithSets: Equatable {
    var exercise: SessionExercise
    var sets: [SessionSet]
}

/// Mirrors Android's `WorkoutSessionWithDetailsDb` (two-level @Relation).
struct SessionWithDetails: Equatable {
    var session: WorkoutSession
    var exercises: [SessionExerciseWithSets]
}

enum TrainingQueries {

    /// The only multi-table JOIN in the schema.
    static func exerciseHistory(d: Database, exerciseId: Int64) throws -> [ExerciseHistoryPoint] {
        try ExerciseHistoryPoint.fetchAll(d, sql: """
            SELECT s.date AS date, st.reps AS reps, st.weightKg AS weightKg, st.rir AS rir
            FROM session_sets st
            JOIN session_exercises se ON st.sessionExerciseId = se.id
            JOIN workout_sessions s ON se.sessionId = s.id
            WHERE se.exerciseId = ? AND s.status = 'COMPLETED' AND st.completed = 1
            ORDER BY s.date, s.startedAt
            """, arguments: [exerciseId])
    }

    /// Sort-order allocator. Empty list yields 0.
    static func nextSortOrder(d: Database, sessionId: Int64) throws -> Int {
        try Int.fetchOne(d, sql: """
            SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM session_exercises WHERE sessionId = ?
            """, arguments: [sessionId]) ?? 0
    }

    static func setCount(d: Database, sessionExerciseId: Int64) throws -> Int {
        try Int.fetchOne(d, sql: """
            SELECT COUNT(*) FROM session_sets WHERE sessionExerciseId = ?
            """, arguments: [sessionExerciseId]) ?? 0
    }

    /// The coach-detector windowed read. Loads the whole graph in one pass to avoid N+1.
    static func completedSessionsSince(d: Database, start: String) throws -> [SessionWithDetails] {
        let sessions = try WorkoutSession
            .filter(sql: "status = 'COMPLETED' AND date >= ?", arguments: [start])
            .order(sql: "date DESC, completedAt DESC")
            .fetchAll(d)
        return try sessions.map { try details(d: d, session: $0) }
    }

    static func activeSession(d: Database) throws -> SessionWithDetails? {
        guard let s = try WorkoutSession
            .filter(Column("status") == "ACTIVE")
            .order(Column("startedAt").desc)
            .fetchOne(d) else { return nil }
        return try details(d: d, session: s)
    }

    private static func details(d: Database, session: WorkoutSession) throws -> SessionWithDetails {
        let exercises = try SessionExercise
            .filter(Column("sessionId") == session.id)
            .order(Column("sortOrder"))
            .fetchAll(d)
        return SessionWithDetails(
            session: session,
            exercises: try exercises.map { ex in
                SessionExerciseWithSets(
                    exercise: ex,
                    sets: try SessionSet
                        .filter(Column("sessionExerciseId") == ex.id)
                        .order(Column("setNumber"))
                        .fetchAll(d)
                )
            }
        )
    }

    // Per-column set updates — same rationale as DailyLogQueries: whole-row writes lost
    // concurrent field updates during fast set entry (review P1-18).
    static func updateSetWeight(d: Database, setId: Int64, value: Double?) throws {
        try d.execute(sql: "UPDATE session_sets SET weightKg = ? WHERE id = ?", arguments: [value, setId])
    }
    static func updateSetReps(d: Database, setId: Int64, value: Int) throws {
        try d.execute(sql: "UPDATE session_sets SET reps = ? WHERE id = ?", arguments: [value, setId])
    }
    static func updateSetRir(d: Database, setId: Int64, value: Int?) throws {
        try d.execute(sql: "UPDATE session_sets SET rir = ? WHERE id = ?", arguments: [value, setId])
    }
    static func updateSetCompleted(d: Database, setId: Int64, value: Bool) throws {
        try d.execute(sql: "UPDATE session_sets SET completed = ? WHERE id = ?", arguments: [value, setId])
    }
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): training JOIN, allocators, relation graphs and per-column set updates"
```

---

## Task 11: The usage aggregate

**Files:** Create `Persistence/Queries/UsageQueries.swift`,
`RecompTrackerTests/Persistence/UsageQueryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct UsageQueryTests {

    @Test func groupsByTypeAndLabelTreatingNilAsItsOwnGroup() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            let rows: [(String, String?)] = [
                ("tab_view", "home"), ("tab_view", "home"), ("tab_view", "body"),
                ("insight_seen", nil), ("insight_seen", nil),
            ]
            for (type, label) in rows {
                var e = UsageEvent(id: nil, timestampEpochMs: 1_000, type: type, label: label)
                try e.insertPreservingID(d)
            }
        }
        let counts = try db.reader.read { try UsageQueries.countsSince(d: $0, sinceEpochMs: 0) }
        #expect(counts.count == 3)
        #expect(counts.first { $0.type == "insight_seen" && $0.label == nil }?.count == 2)
        #expect(counts.first { $0.label == "home" }?.count == 2)
    }

    @Test func excludesEventsBeforeTheCutoff() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            for ts in [100, 200, 300] {
                var e = UsageEvent(id: nil, timestampEpochMs: Int64(ts), type: "t", label: nil)
                try e.insertPreservingID(d)
            }
        }
        #expect(try db.reader.read { try UsageQueries.countsSince(d: $0, sinceEpochMs: 200) }
            .first?.count == 2)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: **FAIL** — `UsageQueries` not found.

- [ ] **Step 3: Implement**

Create `Persistence/Queries/UsageQueries.swift`:
```swift
import GRDB

/// `label` is nullable and NULL is a legitimate group key.
struct UsageCount: Codable, Equatable, FetchableRecord {
    var type: String
    var label: String?
    var count: Int
}

enum UsageQueries {
    /// The only GROUP BY in the schema.
    static func countsSince(d: Database, sinceEpochMs: Int64) throws -> [UsageCount] {
        try UsageCount.fetchAll(d, sql: """
            SELECT type, label, COUNT(*) AS count FROM usage_events
            WHERE timestampEpochMs >= ? GROUP BY type, label
            """, arguments: [sinceEpochMs])
    }

    /// Not on Android — usage_events grows unboundedly there with no retention sweep (P2-24).
    /// Call this from app start.
    static func pruneOlderThan(d: Database, cutoffEpochMs: Int64) throws {
        try d.execute(sql: "DELETE FROM usage_events WHERE timestampEpochMs < ?", arguments: [cutoffEpochMs])
    }
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): usage aggregate with a retention sweep Android lacks"
```

---

## Task 12: The seven transaction bodies

Each of these encodes a fixed production bug. Reproduce the sequence exactly.

**Files:** Create `Persistence/Transactions/PersistenceTransactions.swift`,
`RecompTrackerTests/Persistence/TransactionTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct TransactionTests {

    private func seedExercise(_ d: Database, externalId: String, name: String) throws -> Exercise {
        var e = Exercise(id: nil, source: "lib", sourceVersion: "v1", externalId: externalId,
                         name: name, category: nil, force: nil, level: nil, mechanic: nil,
                         equipment: nil, primaryMuscles: "[]", secondaryMuscles: "[]",
                         instructions: "[]", images: "[]", userCreated: false)
        try e.insertPreservingID(d)
        return e
    }

    /// T2 (P1-20): never REPLACE — a REPLACE deletes the conflicting row and trips the
    /// ON DELETE NO ACTION foreign key from workout_exercises.
    @Test func insertCustomOrGetExistingReturnsTheExistingId() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let (first, second) = try db.writer.write { d -> (Int64, Int64) in
            let a = try Transactions.insertCustomOrGetExisting(
                d: d, exercise: seedTemplate(externalId: "custom-1"))
            let b = try Transactions.insertCustomOrGetExisting(
                d: d, exercise: seedTemplate(externalId: "custom-1"))
            return (a, b)
        }
        #expect(first == second)
        #expect(try db.reader.read { try Exercise.fetchCount($0) } == 1)
    }

    /// T3 (P1-19): the library re-seed must preserve ids, or every routine's exerciseId dangles.
    @Test func upsertLibraryPreservesIdsAcrossAReseed() throws {
        let db = try AppDatabase.inMemoryForTesting()
        let originalId = try db.writer.write { d -> Int64 in
            try Transactions.upsertLibrary(d: d, source: "lib", version: "v1",
                                           exercises: [seedTemplate(externalId: "e1")])
            return try Exercise.fetchOne(d)!.id
        }
        try db.writer.write { d in
            var updated = seedTemplate(externalId: "e1")
            updated.name = "Renamed"
            try Transactions.upsertLibrary(d: d, source: "lib", version: "v2", exercises: [updated])
        }
        let after = try db.reader.read { try Exercise.fetchOne($0) }
        #expect(after?.id == originalId)
        #expect(after?.name == "Renamed")
        #expect(after?.sourceVersion == "v2")
    }

    /// T6 (P1-16): starting a session must leave exactly one ACTIVE.
    @Test func startingASessionAbandonsAnyExistingActiveOne() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            _ = try Transactions.abandonActiveAndInsertSession(d: d, session: session(status: "ACTIVE"))
            _ = try Transactions.abandonActiveAndInsertSession(d: d, session: session(status: "ACTIVE"))
        }
        try db.reader.read { d in
            #expect(try WorkoutSession.filter(Column("status") == "ACTIVE").fetchCount(d) == 1)
            #expect(try WorkoutSession.filter(Column("status") == "ABANDONED").fetchCount(d) == 1)
        }
    }

    /// T7: setNumber is allocated by the transaction; the caller's value is ignored.
    @Test func insertNextSetAllocatesSequentialNumbers() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            let e = try seedExercise(d, externalId: "e1", name: "Squat")
            var s = session(status: "ACTIVE")
            try s.insertPreservingID(d)
            var se = SessionExercise(id: nil, sessionId: s.id, exerciseId: e.id,
                                     exerciseName: "Squat", sortOrder: 0, note: nil)
            try se.insertPreservingID(d)
            for _ in 0..<3 {
                _ = try Transactions.insertNextSet(d: d, set: SessionSet(
                    id: nil, sessionExerciseId: se.id, setNumber: 999, reps: 5,
                    weightKg: 100, rir: 2, completed: true))
            }
            let numbers = try SessionSet.order(Column("id")).fetchAll(d).map(\.setNumber)
            #expect(numbers == [1, 2, 3])
        }
    }

    /// T4: whole-list replace re-densifies sortOrder and assigns fresh ids.
    @Test func replaceIngredientsRedensifiesSortOrder() throws {
        let db = try AppDatabase.inMemoryForTesting()
        try db.writer.write { d in
            var r = Recipe(id: nil, name: "Bowl")
            try r.insertPreservingID(d)
            try Transactions.replaceIngredients(d: d, recipeId: r.id, ingredients: [
                ingredient(name: "A", sortOrder: 7),
                ingredient(name: "B", sortOrder: 3),
            ])
            let got = try RecipeIngredient.order(Column("id")).fetchAll(d)
            #expect(got.map(\.sortOrder) == [0, 1])
            #expect(got.map(\.name) == ["A", "B"])
        }
    }

    // Helpers
    private func seedTemplate(externalId: String) -> Exercise {
        Exercise(id: nil, source: "lib", sourceVersion: "v1", externalId: externalId, name: "X",
                 category: nil, force: nil, level: nil, mechanic: nil, equipment: nil,
                 primaryMuscles: "[]", secondaryMuscles: "[]", instructions: "[]", images: "[]",
                 userCreated: false)
    }
    private func session(status: String) -> WorkoutSession {
        WorkoutSession(id: nil, workoutId: nil, workoutName: "W", date: "2026-08-02",
                       startedAt: "2026-08-02T10:00:00Z", completedAt: nil, status: status,
                       note: nil, durationSeconds: nil)
    }
    private func ingredient(name: String, sortOrder: Int) -> RecipeIngredient {
        RecipeIngredient(id: nil, recipeId: 0, name: name, sortOrder: sortOrder, calories: 1,
                         proteinG: 0, carbsG: 0, fatG: 0, amountGrams: nil,
                         basePer100Calories: nil, basePer100ProteinG: nil, basePer100CarbsG: nil,
                         basePer100FatG: nil, entryServingName: nil, entryServingGrams: nil,
                         loggedByServings: false)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Expected: **FAIL** — `Transactions` not found.

- [ ] **Step 3: Implement**

Create `Persistence/Transactions/PersistenceTransactions.swift`:
```swift
import GRDB

/// The seven imperative transactions from Android's DAOs. Each encodes a fixed production bug —
/// the sequence matters, not just the end state. Call every one inside a single `db.write { }`.
enum Transactions {

    /// T1 (P2-7): dispatch to a single-column UPDATE so a partial write cannot clobber a
    /// concurrent whole-row check-in save. An unrecognised metric is a true no-op — the row
    /// materialisation lives inside each branch, so no stray empty row is left behind.
    enum DailyMetric: String {
        case weightKg = "weight_kg", waistCm = "waist_cm", sleepHours = "sleep_hours"
        case energyScore = "energy_score", hungerScore = "hunger_score", sorenessScore = "soreness_score"
    }

    static func upsertMetric(d: Database, date: String, metric: DailyMetric, value: Double) throws {
        switch metric {
        case .weightKg:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateBodyWeight(d: d, date: date, value: value)
        case .waistCm:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateWaist(d: d, date: date, value: value)
        case .sleepHours:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateSleepHours(d: d, date: date, value: value)
        case .energyScore:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateEnergyScore(d: d, date: date, value: Int(value))
        case .hungerScore:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateHungerScore(d: d, date: date, value: Int(value))
        case .sorenessScore:
            try DailyLogQueries.insertEmptyIfAbsent(d: d, date: date)
            try DailyLogQueries.updateSorenessScore(d: d, date: date, value: Int(value))
        }
    }

    /// T2 (P1-20): lookup-or-insert. Never REPLACE — that deletes the conflicting row and trips
    /// the ON DELETE NO ACTION foreign key from workout_exercises / session_exercises.
    static func insertCustomOrGetExisting(d: Database, exercise: Exercise) throws -> Int64 {
        if let existing = try Int64.fetchOne(d, sql: """
            SELECT id FROM exercises WHERE source = ? AND externalId = ? LIMIT 1
            """, arguments: [exercise.source, exercise.externalId]) {
            return existing
        }
        var copy = exercise
        try copy.insertPreservingID(d)
        return copy.id
    }

    /// T3 (P1-19): id-preserving re-seed of the bundled library. A delete-then-insert re-seed
    /// crashed for every user with a routine. Rows dropped from the new library linger harmlessly.
    static func upsertLibrary(d: Database, source: String, version: String,
                              exercises: [Exercise]) throws {
        for e in exercises {
            if let existingId = try Int64.fetchOne(d, sql: """
                SELECT id FROM exercises WHERE source = ? AND externalId = ? LIMIT 1
                """, arguments: [e.source, e.externalId]) {
                var updated = e
                updated.id = existingId
                try updated.update(d)
            } else {
                var copy = e
                try copy.insertPreservingID(d)
            }
        }
        try d.execute(sql: "UPDATE exercises SET sourceVersion = ? WHERE source = ?",
                      arguments: [version, source])
    }

    /// T4: whole-list replace with re-densified sortOrder and fresh ids.
    static func replaceIngredients(d: Database, recipeId: Int64,
                                   ingredients: [RecipeIngredient]) throws {
        try d.execute(sql: "DELETE FROM recipe_ingredients WHERE recipeId = ?", arguments: [recipeId])
        for (index, ing) in ingredients.enumerated() {
            var copy = ing
            copy.id = nil
            copy.recipeId = recipeId
            copy.sortOrder = index
            try copy.insertPreservingID(d)
        }
    }

    /// T5: two-level replace. sortOrder is 0-based, setNumber 1-based, both re-densified.
    /// Deleting the lines cascades their planned_sets.
    static func replaceWorkoutExercises(
        d: Database, workoutId: Int64,
        lines: [(exercise: WorkoutExercise, plannedSets: [PlannedSet])]
    ) throws {
        try d.execute(sql: "DELETE FROM workout_exercises WHERE workoutId = ?", arguments: [workoutId])
        for (index, line) in lines.enumerated() {
            var ex = line.exercise
            ex.id = nil
            ex.workoutId = workoutId
            ex.sortOrder = index
            try ex.insertPreservingID(d)
            for (n, ps) in line.plannedSets.enumerated() {
                var copy = ps
                copy.id = nil
                copy.workoutExerciseId = ex.id
                copy.setNumber = n + 1
                try copy.insertPreservingID(d)
            }
        }
    }

    /// T6 (P1-16): exactly one ACTIVE session. Prior sets survive; only status changes.
    static func abandonActiveAndInsertSession(d: Database, session: WorkoutSession) throws -> Int64 {
        try d.execute(sql: "UPDATE workout_sessions SET status = 'ABANDONED' WHERE status = 'ACTIVE'")
        var copy = session
        try copy.insertPreservingID(d)
        return copy.id
    }

    /// T7: read-then-write allocator. The caller's `setNumber` is ignored.
    static func insertNextSet(d: Database, set: SessionSet) throws -> Int64 {
        var copy = set
        copy.id = nil
        copy.setNumber = try TrainingQueries.setCount(d: d, sessionExerciseId: set.sessionExerciseId) + 1
        try copy.insertPreservingID(d)
        return copy.id
    }
}
```

- [ ] **Step 4: Run the tests**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): the seven transaction bodies, each pinned by a regression test"
```

---

## Task 13: Reactive reads

Android's DAOs return `Flow`. GRDB's equivalent is `ValueObservation`. Prove the pattern once here;
the UI phases build on it.

**Files:** Create `Persistence/Observation.swift`,
`RecompTrackerTests/Persistence/ObservationTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import GRDB
@testable import RecompTracker

@Suite struct ObservationTests {

    @Test func emitsInitialValueThenUpdatesOnWrite() async throws {
        let db = try AppDatabase.inMemoryForTesting()
        let observation = ValueObservation.tracking { d in try MealEntry.fetchCount(d) }

        var seen: [Int] = []
        let task = Task {
            for try await count in observation.values(in: db.reader) {
                seen.append(count)
                if seen.count == 2 { break }
            }
        }
        try await Task.sleep(for: .milliseconds(100))
        try await db.writer.write { d in
            var m = MealEntry(id: nil, date: "2026-08-02", mealType: "LUNCH", name: "x", calories: 1,
                              proteinG: 0, carbsG: 0, fatG: 0, slotId: nil, amountGrams: nil,
                              basePer100Calories: nil, basePer100ProteinG: nil,
                              basePer100CarbsG: nil, basePer100FatG: nil, entryServingName: nil,
                              entryServingGrams: nil, loggedByServings: false, planned: false)
            try m.insertPreservingID(d)
        }
        try await task.value
        #expect(seen == [0, 1])
    }
}
```

- [ ] **Step 2: Run it to verify it fails or passes**

Run the test command. If it passes immediately, GRDB's built-in API is sufficient and Step 3 only
adds the documented helper. **Note:** in GRDB 7 `ValueObservation.start` defaults to the main actor;
`.values(in:)` iterates on the cooperative pool. Do not add a `@MainActor` annotation that fights that.

- [ ] **Step 3: Add the documented helper**

Create `Persistence/Observation.swift`:
```swift
import GRDB

/// Android's DAOs return `Flow<T>`; the GRDB equivalent is `ValueObservation`, consumed as an
/// `AsyncSequence`. GRDB 7 fosters the main actor for `start`, and `.values(in:)` iterates on the
/// cooperative pool — that is the behaviour the UI layer wants, so do not override it.
///
/// One deliberate difference from Android: Android's ViewModels hold `combine` pipelines for their
/// whole lifetime, so every write recomputes aggregates even for invisible screens (review P2-21).
/// On iOS, scope observations to view lifetime with `.task { }` instead.
extension AppDatabase {
    func observe<T: Sendable>(
        _ fetch: @escaping @Sendable (Database) throws -> T
    ) -> some AsyncSequence<T, any Error> {
        ValueObservation.tracking(fetch).values(in: reader)
    }
}
```

⚠️ The `some AsyncSequence<T, any Error>` opaque return avoids naming GRDB's concrete observation
type, which I have not verified against 7.11.1. If the compiler rejects it, let Xcode tell you the
real type and use that — do not guess.

- [ ] **Step 4: Run the test**

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(db): ValueObservation helper as the Flow equivalent"
```

---

## Task 14: Wire the database into the app

**Files:** Modify `RecompTrackerApp.swift`; create `AppContainer.swift`

- [ ] **Step 1: Create the container**

Create `AppContainer.swift`:
```swift
import Foundation
import GRDB

/// Manual dependency container, mirroring Android's `core/AppContainer`. No DI framework —
/// Android uses hand-rolled DI too, and that choice made the KMP extraction dramatically easier.
@Observable
final class AppContainer {
    let database: AppDatabase

    init(database: AppDatabase) {
        self.database = database
    }

    static func live() throws -> AppContainer {
        AppContainer(database: try AppDatabase.onDisk())
    }
}
```

- [ ] **Step 2: Wire it into the app entry point**

Replace `RecompTrackerApp.swift`:
```swift
import SwiftUI

@main
struct RecompTrackerApp: App {
    @State private var container: AppContainer?
    @State private var startupError: String?

    var body: some Scene {
        WindowGroup {
            Group {
                if let container {
                    ContentView().environment(container)
                } else if let startupError {
                    ContentUnavailableView("Could not open the database", systemImage: "exclamationmark.triangle",
                                           description: Text(startupError))
                } else {
                    ProgressView()
                }
            }
            .task {
                do { container = try AppContainer.live() }
                catch { startupError = String(describing: error) }
            }
        }
    }
}
```

- [ ] **Step 3: Verify the app still builds and runs**

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```
Expected: **BUILD SUCCEEDED**. Then run in the simulator and confirm the shared-core smoke rows from
Phase 0 still render — the database opens without disturbing them.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(app): open the database from a manual app container"
```

---

## Task 15: Full-suite verification and documentation

- [ ] **Step 1: Run everything**

```bash
cd ~/Desktop/RecompTracker-IOS
xcodebuild test -project RecompTracker/RecompTracker.xcodeproj -scheme RecompTracker \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' 2>&1 | tail -20
```
Expected: **TEST SUCCEEDED**, zero failures. Record the total test count.

- [ ] **Step 2: Confirm the schema test still passes against an on-disk database**

The suite uses in-memory databases. Add one on-disk check to
`RecompTrackerTests/Persistence/SchemaEquivalenceTests.swift`:
```swift
@Test func onDiskDatabaseAppliesTheSameSchema() throws {
    let url = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("schema-check-\(UUID().uuidString).db")
    defer { try? FileManager.default.removeItem(at: url) }
    var config = Configuration()
    config.foreignKeysEnabled = true
    let db = try AppDatabase(DatabaseQueue(path: url.path, configuration: config))
    let count = try db.reader.read { d in
        try Int.fetchOne(d, sql: """
            SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'grdb_%'
            """) ?? 0
    }
    #expect(count == 19)
}
```
Re-run the suite. Expected: **PASS**.

- [ ] **Step 3: Update the Android repo's port docs**

In `~/Desktop/Personal Dietitian/docs/ios-port/`:
- `parity-ledger.md` — mark the Foundations rows *19 tables + 14 migrations* ✅, update the summary counts
- `STATUS.md` — set the phase board row for Phase 1a, add a session-log entry with the test count,
  the schema-equivalence result, and anything surprising
- Note the **two deliberate divergences from Android**: the `DEFAULT` clauses (Dec-2) and the extra
  `index_usage_events_timestampEpochMs` + retention sweep

- [ ] **Step 4: Commit both repos**

```bash
cd ~/Desktop/RecompTracker-IOS && git add -A && git commit -m "test(db): on-disk schema check"
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && git add docs/ios-port && \
  git commit -m "docs(ios): Phase 1a complete — GRDB schema landed"
```

---

## What Phase 1a deliberately does NOT do

Named so nobody adds them mid-flight:

- **No backup import.** The codec, the restore ordering and the round-trip test against a real
  Android export are Phase 1b. 1a only guarantees the schema those rows will land in.
- **No preference stores, Keychain, or bundled assets.** All Phase 1b.
- **No exercise-library seed.** The 873-row seed needs the bundled asset — Phase 1b.
- **No UI.** Phase 2.
- **No `PlanHistoryInitializer` / `MealSlotInitializer` equivalents.** Those seed from DataStore and
  at app start; they belong with the preference stores in 1b.

## Rollback

Every task is one commit in the iOS repo, which contains nothing else of value yet. To abandon:
`git reset --hard <sha-before-task-1>`. Nothing in the Android repo changes during Phase 1a except
the documentation update in Task 15.
