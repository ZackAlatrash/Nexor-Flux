# Four-Tab Navigation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current five-tab analysis-heavy navigation with Home, Food, Body, and More destinations that match the approved mockup.

**Architecture:** Keep the repositories, Room entities, DataStore preferences, and ViewModels unchanged. Split the current `TodayScreen` composition into a Food screen and a Body & Recovery screen backed by the existing `TodayViewModel`, redesign `DashboardScreen` as Home, and move detailed analysis routes behind More.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Compose UI instrumentation tests.

---

## File Structure

- Modify `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`: define the four top-level tabs and secondary routes.
- Modify `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`: navigate bottom tabs relative to Home.
- Modify `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`: render the compact Home dashboard and quick actions.
- Create `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`: render daily nutrition and meal-slot management.
- Create `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`: render the daily body and recovery form.
- Delete `app/src/main/java/com/zack/recomptracker/ui/today/TodayScreen.kt`: remove the obsolete combined screen after extracting its focused pieces.
- Modify `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`: render grouped secondary navigation rows.
- Replace `app/src/androidTest/java/com/zack/recomptracker/ui/TodayScreenTest.kt`: verify the focused screen content.

### Task 1: Add Focused Screen UI Tests

**Files:**
- Modify: `app/src/androidTest/java/com/zack/recomptracker/ui/TodayScreenTest.kt`

- [ ] **Step 1: Replace the stale combined-screen test with failing focused-screen tests**

Create tests that render:

```kotlin
FoodContent(
    state = TodayUiState(date = LocalDate.of(2026, 5, 30)),
    actions = noOpFoodActions(),
    onBrowseLibrary = {},
)
```

and:

```kotlin
BodyRecoveryContent(
    state = TodayUiState(date = LocalDate.of(2026, 5, 30)),
    actions = noOpBodyActions(),
)
```

Assert that Food displays `Food`, `Nutrition target`, `Meals`, and `Browse saved foods & meals`. Assert that Body displays `Body & recovery`, `Weight`, `Sleep`, and `Save daily check-in`.

- [ ] **Step 2: Run Android test compilation to verify the new tests fail**

Run:

```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: FAIL because `FoodContent`, `FoodActions`, `BodyRecoveryContent`, and `BodyRecoveryActions` do not exist.

### Task 2: Split Food And Body Screens

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`
- Delete: `app/src/main/java/com/zack/recomptracker/ui/today/TodayScreen.kt`

- [ ] **Step 1: Create `FoodScreen.kt`**

Add:

```kotlin
data class FoodActions(
    val onToggleEditMode: () -> Unit,
    val onAddSlot: (String) -> Unit,
    val onRenameSlot: (Long, String) -> Unit,
    val onDeleteSlot: (Long) -> Unit,
    val onReorderSlots: (List<Long>) -> Unit,
    val onDeleteMeal: (Long) -> Unit,
)

@Composable
fun FoodScreen(
    viewModel: TodayViewModel,
    onAddToSlot: (Long, String) -> Unit,
    onBrowseLibrary: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoodContent(
        state = state,
        actions = FoodActions(
            onToggleEditMode = viewModel::toggleEditMode,
            onAddSlot = viewModel::addSlot,
            onRenameSlot = viewModel::renameSlot,
            onDeleteSlot = viewModel::deleteSlot,
            onReorderSlots = viewModel::reorderSlots,
            onDeleteMeal = viewModel::deleteMeal,
        ),
        onAddToSlot = onAddToSlot,
        onBrowseLibrary = onBrowseLibrary,
    )
}
```

Move the calorie-zone card, macro mini-bars, meal-slot cards, slot dialogs, and slot edit-mode logic from `TodayScreen.kt` into this file. Use Material `Add` and `Restaurant` icons with descriptive content descriptions.

- [ ] **Step 2: Create `BodyRecoveryScreen.kt`**

Add:

```kotlin
data class BodyRecoveryActions(
    val onBodyWeightChanged: (String) -> Unit,
    val onWaistChanged: (String) -> Unit,
    val onStepsChanged: (String) -> Unit,
    val onSleepChanged: (String) -> Unit,
    val onEnergyChanged: (Int) -> Unit,
    val onHungerChanged: (Int) -> Unit,
    val onSorenessChanged: (Int) -> Unit,
    val onTrainedChanged: (Boolean) -> Unit,
    val onNotesChanged: (String) -> Unit,
    val onSaveMetrics: () -> Unit,
)
```

