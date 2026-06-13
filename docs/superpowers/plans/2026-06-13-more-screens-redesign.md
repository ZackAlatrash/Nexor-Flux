# More Section Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "More" junk drawer + 906-line Settings monolith with a clean hub of 8 focused, reusable-component-based screens, and de-duplicate the profile data model.

**Architecture:** Phased. Phase 1 changes the profile data model + domain (TDD). Phases 2–4 extract/build/restyle the 8 screens behind new nav routes while the old Settings screen still works. Phase 5 rebuilds the More hub, rewires navigation, and deletes the dead screens. Every phase ends with the app compiling and tests green.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, ViewModel + StateFlow, DataStore, Navigation Compose, JUnit. Reuse existing components in `ui/component/` — do not create new composables unless nothing fits.

**Design source of truth:** `docs/superpowers/specs/2026-06-13-more-screens-redesign-design.md`. Reuse mapping and the two genuinely-new pieces (colored verdict pill, profile avatar) are defined there.

**Build/verify commands (used throughout):**
- Type-check: `./gradlew :app:compileDebugKotlin`
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Full debug build: `./gradlew :app:assembleDebug`

---

## File Structure

**New files:**
- `ui/profile/ProfileScreen.kt`, `ui/profile/ProfileViewModel.kt`
- `ui/appearance/AppearanceScreen.kt`, `ui/appearance/AppearanceViewModel.kt`
- `ui/aicoach/AiCoachScreen.kt`, `ui/aicoach/AiCoachViewModel.kt`
- `ui/integrations/IntegrationsScreen.kt`
- `ui/databackup/DataBackupScreen.kt`
- `app/src/test/java/.../domain/plan/PlanGeneratorBirthDateTest.kt`
- `app/src/test/java/.../data/preferences/UserProfileMigrationTest.kt`

**Modified files:**
- `data/preferences/UserProfilePreferences.kt` — field changes
- `data/preferences/UserProfilePreferencesStore.kt` — serialization + migration
- `domain/plan/PlanGenerator.kt` — birthDate → age
- `ai/CoachToolsAdapter.kt`, `ai/GemmaCoachCoordinator.kt` — drop removed fields
- `ui/dashboard/DashboardScreen.kt` — restyle `DashboardScreen` → Calorie Decision
- `ui/progress/ProgressScreen.kt` — colored verdict pills
- `ui/plan/PlanScreen.kt` — collapsible advanced rules
- `ui/more/MoreScreen.kt` — rebuilt as hub (reads DashboardViewModel)
- `ui/navigation/AppNavGraph.kt` — routes
- `ui/RecompApp.kt` — remove Progress tab-index refs if any

**Deleted files (Phase 5):**
- `ui/settings/SettingsScreen.kt`
- `ui/more/MoreViewModel.kt` (replaced by `AiCoachViewModel`)
- `SettingsViewModel.kt` is **kept** — reused by Integrations + Data & Backup screens.

**ViewModel reuse map:**
- Calorie Decision + More hub featured card → existing `DashboardViewModel`
- Trends → existing `ProgressViewModel`
- Plan → existing `PlanViewModel`
- AI & Coach → `AiCoachViewModel` (repurposed from `MoreViewModel`)
- Profile → new `ProfileViewModel`
- Appearance → new `AppearanceViewModel`
- Integrations + Data & Backup → existing `SettingsViewModel`

---

# PHASE 1 — Profile data model & domain

### Task 1: Change `UserProfilePreferences` fields

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt`

- [ ] **Step 1: Edit the data class**

Replace the `UserProfilePreferences` data class (lines 6–15) and delete the `TrainingExperience` enum (lines 20–25) and its `displayName()` (lines 68–72). Final data class:

```kotlin
@Serializable
data class UserProfilePreferences(
    val name: String? = null,
    val profilePhotoUri: String? = null,
    val heightCm: Int? = null,
    val birthDate: String? = null,          // ISO yyyy-MM-dd
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,
    val goal: FitnessGoal? = null,
)
```

Remove the lines defining `TrainingExperience` (enum + `displayName`). Keep `BiologicalSex`, `ActivityLevel`, `FitnessGoal` and their `displayName()`/`shortDesc()` helpers untouched.

- [ ] **Step 2: Add an age helper**

Append to the same file:

```kotlin
import java.time.LocalDate
import java.time.Period

/** Age in whole years derived from birthDate, relative to [today]. Null if unset/invalid. */
fun UserProfilePreferences.ageYears(today: LocalDate = LocalDate.now()): Int? {
    val dob = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years
}
```

- [ ] **Step 3: Type-check (expect failures elsewhere — that's fine, later tasks fix them)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — unresolved references to `ageYears` (the property), `trainingExperience`, `plannedTrainingDays` in `PlanGenerator.kt`, `SettingsScreen.kt`, coach files. These are fixed in Tasks 3–5.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt
git commit -m "refactor(profile): drop dead fields, add name/photo/birthDate to profile model"
```

---

