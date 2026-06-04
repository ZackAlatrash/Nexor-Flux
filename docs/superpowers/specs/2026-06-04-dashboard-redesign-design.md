# Dashboard Redesign — Design Spec
Date: 2026-06-04

## Goal
Improve the home dashboard from 2 cards to a richer, more motivational screen. Add Daily Check-In and Log Food liquid glass pill buttons floating above the nav bar, a daily rotating motivational message card, and two stat tiles (Adherence, Weight Trend).

## Screen Layout (top → bottom)

```
┌─────────────────────────────┐
│  Dashboard          Wed Jun4│  ← existing header
├─────────────────────────────┤
│  MotivationalCard           │  ← NEW: violet-tinted FrostedCard
│  "Small daily improvements…"│
├─────────────────────────────┤
│  TodayCard (unchanged)      │  ← existing component
│  calories, zone bar, macros │
├─────────────────────────────┤
│  [  87%  ] [  −0.3 kg  ]   │  ← NEW: 2 stat tiles (no streak)
│  Adherence   Trend/wk       │
├─────────────────────────────┤
│  SevenDayChartCard (unchanged)│ ← existing component
│  sparkline + stats row      │
├─────────────────────────────┤
│  ┌──────────────┐ ┌────────┐│  ← NEW: liquid glass pill buttons
│  │Daily Check-In│ │Log Food││    above nav bar
│  └──────────────┘ └────────┘│
└─────────────────────────────┘
```

## Components

### 1. Motivational Card (new)
- Style: violet-gradient `FrostedCard` (`background: LinearGradient(135°, rgba(124,58,237,0.14), rgba(109,40,217,0.06))`, `border: rgba(139,92,246,0.20)`)
- Content: single quote string from `DashboardUiState.motivationalMessage`
- Subtitle line: "Refreshes every time you open the app" in muted violet
- No interaction

### 2. Stat Tiles Row (new)
- Two tiles side-by-side using `FrostedCard`-style tile (translucent, 14dp radius)
- Tile 1: `adherencePercent` formatted as % (violet color)
- Tile 2: `weightTrendKgPerWeek` formatted as `+/−X.X kg` (red for negative/loss, matching existing `ErrorRed`)
- Labels: "Adherence" and "Trend / week"
- Data already present in `DashboardUiState` — no ViewModel changes beyond adding the message

### 3. Floating Buttons (new)
- `Box(Alignment.BottomCenter)` with `padding(bottom = FloatingNavHeight + 12.dp)` — same pattern as `BodyRecoveryScreen`
- Inner `Row` with `Arrangement.spacedBy(9.dp)`
- Left: `LiquidPrimaryButton(text = "Daily Check-In", onClick = onCheckIn, modifier = Modifier.weight(1f))`
- Right: `LiquidSecondaryButton(text = "Log Food", onClick = onLogFood, modifier = Modifier.weight(1f))`
- Shape: `Capsule()` — already baked into both button components, no override needed

### 4. Motivational Messages (new)
- 20 handpicked strings stored as a `val` constant in `DashboardViewModel` companion object
- Selected once in ViewModel `init` block: `messages.random()`
- Stored in `DashboardUiState.motivationalMessage: String`
- Changes on every cold app open (ViewModel is re-created); stable within a session

## Data Flow Changes

### `DashboardUiState`
Add one field:
```kotlin
val motivationalMessage: String = ""
```

### `DashboardViewModel`
- Add `MOTIVATIONAL_MESSAGES` list in companion object (~20 messages)
- Set `motivationalMessage = MOTIVATIONAL_MESSAGES.random()` in `buildState()` (or once in `init` — whichever is simpler; since `buildState` is called reactively, use a `val` set once in `init` and passed through)

Preferred approach: set a `private val todayMessage = MOTIVATIONAL_MESSAGES.random()` at ViewModel construction time, then include it in each `buildState` return.

### `HomeDashboardScreen` / `HomeDashboardContent`
Add two new parameters:
```kotlin
onCheckIn: () -> Unit,
onLogFood: () -> Unit,
```

### `AppNavGraph`
Wire up navigation at the call site:
```kotlin
HomeDashboardScreen(
    viewModel = ...,
    onCheckIn = { navController.navigate(TopLevelDestination.Body.route) { … } },
    onLogFood  = { navController.navigate(Routes.Food) { … } },
)
```

## Files Changed
1. `ui/dashboard/DashboardViewModel.kt` — add message list + field
2. `ui/dashboard/DashboardScreen.kt` — add motivational card, stat tiles, floating buttons; update function signatures
3. `ui/navigation/AppNavGraph.kt` — pass nav callbacks to HomeDashboardScreen

## Out of Scope
- Streak counter (explicitly removed)
- AI-generated messages (static list only — no network/Gemma)
- Any changes to TodayCard or SevenDayChartCard internals
