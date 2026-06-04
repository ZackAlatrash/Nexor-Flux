# Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a motivational message card, two stat tiles (Adherence + Weight Trend), and two floating Apple-style liquid glass pill buttons (Daily Check-In + Log Food) to the home dashboard.

**Architecture:** Extend `DashboardUiState` with a `motivationalMessage` field picked once at ViewModel construction time. Add three new composables to `DashboardScreen.kt` (`MotivationalCard`, `StatTilesRow`, floating button `Row`) and wire navigation callbacks through `AppNavGraph`. No new files — all changes are additive to three existing files.

**Tech Stack:** Kotlin, Jetpack Compose, existing `LiquidPrimaryButton` / `LiquidSecondaryButton` / `FrostedCard` components, `DashboardUiState`, `DashboardViewModel`.

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt` | Add `MOTIVATIONAL_MESSAGES` companion list + `todayMessage` field; include in `DashboardUiState` |
| `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt` | Add `MotivationalCard`, `StatTilesRow`, floating buttons; update `HomeDashboardScreen` + `HomeDashboardContent` signatures |
| `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt` | Pass `onCheckIn` and `onLogFood` callbacks to `HomeDashboardScreen` |
| `app/src/test/java/com/zack/recomptracker/ui/dashboard/DashboardViewModelMessagesTest.kt` | New unit tests for motivational message logic |

---

### Task 1: Add `motivationalMessage` to `DashboardUiState` and `DashboardViewModel`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt`
- Create: `app/src/test/java/com/zack/recomptracker/ui/dashboard/DashboardViewModelMessagesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/zack/recomptracker/ui/dashboard/DashboardViewModelMessagesTest.kt`:

```kotlin
package com.zack.recomptracker.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelMessagesTest {

    @Test
    fun motivationalMessagesListIsNotEmpty() {
        val messages = DashboardViewModel.MOTIVATIONAL_MESSAGES
        assertFalse("Message list must not be empty", messages.isEmpty())
    }

    @Test
    fun everyMessageIsNonBlank() {
        DashboardViewModel.MOTIVATIONAL_MESSAGES.forEachIndexed { index, msg ->
            assertFalse("Message at index $index is blank", msg.isBlank())
        }
    }

    @Test
    fun defaultUiStateHasEmptyMessage() {
        val state = DashboardUiState()
        assertTrue("Default motivationalMessage should be empty", state.motivationalMessage.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian"
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.DashboardViewModelMessagesTest" 2>&1 | tail -20
```

Expected: FAIL — `DashboardViewModel.MOTIVATIONAL_MESSAGES` does not exist yet.

- [ ] **Step 3: Add `motivationalMessage` to `DashboardUiState`**

In `DashboardViewModel.kt`, locate the `DashboardUiState` data class (around line 45) and add the new field before `result`:

```kotlin
data class DashboardUiState(
    val preferences: PlanPreferences = PlanPreferences(),
    val todayTotals: MacroTotals = MacroTotals(),
    val sevenDayWeightAverage: Double? = null,
    val weightTrendKgPerWeek: Double = 0.0,
    val waistTrendCmPerWeek: Double = 0.0,
    val adherencePercent: Double = 0.0,
    val daysLogged: Int = 0,
    val last7DaysCalories: List<DayCalories> = emptyList(),
    val inZoneDays7: Int = 0,
    val motivationalMessage: String = "",          // NEW
    val result: AdjustmentResult = AdjustmentResult(
        verdict = AdjustmentVerdict.WAIT_FOR_DATA,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("NO_DATA"),
        summary = "Log today to start building a review window.",
    ),
)
```

- [ ] **Step 4: Add `MOTIVATIONAL_MESSAGES` companion object and `todayMessage` val to `DashboardViewModel`**

Add `private val todayMessage` directly after the `uiState` property declaration (before `init`), and add the `companion object` at the end of the class body:

