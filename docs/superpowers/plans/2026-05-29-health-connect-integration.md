# Health Connect Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate Samsung Health via Health Connect to auto-fill steps, body weight, and sleep on the Today screen as an opt-in feature where manual entry always wins.

**Architecture:** A new `HealthConnectRepository` encapsulates all HC SDK calls (availability check, permission contract, data reads). `LogRepository.applyHealthConnectSync` applies HC data to the DB using a "null fields only" rule. `TodayViewModel` triggers a sync on init; `SettingsViewModel` handles connect/disconnect/sync-now actions.

**Tech Stack:** Health Connect SDK (`androidx.health.connect:connect-client:1.1.0-rc01`), Room (existing), DataStore (existing), Jetpack Compose (existing), JUnit4 + kotlinx-coroutines-test (existing).

**Spec:** `docs/superpowers/specs/2026-05-29-health-connect-design.md`

---

## File Map

| Status | File | Role |
|--------|------|------|
| **Create** | `app/src/main/java/…/data/health/HealthConnectModels.kt` | Sealed availability enum + read result data class |
| **Create** | `app/src/main/java/…/data/health/HealthConnectRepository.kt` | All HC SDK calls; lazy client; never throws |
| **Create** | `app/src/test/java/…/data/LogRepositorySyncTest.kt` | Unit tests for "manual entry wins" logic |
| **Modify** | `gradle/libs.versions.toml` | Add `healthConnect = "1.1.0-rc01"` version + library alias |
| **Modify** | `app/build.gradle.kts` | Add implementation dependency |
| **Modify** | `app/src/main/AndroidManifest.xml` | Add 3 health permissions + `<queries>` element |
| **Modify** | `app/src/main/java/…/data/preferences/PlanPreferences.kt` | Add `healthConnectEnabled: Boolean = false` |
| **Modify** | `app/src/main/java/…/data/preferences/AppPreferences.kt` | Add key + read/write for new field |
| **Modify** | `app/src/main/java/…/data/repository/LogRepository.kt` | Add `applyHealthConnectSync()` |
| **Modify** | `app/src/main/java/…/core/AppContainer.kt` | Instantiate `HealthConnectRepository`; pass to both VM factories |
| **Modify** | `app/src/main/java/…/ui/today/TodayViewModel.kt` | Add `hcRepository` param; auto-sync on init |
| **Modify** | `app/src/main/java/…/ui/settings/SettingsViewModel.kt` | Add `hcRepository` param; HC state + actions |
| **Modify** | `app/src/main/java/…/ui/settings/SettingsScreen.kt` | Samsung Health section card + permission launcher |

Short package prefix used in all paths: `com/zack/recomptracker`

---

## Task 1: Dependency & Manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add Health Connect version to `libs.versions.toml`**

In the `[versions]` block, add after the existing `vico` line:
```toml
healthConnect = "1.1.0-rc01"
```

In the `[libraries]` block, add after the `vico-compose-m3` line:
```toml
androidx-health-connect = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
```

- [ ] **Step 2: Add dependency to `app/build.gradle.kts`**

In the `dependencies { }` block, add after `implementation(libs.vico.compose.m3)`:
```kotlin
implementation(libs.androidx.health.connect)
```

- [ ] **Step 3: Add permissions and queries element to `AndroidManifest.xml`**

Replace the current `<manifest xmlns:android=…>` opening with:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_WEIGHT" />
    <uses-permission android:name="android.permission.health.READ_SLEEP_SESSION" />

    <queries>
        <package android:name="com.google.android.apps.healthdata" />
    </queries>

    <application
```

(Keep everything inside `<application>` exactly as-is.)

- [ ] **Step 4: Sync Gradle and verify the project builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no HC import errors yet — the dependency is just declared)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: add Health Connect dependency and manifest permissions"
```

---

## Task 2: Data Models

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/health/HealthConnectModels.kt`

- [ ] **Step 1: Create the models file**

```kotlin
package com.zack.recomptracker.data.health