### Task 2: Migration + serialization in the store

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt`
- Test: `app/src/test/java/com/zack/recomptracker/data/preferences/UserProfileMigrationTest.kt`

First read `UserProfilePreferencesStore.kt` to see how it serializes (kotlinx JSON string in DataStore vs per-key). The migration must convert any legacy `ageYears` integer into a `birthDate` of `Jan 1 of (currentYear - ageYears)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.data.preferences

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UserProfileMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyAgeYears_migratesToApproxBirthDate() {
        val legacy = """{"heightCm":180,"ageYears":28,"goal":"RECOMP"}"""
        val migrated = migrateLegacyProfileJson(legacy, today = LocalDate.of(2026, 6, 13))
        assertEquals("1998-01-01", migrated.birthDate)
        assertEquals(180, migrated.heightCm)
        assertEquals(FitnessGoal.RECOMP, migrated.goal)
    }

    @Test
    fun legacyTrainingFields_areDroppedSilently() {
        val legacy = """{"trainingExperience":"ADVANCED","plannedTrainingDays":5,"ageYears":40}"""
        val migrated = migrateLegacyProfileJson(legacy, today = LocalDate.of(2026, 6, 13))
        assertEquals("1986-01-01", migrated.birthDate)
    }

    @Test
    fun modernJson_withBirthDate_isUnchanged() {
        val modern = """{"birthDate":"1990-05-02","heightCm":175}"""
        val migrated = migrateLegacyProfileJson(modern, today = LocalDate.of(2026, 6, 13))
        assertEquals("1990-05-02", migrated.birthDate)
    }

    @Test
    fun noAge_noBirthDate_staysNull() {
        val migrated = migrateLegacyProfileJson("{}", today = LocalDate.of(2026, 6, 13))
        assertNull(migrated.birthDate)
    }
}
```

- [ ] **Step 2: Run it (fails — function missing)**

Run: `./gradlew :app:testDebugUnitTest --tests "*UserProfileMigrationTest*"`
Expected: FAIL — `migrateLegacyProfileJson` unresolved.

- [ ] **Step 3: Implement the migration function**

Add to `UserProfilePreferencesStore.kt` (top-level, file scope), and call it wherever the store currently decodes the persisted JSON into `UserProfilePreferences`:

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

private val migrationJson = Json { ignoreUnknownKeys = true }

/** Decodes persisted profile JSON, converting a legacy ageYears int into an approx birthDate. */
fun migrateLegacyProfileJson(raw: String, today: LocalDate = LocalDate.now()): UserProfilePreferences {
    val base = migrationJson.decodeFromString<UserProfilePreferences>(raw)
    if (base.birthDate != null) return base
    val legacyAge = runCatching {
        migrationJson.parseToJsonElement(raw).jsonObject["ageYears"]?.jsonPrimitive?.intOrNull
    }.getOrNull() ?: return base
    return base.copy(birthDate = LocalDate.of(today.year - legacyAge, 1, 1).toString())
}
```

In the store's read/decode path, replace the direct `Json.decodeFromString<UserProfilePreferences>(raw)` with `migrateLegacyProfileJson(raw)`. (`ignoreUnknownKeys` ensures the now-removed `trainingExperience`/`plannedTrainingDays`/`ageYears` keys in old blobs don't crash decoding.) Ensure the store's `Json` instance has `ignoreUnknownKeys = true`.

- [ ] **Step 4: Run tests (pass)**

Run: `./gradlew :app:testDebugUnitTest --tests "*UserProfileMigrationTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt app/src/test/java/com/zack/recomptracker/data/preferences/UserProfileMigrationTest.kt
git commit -m "feat(profile): migrate legacy ageYears to birthDate; tolerate dropped fields"
```

---

### Task 3: `PlanGenerator` uses birthDate-derived age

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/plan/PlanGenerator.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/plan/PlanGeneratorBirthDateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.plan

import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorBirthDateTest {
    private val gen = PlanGenerator()
    private val today = LocalDate.of(2026, 6, 13)

    private fun fullProfile() = UserProfilePreferences(
        heightCm = 180,
        birthDate = "1998-01-01",
        biologicalSex = BiologicalSex.MALE,
        activityLevel = ActivityLevel.MODERATELY_ACTIVE,
        goal = FitnessGoal.RECOMP,
    )

    @Test
    fun missingBirthDate_reportsAgeMissing() {
        val outcome = gen.generate(fullProfile().copy(birthDate = null), weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.MissingProfileFields)
        assertTrue((outcome as PlanGenerationOutcome.MissingProfileFields).fields.contains("Age"))
    }

    @Test
    fun completeProfile_withBirthDate_generates() {
        val outcome = gen.generate(fullProfile(), weightKg = 80.0, today = today)
        assertTrue(outcome is PlanGenerationOutcome.Ready)
    }
}
```

- [ ] **Step 2: Run it (fails — signature mismatch)**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanGeneratorBirthDateTest*"`
Expected: FAIL — `generate` has no `today` param / references `ageYears` property.

- [ ] **Step 3: Update `PlanGenerator.generate`**

```kotlin
import com.zack.recomptracker.data.preferences.ageYears
import java.time.LocalDate

fun generate(
    profile: UserProfilePreferences,
    weightKg: Double?,
    today: LocalDate = LocalDate.now(),
): PlanGenerationOutcome {
    val age = profile.ageYears(today)
    val missing = buildList {
        if (profile.heightCm == null) add("Height")
        if (age == null) add("Age")
        if (profile.biologicalSex == null) add("Sex")
        if (profile.activityLevel == null) add("Activity level")
        if (profile.goal == null) add("Goal")
    }
    if (missing.isNotEmpty()) return PlanGenerationOutcome.MissingProfileFields(missing)
    if (weightKg == null || weightKg <= 0.0) return PlanGenerationOutcome.NeedsWeight

    val plan = calculator.generate(
        PlanCalculatorInput(
            heightCm = profile.heightCm!!,
            ageYears = age!!,
            sex = profile.biologicalSex!!,
            activityLevel = profile.activityLevel!!,
            goal = profile.goal!!,
            weightKg = weightKg,
        ),
    )
    return PlanGenerationOutcome.Ready(plan)
}
```

