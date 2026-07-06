# Plan Change Historical Integrity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every day be judged against the plan that was in effect *on that day*, so changing the plan only affects today forward and never re-judges already-logged days.

**Architecture:** A new effective-dated `plan_versions` Room table is the historical ledger of targets. A pure `PlanHistory` resolver answers "the plan on date X". `PlanRepository.save()` is the single choke point that appends a version (for today) whenever target fields change. A one-time idempotent initializer seeds the current plan as the baseline. Every historical/range consumer switches from the live plan to the resolver.

**Tech Stack:** Kotlin, Room (SQLite), DataStore, Coroutines/Flow, JUnit. Manual DI via `AppContainer`. Build: `./gradlew :app:testDebugUnitTest`, type-check: `./gradlew :app:compileDebugKotlin`.

**Spec:** `docs/superpowers/specs/2026-06-30-plan-change-historical-integrity-design.md`

---

## File Structure

**New files**
- `domain/plan/PlanTargets.kt` — pure model: the day-judging targets (calories, macros, zone).
- `domain/plan/PlanVersion.kt` — pure model: `PlanTargets` + `effectiveFrom: LocalDate`.
- `domain/plan/PlanHistory.kt` — pure resolver: `planOn`, `resolve`, `targetsChanged`, `BASELINE_DATE`.
- `data/local/entity/PlanVersionEntity.kt` — Room entity (`plan_versions`).
- `data/local/dao/PlanVersionDao.kt` — Room DAO.
- `data/repository/PlanHistoryInitializer.kt` — idempotent backfill seeding.
- Tests: `domain/plan/PlanHistoryTest.kt`, `domain/adherence/AdherenceCalculatorTest.kt` (extend if exists), `data/repository/PlanHistoryInitializerTest.kt`, `data/repository/StreakRepositoryTest.kt` (extend if exists).

**Modified files**
- `data/local/RecompDatabase.kt` — version 13, register entity + DAO + `MIGRATION_12_13`.
- `data/repository/PlanRepository.kt` — inject `PlanVersionDao` + `DateProvider`; record history on `save()`/`resetDefaults()`; expose `observeVersions()`, `planOn(date)`, `observePlanOn(date)`, `targetsByDate(dates)`.
- `domain/adherence/AdherenceCalculator.kt` — `NutritionDay` gains `targetCalories`; `calculate(days)` uses per-day target.
- `ui/progress/ProgressViewModel.kt`, `ui/dashboard/DashboardViewModel.kt`, `core/AppContainer.kt`, `data/repository/StreakRepository.kt`, `ui/today/FoodLogViewModel.kt`, `ui/today/FoodScreen.kt`, `ui/component/WeekCalorieStrip.kt` (+ caller), `ai/CoachToolExecutor.kt`, `data/repository/BackupRepository.kt`.

---

## PHASE 1 — Core (sequential; land before Phase 2)

### Task 1: Pure plan-history model + resolver

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanTargets.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanVersion.kt`
- Create: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanHistory.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/plan/PlanHistoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.plan

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanHistoryTest {
    private fun targets(cal: Int) = PlanTargets(
        calories = cal, proteinG = 1, carbsG = 1, fatG = 1,
        zoneLowerBound = cal - 100, zoneUpperBound = cal + 100,
    )
    private fun version(date: String, cal: Int) = PlanVersion(LocalDate.parse(date), targets(cal))

    @Test fun `planOn returns the version effective on the date`() {
        val versions = listOf(version("2026-01-01", 2500), version("2026-06-01", 2200))
        assertEquals(2500, PlanHistory.planOn(versions, LocalDate.parse("2026-03-15")).calories)
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2026-06-01")).calories)
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2026-12-31")).calories)
    }

    @Test fun `planOn clamps to earliest version for dates before history`() {
        val versions = listOf(version("2026-06-01", 2200))
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2020-01-01")).calories)
    }

    @Test fun `planOn with unsorted input still resolves correctly`() {
        val versions = listOf(version("2026-06-01", 2200), version("2026-01-01", 2500))
        assertEquals(2500, PlanHistory.planOn(versions, LocalDate.parse("2026-02-01")).calories)
    }

    @Test fun `resolve maps each requested date to its version`() {
        val versions = listOf(version("2026-01-01", 2500), version("2026-06-01", 2200))
        val dates = listOf(LocalDate.parse("2026-05-31"), LocalDate.parse("2026-06-02"))
        val map = PlanHistory.resolve(versions, dates)
        assertEquals(2500, map.getValue(dates[0]).calories)
        assertEquals(2200, map.getValue(dates[1]).calories)
    }

    @Test fun `targetsChanged detects calorie, macro and zone differences only`() {
        val a = targets(2500)
        assertFalse(PlanHistory.targetsChanged(a, a.copy()))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(calories = 2400)))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(proteinG = 200)))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(zoneLowerBound = 0)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.plan.PlanHistoryTest"`
Expected: FAIL — unresolved references `PlanTargets`, `PlanVersion`, `PlanHistory`.

- [ ] **Step 3: Write minimal implementation**

`PlanTargets.kt`:
```kotlin
package com.zack.recomptracker.domain.plan

/** The plan fields used to JUDGE a single day (calories, macros, calorie zone). Pure. */
data class PlanTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val zoneLowerBound: Int,
    val zoneUpperBound: Int,
)
```

`PlanVersion.kt`:
```kotlin
package com.zack.recomptracker.domain.plan

import java.time.LocalDate

/** A plan version that takes effect on [effectiveFrom] (inclusive) for all later days. */
data class PlanVersion(
    val effectiveFrom: LocalDate,
    val targets: PlanTargets,
)
```