sealed class HealthConnectAvailability {
    object Available : HealthConnectAvailability()
    object NotInstalled : HealthConnectAvailability()
    object NotSupported : HealthConnectAvailability()
}

data class HealthConnectReadResult(
    val steps: Int? = null,
    val weightKg: Double? = null,
    val sleepHours: Double? = null,
)
```

- [ ] **Step 2: Build to verify no compile errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/health/HealthConnectModels.kt
git commit -m "feat: add HealthConnectModels (availability + read result)"
```

---

## Task 3: HealthConnectRepository

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/health/HealthConnectRepository.kt`

- [ ] **Step 1: Create the repository**

```kotlin
package com.zack.recomptracker.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(private val context: Context) {

    // Created lazily — HealthConnectClient warns against multiple instances.
    // Only accessed after availability() confirms SDK_AVAILABLE.
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availability(): HealthConnectAvailability = when (
        HealthConnectClient.getSdkStatus(context)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NotInstalled
        else -> HealthConnectAvailability.NotSupported
    }

    fun permissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(): Boolean = runCatching {
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }.getOrDefault(false)

    suspend fun readToday(date: LocalDate): HealthConnectReadResult = runCatching {
        val zone = ZoneId.systemDefault()
        val startOfDay: Instant = date.atStartOfDay(zone).toInstant()
        val now: Instant = Instant.now()
        val thirtyDaysAgo: Instant = date.minusDays(30).atStartOfDay(zone).toInstant()
        val yesterdayNoon: Instant = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()

        val steps = readSteps(startOfDay, now)
        val weightKg = readLatestWeight(thirtyDaysAgo, now)
        val sleepHours = readLatestSleep(yesterdayNoon, now)

        HealthConnectReadResult(steps = steps, weightKg = weightKg, sleepHours = sleepHours)
    }.getOrDefault(HealthConnectReadResult())

    private suspend fun readSteps(start: Instant, end: Instant): Int? {
        val response = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, end))
        )
        return if (response.records.isEmpty()) null
        else response.records.sumOf { it.count }.toInt()
    }

    private suspend fun readLatestWeight(start: Instant, end: Instant): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end))
        )
        return response.records.maxByOrNull { it.time }?.weight?.inKilograms
    }

    private suspend fun readLatestSleep(start: Instant, end: Instant): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
        )
        val latest = response.records.maxByOrNull { it.endTime } ?: return null
        return Duration.between(latest.startTime, latest.endTime).toMinutes() / 60.0
    }
}
```

- [ ] **Step 2: Build to verify no compile errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/health/HealthConnectRepository.kt
git commit -m "feat: add HealthConnectRepository (availability, permissions, readToday)"
```

---