Add `BodyRecoveryScreen(viewModel)` as the StateFlow collector. Add stateless `BodyRecoveryContent(state, actions)` and move the existing metrics form into it. Label the primary action `Save daily check-in`.

- [ ] **Step 3: Delete the obsolete combined screen**

Delete `TodayScreen.kt` after all meal-slot child composables have moved into `FoodScreen.kt`.

- [ ] **Step 4: Run Android test compilation**

Run:

```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: PASS.

### Task 3: Restructure Navigation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

- [ ] **Step 1: Replace the top-level destination enum**

Use:

```kotlin
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Default.Home),
    Food("food", "Food", Icons.Default.Restaurant),
    Body("body", "Body", Icons.Default.Favorite),
    More("more", "More", Icons.Default.MoreHoriz),
}
```

- [ ] **Step 2: Add secondary routes**

Use:

```kotlin
object Routes {
    const val Stats = "stats"
    const val Charts = "charts"
    const val Plan = "plan"
    const val FoodLibrary = "food_library"
    const val Foods = "foods"
    const val Settings = "settings"
}
```

- [ ] **Step 3: Connect routes to focused screens**

Set `TopLevelDestination.Home.route` as the start destination. Wire:

```kotlin
DashboardScreen(
    viewModel = viewModel(factory = factory),
    onLogFood = { navController.navigate(TopLevelDestination.Food.route) },
    onUpdateBody = { navController.navigate(TopLevelDestination.Body.route) },
)
```

Wire Food to `FoodScreen`, Body to `BodyRecoveryScreen`, and More to the grouped secondary callbacks. Keep the existing URL-encoded slot-specific food-library navigation.

- [ ] **Step 4: Update bottom-bar state restoration**

Change the `popUpTo` target in `RecompApp.kt` from the obsolete Today route to:

```kotlin
popUpTo(TopLevelDestination.Home.route) {
    saveState = true
}
```

- [ ] **Step 5: Run debug compilation**

Run:

```bash
./gradlew compileDebugKotlin
```

Expected: PASS.

### Task 4: Redesign Home And More

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/more/MoreScreen.kt`

- [ ] **Step 1: Make Dashboard the concise Home screen**

Keep `DashboardViewModel`. Change the screen signature to:

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLogFood: () -> Unit,
    onUpdateBody: () -> Unit,
)
```

Render:

- `Dashboard` header and `Your recomposition snapshot.`
- `Weekly direction` card with verdict and summary.
- two-column snapshot grid for weight trend, adherence, calories logged today, and logged days.
- full-width quick-action rows for `Log food` and `Update body check-in`.

Use Material icons and `MaterialTheme.colorScheme` tokens.

- [ ] **Step 2: Make More a grouped secondary menu**

Change the signature to:

```kotlin
@Composable
fun MoreScreen(
    onStatsClick: () -> Unit,
    onChartsClick: () -> Unit,
    onPlanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Render grouped menu rows:

```text
INSIGHTS
Stats
Charts

PLANNING
Plan

APP
Settings
```

Use `GridView`, `TrendingUp`, `Person`, `Settings`, and `ChevronRight` Material icons.

- [ ] **Step 3: Run debug compilation**

Run:

```bash
./gradlew compileDebugKotlin
```

Expected: PASS.

### Task 5: Verify The Redesign

**Files:**
- No additional code changes expected.

- [ ] **Step 1: Run unit tests**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 2: Assemble the debug app**

Run:

```bash
./gradlew assembleDebug
```

Expected: PASS.

- [ ] **Step 3: Compile instrumentation tests**

Run:

```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 4: Run connected tests when a device or emulator is available**

Run:

```bash
./gradlew connectedAndroidTest
```

Expected: PASS when an Android device or emulator is available. If none is connected, report that boundary explicitly.

- [ ] **Step 5: Review changed files**

Run:

```bash
git status --short
git diff --stat
```

Confirm that pre-existing Health Connect changes remain intact and are not included in UI-only edits.