- [ ] **Step 4: Run tests (pass)**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanGeneratorBirthDateTest*"`
Expected: PASS.

- [ ] **Step 5: Fix the PlanViewModel call site**

Read `ui/plan/PlanViewModel.kt`, find the `generate(profile, weight)` call, and confirm it still compiles (the `today` param defaults). If it referenced `profile.ageYears` directly anywhere, replace with `profile.ageYears()`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/plan/PlanGenerator.kt app/src/test/java/com/zack/recomptracker/domain/plan/PlanGeneratorBirthDateTest.kt
git commit -m "feat(plan): derive age from birthDate in PlanGenerator"
```

---

### Task 4: Drop removed fields from coach prompt builders

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` (~line 58)
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt` (~line 403)

- [ ] **Step 1: Inspect both builders**

Run: `grep -n "plannedTrainingDays\|trainingExperience\|ageYears\|weeklyGymSessions\|Age:" app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt`

- [ ] **Step 2: Remove dead-field lines, fix age**

In both files: delete any line adding `trainingExperience` or `plannedTrainingDays` to the prompt. Keep the `weeklyGymSessions` line. If a line emits age from `profile.ageYears`, change it to use the derived helper:

```kotlin
import com.zack.recomptracker.data.preferences.ageYears
// ...
profile.ageYears()?.let { add("Age: $it") }
```

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: remaining failures only in `SettingsScreen.kt` (fixed next).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt
git commit -m "refactor(ai): drop removed profile fields from coach prompt"
```

---

### Task 5: Interim compile-fix for `SettingsScreen.UserProfileSection`

The old Settings profile UI (`SettingsScreen.kt:403–529`) references removed fields. This is interim — the section is replaced by `ProfileScreen` in Task 6 and the whole file is deleted in Phase 5 — so do the **minimum** to compile.

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt:403–529`

- [ ] **Step 1: Remove the dead-field UI blocks**

Delete the "Training experience" block (`463–481`) and the "Planned training days" `ScoreStepper` (`495–501`). Delete the `import ...TrainingExperience` line.

- [ ] **Step 2: Replace the Age input**

In the Height & Age row (`505–527`), remove the Age `GlassInputField` and leave Height as a single full-width field. (No DOB picker here — that's built properly in `ProfileScreen`.) This is interim UI.

- [ ] **Step 3: Type-check + tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
git commit -m "chore(settings): interim profile-section compile-fix for new model"
```

---

# PHASE 2 — Extract setup screens (Profile, Appearance, Integrations, Data & Backup)

All four are reachable via new routes added in Task 10; the old Settings screen stays live until Phase 5.

### Task 6: Profile screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/profile/ProfileViewModel.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/profile/ProfileScreen.kt`

Reuse: `FrostedCard`, `SectionLabel`, `ProfileOptionRow`, `SexChip`, `ScoreStepper`, `GlassInputField`. New pieces (justified — nothing exists): a circular **avatar with photo picker**, a **DOB date picker row**, and **bottom-sheet pickers** for Goal/Activity/Sex (use Material3 `ModalBottomSheet` populated with the existing `ProfileOptionRow`).

- [ ] **Step 1: Create `ProfileViewModel`**

Model it on the relevant slice of `SettingsViewModel` (profile read/write) plus a read-only current weight from the body repository. Read `core/AppContainer.kt` for the exact store/repo names (e.g. `userProfilePreferencesStore`, the body/log repository exposing latest weight). Template:

```kotlin
package com.zack.recomptracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val store: UserProfilePreferencesStore,
    latestWeightKgFlow: kotlinx.coroutines.flow.Flow<Double?>,
) : ViewModel() {
    val profile: StateFlow<UserProfilePreferences> =
        store.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfilePreferences())

    val currentWeightKg: StateFlow<Double?> =
        latestWeightKgFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(p: UserProfilePreferences) { viewModelScope.launch { store.save(p) } }
}
```