## Task 4: PlanPreferences + AppPreferences

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt`

- [ ] **Step 1: Add `healthConnectEnabled` to `PlanPreferences`**

Add the field as the last property of the data class (after `calorieZoneUpperBound`):

```kotlin
@Serializable
data class PlanPreferences(
    val targetCalories: Int = 2550,
    val targetProteinG: Int = 165,
    val targetCarbsG: Int = 320,
    val targetFatG: Int = 68,
    val maintenancePhaseStartDate: String? = null,
    val weightTrendThresholdKgPerWeek: Double = 0.20,
    val waistIncreaseThresholdCm: Double = 0.5,
    val adherenceMinimumPercent: Double = 85.0,
    val reviewCadenceDays: Int = 7,
    val useMetricUnits: Boolean = true,
    val calorieZoneLowerBound: Int = 2400,
    val calorieZoneUpperBound: Int = 2600,
    val healthConnectEnabled: Boolean = false,
)
```

- [ ] **Step 2: Add `healthConnectEnabled` to `AppPreferences`**

In the `private object Keys` block, add:
```kotlin
val HealthConnectEnabled = booleanPreferencesKey("health_connect_enabled")
```

In the `preferences` flow `.map { prefs -> PlanPreferences(…) }` block, add (after `useMetricUnits`):
```kotlin
calorieZoneLowerBound = prefs[Keys.CalorieZoneLowerBound] ?: 2400,
calorieZoneUpperBound = prefs[Keys.CalorieZoneUpperBound] ?: 2600,
healthConnectEnabled = prefs[Keys.HealthConnectEnabled] ?: false,
```

Wait — check what the existing read block looks like first and add the line after `useMetricUnits`. Open the file and confirm it reads `calorieZoneLowerBound` and `calorieZoneUpperBound` already. If those keys are missing from `AppPreferences` (they may have been added to `PlanPreferences` without updating `AppPreferences`), add them too. If present, only add `HealthConnectEnabled`.

In the `save()` suspend function, add after the `useMetricUnits` line:
```kotlin
prefs[Keys.HealthConnectEnabled] = preferences.healthConnectEnabled
```

- [ ] **Step 3: Build and run existing tests to verify no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass (the new field has a default of `false`, so existing deserialization is unaffected)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/PlanPreferences.kt \
        app/src/main/java/com/zack/recomptracker/data/preferences/AppPreferences.kt
git commit -m "feat: add healthConnectEnabled to PlanPreferences and AppPreferences"
```

---

## Task 5: LogRepository.applyHealthConnectSync + Unit Tests

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt`
- Create: `app/src/test/java/com/zack/recomptracker/data/LogRepositorySyncTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `app/src/test/java/com/zack/recomptracker/data/LogRepositorySyncTest.kt`:

```kotlin
package com.zack.recomptracker.data

import com.zack.recomptracker.data.health.HealthConnectReadResult
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogRepositorySyncTest {

    private val date = LocalDate.of(2026, 5, 29)

    private fun buildRepository(dao: FakeDailyLogDao) = LogRepository(
        dailyLogDao = dao,
        mealEntryDao = NoopMealEntryDao,
        savedFoodDao = NoopSavedFoodDao,
        savedMealDao = NoopSavedMealDao,
        performanceDao = NoopPerformanceDao,
        weeklyReviewDao = NoopWeeklyReviewDao,
        mealSlotDao = NoopMealSlotDao,
    )

    @Test
    fun `sync writes all fields when no log exists for today`() = runTest {
        val dao = FakeDailyLogDao()
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 8_000, weightKg = 75.5, sleepHours = 7.5)

        repo.applyHealthConnectSync(date, result)

        val saved = dao.logs[date.toString()]!!
        assertEquals(8_000, saved.steps)
        assertEquals(75.5, saved.bodyWeightKg)
        assertEquals(7.5, saved.sleepHours)
    }

    @Test
    fun `sync does not overwrite fields already set by the user`() = runTest {
        val dao = FakeDailyLogDao()
        dao.logs[date.toString()] = DailyLogEntity(
            date = date.toString(),
            steps = 10_000,
            bodyWeightKg = 80.0,
            sleepHours = null,
        )
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 5_000, weightKg = 77.0, sleepHours = 6.0)

        repo.applyHealthConnectSync(date, result)

        val saved = dao.logs[date.toString()]!!
        assertEquals(10_000, saved.steps)        // kept original
        assertEquals(80.0, saved.bodyWeightKg)   // kept original
        assertEquals(6.0, saved.sleepHours)      // filled from HC (was null)
    }

    @Test
    fun `sync does nothing when result has all nulls`() = runTest {
        val dao = FakeDailyLogDao()
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult()

        repo.applyHealthConnectSync(date, result)

        // No upsert should have been called because updated == null (no existing + all nulls = default entity == null in terms of meaningful data)
        // The entity would equal the default-constructed entity which has all nulls, so no upsert is needed.
        // Verify: if no prior log, the result entity is identical to DailyLogEntity(date=…) with all nulls,
        // which equals the existing (null) condition — so upsert is NOT called.
        assertNull(dao.logs[date.toString()])
    }

    @Test
    fun `sync does not call upsert when nothing would change`() = runTest {
        val dao = FakeDailyLogDao()
        dao.logs[date.toString()] = DailyLogEntity(
            date = date.toString(),
            steps = 9_000,
            bodyWeightKg = 78.0,
            sleepHours = 8.0,
        )
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 1_000, weightKg = 60.0, sleepHours = 5.0)

        val upsertCountBefore = dao.upsertCount
        repo.applyHealthConnectSync(date, result)

        assertEquals(upsertCountBefore, dao.upsertCount) // nothing changed, no write
    }
}

// ── Fake DAO ────────────────────────────────────────────────────────────────

private class FakeDailyLogDao : DailyLogDao {
    val logs = mutableMapOf<String, DailyLogEntity>()
    var upsertCount = 0

    override suspend fun getByDate(date: String): DailyLogEntity? = logs[date]
    override suspend fun upsert(log: DailyLogEntity) { logs[log.date] = log; upsertCount++ }

    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(logs[date]) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(logs.values.toList()) }
    override suspend fun getAll(): List<DailyLogEntity> = logs.values.toList()
    override fun observeBetween(startDate: String, endDate: String): Flow<List<DailyLogEntity>> =
        flow { emit(emptyList()) }
    override suspend fun insertAll(logs: List<DailyLogEntity>) = logs.forEach { this.logs[it.date] = it }
    override suspend fun deleteAll() = this.logs.clear()
}

// ── Noop stubs for DAOs not under test ──────────────────────────────────────

private object NoopMealEntryDao : MealEntryDao {
    override fun observeForDate(date: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getForDate(date: String): List<MealEntryEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeAll(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealEntryEntity> = emptyList()
    override suspend fun insert(entry: MealEntryEntity): Long = 0L
    override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
    override suspend fun update(entry: MealEntryEntity) = Unit
    override suspend fun delete(entry: MealEntryEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteForDate(date: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun clearSlotId(slotId: Long) = Unit
}

private object NoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedFoodEntity> = emptyList()
    override suspend fun insert(food: SavedFoodEntity): Long = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedMealEntity> = emptyList()
    override suspend fun insert(meal: SavedMealEntity): Long = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<LiftPerformanceEntity> = emptyList()
    override suspend fun insert(performance: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(performances: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<WeeklyReviewEntity> = emptyList()
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealSlotEntity> = emptyList()
    override suspend fun insert(slot: MealSlotEntity): Long = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*.LogRepositorySyncTest"`
Expected: FAIL — `applyHealthConnectSync` does not exist yet

- [ ] **Step 3: Add `applyHealthConnectSync` to `LogRepository`**

Add this method to the `LogRepository` class (after the `observeWeeklyReviews` function):

```kotlin
suspend fun applyHealthConnectSync(date: LocalDate, result: HealthConnectReadResult) {
    val existing = dailyLogDao.getByDate(date.toString())
    val updated = (existing ?: DailyLogEntity(date = date.toString())).copy(
        steps = existing?.steps ?: result.steps,
        bodyWeightKg = existing?.bodyWeightKg ?: result.weightKg,
        sleepHours = existing?.sleepHours ?: result.sleepHours,
    )
    if (updated != existing) dailyLogDao.upsert(updated)
}
```

Also add the import at the top of `LogRepository.kt`:
```kotlin
import com.zack.recomptracker.data.health.HealthConnectReadResult
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.LogRepositorySyncTest"`
Expected: 4 tests pass

- [ ] **Step 5: Run the full test suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/repository/LogRepository.kt \
        app/src/test/java/com/zack/recomptracker/data/LogRepositorySyncTest.kt