```kotlin
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Picked once at ViewModel construction — stable for the whole session.
    private val todayMessage: String = MOTIVATIONAL_MESSAGES.random()

    init {
        // ... existing init block unchanged ...
    }

    // ... existing private methods unchanged ...

    companion object {
        val MOTIVATIONAL_MESSAGES: List<String> = listOf(
            "Small daily improvements lead to stunning long-term results.",
            "Discipline is the bridge between goals and accomplishment.",
            "You don't have to be extreme, just consistent.",
            "Progress, not perfection.",
            "Every rep, every meal — it all compounds.",
            "The body achieves what the mind believes.",
            "Eat well. Move well. Sleep well. Repeat.",
            "Trust the process — the data doesn't lie.",
            "One more logged day. One step closer.",
            "Recomposition is a marathon, not a sprint.",
            "Fuel your body like you mean it.",
            "You showed up today. That's already a win.",
            "Strong is built one decision at a time.",
            "Consistency beats intensity every time.",
            "Log it, track it, own it.",
            "Your future self will thank you.",
            "Build habits, not excuses.",
            "The scale tells one story. The trend tells the truth.",
            "Focus on what you can control today.",
            "Every check-in is a data point in your favour.",
        )
    }
}
```

Then in `buildState(...)`, add `motivationalMessage = todayMessage` to the returned `DashboardUiState(...)` call.

- [ ] **Step 5: Run the test — verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "com.zack.recomptracker.ui.dashboard.DashboardViewModelMessagesTest" 2>&1 | tail -20
```

Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardViewModel.kt \
        app/src/test/java/com/zack/recomptracker/ui/dashboard/DashboardViewModelMessagesTest.kt
git commit -m "feat(dashboard): add motivationalMessage to DashboardUiState + ViewModel"
```

---

### Task 2: Add `MotivationalCard` and `StatTilesRow` composables

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Verify imports already present in `DashboardScreen.kt`**

The new composables need: `Brush`, `border`, `clip`, `RoundedCornerShape`, `Column`, `Box`, `Alignment`. All are already imported in the file. Confirm by checking the import block at the top; add any that are missing:

```kotlin
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
```

- [ ] **Step 2: Add `MotivationalCard` composable**

`FrostedCard` only accepts `modifier`, `contentPadding`, and `content` — no color params. Use a plain `Box` with an explicit gradient background.

Add this private function after `SevenDayChartCard` (around line 441, before the legacy `DashboardScreen` function):

```kotlin
// ── Card: MOTIVATIONAL MESSAGE ────────────────────────────────────────────────

@Composable
private fun MotivationalCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0x247C3AED), Color(0x106D28D9)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(
                        Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    ),
                ),
            )
            .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = "“$message”",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEDE9FE),
                lineHeight = 19.sp,
                letterSpacing = (-0.2).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Refreshes every time you open the app",
                fontSize = 10.sp,
                color = Color(0x8CA78BFA),
            )
        }
    }
}
```

- [ ] **Step 3: Add `StatTilesRow` and `StatTile` composables**

Add immediately after `MotivationalCard`:

```kotlin
// ── Stat Tiles Row ────────────────────────────────────────────────────────────

@Composable
private fun StatTilesRow(
    adherencePercent: Double,
    weightTrendKgPerWeek: Double,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            value = adherencePercent.formatPercent(),
            label = "Adherence",
            valueColor = Violet400,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = "${weightTrendKgPerWeek.formatSignedOneDecimal()} kg",
            label = "Trend / week",
            valueColor = if (weightTrendKgPerWeek <= 0.0) ErrorRed else Color(0xFF4ADE80),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = valueColor,
                letterSpacing = (-0.5).sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted,
                letterSpacing = 0.06.sp,
            )
        }
    }
}
```

Both `formatPercent` and `formatSignedOneDecimal` are already imported in `DashboardScreen.kt`. `ErrorRed` and `Violet400` are also already imported.