`PlanHistory.kt`:
```kotlin
package com.zack.recomptracker.domain.plan

import java.time.LocalDate

/**
 * Pure resolver for "what plan was in effect on date X". The single source of truth for
 * historical target judgments — every consumer routes day/range judgments through here so a
 * plan change only affects today forward and never re-judges already-logged days.
 */
object PlanHistory {

    /** Sentinel effective date for the seeded baseline version (covers all pre-existing days). */
    val BASELINE_DATE: LocalDate = LocalDate.parse("1970-01-01")

    /**
     * The targets in effect on [date]: the version with the greatest effectiveFrom <= date.
     * If [date] precedes the earliest version, clamps to the earliest version. Throws if
     * [versions] is empty — callers must guarantee a baseline exists (see PlanHistoryInitializer).
     */
    fun planOn(versions: List<PlanVersion>, date: LocalDate): PlanTargets {
        require(versions.isNotEmpty()) { "planOn requires at least one version" }
        val sorted = versions.sortedBy { it.effectiveFrom }
        val match = sorted.lastOrNull { !it.effectiveFrom.isAfter(date) } ?: sorted.first()
        return match.targets
    }

    /** Resolve many dates at once (sorts versions once). */
    fun resolve(versions: List<PlanVersion>, dates: List<LocalDate>): Map<LocalDate, PlanTargets> {
        if (versions.isEmpty()) return emptyMap()
        val sorted = versions.sortedBy { it.effectiveFrom }
        return dates.associateWith { date ->
            (sorted.lastOrNull { !it.effectiveFrom.isAfter(date) } ?: sorted.first()).targets
        }
    }

    /** True when any day-judging field differs (calories, macros, or zone bounds). */
    fun targetsChanged(old: PlanTargets, new: PlanTargets): Boolean = old != new
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.plan.PlanHistoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/plan app/src/test/java/com/zack/recomptracker/domain/plan
git commit -m "feat(plan): pure PlanHistory resolver + PlanTargets/PlanVersion models"
```

---

