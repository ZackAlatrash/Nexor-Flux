# User Profile + AI Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user profile (height, age, sex, activity level, weekly gym sessions, goal) stored in its own DataStore, editable in Settings, and injected into the AI coach's system prompt.

**Architecture:** A new `UserProfilePreferences` data class + `UserProfilePreferencesStore` (own DataStore, mirrors `AppPreferences` pattern) gets wired into `AppContainer`. `SettingsViewModel` gains a profile flow + save function. `SettingsScreen` gets a "My Profile" section. `GemmaCoachCoordinator` reads the profile once at conversation start and injects it into `buildSystemPrompt()`.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore (Preferences), kotlinx.serialization (for enum String keys), Coroutines/Flow.

---

## File Map

| File | Action |
|---|---|
| `data/preferences/UserProfilePreferences.kt` | **Create** — data class + 3 enums |
| `data/preferences/UserProfilePreferencesStore.kt` | **Create** — DataStore wrapper |
| `core/AppContainer.kt` | **Modify** — instantiate store, pass to coordinator + SettingsViewModel |
| `ui/settings/SettingsViewModel.kt` | **Modify** — add `profileState` flow + `saveProfile()` |
| `ui/settings/SettingsScreen.kt` | **Modify** — add "My Profile" section |
| `ai/GemmaCoachCoordinator.kt` | **Modify** — read profile in `createConversation()`, inject in `buildSystemPrompt()` |

---

## Task 1: Data model — `UserProfilePreferences`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.zack.recomptracker.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class UserProfilePreferences(
    val heightCm: Int? = null,
    val ageYears: Int? = null,
    val biologicalSex: BiologicalSex? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyGymSessions: Int? = null,
    val goal: FitnessGoal? = null,
)

enum class BiologicalSex { MALE, FEMALE }

enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
}

enum class FitnessGoal {
    AGGRESSIVE_CUT,
    MODERATE_CUT,
    MINI_CUT,
    RECOMP,
    LEAN_BULK,
    MODERATE_BULK,
    AGGRESSIVE_BULK,
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferences.kt
git commit -m "feat: add UserProfilePreferences data class and enums"
```

---

## Task 2: Storage — `UserProfilePreferencesStore`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt`

- [ ] **Step 1: Create the store**

```kotlin
package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profile_preferences")

class UserProfilePreferencesStore(private val context: Context) {

    val preferences: Flow<UserProfilePreferences> = context.userProfileDataStore.data.map { prefs ->
        UserProfilePreferences(
            heightCm = prefs[Keys.HeightCm],
            ageYears = prefs[Keys.AgeYears],
            biologicalSex = prefs[Keys.BiologicalSex]?.let {
                runCatching { BiologicalSex.valueOf(it) }.getOrNull()
            },
            activityLevel = prefs[Keys.ActivityLevel]?.let {
                runCatching { ActivityLevel.valueOf(it) }.getOrNull()
            },
            weeklyGymSessions = prefs[Keys.WeeklyGymSessions],
            goal = prefs[Keys.Goal]?.let {
                runCatching { FitnessGoal.valueOf(it) }.getOrNull()
            },
        )
    }

    suspend fun save(profile: UserProfilePreferences) {
        context.userProfileDataStore.edit { prefs ->
            if (profile.heightCm != null) prefs[Keys.HeightCm] = profile.heightCm
            else prefs.remove(Keys.HeightCm)

            if (profile.ageYears != null) prefs[Keys.AgeYears] = profile.ageYears
            else prefs.remove(Keys.AgeYears)

            if (profile.biologicalSex != null) prefs[Keys.BiologicalSex] = profile.biologicalSex.name
            else prefs.remove(Keys.BiologicalSex)

            if (profile.activityLevel != null) prefs[Keys.ActivityLevel] = profile.activityLevel.name
            else prefs.remove(Keys.ActivityLevel)

            if (profile.weeklyGymSessions != null) prefs[Keys.WeeklyGymSessions] = profile.weeklyGymSessions
            else prefs.remove(Keys.WeeklyGymSessions)

            if (profile.goal != null) prefs[Keys.Goal] = profile.goal.name
            else prefs.remove(Keys.Goal)
        }
    }

    private object Keys {
        val HeightCm = intPreferencesKey("height_cm")
        val AgeYears = intPreferencesKey("age_years")
        val BiologicalSex = stringPreferencesKey("biological_sex")
        val ActivityLevel = stringPreferencesKey("activity_level")
        val WeeklyGymSessions = intPreferencesKey("weekly_gym_sessions")
        val Goal = stringPreferencesKey("goal")
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/data/preferences/UserProfilePreferencesStore.kt
git commit -m "feat: add UserProfilePreferencesStore with DataStore persistence"
```

---

## Task 3: Wire into AppContainer

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add the import and instantiate the store**

Add this import at the top of `AppContainer.kt`:
```kotlin
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
```

Add this line right after the `val uiPreferences = ...` line (around line 52):
```kotlin
val userProfilePreferencesStore = UserProfilePreferencesStore(context.applicationContext)
```

- [ ] **Step 2: Pass the store to `GemmaCoachCoordinator`**

The `GemmaCoachCoordinator` constructor will need `userProfilePreferencesStore` in Task 6. For now just ensure it's on the container. The `SettingsViewModel` factory entry also needs it — update that in Task 4.

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: instantiate UserProfilePreferencesStore in AppContainer"
```

---

## Task 4: SettingsViewModel — profile state + save

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add the store to SettingsViewModel's constructor**

Add import at the top of `SettingsViewModel.kt`:
```kotlin
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
```

Change the constructor signature from:
```kotlin
class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val hcRepository: HealthConnectRepository,
    private val foodCatalogRepository: FoodCatalogRepository,
    private val personalFoodRepository: PersonalFoodRepository,
) : ViewModel() {
```
To:
```kotlin
class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val hcRepository: HealthConnectRepository,
    private val foodCatalogRepository: FoodCatalogRepository,
    private val personalFoodRepository: PersonalFoodRepository,
    private val userProfileStore: UserProfilePreferencesStore,
) : ViewModel() {
```

- [ ] **Step 2: Expose profile state and add saveProfile()**

Add these two members directly below the existing `val nutritionPermission` line:

```kotlin
val profileState: StateFlow<UserProfilePreferences> =
    userProfileStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfilePreferences())