Adjust `store.flow`/`store.save` to the real API found in `UserProfilePreferencesStore.kt`. Register it in the `viewModelFactory` in `core/AppContainer.kt` exactly like the existing `SettingsViewModel` registration (find that block and mirror it, wiring the body/log repo's latest-weight flow).

- [ ] **Step 2: Create `ProfileScreen`**

Layout (matches approved mockup `profile-v4`):
1. Avatar + photo picker (`rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())`; on result, persist the URI string into `profile.profilePhotoUri` via `vm.update(...)`; render with Coil `AsyncImage` if set, else a gradient placeholder). Coil is available (`coil-compose`).
2. Editable name `GlassInputField` writing `profile.name`.
3. Read-only current-weight `FrostedCard` showing `currentWeightKg` with a "View ›" affordance calling `onOpenBody()`.
4. "Plan inputs" `FrostedCard`: Goal row + Activity row (each opens a `ModalBottomSheet` of `ProfileOptionRow`s built from `FitnessGoal.entries` / `ActivityLevel.entries` with their `shortDesc()`), and a `ScoreStepper` for Gym sessions/week (0..7).
5. "About you" `FrostedCard`: Biological sex row (opens a sheet of `BiologicalSex.entries`), Date of birth row (opens Material3 `DatePickerDialog`; on confirm store ISO into `birthDate`; display `birthDate` + " · ${profile.ageYears()} yrs"), Height `GlassInputField`.

Signature: `fun ProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit, onOpenBody: () -> Unit)`. Wrap in the standard screen scaffold used by `PlanScreen` (LazyColumn, `FloatingNavHeight` bottom padding, a back affordance in the header).

- [ ] **Step 3: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/profile/
git commit -m "feat(profile): add Profile screen + ViewModel (avatar, name, DOB, picker sheets)"
```

---

### Task 7: Appearance screen + ViewModel

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceViewModel.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/appearance/AppearanceScreen.kt`

Reuse: `FontPicker` (currently a private composable in `MoreScreen.kt:813–843` — move it to `ui/component/Components.kt` and make it `internal`), `AccentThemePicker` (private in `SettingsScreen.kt:839–890` — move to `ui/component/Components.kt`, make `internal`), `TintedCard`/`FrostedCard`.

- [ ] **Step 1: Promote the two pickers to shared components**

Cut `FontPicker` from `MoreScreen.kt` and `AccentThemePicker` from `SettingsScreen.kt` into `ui/component/Components.kt`, changing `private` to `internal`. Update the old call sites to the shared versions (they'll be deleted later anyway, but must compile now).

- [ ] **Step 2: Create `AppearanceViewModel`**

Reads/writes both font and accent via `UiPreferences` (find exact API in the file that backs `uiPreferences.accentTheme` / the font key). Template:

```kotlin
package com.zack.recomptracker.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ui.theme.AccentTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppearanceViewModel(private val uiPrefs: /* UiPreferences type */ Any) : ViewModel() {
    val font: StateFlow<String> = TODO("uiPrefs.selectedFont as StateFlow")
    val accent: StateFlow<AccentTheme> = TODO("uiPrefs.accentTheme as StateFlow")
    fun setFont(value: String) { viewModelScope.launch { /* uiPrefs.setFont(value) */ } }
    fun setAccent(theme: AccentTheme) { viewModelScope.launch { /* uiPrefs.setAccentTheme(theme) */ } }
}
```

Replace the `TODO`/placeholders with the real `UiPreferences` API discovered by reading the file (the existing `MoreViewModel.setFont` and `SettingsViewModel.setAccentTheme` show the exact calls — copy them). Register in `viewModelFactory`.

- [ ] **Step 3: Create `AppearanceScreen`** (mockup `appearance` option A)

`fun AppearanceScreen(viewModel: AppearanceViewModel, onBack: () -> Unit)`:
1. **Preview** `TintedCard`: sample title + a `LiquidButton` + a `VioletBadge`, rendered with the selected font + accent (accent comes live from the app theme since selecting it updates `UiPreferences` globally).
2. **Font** section: the shared `FontPicker` bound to `font`/`setFont`.
3. **Accent color** section: the shared `AccentThemePicker` bound to `accent`/`setAccent`.

- [ ] **Step 4: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/appearance/ app/src/main/java/com/zack/recomptracker/ui/component/Components.kt app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
git commit -m "feat(appearance): add Appearance screen merging font + accent"
```

---

### Task 8: Integrations screen (reuses `SettingsViewModel`)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/integrations/IntegrationsScreen.kt`

Reuse: `SettingsViewModel` (already has every HC/Samsung/NEVO action — see map below), plus the `HealthConnectSection` (`SettingsScreen.kt:550–662`), `HcStatusRow` (`892–906`), `HistoricalFoodReviewDialog` (`666–719`), `SettingsCard`, `SectionLabel`, `MessageText`. Promote `HealthConnectSection`, `HcStatusRow`, `HistoricalFoodReviewDialog`, `SettingsCard`, `CardDivider` from `private` in `SettingsScreen.kt` to `internal` (in place; they're deleted with the file in Phase 5, so copy them into a new `ui/integrations/IntegrationsComponents.kt` instead to avoid losing them). **Decision: copy** these helpers into `IntegrationsComponents.kt` so Phase 5's deletion of `SettingsScreen.kt` is safe.

State/actions used (from `SettingsViewModel`): `healthConnectAvailability/Enabled/HasPermissions/Syncing`, `pendingHcPermissionRequest`, `pendingNutritionPermissionRequest`, `healthConnectMessage/Kind`, `nevoSourceVersion`, `historicalFoodCandidates`, `selectedHistoricalFoodIdentities`, `historicalFoodBusy`; actions `onHealthConnectToggled`, `syncNow`, `startHistoricalFoodImport`, `onPermissionsResult`, `onNutritionPermissionsResult`, `onHcPermissionRequestConsumed`, `onNutritionPermissionRequestConsumed`, `scanSamsungHealthFoodExport`, `importNevoFromUri`, `removeNevoCatalog`, `toggleHistoricalFoodCandidate`, `dismissHistoricalFoodReview`, `importSelectedHistoricalFoods`.

- [ ] **Step 1: Copy shared helpers**

Create `ui/integrations/IntegrationsComponents.kt` and move (cut) `HealthConnectSection`, `HcStatusRow`, `HistoricalFoodReviewDialog`, `SettingsCard`, `CardDivider` out of `SettingsScreen.kt` into it as `internal` composables. Update `SettingsScreen.kt` to import them (keeps Settings compiling).

- [ ] **Step 2: Create `IntegrationsScreen`** (mockup `integrations` option B)

`fun IntegrationsScreen(viewModel: SettingsViewModel, onBack: () -> Unit)`. Port the permission/result `LaunchedEffect`s and activity-result launchers from `SettingsScreen.kt:94–174` (only the HC/Samsung/NEVO ones). Layout:
- **Health sync**: `SectionLabel("Health sync")` + `HealthConnectSection(...)` card.
- **Food sources**: `SectionLabel("Food sources")` + a `SettingsCard` with two `SettingRow`-style rows — Samsung Health ("Import", launches the food_info picker → `scanSamsungHealthFoodExport`) and NEVO ("Manage", import/replace via `importNevoFromUri`, shows `nevoSourceVersion`, remove via `removeNevoCatalog` behind a `ConfirmDialog`).
- Render `HistoricalFoodReviewDialog` when `historicalFoodCandidates` is non-empty.

(`SettingRow` is private in `MoreScreen.kt:760–792`; copy it into `ui/component/Components.kt` as `internal` so both Integrations and Data&Backup can use it — do this here.)

- [ ] **Step 3: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/integrations/ app/src/main/java/com/zack/recomptracker/ui/component/Components.kt app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt
git commit -m "feat(integrations): add Integrations screen (HC + Samsung + NEVO)"
```

---

### Task 9: Data & Backup screen (reuses `SettingsViewModel`)

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/databackup/DataBackupScreen.kt`

Reuse: `SettingsViewModel` actions `exportToUri`, `importFromUri`, `exportPersonalFoodsToUri`, `importPersonalFoodsFromUri`, `clearFoodLibrary`, `resetLogsOnly`, `resetEverything`; components `SettingsCard`, `SettingRow` (now shared), `ConfirmDialog`, `SectionLabel`, `MessageText`.

- [ ] **Step 1: Create `DataBackupScreen`** (mockup `data-backup` option A)

`fun DataBackupScreen(viewModel: SettingsViewModel, onBack: () -> Unit)`. Port the backup/foods activity-result launchers from `SettingsScreen.kt:107–124`. Layout:
- **Full backup**: `SettingsCard` with two `SettingRow`s — Export backup (`CreateDocument("application/json")` → `exportToUri`), Import backup (`OpenDocument()` → `importFromUri`, behind an import `ConfirmDialog`).
- **Personal foods**: `SettingsCard` with Export personal foods + Import personal foods rows.
- **Danger zone**: a red-tinted `SettingsCard` (pass a red border/tint — reuse `SettingsCard` with a `dangerous = true` flag added, or wrap with a red-bordered `FrostedCard`) containing Clear food library, Reset logs only, Reset all local data — each behind its existing `ConfirmDialog`.

- [ ] **Step 2: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/databackup/
git commit -m "feat(data): add Data & Backup screen (backup, foods, danger zone)"
```

---

### Task 10: Add routes for the four new screens

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add route constants**

In `object Routes` add: `const val Profile = "profile"`, `const val Appearance = "appearance"`, `const val Integrations = "integrations"`, `const val DataBackup = "data_backup"`.

- [ ] **Step 2: Add `composable` blocks**

Add four blocks mirroring the existing `Routes.Settings` block (use `screenEnter`/`screenExit`), e.g.:

```kotlin
composable(route = Routes.Profile, enterTransition = { screenEnter }, exitTransition = { screenExit }) {
    ProfileScreen(
        viewModel = viewModel<ProfileViewModel>(factory = factory),
        onBack = { navController.popBackStack() },
        onOpenBody = { navController.navigate(TopLevelDestination.Body.route) },
    )
}
composable(route = Routes.Appearance, enterTransition = { screenEnter }, exitTransition = { screenExit }) {
    AppearanceScreen(viewModel<AppearanceViewModel>(factory = factory), onBack = { navController.popBackStack() })
}
composable(route = Routes.Integrations, enterTransition = { screenEnter }, exitTransition = { screenExit }) {
    IntegrationsScreen(viewModel<SettingsViewModel>(factory = factory), onBack = { navController.popBackStack() })
}
composable(route = Routes.DataBackup, enterTransition = { screenEnter }, exitTransition = { screenExit }) {
    DataBackupScreen(viewModel<SettingsViewModel>(factory = factory), onBack = { navController.popBackStack() })
}
```

Add the imports for the new screens/VMs.

- [ ] **Step 3: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(nav): add routes for Profile, Appearance, Integrations, Data & Backup"
```

---

# PHASE 3 — AI & Coach screen

### Task 11: Repurpose `MoreViewModel` → `AiCoachViewModel`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachViewModel.kt`
- Modify: `core/AppContainer.kt` (factory)

- [ ] **Step 1: Create `AiCoachViewModel`**

Copy `MoreViewModel.kt` into `ui/aicoach/AiCoachViewModel.kt`, renaming the class to `AiCoachViewModel` and the state to `AiCoachUiState`. **Keep** all AI fields/actions (`aiInsightsEnabled`, `aiBackend`, `cloudBaseUrl`, `cloudModelId`, `cloudHasKey`, `testConnectionResult`, `testingConnection`, `aiInsightState`, `selectedModel`, and actions `setAiInsights`, `setAiBackend`, `setModel`, `requestModelDownload`, `cancelDownload`, `deleteModel`, `setCloudBaseUrl`, `setCloudModelId`, `setCloudApiKey`, `clearCloudApiKey`, `testCloudConnection`, `clearMessage`). **Remove** `selectedFont`/`setFont`, `healthConnectConnected`, and `exportToUri`/`importFromUri` (those now live in Appearance / Integrations / Data & Backup). Keep `busy`/`message`.

- [ ] **Step 2: Register in factory**

In `core/AppContainer.kt`, add an `AiCoachViewModel` branch to `viewModelFactory` mirroring the existing `MoreViewModel` branch with the same dependencies minus the removed ones (font prefs, backup repo, HC repo). Leave the `MoreViewModel` branch for now.

- [ ] **Step 3: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat(ai): add AiCoachViewModel (AI slice of MoreViewModel)"
```

---

### Task 12: AI & Coach screen

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/aicoach/AiCoachScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

Reuse: move the AI composables out of `MoreScreen.kt` into `ui/aicoach/AiCoachComponents.kt` as `internal`: `AiModelSection` (`397–527`), `ModelVariantSelector` (`529–577`), `AiEngineSelector` (`581–629`), `ProviderPresetChips` (`640–663`), `CloudStatusLine` (`667–684`), `AiModelHeader` (`686–702`). Also reuse `TintedCard`, `ToggleRow`/`VioletToggle`, `GlassInputField`, `AiBadge`.

- [ ] **Step 1: Move the AI composables**

Cut the six composables above from `MoreScreen.kt` into `ui/aicoach/AiCoachComponents.kt` (as `internal`). `MoreScreen.kt` will be rebuilt in Phase 5 and won't need them.

- [ ] **Step 2: Create `AiCoachScreen`** (mockup `ai-coach` option B — engine radio cards)

`fun AiCoachScreen(viewModel: AiCoachViewModel, onBack: () -> Unit)`. Layout, all in tinted style:
1. **Enable AI** master `TintedCard` with 🤖 icon + `VioletToggle` bound to `aiInsightsEnabled`/`setAiInsights`. When off, hide the rest.
2. **Engine** — two radio cards (On-device / Cloud) bound to `aiBackend`/`setAiBackend`. Build the radio card as a small `internal` composable in `AiCoachComponents.kt` using `FrostedCard` + a radio indicator (this is a thin new piece; justified — `AiEngineSelector`'s segmented style was replaced by radio cards per the approved design; keep `AiEngineSelector` available but use radio cards here).
3. **Contextual config:**
   - `AiBackend.LOCAL` → `AiModelSection(aiState, selectedModel, onModelSelect=setModel, onDownload=requestModelDownload, onCancel=cancelDownload, onDelete=deleteModel)`.
   - `AiBackend.CLOUD` → cloud `TintedCard`: `ProviderPresetChips(onPick=...)`, Base URL/Model ID `GlassInputField`s, API-key password field, `CloudStatusLine(state)`, Test-connection button → `testCloudConnection`.

- [ ] **Step 3: Add the route**

In `AppNavGraph.kt` add `const val AiCoach = "ai_coach"` and a `composable` block: `AiCoachScreen(viewModel<AiCoachViewModel>(factory = factory), onBack = { navController.popBackStack() })`.

- [ ] **Step 4: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/aicoach/ app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt
git commit -m "feat(ai): add AI & Coach screen (engine radio cards + contextual config)"
```

---

# PHASE 4 — Restyle Calorie Decision, Trends, Plan

### Task 13: Calorie Decision (restyle `DashboardScreen`)

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt:660–747`

- [ ] **Step 1: Edit the `DashboardScreen` composable**

Per mockup `calorie-decision-v2` (the `HomeDashboardScreen` composable is **untouched**):
- Change header title from "Stats" to "Calorie Decision"; add an `onBack` param + back affordance: `fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit)`.
- Keep sections: Calorie verdict (`679–686`), AI Insight (`687–695`), Current targets (`696–701`), Trend summary (`737–745`).
- **Delete** the "Today" section (`702–735`) — the `CalorieZoneBar` + `MacroMiniBar` block.
- Make the verdict the visual hero (it already leads). Keep using `SectionCard`, `AiInsightSection`, `StatRow`, `VioletBadge`.

- [ ] **Step 2: Type-check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL only at the `Routes.Stats` call site in `AppNavGraph.kt` (now needs `onBack`). Fix it: `DashboardScreen(viewModel<DashboardViewModel>(factory = factory), onBack = { navController.popBackStack() })`. (Route is renamed in Phase 5.)

- [ ] **Step 3: Build + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "refactor(dashboard): restyle stats screen into Calorie Decision; drop Today card"
```

---

### Task 14: Trends — colored verdict pills

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/Components.kt` (extend `VioletBadge` → status colors)
- Test: `app/src/test/java/com/zack/recomptracker/ui/progress/VerdictPillTest.kt`

The screen already groups Body/Nutrition/Performance and shows `trendLabel` + `trendIsGood` (`ChartHeader` color-codes green/red). The change: render the trend as a **colored pill** beside the value, with four states (good/caution/off-track/neutral).

- [ ] **Step 1: Write the failing test for pill-status mapping**

```kotlin
package com.zack.recomptracker.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictPillTest {
    @Test fun goodTrend_isGoodStatus() = assertEquals(PillStatus.GOOD, pillStatus(trendIsGood = true, isNeutral = false))
    @Test fun badTrend_isOffTrack() = assertEquals(PillStatus.OFF_TRACK, pillStatus(trendIsGood = false, isNeutral = false))
    @Test fun flatTrend_isNeutral() = assertEquals(PillStatus.NEUTRAL, pillStatus(trendIsGood = true, isNeutral = true))
}
```

- [ ] **Step 2: Run it (fails)**

Run: `./gradlew :app:testDebugUnitTest --tests "*VerdictPillTest*"`
Expected: FAIL — `PillStatus`/`pillStatus` unresolved.

- [ ] **Step 3: Implement `PillStatus` + mapping**

In `ProgressScreen.kt` (or a small `ui/progress/VerdictPill.kt`):

```kotlin
enum class PillStatus { GOOD, CAUTION, OFF_TRACK, NEUTRAL }

fun pillStatus(trendIsGood: Boolean, isNeutral: Boolean): PillStatus = when {
    isNeutral -> PillStatus.NEUTRAL
    trendIsGood -> PillStatus.GOOD
    else -> PillStatus.OFF_TRACK
}
```

(CAUTION is reserved for future "slightly over target" logic; not wired from current state — leave it in the enum, documented.) Determine `isNeutral` from a near-zero trend: treat `trendLabel` of "steady"/"—" or a trend magnitude below the series' threshold as neutral (use the existing flat-trend signal in `ChartSeries`; if none exists, treat empty/"steady" label as neutral).

- [ ] **Step 4: Extend `VioletBadge` to take a status color**

Add an overload/param to `VioletBadge` in `Components.kt` accepting a background/border/content color trio (or a `PillStatus`), mapping: GOOD→`#86efac`, CAUTION→`#fbbf24`, OFF_TRACK→`ErrorRed`, NEUTRAL→muted white. Keep the existing no-arg behavior (default violet) for other callers.

- [ ] **Step 5: Render the pill in `ChartHeader`**

In `ChartHeader` (`278–326`) and `MiniChartCard` (`223–274`), replace/augment the plain `trendLabel` text with the colored pill via `VioletBadge(status = pillStatus(series.trendIsGood, series.isNeutral), text = series.trendLabel)`. Apply the per-metric line color already present (no change needed to chart stroke).

- [ ] **Step 6: Run tests + type-check**

Run: `./gradlew :app:testDebugUnitTest --tests "*VerdictPillTest*" && ./gradlew :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/progress/ app/src/main/java/com/zack/recomptracker/ui/component/Components.kt app/src/test/java/com/zack/recomptracker/ui/progress/VerdictPillTest.kt
git commit -m "feat(trends): colored verdict pills on chart cards"
```

---

### Task 15: Plan — collapsible advanced review rules

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt:98–122`

- [ ] **Step 1: Surface the calorie zone, collapse the rest** (mockup `plan` option B)

Split the current "Review rules" `SectionCard` (`98–122`) into:
- A "Calorie zone" `SectionCard` with the two zone `NumberField`s (lower/upper bound).
- An **Advanced** collapsible: a clickable header row (`var advancedOpen by remember { mutableStateOf(false) }`) that toggles an `AnimatedVisibility` wrapping a `SectionCard` with the remaining fields (weight-trend threshold, waist threshold, adherence minimum, review cadence, phase start date picker, metric-units `ToggleRow`). Default collapsed.

Keep all existing `viewModel` action calls unchanged. Reuse `SectionCard`, `NumberField`, `ToggleRow`.

- [ ] **Step 2: Type-check + commit**

Run: `./gradlew :app:compileDebugKotlin`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt
git commit -m "refactor(plan): surface calorie zone, collapse advanced review rules"
```

---

# PHASE 5 — Rebuild More hub, rewire nav, delete dead screens

### Task 16: Rebuild `MoreScreen` as the hub

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`

The hub shows a live featured Calorie Decision card (verdict + adherence) + a Trends row + the six setup rows. It reads `DashboardViewModel` for the featured preview.

- [ ] **Step 1: Rewrite `MoreScreen`**

New signature:

```kotlin
fun MoreScreen(
    dashboardViewModel: DashboardViewModel,
    onCalorieDecision: () -> Unit,
    onTrends: () -> Unit,
    onProfile: () -> Unit,
    onPlan: () -> Unit,
    onAppearance: () -> Unit,
    onAiCoach: () -> Unit,
    onIntegrations: () -> Unit,
    onDataBackup: () -> Unit,
)
```

Layout (mockup `more-hub-v3`), reusing `MenuCard`/`MenuRow` (keep these in `MoreScreen.kt`), `TintedCard`, `SectionLabel`, `MenuIcon`:
- Header "More".
- **Insights** `SectionLabel`:
  - Featured **Calorie Decision** `TintedCard` (clickable → `onCalorieDecision`) showing `dashboardState.result` verdict label + recommended change + `adherencePercent` + `loggedDaysInWindow` as the subtitle.
  - **Trends** `MenuRow` → `onTrends`.
- **Setup** `SectionLabel` + a `MenuCard` of `MenuRow`s: Profile, Plan, Appearance, AI & Coach, Integrations, Data & Backup → respective callbacks.

Delete the now-unused AI/font/export/HC code remnants from this file (the composables were moved in Tasks 7/8/12; remove leftover imports). Collect `DashboardViewModel.uiState`.

- [ ] **Step 2: Type-check (will fail at `AppNavGraph` More block — fixed next task)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL at the `MoreScreen(...)` call site (old params). Fixed in Task 17.

- [ ] **Step 3: Commit (after Task 17 compiles — or stage together)**

Stage with Task 17.

---

### Task 17: Rewire navigation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt` (if it references `TopLevelDestination.Progress`)

- [ ] **Step 1: Rename/replace routes**

In `object Routes`: rename `Stats` → `const val CalorieDecision = "calorie_decision"`; replace `Charts` with `const val Trends = "trends"`; delete `const val Settings`.

- [ ] **Step 2: Update composable blocks**

- Rename the `Routes.Stats` block to `Routes.CalorieDecision` (still `DashboardScreen(..., onBack=...)`).
- Replace the `Routes.Charts` block AND the `TopLevelDestination.Progress.route` block with a single `Routes.Trends` block: `ProgressScreen(viewModel<ProgressViewModel>(factory = factory), onBack = { navController.popBackStack() })`. (Add an `onBack` param to `ProgressScreen` like Task 13 did for DashboardScreen.)
- Delete the `Routes.Settings` composable block.
- Update the `More` block to the new `MoreScreen` signature:

```kotlin
MoreScreen(
    dashboardViewModel = viewModel<DashboardViewModel>(factory = factory),
    onCalorieDecision = { navController.navigate(Routes.CalorieDecision) },
    onTrends = { navController.navigate(Routes.Trends) },
    onProfile = { navController.navigate(Routes.Profile) },
    onPlan = { navController.navigate(Routes.Plan) },
    onAppearance = { navController.navigate(Routes.Appearance) },
    onAiCoach = { navController.navigate(Routes.AiCoach) },
    onIntegrations = { navController.navigate(Routes.Integrations) },
    onDataBackup = { navController.navigate(Routes.DataBackup) },
)
```

- [ ] **Step 3: Remove the `Progress` top-level entry**

Delete `Progress("progress", "Progress")` from `TopLevelDestination` (lines 56). Run `grep -rn "TopLevelDestination.Progress" app/src/main/java` and fix any references (the old `MoreScreen.onProgressClick` is already gone; check `RecompApp.kt`). The bottom bar (`RecompApp.kt:75–81`) does not include Progress, so no tab change.

- [ ] **Step 4: Add the missing `onBack` to `ProgressScreen`**

Edit `ProgressScreen.kt:52` to `fun ProgressScreen(viewModel: ProgressViewModel, onBack: () -> Unit)` and add a back affordance in its header (`89–105`), mirroring DashboardScreen.

- [ ] **Step 5: Type-check + tests + commit (with Task 16)**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
```bash
git add app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt app/src/main/java/com/zack/recomptracker/ui/progress/ProgressScreen.kt app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
git commit -m "feat(more): rebuild More as hub; dedupe Trends; rewire navigation"
```

---

### Task 18: Delete the dead screens

**Files:**
- Delete: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`
- Delete: `app/src/main/java/com/zack/recomptracker/ui/more/MoreViewModel.kt`

- [ ] **Step 1: Confirm no references remain**

Run: `grep -rn "SettingsScreen\|MoreViewModel" app/src/main/java`
Expected: only the `import`/factory lines you're about to remove. `SettingsViewModel` must still be referenced (by Integrations + Data & Backup) — do **not** delete it.

- [ ] **Step 2: Delete the files + factory branch**

Delete the two files. Remove the `MoreViewModel` branch from `viewModelFactory` in `core/AppContainer.kt` and any now-unused imports in `AppNavGraph.kt`.

- [ ] **Step 3: Type-check + tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(more): delete dead SettingsScreen and MoreViewModel"
```

---

### Task 19: Full build + manual verification

- [ ] **Step 1: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke test (emulator/device)**

Verify each item:
- More hub shows the featured Calorie Decision card with live verdict + adherence, and a Trends row.
- Every Setup row opens its screen and Back returns to More.
- Calorie Decision: verdict + AI why + targets + trend summary; no Today card.
- Trends: 7/14/28 range works; chart cards show colored verdict pills.
- Plan: advanced rules collapsed by default, expand works, Save persists.
- Profile: avatar photo pick persists; name edits; Goal/Activity/Sex sheets select; DOB picker sets age; current weight shows from Body.
- Appearance: font + accent change apply live; preview reflects them.
- AI & Coach: enable toggle; switch engine; on-device model manager + cloud config both render; test connection works.
- Integrations: HC toggle/sync; Samsung import; NEVO import/remove.
- Data & Backup: export/import round-trips; danger-zone confirmations fire.
- Coach chat still works (profile prompt no longer references removed fields).

- [ ] **Step 3: Final commit (if any manual-fix tweaks were needed)**

```bash
git add -A
git commit -m "fix(more): manual-verification polish"
```

---

## Self-Review notes

- **Spec coverage:** Hub (T16), Calorie Decision (T13), Trends (T14), Plan (T15), Profile (T6), Appearance (T7), AI & Coach (T11–12), Integrations (T8), Data & Backup (T9), nav/dedupe/delete (T10,17,18), data-model + migration + domain (T1–5). All spec sections mapped.
- **Component reuse:** every screen task names the existing components it reuses; the only new pieces are the avatar/photo picker, DOB picker, bottom-sheet pickers, the engine radio card, and the colored pill (a `VioletBadge` extension) — all flagged in the spec.
- **Type consistency:** `ageYears` is a function `ageYears(today)` everywhere; `migrateLegacyProfileJson`, `pillStatus`/`PillStatus`, `AiCoachViewModel`/`AiCoachUiState`, route names (`CalorieDecision`, `Trends`, `Profile`, `Appearance`, `AiCoach`, `Integrations`, `DataBackup`) used consistently across tasks.
- **Open decisions resolved:** migration = approx birthDate (Jan 1); no target-weight field added (out of scope); photo stored as a persisted URI string via PickVisualMedia.