git commit -m "feat: add LogRepository.applyHealthConnectSync with manual-entry-wins logic"
```

---

## Task 6: AppContainer Wiring

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add `HealthConnectRepository` to `AppContainer`**

Add the import at the top:
```kotlin
import com.zack.recomptracker.data.health.HealthConnectRepository
```

Add the property after `val backupRepository`:
```kotlin
val healthConnectRepository = HealthConnectRepository(context.applicationContext)
```

- [ ] **Step 2: Update `AppViewModelFactory` — `TodayViewModel` entry**

In the `when` block, update the `TodayViewModel::class.java` branch:
```kotlin
TodayViewModel::class.java -> TodayViewModel(
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    dateProvider = container.dateProvider,
    hcRepository = container.healthConnectRepository,
)
```

- [ ] **Step 3: Update `AppViewModelFactory` — `SettingsViewModel` entry**

Update the `SettingsViewModel::class.java` branch:
```kotlin
SettingsViewModel::class.java -> SettingsViewModel(
    backupRepository = container.backupRepository,
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    hcRepository = container.healthConnectRepository,
)
```

(The `TodayViewModel` and `SettingsViewModel` constructors don't have this parameter yet — they'll be added in Tasks 7 and 8. The project won't compile until both are done. Complete Tasks 7 and 8 before running a build.)

- [ ] **Step 4: Commit (after Tasks 7 and 8 compile successfully)**

Hold this commit until after Task 8 completes. The commit will be done at the end of Task 8.

---

## Task 7: TodayViewModel Auto-Sync

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`

- [ ] **Step 1: Add `hcRepository` parameter to `TodayViewModel`**

Update the class constructor — add `hcRepository` as the last parameter:
```kotlin
class TodayViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val hcRepository: HealthConnectRepository,
) : ViewModel() {
```

Add the import at the top:
```kotlin
import com.zack.recomptracker.data.health.HealthConnectRepository
```

- [ ] **Step 2: Add the auto-sync coroutine to the `init` block**

After the existing `viewModelScope.launch { combine(…).collect {…} }` block (but still inside `init`), add:

```kotlin
viewModelScope.launch {
    if (planRepository.preferences.first().healthConnectEnabled
        && hcRepository.hasPermissions()
    ) {
        val result = hcRepository.readToday(today)
        logRepository.applyHealthConnectSync(today, result)
    }
}
```

Add the missing import:
```kotlin
import kotlinx.coroutines.flow.first
```

- [ ] **Step 3: Verify the build compiles (requires Task 6 Step 2 to already be written)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (or only fails on SettingsViewModel which is next)

---

## Task 8: SettingsViewModel HC State & Actions

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Expand `SettingsUiState` with HC fields**

Replace the existing `data class SettingsUiState` with:

```kotlin
data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val healthConnectEnabled: Boolean = false,
    val healthConnectHasPermissions: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val pendingHcPermissionRequest: Boolean = false,
)
```

- [ ] **Step 2: Add `hcRepository` to `SettingsViewModel` constructor and update the class**

Replace the full `SettingsViewModel` class with:

```kotlin
class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val hcRepository: HealthConnectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val hcPermissionsContract = hcRepository.permissionsContract()

    init {
        val availability = hcRepository.availability()
        viewModelScope.launch {
            val hasPerms = if (availability == HealthConnectAvailability.Available) {
                hcRepository.hasPermissions()
            } else false
            val prefs = planRepository.preferences.first()
            _uiState.update {
                it.copy(
                    healthConnectAvailability = availability,
                    healthConnectEnabled = prefs.healthConnectEnabled,
                    healthConnectHasPermissions = hasPerms,
                )
            }
        }
    }

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusy("Backup exported.") {
                val json = backupRepository.createBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } ?: error("Could not open export destination.")
                }
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusy("Backup imported.") {
                val rawJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: error("Could not open backup file.")
                }
                backupRepository.restoreFromJson(rawJson)
            }
        }
    }

    fun resetLogsOnly() {
        viewModelScope.launch {
            runBusy("Logs reset. Saved foods and meals kept.") {
                logRepository.resetAllLogs()
            }
        }
    }

    fun resetEverything() {
        viewModelScope.launch {
            runBusy("All local data reset.") {
                backupRepository.resetEverything(PlanPreferences())
            }
        }
    }

    fun onHealthConnectToggled(enabled: Boolean) {
        if (enabled) {
            when (hcRepository.availability()) {
                HealthConnectAvailability.Available ->
                    _uiState.update { it.copy(pendingHcPermissionRequest = true) }
                HealthConnectAvailability.NotInstalled ->
                    _uiState.update { it.copy(message = "Install Health Connect from the Play Store first.") }
                HealthConnectAvailability.NotSupported ->
                    _uiState.update { it.copy(message = "Health Connect is not supported on this device.") }
            }
        } else {
            viewModelScope.launch {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = false))
                _uiState.update { it.copy(healthConnectEnabled = false) }
            }
        }
    }

    fun onHcPermissionRequestConsumed() {
        _uiState.update { it.copy(pendingHcPermissionRequest = false) }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            onHcPermissionRequestConsumed()
            if (granted.containsAll(hcRepository.requiredPermissions)) {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = true))
                _uiState.update {
                    it.copy(
                        healthConnectEnabled = true,
                        healthConnectHasPermissions = true,
                    )
                }
            } else {
                _uiState.update { it.copy(message = "All permissions are required to sync health data.") }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(healthConnectSyncing = true) }
            val result = hcRepository.readToday(LocalDate.now())
            logRepository.applyHealthConnectSync(LocalDate.now(), result)
            _uiState.update { it.copy(healthConnectSyncing = false, message = "Synced.") }
        }
    }

    private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(busy = true, message = null) }
        runCatching { block() }
            .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage) } }
            .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.") } }
    }
}
```

Add the new imports at the top of the file:
```kotlin
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.data.health.HealthConnectRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
```

- [ ] **Step 3: Build the full project — all three changed files should now compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 5: Commit Tasks 6, 7, and 8 together (they form one compilable unit)**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt \
        app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt \
        app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt
git commit -m "feat: wire HealthConnectRepository into AppContainer, TodayViewModel, SettingsViewModel"
```

---

## Task 9: SettingsScreen HC UI

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Replace `SettingsScreen` with the version that includes the HC section**

Replace the entire file content with:

```kotlin
package com.zack.recomptracker.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ai.GemmaInsightService
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.ui.component.MessageText
import com.zack.recomptracker.ui.component.SectionCard
import com.zack.recomptracker.ui.component.ToggleRow
import java.time.LocalDate

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportToUri(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFromUri(context, uri)
    }
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.hcPermissionsContract,
    ) { grantedPerms ->
        viewModel.onPermissionsResult(grantedPerms)
    }

    LaunchedEffect(state.pendingHcPermissionRequest) {
        if (state.pendingHcPermissionRequest) {
            hcPermissionLauncher.launch(Unit)
            viewModel.onHcPermissionRequestConsumed()
        }
    }

    val gemma = GemmaInsightService().availability()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Local-only controls")
                MessageText(state.message)
            }
        }
        item {
            SectionCard("Backup") {
                Button(
                    onClick = { exportLauncher.launch("recomp-tracker-${LocalDate.now()}.json") },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export JSON backup")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import JSON backup")
                }
            }
        }
        item {
            SectionCard("Samsung Health") {
                when (state.healthConnectAvailability) {
                    HealthConnectAvailability.NotInstalled -> {
                        Text("Health Connect is not installed on this device.")
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Install Health Connect")
                        }
                    }
                    HealthConnectAvailability.NotSupported -> {
                        Text("Health Connect is not supported on this device.")
                    }
                    HealthConnectAvailability.Available -> {
                        ToggleRow(
                            label = "Sync steps, weight & sleep automatically",
                            checked = state.healthConnectEnabled,
                            onCheckedChange = viewModel::onHealthConnectToggled,
                        )
                        val statusText = when {
                            state.healthConnectEnabled && state.healthConnectHasPermissions -> "Connected"
                            state.healthConnectEnabled && !state.healthConnectHasPermissions ->
                                "Permissions required — tap the toggle to reconnect"
                            else -> "Not connected"
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.healthConnectEnabled && state.healthConnectHasPermissions) {
                            if (state.healthConnectSyncing) {
                                CircularProgressIndicator()
                            } else {
                                OutlinedButton(
                                    onClick = viewModel::syncNow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Sync now")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard("Reset") {
                OutlinedButton(
                    onClick = viewModel::resetLogsOnly,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset logs only")
                }
                OutlinedButton(
                    onClick = viewModel::resetEverything,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset all local data")
                }
            }
        }
        item {
            SectionCard("Gemma status") {
                Text(if (gemma.available) "Available" else "Not enabled")
                Text(gemma.reason)
            }
        }
    }
}
```

- [ ] **Step 2: Build the project**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 4: Manual verification checklist**

Install on a device or emulator that has Health Connect. Verify:
- [ ] Settings screen shows the "Samsung Health" card
- [ ] On a device without HC installed → card shows the install button
- [ ] On a device with HC → toggle appears; tapping it launches the HC permission dialog
- [ ] Granting all permissions → toggle turns on, status shows "Connected"
- [ ] Denying permissions → toggle stays off, message shows "All permissions are required…"
- [ ] "Sync now" button appears when connected; tapping it syncs and shows "Synced."
- [ ] Navigate away and back to Today — metrics auto-fill from HC if the fields were empty
- [ ] Manually entering a step count and re-syncing does not overwrite it
- [ ] Toggling HC off in Settings → "Not connected" status

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
git commit -m "feat: add Samsung Health section to SettingsScreen (connect, sync now, status)"
```

---

## Self-Review

**Spec coverage check:**

| Spec section | Plan task |
|---|---|
| §1 Behaviour — read-only, 3 fields, auto+manual trigger, conflict rule, opt-in | Tasks 3, 5, 7, 8 |
| §2 New files | Tasks 2, 3 |
| §2 Modified files (all 10 listed) | Tasks 1, 4, 5, 6, 7, 8, 9 |
| §3 Data models | Task 2 |
| §4 HealthConnectRepository (incl. lazy client note) | Task 3 |
| §5 PlanPreferences addition | Task 4 |
| §6 SettingsViewModel additions | Task 8 |
| §6 SettingsScreen HC section | Task 9 |
| §7 TodayViewModel change | Task 7 |
| §8 applyHealthConnectSync | Task 5 |
| §9 Manifest | Task 1 |
| §10 Dependency | Task 1 |
| §11 Error handling | Task 3 (runCatching), Task 8 (availability branch), Task 9 (status text) |
| §12 What does not change | No tasks modify AdjustmentEngine, DAOs, etc. ✓ |

No gaps found.

**Placeholder scan:** No TBD, TODO, or "similar to" references. All steps include complete code.

**Type consistency check:**
- `HealthConnectAvailability` — defined in Task 2, used in Tasks 3, 8, 9 ✓
- `HealthConnectReadResult` — defined in Task 2, used in Tasks 3, 5, 7, 8 ✓
- `hcRepository.requiredPermissions` — `Set<String>`, used in Tasks 3, 8 ✓
- `hcRepository.permissionsContract()` returns `ActivityResultContract<Set<String>, Set<String>>` — used in Task 9 as `viewModel.hcPermissionsContract` ✓
- `applyHealthConnectSync(date: LocalDate, result: HealthConnectReadResult)` — defined in Task 5, called in Tasks 7 and 8 ✓
- `pendingHcPermissionRequest: Boolean` in `SettingsUiState` — set in Task 8, observed in Task 9 ✓
- `onHcPermissionRequestConsumed()` — defined in Task 8, called in Task 9 ✓
