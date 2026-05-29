# Health Connect Integration — Design Spec
**Date:** 2026-05-29
**Scope:** One-way read from Samsung Health via Health Connect. Auto-fills steps, body weight, and sleep on the Today screen. Opt-in from Settings. Manual entry always wins over synced values.

---

## 1. Behaviour Summary

| Behaviour | Detail |
|---|---|
| Direction | Read-only. App reads from Health Connect; never writes back. |
| Fields synced | Steps, body weight (kg), sleep hours |
| Trigger — auto | `TodayViewModel` init: if HC is connected, reads today's data and fills any **empty** fields |
| Trigger — manual | "Sync now" button in Settings: same read, same empty-field-only rule |
| Conflict rule | Manual entry always wins. If the user has already typed a value for a field today, the synced value is ignored for that field. |
| Opt-in | Disabled by default. User connects from Settings. App never requests permissions until the user taps "Connect Samsung Health". |

---

## 2. Architecture

Follows the existing `LogRepository` / `PlanRepository` pattern. All Health Connect API calls are isolated in `HealthConnectRepository`. ViewModels call a single suspend function and apply the result to empty fields. No domain-layer use cases needed — this is data access, not business logic.

### New files
```
data/health/HealthConnectRepository.kt
data/health/HealthConnectModels.kt
```

### Modified files
```
data/preferences/PlanPreferences.kt       — add healthConnectEnabled: Boolean = false
data/repository/LogRepository.kt          — add applyHealthConnectSync()
core/AppContainer.kt                      — wire HealthConnectRepository; pass to TodayViewModel + SettingsViewModel
ui/settings/SettingsViewModel.kt          — add hcRepository param; connect/disconnect/sync-now actions
ui/settings/SettingsScreen.kt             — HC section UI + permission launcher
ui/today/TodayViewModel.kt                — add hcRepository param; auto-sync on init
gradle/libs.versions.toml                 — add health-connect dependency
app/build.gradle.kts                      — add dependency
AndroidManifest.xml                       — add HC permissions + queries element
```

---

## 3. Data Models (`HealthConnectModels.kt`)

```kotlin
sealed class HealthConnectAvailability {
    object Available : HealthConnectAvailability()
    object NotInstalled : HealthConnectAvailability()   // needs HC app from Play Store
    object NotSupported : HealthConnectAvailability()   // device unsupported (rare)
}

data class HealthConnectReadResult(
    val steps: Int? = null,         // sum of StepsRecord for today; null = no data
    val weightKg: Double? = null,   // most recent WeightRecord (may be from past days)
    val sleepHours: Double? = null  // duration of most recent SleepSessionRecord ending today or yesterday
)
```

---

## 4. `HealthConnectRepository`

```kotlin
class HealthConnectRepository(private val context: Context) {

    fun availability(): HealthConnectAvailability

    /** Returns the ActivityResultContract for requesting HC permissions. */
    fun permissionsContract(): ActivityResultContract<Set<String>, Set<String>>

    /** The three permissions this app needs. */
    val requiredPermissions: Set<String>   // READ_STEPS, READ_WEIGHT, READ_SLEEP_SESSION

    /** True if all three permissions are currently granted. */
    suspend fun hasPermissions(): Boolean

    /**
     * Read today's steps, most recent weight, and most recent sleep session.
     * Returns nulls for any record type with no data.
     * Never throws — returns empty result on any HC error.
     */
    suspend fun readToday(date: LocalDate): HealthConnectReadResult
}
```

**Implementation notes — client initialization:**
`HealthConnectClient.getOrCreate(context)` must be called lazily (once, via `by lazy { }` in the repository) rather than per-call. The client is stateful and the SDK documentation warns against creating multiple instances.