fun saveProfile(profile: UserProfilePreferences) {
    viewModelScope.launch { userProfileStore.save(profile) }
}
```

Also add these imports at the top of the file:
```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 3: Update the SettingsViewModel factory in AppContainer**

In `AppContainer.kt`, find the `SettingsViewModel::class.java ->` block and add the new parameter:

```kotlin
SettingsViewModel::class.java -> SettingsViewModel(
    backupRepository = container.backupRepository,
    logRepository = container.logRepository,
    planRepository = container.planRepository,
    hcRepository = container.healthConnectRepository,
    foodCatalogRepository = container.foodCatalogRepository,
    personalFoodRepository = container.personalFoodRepository,
    userProfileStore = container.userProfilePreferencesStore,
)
```

- [ ] **Step 4: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: expose user profile state and saveProfile() in SettingsViewModel"
```

---

## Task 5: Settings UI — "My Profile" section

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add the necessary imports at the top of SettingsScreen.kt**

Add these imports (after the existing imports block):
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.KeyboardType
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
```

- [ ] **Step 2: Read profile state in SettingsScreen**

Inside `SettingsScreen`, add this line right after `val state by viewModel.uiState.collectAsStateWithLifecycle()`:

```kotlin
val profile by viewModel.profileState.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Add the "My Profile" LazyColumn item**

Add a new `item { }` block at the top of the `LazyColumn` content (after the title item and before the "Backup" item):

```kotlin
item {
    UserProfileSection(
        profile = profile,
        onProfileChange = viewModel::saveProfile,
    )
}
```

- [ ] **Step 4: Add the UserProfileSection composable**

Add this composable at the bottom of `SettingsScreen.kt`, below the existing composables:

```kotlin
@Composable
private fun UserProfileSection(
    profile: UserProfilePreferences,
    onProfileChange: (UserProfilePreferences) -> Unit,
) {
    SectionCard("My Profile") {
        // Goal — vertical radio list
        Text("Goal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FitnessGoal.entries.forEach { g ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = profile.goal == g,
                            onClick = {
                                onProfileChange(profile.copy(goal = if (profile.goal == g) null else g))
                            },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = profile.goal == g,
                        onClick = {
                            onProfileChange(profile.copy(goal = if (profile.goal == g) null else g))
                        },
                    )
                    Text(g.displayName())
                }
            }
        }

        // Biological sex — chip row
        Text("Biological Sex", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BiologicalSex.entries.forEach { s ->
                FilterChip(
                    selected = profile.biologicalSex == s,
                    onClick = {
                        onProfileChange(profile.copy(biologicalSex = if (profile.biologicalSex == s) null else s))
                    },
                    label = { Text(s.displayName()) },
                )
            }
        }

        // Activity level — chip rows
        Text("Activity Level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActivityLevel.entries.forEach { a ->
                FilterChip(
                    selected = profile.activityLevel == a,
                    onClick = {
                        onProfileChange(profile.copy(activityLevel = if (profile.activityLevel == a) null else a))
                    },
                    label = { Text(a.displayName()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Weekly gym sessions — stepper
        Text("Weekly Gym Sessions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LiquidSecondaryButton(
                text = "−",
                onClick = {
                    val cur = profile.weeklyGymSessions ?: 0
                    if (cur > 0) onProfileChange(profile.copy(weeklyGymSessions = cur - 1))
                },
                enabled = (profile.weeklyGymSessions ?: 0) > 0,
            )
            Text(
                text = "${profile.weeklyGymSessions ?: 0} days/week",
                style = MaterialTheme.typography.bodyLarge,
            )
            LiquidSecondaryButton(
                text = "+",
                onClick = {
                    val cur = profile.weeklyGymSessions ?: 0
                    if (cur < 7) onProfileChange(profile.copy(weeklyGymSessions = cur + 1))
                },
                enabled = (profile.weeklyGymSessions ?: 0) < 7,
            )
        }

        // Height — cm field (stored always as cm)
        Text("Height (cm)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = profile.heightCm?.toString() ?: "",
            onValueChange = { raw ->
                val parsed = raw.filter { it.isDigit() }.take(3).toIntOrNull()
                onProfileChange(profile.copy(heightCm = parsed))
            },
            placeholder = { Text("e.g. 178") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // Age — text field
        Text("Age", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = profile.ageYears?.toString() ?: "",
            onValueChange = { raw ->
                val parsed = raw.filter { it.isDigit() }.take(3).toIntOrNull()
                onProfileChange(profile.copy(ageYears = parsed))
            },
            placeholder = { Text("e.g. 26") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

private fun FitnessGoal.displayName(): String = when (this) {
    FitnessGoal.AGGRESSIVE_CUT -> "Aggressive Cut"
    FitnessGoal.MODERATE_CUT -> "Moderate Cut"
    FitnessGoal.MINI_CUT -> "Mini Cut"
    FitnessGoal.RECOMP -> "Recomp"
    FitnessGoal.LEAN_BULK -> "Lean Bulk"
    FitnessGoal.MODERATE_BULK -> "Moderate Bulk"
    FitnessGoal.AGGRESSIVE_BULK -> "Aggressive Bulk"
}

private fun BiologicalSex.displayName(): String = when (this) {
    BiologicalSex.MALE -> "Male"
    BiologicalSex.FEMALE -> "Female"
}

private fun ActivityLevel.displayName(): String = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly Active"
    ActivityLevel.MODERATELY_ACTIVE -> "Moderately Active"
    ActivityLevel.VERY_ACTIVE -> "Very Active"
}
```

- [ ] **Step 5: Build and verify**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
git commit -m "feat: add My Profile section to Settings screen"
```

---

## Task 6: AI integration — inject profile into system prompt

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt`

- [ ] **Step 1: Add the store to GemmaCoachCoordinator's constructor**

Add import at the top of `GemmaCoachCoordinator.kt`:
```kotlin
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
```

Change the constructor from:
```kotlin
class GemmaCoachCoordinator(
    private val serviceHolder: GemmaServiceHolder,
    private val insightCoordinator: AiInsightCoordinator,
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val scope: CoroutineScope,
) : CoachCoordinator {
```
To:
```kotlin
class GemmaCoachCoordinator(
    private val serviceHolder: GemmaServiceHolder,
    private val insightCoordinator: AiInsightCoordinator,
    private val toolExecutor: CoachToolExecutor,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
    private val scope: CoroutineScope,
) : CoachCoordinator {
```

- [ ] **Step 2: Read the profile in createConversation()**

In `createConversation()`, the method currently reads `planRepository.preferences.first()` and `dateProvider.today()`. Add a profile read right after the prefs read:

```kotlin
private suspend fun createConversation(service: GemmaInsightService): Conversation {
    val toolProviders: List<ToolProvider> = COACH_TOOLS.map { tool(it) }
    val prefs = planRepository.preferences.first()
    val profile = userProfileStore.preferences.first()   // add this line
    val today = dateProvider.today()
    val todaySummary = withContext(Dispatchers.IO) {
        toolExecutor.execute("get_today_summary", emptyMap())
    }
    Log.d("RecompCoach", "snapshot: $todaySummary")
    val config = ConversationConfig(
        systemInstruction = Contents.of(buildSystemPrompt(prefs, profile, today, todaySummary)),  // add profile
        tools = toolProviders,
        automaticToolCalling = false,
    )
    return service.createConversation(config)
}
```

- [ ] **Step 3: Update buildSystemPrompt() signature and add the profile block**

Change the signature from:
```kotlin
private fun buildSystemPrompt(
    prefs: PlanPreferences,
    today: java.time.LocalDate,
    todaySummary: String,
): String = buildString {
```
To:
```kotlin
private fun buildSystemPrompt(
    prefs: PlanPreferences,
    profile: UserProfilePreferences,
    today: java.time.LocalDate,
    todaySummary: String,
): String = buildString {
```

Then add the profile block right after the plan line (after the `appendLine("Plan: ...")` line and before the snapshot section):

```kotlin
// User profile block — only include fields that have been set
val profileParts = buildList {
    profile.goal?.let { add("Goal: ${it.displayName()}") }
    profile.biologicalSex?.let { add("Sex: ${it.displayName()}") }
    profile.ageYears?.let { add("Age: $it") }
    profile.heightCm?.let { add("Height: $it cm") }
    profile.activityLevel?.let { add("Activity: ${it.displayName()}") }
    profile.weeklyGymSessions?.let { add("Gym sessions/week: $it") }
}
if (profileParts.isNotEmpty()) {
    appendLine()
    appendLine("=== USER PROFILE ===")
    appendLine(profileParts.joinToString(" | "))
    appendLine("=== END PROFILE ===")
}
```

- [ ] **Step 4: Add the displayName extension functions**

Add these private extension functions at the bottom of `GemmaCoachCoordinator.kt`, just before the closing `}`:

```kotlin
private fun FitnessGoal.displayName(): String = when (this) {
    FitnessGoal.AGGRESSIVE_CUT -> "Aggressive Cut"
    FitnessGoal.MODERATE_CUT -> "Moderate Cut"
    FitnessGoal.MINI_CUT -> "Mini Cut"
    FitnessGoal.RECOMP -> "Recomp"
    FitnessGoal.LEAN_BULK -> "Lean Bulk"
    FitnessGoal.MODERATE_BULK -> "Moderate Bulk"
    FitnessGoal.AGGRESSIVE_BULK -> "Aggressive Bulk"
}

private fun BiologicalSex.displayName(): String = when (this) {
    BiologicalSex.MALE -> "Male"
    BiologicalSex.FEMALE -> "Female"
}

private fun ActivityLevel.displayName(): String = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly Active"
    ActivityLevel.MODERATELY_ACTIVE -> "Moderately Active"
    ActivityLevel.VERY_ACTIVE -> "Very Active"
}
```

- [ ] **Step 5: Update AppContainer to pass userProfilePreferencesStore to GemmaCoachCoordinator**

In `AppContainer.kt`, find the `coachCoordinator` property and add the new parameter:

```kotlin
val coachCoordinator: CoachCoordinator = GemmaCoachCoordinator(
    serviceHolder = gemmaServiceHolder,
    insightCoordinator = aiInsightCoordinator,
    toolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
    ),
    planRepository = planRepository,
    userProfileStore = userProfilePreferencesStore,
    dateProvider = dateProvider,
    scope = appScope,
)
```

- [ ] **Step 6: Verify the full build passes**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/GemmaCoachCoordinator.kt \
        app/src/main/java/com/zack/recomptracker/core/AppContainer.kt
git commit -m "feat: inject user profile into AI coach system prompt"
```

---

## Task 7: Manual smoke test

- [ ] **Step 1: Install and open the app on a device or emulator**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Navigate to Settings → My Profile**

Verify the section appears with all controls: goal radio list, sex chips, activity chips, gym sessions stepper, height and age text fields.

- [ ] **Step 3: Fill in a profile**

Set Goal = Lean Bulk, Sex = Male, Activity = Moderately Active, Gym sessions = 4, Height = 178, Age = 26. Kill and reopen the app — confirm the values are still there.

- [ ] **Step 4: Open the AI Coach and start a conversation**

Ask: "What's my goal?" — the coach should answer "Lean Bulk" without calling any tool (it has the profile in its system prompt).

Ask: "Based on my height and age, does my current weight seem reasonable?" — the coach should reference 178 cm and age 26 in its answer.

- [ ] **Step 5: Verify blank profile still works**

Clear all fields (set everything back to blank/empty). Open the coach, send a message. Confirm it responds normally (no crash, no mention of missing profile data).

- [ ] **Step 6: Commit the final state**

```bash
git add -p   # review any stray changes
git commit -m "feat: user profile with AI context injection — complete"
```