- [ ] **Step 4: Verify the file compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(dashboard): add MotivationalCard and StatTilesRow composables"
```

---

### Task 3: Add floating action buttons and update `HomeDashboardContent`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Add missing imports**

Add these imports to `DashboardScreen.kt` if not already present:

```kotlin
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.FloatingNavHeight
```

- [ ] **Step 2: Update `HomeDashboardScreen` to accept and forward callbacks**

Replace the current `HomeDashboardScreen` function:

```kotlin
@Composable
fun HomeDashboardScreen(
    viewModel: DashboardViewModel,
    onCheckIn: () -> Unit,
    onLogFood: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeDashboardContent(state = state, onCheckIn = onCheckIn, onLogFood = onLogFood)
}
```

- [ ] **Step 3: Replace `HomeDashboardContent` with updated layout**

Replace the entire `HomeDashboardContent` function:

```kotlin
@Composable
fun HomeDashboardContent(
    state: DashboardUiState,
    onCheckIn: () -> Unit,
    onLogFood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb 1 — top-left violet bloom
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x338B5CF6), Color.Transparent),
                    ),
                ),
        )
        // Ambient orb 2 — right-center secondary bloom
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 200.dp, y = 260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x14a78bfa), Color.Transparent),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = FloatingNavHeight + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { MotivationalCard(state.motivationalMessage) }
                item { TodayCard(state) }
                item { StatTilesRow(state.adherencePercent, state.weightTrendKgPerWeek) }
                item { SevenDayChartCard(state) }
            }
        }

        // Floating liquid glass pill buttons above the nav bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FloatingNavHeight + 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                LiquidPrimaryButton(
                    text = "Daily Check-In",
                    onClick = onCheckIn,
                    modifier = Modifier.weight(1f),
                )
                LiquidSecondaryButton(
                    text = "Log Food",
                    onClick = onLogFood,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/dashboard/DashboardScreen.kt
git commit -m "feat(dashboard): floating liquid glass action buttons + updated layout"
```

---

### Task 4: Wire navigation callbacks in `AppNavGraph`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Update the `HomeDashboardScreen` call site**

Locate this block (around line 73):

```kotlin
composable(TopLevelDestination.Home.route) {
    HomeDashboardScreen(
        viewModel = viewModel<DashboardViewModel>(factory = factory),
    )
}
```

Replace it with:

```kotlin
composable(TopLevelDestination.Home.route) {
    HomeDashboardScreen(
        viewModel = viewModel<DashboardViewModel>(factory = factory),
        onCheckIn = {
            navController.navigate(TopLevelDestination.Body.route) {
                popUpTo(TopLevelDestination.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onLogFood = {
            navController.navigate(Routes.Food) {
                popUpTo(TopLevelDestination.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}
```

- [ ] **Step 2: Full build to confirm no errors**

```bash
./gradlew :app:assembleDebug 2>&1 | grep -E "error:" | head -20
```

Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 3: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: all existing tests PASS plus the 3 new `DashboardViewModelMessagesTest` tests.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(dashboard): wire Daily Check-In and Log Food nav callbacks"
```

---

## Self-Review

**Spec coverage:**
- Motivational card (new, violet-gradient box, message from ViewModel) — Task 2
- Message rotates per app open (ViewModel `init` sets `todayMessage` once) — Task 1
- Today card unchanged — retained in LazyColumn, Task 3
- Stat tiles: Adherence + Weight Trend, no streak — Task 2
- 7-day chart card unchanged — retained in LazyColumn, Task 3
- Floating liquid glass pill buttons (capsule shape from `LiquidGlassButton`) — Task 3
- Daily Check-In navigates to Body tab — Task 4
- Log Food navigates to Food tab — Task 4
- Reuses existing components throughout — confirmed

**Placeholder scan:** None.

**Type consistency:**
- `DashboardUiState.motivationalMessage: String` — defined Task 1, read as `state.motivationalMessage` in Task 3
- `StatTilesRow(state.adherencePercent, state.weightTrendKgPerWeek)` — both fields already in existing `DashboardUiState`
- `onCheckIn` / `onLogFood: () -> Unit` — defined in Task 3 signatures, wired in Task 4