**`readToday` implementation notes:**
- Steps: `HealthConnectClient.readRecords(StepsRecord, TimeRangeFilter.between(startOfDay, now))` — sums all records
- Weight: `HealthConnectClient.readRecords(WeightRecord, TimeRangeFilter.between(30 days ago, now))` — takes the most recent entry
- Sleep: `HealthConnectClient.readRecords(SleepSessionRecord, TimeRangeFilter.between(yesterday noon, now))` — takes the most recent session; duration = `endTime - startTime` in hours
- Catches `HealthConnectException` and any other exception, returns `HealthConnectReadResult()` (all nulls) so callers never crash

---

## 5. `PlanPreferences` Addition

Add one field:
```kotlin
val healthConnectEnabled: Boolean = false
```

Default `false` — opt-in required.

---

## 6. Settings Integration

### `SettingsViewModel` additions

```kotlin
// State additions to SettingsUiState
val healthConnectAvailability: HealthConnectAvailability
val healthConnectEnabled: Boolean
val healthConnectHasPermissions: Boolean
val healthConnectSyncing: Boolean

// Actions
fun onHealthConnectToggled(enabled: Boolean)   // called when user taps toggle
fun syncNow()                                   // manual sync → applies to today's empty fields
```

**Toggle ON flow:**
1. `SettingsViewModel` checks `hcRepository.availability()` — if not available, shows appropriate message
2. If available, sets a `pendingPermissionRequest = true` flag in state
3. `SettingsScreen` observes this flag and launches the `ActivityResultLauncher`
4. On result: if all permissions granted → `planRepository.save(prefs.copy(healthConnectEnabled = true))`; if denied → show "Permissions required" message, toggle stays off

**Toggle OFF flow:**
- Saves `healthConnectEnabled = false` to DataStore
- Does not revoke permissions (Android does not allow apps to revoke granted permissions)

**Sync Now:**
- Calls `hcRepository.readToday(today)` then calls `logRepository.applyHealthConnectSync(today, result)` (see §8)

### `SettingsScreen` additions

New "Samsung Health" section card:
```
SAMSUNG HEALTH

[Toggle]  Sync steps, weight & sleep automatically
          [status line: "Connected" / "Not connected" / "Health Connect not installed"]

[Button]  Sync now         ← only shown when enabled + hasPermissions
```

If `availability == NotInstalled`:
- Toggle is disabled
- Show: "Install Health Connect from the Play Store to enable this feature" with a link button

---

## 7. Today Screen Integration

### `TodayViewModel` change

In the `init` block, after the existing `combine` starts, add a second coroutine:

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

This runs once per ViewModel creation (i.e., once per screen visit). The `applyHealthConnectSync` only writes fields that are currently null/absent in `DailyLogEntity` for today — so if the user has already saved metrics, nothing is overwritten.

---

## 8. `LogRepository.applyHealthConnectSync`

New method added to `LogRepository`:

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

**"Manual entry wins" is enforced here:** `existing?.steps ?: result.steps` — if the DB already has a value, it is kept; the synced value is only used when the field is null.

---

## 9. Manifest Changes

```xml
<!-- Inside <manifest>, before <application> -->
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_SLEEP_SESSION" />

<!-- Required for Health Connect SDK to resolve the HC package -->
<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>
```

---

## 10. Dependency

```toml
# libs.versions.toml
[versions]
healthConnect = "1.1.0-rc01"   # verify latest stable at implementation time

[libraries]
androidx-health-connect = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.health.connect)
```

---

## 11. Error Handling

| Scenario | Behaviour |
|---|---|
| HC not installed | Toggle disabled, install prompt shown |
| Permissions denied | Toggle off, "Permissions required to sync" message |
| HC throws during read | Silent — `readToday` catches all exceptions, returns empty result |
| No data for a field | Field stays at whatever the user has (or remains empty) |
| Sync on a day with no HC records | Nothing happens, no error shown |

---

## 12. What Does Not Change

- AdjustmentEngine, TrendCalculator, AdherenceCalculator — no changes
- All existing Room entities and DAOs — no changes. `DailyLogDao.getByDate(date: String): DailyLogEntity?` already exists as a suspend function; no modification needed.
- Export/backup — `healthConnectEnabled` is part of `PlanPreferences` which is already backed up
- The app works 100% without Health Connect — it is purely additive