### Task 2: Room entity, DAO, migration v12→v13

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/local/entity/PlanVersionEntity.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/local/dao/PlanVersionDao.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/local/RecompDatabase.kt`

- [ ] **Step 1: Create the entity**

`PlanVersionEntity.kt`:
```kotlin
package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One historical plan version. PK = effectiveFrom (ISO date); one row per change-day. */
@Serializable
@Entity(tableName = "plan_versions")
data class PlanVersionEntity(
    @PrimaryKey val effectiveFrom: String,
    val targetCalories: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val calorieZoneLowerBound: Int,
    val calorieZoneUpperBound: Int,
    val createdAt: String,
)
```

- [ ] **Step 2: Create the DAO**

`PlanVersionDao.kt`:
```kotlin
package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanVersionDao {
    @Query("SELECT * FROM plan_versions ORDER BY effectiveFrom ASC")
    fun observeAll(): Flow<List<PlanVersionEntity>>

    @Query("SELECT * FROM plan_versions ORDER BY effectiveFrom ASC")
    suspend fun getAll(): List<PlanVersionEntity>

    @Query("SELECT COUNT(*) FROM plan_versions")
    suspend fun count(): Int

    /** Upsert by PK (effectiveFrom): a second change on the same day replaces that day's row. */
    @Upsert
    suspend fun upsert(version: PlanVersionEntity)

    @Query("DELETE FROM plan_versions")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Register entity, DAO, migration, version bump in `RecompDatabase.kt`**

Add import near the other entity imports:
```kotlin
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
```
Add import near the other dao imports:
```kotlin
import com.zack.recomptracker.data.local.dao.PlanVersionDao
```
Add `PlanVersionEntity::class,` to the `entities = [ ... ]` list, and change `version = 12` to `version = 13`.

Add the abstract accessor with the others:
```kotlin
    abstract fun planVersionDao(): PlanVersionDao
```

Add the migration in the companion (next to `MIGRATION_11_12`):
```kotlin
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan_versions (" +
                        "effectiveFrom TEXT PRIMARY KEY NOT NULL, " +
                        "targetCalories INTEGER NOT NULL, " +
                        "targetProteinG INTEGER NOT NULL, " +
                        "targetCarbsG INTEGER NOT NULL, " +
                        "targetFatG INTEGER NOT NULL, " +
                        "calorieZoneLowerBound INTEGER NOT NULL, " +
                        "calorieZoneUpperBound INTEGER NOT NULL, " +
                        "createdAt TEXT NOT NULL)",
                )
            }
        }
```

Append the migration to `.addMigrations(...)` on the build call (after `MIGRATION_11_12`):
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
```

- [ ] **Step 4: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (KSP regenerates the Room implementation with the new DAO).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/local
git commit -m "feat(plan): plan_versions Room entity + DAO + migration v12->v13"
```

---

### Task 3: PlanRepository records history at the save() choke point

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/PlanRepository.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt:126`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/PlanRepositoryHistoryTest.kt`

> **Design note:** `PlanRepository` already wraps `AppPreferences` (DataStore, needs a `Context` — not unit-testable as-is). Keep the *decision* logic pure (it lives in `PlanHistory.targetsChanged`, already tested in Task 1) and unit-test the repository's mapping/upsert wiring with a fake DAO via a small extracted helper. The test below verifies the mapping helpers, which carry the real logic.

- [ ] **Step 1: Write the failing test (mapping + version-row construction)**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.preferences.PlanPreferences
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanRepositoryHistoryTest {
    @Test fun `toTargets pulls the day-judging fields from preferences`() {
        val prefs = PlanPreferences(
            targetCalories = 2200, targetProteinG = 180, targetCarbsG = 200, targetFatG = 60,
            calorieZoneLowerBound = 2100, calorieZoneUpperBound = 2300,
        )
        val t = prefs.toPlanTargets()
        assertEquals(2200, t.calories)
        assertEquals(180, t.proteinG)
        assertEquals(2100, t.zoneLowerBound)
        assertEquals(2300, t.zoneUpperBound)
    }

    @Test fun `versionEntity stamps effectiveFrom and createdAt`() {
        val prefs = PlanPreferences(targetCalories = 2000)
        val entity = prefs.toPlanVersionEntity(
            effectiveFrom = LocalDate.parse("2026-06-30"),
            createdAt = "2026-06-30T10:00:00Z",
        )
        assertEquals("2026-06-30", entity.effectiveFrom)
        assertEquals(2000, entity.targetCalories)
        assertEquals("2026-06-30T10:00:00Z", entity.createdAt)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.PlanRepositoryHistoryTest"`
Expected: FAIL — unresolved `toPlanTargets`, `toPlanVersionEntity`.

- [ ] **Step 3: Rewrite `PlanRepository.kt`**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The day-judging targets from the current preferences. */
fun PlanPreferences.toPlanTargets(): PlanTargets = PlanTargets(
    calories = targetCalories,
    proteinG = targetProteinG,
    carbsG = targetCarbsG,
    fatG = targetFatG,
    zoneLowerBound = calorieZoneLowerBound,
    zoneUpperBound = calorieZoneUpperBound,
)

/** Build a historical version row from preferences, stamped with [effectiveFrom]/[createdAt]. */
fun PlanPreferences.toPlanVersionEntity(effectiveFrom: LocalDate, createdAt: String): PlanVersionEntity =
    PlanVersionEntity(
        effectiveFrom = effectiveFrom.toString(),
        targetCalories = targetCalories,
        targetProteinG = targetProteinG,
        targetCarbsG = targetCarbsG,
        targetFatG = targetFatG,
        calorieZoneLowerBound = calorieZoneLowerBound,
        calorieZoneUpperBound = calorieZoneUpperBound,
        createdAt = createdAt,
    )

private fun PlanVersionEntity.toPlanVersion(): PlanVersion = PlanVersion(
    effectiveFrom = LocalDate.parse(effectiveFrom),
    targets = PlanTargets(
        calories = targetCalories,
        proteinG = targetProteinG,
        carbsG = targetCarbsG,
        fatG = targetFatG,
        zoneLowerBound = calorieZoneLowerBound,
        zoneUpperBound = calorieZoneUpperBound,
    ),
)

class PlanRepository(
    private val appPreferences: AppPreferences,
    private val planVersionDao: PlanVersionDao,
    private val dateProvider: DateProvider,
) {
    val preferences: Flow<PlanPreferences> = appPreferences.preferences

    /** All historical plan versions, oldest first. */
    fun observeVersions(): Flow<List<PlanVersion>> =
        planVersionDao.observeAll().map { rows -> rows.map { it.toPlanVersion() } }

    /** The plan in effect on [date] as a Flow (re-emits when history changes). */
    fun observePlanOn(date: LocalDate): Flow<PlanTargets> =
        observeVersions().map { versions -> PlanHistory.planOn(versions, date) }

    /** The plan in effect on [date] (one-shot read). */
    suspend fun planOn(date: LocalDate): PlanTargets =
        PlanHistory.planOn(planVersionDao.getAll().map { it.toPlanVersion() }, date)

    /** Resolve many dates at once (one DAO read). */
    suspend fun targetsByDate(dates: List<LocalDate>): Map<LocalDate, PlanTargets> =
        PlanHistory.resolve(planVersionDao.getAll().map { it.toPlanVersion() }, dates)

    /**
     * Persist the plan and, if the day-judging targets changed, record a history version for
     * today (upsert by date — multiple same-day changes keep only the final value). This is the
     * single choke point: every plan writer routes through save(), so history stays complete and
     * non-target edits (e.g. the Health Connect toggle) never create a version.
     */
    suspend fun save(preferences: PlanPreferences) {
        val previous = appPreferences.preferences.first()
        appPreferences.save(preferences)
        if (PlanHistory.targetsChanged(previous.toPlanTargets(), preferences.toPlanTargets())) {
            planVersionDao.upsert(
                preferences.toPlanVersionEntity(
                    effectiveFrom = dateProvider.today(),
                    createdAt = Instant.now().toString(),
                ),
            )
        }
    }

    suspend fun resetDefaults() {
        appPreferences.resetDefaults()
        planVersionDao.upsert(
            PlanPreferences().toPlanVersionEntity(
                effectiveFrom = dateProvider.today(),
                createdAt = Instant.now().toString(),
            ),
        )
    }
}
```

- [ ] **Step 4: Update the `AppContainer` construction (line 126)**

Replace:
```kotlin
    val planRepository = PlanRepository(appPreferences)
```
with:
```kotlin
    val planRepository = PlanRepository(
        appPreferences = appPreferences,
        planVersionDao = database.planVersionDao(),
        dateProvider = dateProvider,
    )
```
(`dateProvider` is defined at line 120, above this point; `database` is lazy and safe to touch.)

- [ ] **Step 5: Run tests + type-check**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.PlanRepositoryHistoryTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/PlanRepository.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/data/repository/PlanRepositoryHistoryTest.kt
git commit -m "feat(plan): PlanRepository records a version on target change + history read helpers"
```

---

### Task 4: Idempotent backfill initializer + AppContainer wiring

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/PlanHistoryInitializer.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (construct + launch)
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/PlanHistoryInitializerTest.kt`

- [ ] **Step 1: Write the failing test (fake DAO)**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanHistoryInitializerTest {
    private class FakeDao(initial: List<PlanVersionEntity> = emptyList()) : PlanVersionDao {
        val rows = initial.toMutableList()
        override fun observeAll(): Flow<List<PlanVersionEntity>> = flowOf(rows)
        override suspend fun getAll(): List<PlanVersionEntity> = rows
        override suspend fun count(): Int = rows.size
        override suspend fun upsert(version: PlanVersionEntity) {
            rows.removeAll { it.effectiveFrom == version.effectiveFrom }
            rows.add(version)
        }
        override suspend fun deleteAll() = rows.clear()
    }

    @Test fun `seeds one baseline from current prefs when empty`() = runTest {
        val dao = FakeDao()
        val init = PlanHistoryInitializer(dao) { PlanPreferences(targetCalories = 2345) }
        init.seedIfEmpty()
        assertEquals(1, dao.rows.size)
        assertEquals(PlanHistory.BASELINE_DATE.toString(), dao.rows.first().effectiveFrom)
        assertEquals(2345, dao.rows.first().targetCalories)
    }

    @Test fun `is idempotent — does nothing when already seeded`() = runTest {
        val dao = FakeDao(
            listOf(
                PlanVersionEntity(
                    effectiveFrom = "2026-01-01", targetCalories = 2000, targetProteinG = 1,
                    targetCarbsG = 1, targetFatG = 1, calorieZoneLowerBound = 1,
                    calorieZoneUpperBound = 1, createdAt = "x",
                ),
            ),
        )
        val init = PlanHistoryInitializer(dao) { PlanPreferences(targetCalories = 9999) }
        init.seedIfEmpty()
        assertEquals(1, dao.rows.size)
        assertEquals(2000, dao.rows.first().targetCalories) // unchanged
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.PlanHistoryInitializerTest"`
Expected: FAIL — `PlanHistoryInitializer` not defined.

- [ ] **Step 3: Implement `PlanHistoryInitializer.kt`**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Seeds the plan-history ledger exactly once. Room migrations can't read DataStore, so the
 * v12->v13 migration only creates the (empty) table; this runs at app start and, when the table
 * is empty, writes ONE baseline row from the current DataStore plan effective from
 * [PlanHistory.BASELINE_DATE]. Every already-logged day then resolves to the current plan (no
 * behavioral regression), and the user's NEXT plan change correctly only affects today forward.
 * Idempotent: a populated table is left untouched.
 */
class PlanHistoryInitializer(
    private val planVersionDao: PlanVersionDao,
    private val currentPreferences: suspend () -> PlanPreferences,
) {
    suspend fun seedIfEmpty() {
        if (planVersionDao.count() > 0) return
        val prefs = currentPreferences()
        planVersionDao.upsert(
            prefs.toPlanVersionEntity(
                effectiveFrom = PlanHistory.BASELINE_DATE,
                createdAt = Instant.now().toString(),
            ),
        )
    }

    companion object {
        /** Convenience factory using a repository's preferences flow as the current source. */
        fun from(planVersionDao: PlanVersionDao, planRepository: PlanRepository) =
            PlanHistoryInitializer(planVersionDao) { planRepository.preferences.first() }
    }
}
```

- [ ] **Step 4: Wire into `AppContainer`**

After the `appScope` definition (line 146) and after `planRepository` exists, add:
```kotlin
    val planHistoryInitializer = PlanHistoryInitializer.from(database.planVersionDao(), planRepository)

    init {
        appScope.launch { planHistoryInitializer.seedIfEmpty() }
    }
```
Ensure these imports exist in `AppContainer.kt`:
```kotlin
import com.zack.recomptracker.data.repository.PlanHistoryInitializer
import kotlinx.coroutines.launch
```
(If an `init {}` block already exists in `AppContainer`, add the `appScope.launch { ... }` line inside it instead of adding a second block.)

- [ ] **Step 5: Run tests + type-check**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.PlanHistoryInitializerTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/PlanHistoryInitializer.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/data/repository/PlanHistoryInitializerTest.kt
git commit -m "feat(plan): idempotent plan-history backfill seeded at app start"
```

---

## PHASE 2 — Consumers (parallelizable by area; each depends only on Phase 1)

### Task 5: AdherenceCalculator — per-day targets

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/adherence/AdherenceCalculator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/adherence/AdherenceCalculatorTest.kt` (create if absent; otherwise extend)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.adherence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorPerDayTest {
    private val calc = AdherenceCalculator()
    private val d0 = LocalDate.parse("2026-06-01")

    @Test fun `each day is graded against its own target`() {
        // Day hit its (old, higher) target exactly -> 100%; lowering a later target must not
        // retroactively penalize it.
        val days = listOf(
            NutritionDay(d0, calories = 2500, targetCalories = 2500),
            NutritionDay(d0.plusDays(1), calories = 2200, targetCalories = 2200),
        )
        assertEquals(100.0, calc.calculate(days), 0.001)
    }

    @Test fun `unlogged days are excluded`() {
        val days = listOf(
            NutritionDay(d0, calories = 0, targetCalories = 2500),
            NutritionDay(d0.plusDays(1), calories = 2200, targetCalories = 2200),
        )
        assertEquals(100.0, calc.calculate(days), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.adherence.AdherenceCalculatorPerDayTest"`
Expected: FAIL — `NutritionDay` has no `targetCalories`; `calculate(days)` overload missing.

- [ ] **Step 3: Modify `AdherenceCalculator.kt`**

Change `NutritionDay` to:
```kotlin
data class NutritionDay(
    val date: LocalDate,
    val calories: Int,
    val targetCalories: Int,
)
```
Replace the `calculate(days, targetCalories)` method with a per-day version (keep `dailyAdherencePercent` and `loggingConsistency` exactly as they are):
```kotlin
    /**
     * Adherence QUALITY across LOGGED days only, each graded against ITS OWN day target
     * (see [NutritionDay.targetCalories]). Days with no intake are excluded. Returns 0 if there
     * are no logged days.
     */
    fun calculate(days: List<NutritionDay>): Double {
        val logged = days.distinctBy { it.date }.filter { it.calories > 0 && it.targetCalories > 0 }
        if (logged.isEmpty()) return 0.0
        val sum = logged.sumOf { dailyAdherencePercent(it.calories, it.targetCalories) }
        return sum / logged.size.toDouble()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.adherence.AdherenceCalculatorPerDayTest"`
Expected: PASS. (Compilation of the three call sites is fixed in Tasks 6–7 and 11; until then the app module won't fully compile — run those next.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/adherence/AdherenceCalculator.kt app/src/test/java/com/zack/recomptracker/domain/adherence
git commit -m "feat(adherence): grade each day against its own historical target"
```

> **Note for executor:** Tasks 6, 7, and 11 update the three `NutritionDay`/`calculate` call sites. Do them together (or immediately after Task 5) so `:app:compileDebugKotlin` is green again before moving on.

---

### Task 6: ProgressViewModel — per-day plan

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt`

- [ ] **Step 1: Swap the live plan for plan history in the combine**

In the `init` combine (line ~76), replace the `planRepository.preferences` source with `planRepository.observeVersions()`:
```kotlin
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntries(),
                logRepository.observePerformances(),
                planRepository.observeVersions(),
                rangeDays,
            ) { logs, meals, performances, versions, range ->
```

- [ ] **Step 2: Resolve targets per date and use them for adherence**

After `val dates = ...` (line ~84) add:
```kotlin
                val targetsByDate = com.zack.recomptracker.domain.plan.PlanHistory.resolve(versions, dates)
```
Replace the `adherenceValues` block (lines ~97-102) with:
```kotlin
                val adherenceValues = dates.map {
                    adherenceCalculator.dailyAdherencePercent(
                        calories = mealsByDate[it].orEmpty().macroTotals().calories,
                        targetCalories = targetsByDate[it]?.calories ?: 0,
                    ).toFloat()
                }
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (ProgressViewModel no longer references `preferences.targetCalories`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ProgressViewModel.kt
git commit -m "fix(progress): adherence chart grades each day against its historical plan"
```

---

### Task 7: DashboardViewModel + AppContainer weekly review — per-day plan

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

> Both build a 14-day `nutritionDays` list and call `adherenceCalculator.calculate(days, prefs.targetCalories)`, plus a 7-day in-zone count using `prefs.calorieZone*`. Switch both to per-day targets via `planRepository.observeVersions()` + `PlanHistory.resolve`.

- [ ] **Step 1: DashboardViewModel — add versions to the combine**

In `init` (line ~226) add `planRepository.observeVersions()` as a 5th source and thread it into `buildState`:
```kotlin
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntriesSince(windowStart),
                logRepository.observePerformances(),
                planRepository.preferences,
                planRepository.observeVersions(),
            ) { logs, meals, performances, preferences, versions ->
                buildState(logs, meals, performances, preferences, versions)
            }
```
Update the `buildState` signature to accept `versions: List<com.zack.recomptracker.domain.plan.PlanVersion>`.

- [ ] **Step 2: DashboardViewModel — per-day targets for nutritionDays + zone**

Inside `buildState`, after `val mealsByDate = ...` (line ~257) add:
```kotlin
        val dayTargets = com.zack.recomptracker.domain.plan.PlanHistory.resolve(
            versions,
            (0..13).map { last14Start.plusDays(it.toLong()) } + (0..6).map { last7Start.plusDays(it.toLong()) },
        )
```
Replace the `nutritionDays` construction (lines ~259-262) with:
```kotlin
        val nutritionDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            NutritionDay(
                date = date,
                calories = mealsByDate[date].orEmpty().macroTotals().calories,
                targetCalories = dayTargets[date]?.calories ?: preferences.targetCalories,
            )
        }
```
Replace `val adherence = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories)` with:
```kotlin
        val adherence = adherenceCalculator.calculate(nutritionDays)
```
Replace the `inZoneDays7` block (lines ~316+) so each day uses its own zone:
```kotlin
        val inZoneDays7 = last7DaysCalories.count { day ->
            val d = last7Start.plusDays(last7DaysCalories.indexOf(day).toLong())
            val z = dayTargets[d]
            z != null && z.zoneLowerBound > 0 && day.calories > 0 &&
                day.calories >= z.zoneLowerBound && day.calories <= z.zoneUpperBound
        }
```
> Executor: if `last7DaysCalories` is an `ImmutableList` of `DayCalories` whose index↔date mapping is awkward with `indexOf`, instead compute `inZoneDays7` in the same `(0..6)` loop that builds `last7DaysCalories` (lines ~304-315), resolving `dayTargets[date]` there. Prefer that cleaner form.

- [ ] **Step 3: AppContainer.computeWeeklyReviewData — per-day plan + historical week target**

In `weeklyReviewDataFlow` (line ~244) add `planRepository.observeVersions()` as a 5th source and pass it to `computeWeeklyReviewData`. Update that function's signature with `versions: List<PlanVersion>`.

Replace the `nutritionDays` block (lines ~268-271) with:
```kotlin
        val weekTargets = PlanHistory.resolve(versions, (0..13).map { last14Start.plusDays(it.toLong()) })
        val nutritionDays = (0..13).map { off ->
            val d = last14Start.plusDays(off.toLong())
            NutritionDay(d, mealsByDate[d].orEmpty().macroTotals().calories, weekTargets[d]?.calories ?: prefs.targetCalories)
        }
```
Replace `adherencePercent = adherenceCalculator.calculate(nutritionDays, prefs.targetCalories)` (line ~288) with:
```kotlin
            adherencePercent = adherenceCalculator.calculate(nutritionDays),
```
Replace the final build call (line ~302) so the review's "current target" is the plan in effect at week end — this also makes the briefing **signature stable** when the live plan later changes (fixes spurious regeneration):
```kotlin
        val weekEndTarget = PlanHistory.planOn(versions, today).calories
        return weeklyReviewComputer.build(weekStart, input, result, weekEndTarget)
```
Add imports to `AppContainer.kt`:
```kotlin
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanVersion
```

- [ ] **Step 4: Type-check + run dashboard tests if present**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.*"`
Expected: BUILD SUCCESSFUL; existing dashboard tests pass (update any test that constructs `NutritionDay(date, calories)` to add `targetCalories`, or that calls `calculate(days, target)` to use `calculate(days)`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "fix(dashboard,review): adherence + in-zone + weekly review use per-day historical plan"
```

---

### Task 8: StreakRepository — per-day zone

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/StreakRepositoryTest.kt` (extend if exists)

- [ ] **Step 1: Write the failing test (pure `buildStreaks`)**

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRepositoryZoneTest {
    @Test fun `a past in-zone day stays a hit after the zone is lowered later`() {
        val today = LocalDate.parse("2026-06-30")
        val pastDay = today.minusDays(1)
        // Old plan (effective from the past): zone 2400..2600. New plan (today): zone 1900..2100.
        val versions = listOf(
            PlanVersion(LocalDate.parse("2026-01-01"), PlanTargets(2500, 1, 1, 1, 2400, 2600)),
            PlanVersion(today, PlanTargets(2000, 1, 1, 1, 1900, 2100)),
        )
        val streaks = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(pastDay to 2500), // in OLD zone, NOT in new zone
            completedSessionDates = emptyList(),
            versions = versions,
            dailyStepGoal = null,
            today = today,
            calculator = StreakCalculator(),
        )
        // pastDay was within its day's zone -> must count as a calorie hit.
        assertTrue(streaks.calorie.last7.dropLast(1).last())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.StreakRepositoryZoneTest"`
Expected: FAIL — `buildStreaks` takes `prefs`, not `versions`.

- [ ] **Step 3: Modify `StreakRepository.kt`**

In `streaks()` combine (line ~27), replace `planRepository.preferences` with `planRepository.observeVersions()` and rename the lambda param `prefs` → `versions`; pass `versions = versions` into `buildStreaks`.

Change `buildStreaks` signature: replace `prefs: PlanPreferences` with
`versions: List<com.zack.recomptracker.domain.plan.PlanVersion>`.

Replace the `calorieDays` block (lines ~72-79) with per-day zone resolution:
```kotlin
    val calorieDays: Set<LocalDate> = eatenCaloriesByDate
        .filter { (date, cals) ->
            val z = com.zack.recomptracker.domain.plan.PlanHistory.planOn(versions, date)
            cals > 0 && z.zoneLowerBound > 0 && cals >= z.zoneLowerBound && cals <= z.zoneUpperBound
        }
        .keys
```
Remove the now-unused `import com.zack.recomptracker.data.preferences.PlanPreferences` if nothing else uses it. Add `import com.zack.recomptracker.domain.plan.PlanHistory` (or use the FQN as shown).

> Guard: `PlanHistory.planOn` throws on empty `versions`. In production `versions` is never empty (the initializer always seeds a baseline). For safety against a not-yet-seeded first frame, in `buildStreaks` early-return `Streaks()`-equivalent empty result when `versions.isEmpty()`, OR have the caller filter. Simplest: at the top of `buildStreaks`, `if (versions.isEmpty()) return Streaks(...empty...)` mirroring the existing empty shape. Executor: match the real `Streaks` empty construction used elsewhere.

- [ ] **Step 4: Run test + type-check**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.StreakRepositoryZoneTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL. (Fix any existing StreakRepository test that passed `prefs` to `buildStreaks`.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/StreakRepository.kt app/src/test/java/com/zack/recomptracker/data/repository/StreakRepositoryZoneTest.kt
git commit -m "fix(streak): calorie streak uses each day's historical calorie zone"
```

---

### Task 9: FoodLogViewModel + FoodScreen + WeekCalorieStrip — per-date targets

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt`

> Strategy: keep `FoodLogUiState.target: PlanPreferences` (so `FoodScreen` is barely touched) but overlay the *day-judging* fields from `planOn(selectedDate)`. Supply the week strip per-day targets.

- [ ] **Step 1: FoodLogViewModel — overlay per-date targets onto `target`**

Replace the main `init` combine (lines ~85-92) so the selected date drives the plan lookup:
```kotlin
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                combine(
                    logRepository.observeDay(date),
                    planRepository.preferences,
                    planRepository.observePlanOn(date),
                    logRepository.observeSlots(),
                ) { day, prefs, dayPlan, slots ->
                    Quadruple(day, prefs, dayPlan, slots)
                }
            }.collect { (day, prefs, dayPlan, slots) ->
                val dayTarget = prefs.copy(
                    targetCalories = dayPlan.calories,
                    targetProteinG = dayPlan.proteinG,
                    targetCarbsG = dayPlan.carbsG,
                    targetFatG = dayPlan.fatG,
                    calorieZoneLowerBound = dayPlan.zoneLowerBound,
                    calorieZoneUpperBound = dayPlan.zoneUpperBound,
                )
                val slotMap = day.meals.groupBy { it.slotId }
                val slottedEntries = slots.map { slot ->
                    val entries = slotMap[slot.id].orEmpty()
                    MealSlotWithEntries(slot = slot, entries = entries, totals = entries.macroTotals())
                }.toImmutableList()
                _uiState.update {
                    it.copy(
                        selectedDate = day.date,
                        target = dayTarget,
                        totals = day.totals,
                        plannedTotals = day.plannedTotals,
                        hasPlannedEntries = day.meals.any { meal -> meal.planned },
                        slots = slottedEntries,
                        restOfDayInsightContext = if (day.date == today) {
                            buildRestOfDayInsightContext(day.totals, dayTarget, day.meals.size)
                        } else null,
                    )
                }
            }
        }
```
Add a small local `Quadruple` data class at the bottom of the file (Kotlin has no built-in Quadruple):
```kotlin
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
```
(`flatMapLatest`, `combine` are already imported; `observePlanOn` returns `Flow<PlanTargets>`.)

- [ ] **Step 2: FoodLogViewModel — per-day targets for the week strip**

The week-strip launch (lines ~121-129) builds `weekSummary` from calories only. The strip needs each day's zone. Extend `DayCalorieSummary` consumption: resolve targets for the 7 dates and stash them. Simplest within scope — replace that launch with:
```kotlin
        viewModelScope.launch {
            val weekDates = (0..6).map { today.minusDays((6 - it).toLong()) }
            combine(
                logRepository.observeWeekCalories(today.minusDays(6), today),
                planRepository.observeVersions(),
            ) { weekMap, versions ->
                val byDate = com.zack.recomptracker.domain.plan.PlanHistory.resolve(versions, weekDates)
                weekDates.map { d ->
                    DayCalorieSummary(
                        date = d,
                        calories = weekMap[d] ?: 0,
                        targetCalories = byDate[d]?.calories ?: 0,
                        zoneLowerBound = byDate[d]?.zoneLowerBound ?: 0,
                        zoneUpperBound = byDate[d]?.zoneUpperBound ?: 0,
                    )
                }.toImmutableList()
            }.collect { summaries -> _uiState.update { it.copy(weekSummary = summaries) } }
        }
```
> Executor: this requires `DayCalorieSummary` (in `data/repository/`) to carry `targetCalories`, `zoneLowerBound`, `zoneUpperBound` (with defaults `= 0` to avoid breaking other constructors). Add those fields. If other producers of `DayCalorieSummary` exist, the defaults keep them compiling.

- [ ] **Step 3: WeekCalorieStrip — consume per-day zone**

`WeekCalorieStrip` currently takes scalar `targetCalories`/`targetLow`/`targetHigh`. Change its API to read the per-day bounds from each `DayCalorieSummary` it renders (it already receives the list). Remove the scalar zone params; for each bar compute over/under/in-zone from that day's `summary.zoneLowerBound`/`summary.zoneUpperBound` (fall back to no-zone when `0`). Update the call site in `FoodScreen.kt` to stop passing the scalar zone params.

> Executor: read `WeekCalorieStrip.kt` (lines ~51-69) and its `FoodScreen` call site, then make the per-day swap. Keep the visual design identical (same colors/sizes — see `docs/design-system.md`); only the data source per bar changes.

- [ ] **Step 4: FoodScreen.calorieStatus — verify it reads `state.target`**

`calorieStatus()` (lines ~516-529) already derives over/under from `state.target` (now date-correct). No logic change expected; confirm it does not read `planRepository`/live prefs directly. If it references any live-plan source, switch it to `state.target`.

- [ ] **Step 5: Type-check + run today/foodlog tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.today.*"`
Expected: BUILD SUCCESSFUL; tests pass (update any test asserting past-day `target` equals live prefs).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt app/src/main/java/com/zack/recomptracker/ui/component/WeekCalorieStrip.kt app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt
git commit -m "fix(foodlog): past days and week strip judged against their historical plan"
```

---

### Task 10: WeeklyReviewComputer signature — verify stability

**Files:**
- Modify (verify): `app/src/main/java/com/zack/recomptracker/domain/review/WeeklyReviewComputer.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/review/WeeklyReviewComputerTest.kt` (create if absent)

> The signature includes `data.currentTargetCalories` (line ~50). Task 7 already makes `AppContainer` pass the *week-end historical* target instead of the live target, so a later plan change no longer alters a past week's signature. This task locks that in with a test; no production change needed if Task 7 is done.

- [ ] **Step 1: Write the test**

```kotlin
package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyReviewComputerSignatureTest {
    private val computer = WeeklyReviewComputer()
    private fun input() = AdjustmentInput(
        daysLogged = 14, adherencePercent = 90.0, weeksSincePhaseStart = 4,
        weightTrendKgPerWeek = 0.0, waistTrendCmPerWeek = 0.0,
        performanceTrend = PerformanceTrend.STABLE, recoveryTrend = RecoveryTrend.GOOD,
    )
    private fun result() = AdjustmentResult(AdjustmentVerdict.HOLD, 0, emptyList())

    @Test fun `signature is stable for a fixed week target regardless of inputs unchanged`() {
        val a = computer.build("2026-06-22", input(), result(), currentTargetCalories = 2500)
        val b = computer.build("2026-06-22", input(), result(), currentTargetCalories = 2500)
        assertEquals(computer.signature(a), computer.signature(b))
    }
}
```
> Executor: confirm the real constructor signatures for `AdjustmentResult`/`AdjustmentInput` and adjust the test if field names differ.

- [ ] **Step 2: Run + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.domain.review.WeeklyReviewComputerSignatureTest"`
Expected: PASS.
```bash
git add app/src/test/java/com/zack/recomptracker/domain/review/WeeklyReviewComputerSignatureTest.kt
git commit -m "test(review): lock weekly-review signature stability against current-plan changes"
```

---

### Task 11: CoachToolExecutor.getWeeklyTrends — per-day plan

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`

- [ ] **Step 1: Replace the single live target with per-day targets**

In `getWeeklyTrends()` (lines ~64-86), replace:
```kotlin
        val targetCalories = planRepository.preferences.first().targetCalories
```
with a per-day resolution and update the `nutritionDays` + `adherencePercent`:
```kotlin
        val weekDates = (0..6).map { start.plusDays(it.toLong()) }
        val targetsByDate = planRepository.targetsByDate(weekDates)
        val nutritionDays = weekDates.map { date ->
            NutritionDay(
                date = date,
                calories = macroMap[date]?.calories ?: 0,
                targetCalories = targetsByDate[date]?.calories ?: 0,
            )
        }
        val adherencePercent = adherenceCalculator.calculate(nutritionDays).toInt()
```
Remove the now-unused `.first()` import only if nothing else in the file uses `first()` (the today-summary path may still use it — check before removing).

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt
git commit -m "fix(coach): weekly trends grade each day against its historical plan"
```

---

### Task 12: BackupRepository — export/import + reset plan_versions

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/repository/BackupRepositoryTest.kt` (extend if exists)

- [ ] **Step 1: Read the current backup format**

Read `BackupRepository.kt` fully. Identify the serialized payload data class (the export/import DTO) and `restoreFromJson()` / `resetEverything()` (lines ~41-80).

- [ ] **Step 2: Add `planVersions` to the payload**

Add a `planVersions: List<PlanVersionEntity> = emptyList()` field to the serialized backup DTO (`PlanVersionEntity` is already `@Serializable`). On **export**, populate it from `database.planVersionDao().getAll()`. On **import** (`restoreFromJson`), after restoring prefs, `database.planVersionDao().deleteAll()` then re-insert each `planVersions` row (use `upsert` in a loop). The default `= emptyList()` keeps older backup files importable (they just have no history → the initializer will seed a baseline on next launch).

- [ ] **Step 3: resetEverything clears history**

In `resetEverything()`, add `database.planVersionDao().deleteAll()` alongside the other table clears. (The initializer re-seeds the default baseline on next app start; or call `planRepository.resetDefaults()` which now writes a baseline row.)

- [ ] **Step 4: Test the round-trip (extend existing backup test or add)**

Add a test asserting that an exported payload containing a `plan_versions` row imports back into the DAO. If the existing backup test uses an in-memory Room DB, follow that pattern; otherwise assert at the DTO (de)serialization level that `planVersions` survives `Json.encodeToString`/`decodeFromString`.

- [ ] **Step 5: Type-check + test + commit**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.data.repository.BackupRepository*"`
Expected: BUILD SUCCESSFUL + PASS.
```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/BackupRepository.kt app/src/test/java/com/zack/recomptracker/data/repository/BackupRepositoryTest.kt
git commit -m "feat(backup): export/import/reset plan version history"
```

---

## PHASE 3 — Verification

### Task 13: Full build, test, and manual smoke checklist

- [ ] **Step 1: Full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Hand off to the user for on-device verification** (per project rule — the user tests UI). Provide this checklist:
  - Log food on a past day so it lands inside the current zone.
  - Lower the calorie target on the Plan screen.
  - Verify: the past day's status, calorie bar, week strip, adherence chart, dashboard adherence, and calorie streak are **unchanged** for that past day; only **today/future** reflect the new (lower) plan.
  - Toggle Health Connect on/off → confirm **no** new plan version is created (no behavior change to past days).
  - Coach: ask for weekly trends → adherence reflects each day's plan.
  - Export a backup, reset all data, re-import → plan history round-trips; past days judged correctly.

- [ ] **Step 4: Finish the branch** via `superpowers:finishing-a-development-branch`.

---

## Self-Review notes (author)

- **Spec coverage:** every affected consumer from the spec table has a task (FoodScreen/zone bar/week strip → T9; ProgressVM → T6; DashboardVM + AppContainer review input → T7; StreakRepository → T8; WeeklyReviewComputer → T7+T10; CoachToolExecutor → T11; FoodLogViewModel → T9; backup → T12). Storage/write-path/backfill → T1–T4.
- **Type consistency:** `PlanTargets`/`PlanVersion`/`PlanHistory.planOn|resolve|targetsChanged|BASELINE_DATE`, `NutritionDay(date, calories, targetCalories)`, `AdherenceCalculator.calculate(days)`, `PlanRepository.observeVersions()/planOn/observePlanOn/targetsByDate`, `PlanVersionDao.observeAll/getAll/count/upsert/deleteAll` are used consistently across tasks.
- **Known executor reads required** (file too large/visual to inline fully): `WeekCalorieStrip.kt` + its `FoodScreen` call site (T9.3), `BackupRepository.kt` payload DTO (T12), exact `Streaks` empty-construction (T8.3). Each task names the precise lines and the exact transformation.
