# Centralized Toast System — Design Spec
_Date: 2026-06-05_

## Problem

`SnackbarHost` is placed inside the `Scaffold` in `RecompApp`. The floating `LiquidBottomTabs` nav bar is layered outside the Scaffold in a separate `Box` overlay, so it always covers the snackbar. Three screens currently trigger messages that users never see.

## Approach

Custom `ToastController` backed by a coroutine `Channel`, provided via `CompositionLocal`. A `ToastOverlay` composable in the root outer `Box` (above the nav) collects from the channel and renders themed toasts.

## Data Model

```kotlin
enum class ToastType { Success, Error, Info }

data class ToastMessage(
    val text: String,
    val type: ToastType,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)
```

## Components

### `ToastController`
- Wraps a `Channel<ToastMessage>(capacity = BUFFERED)`
- `suspend fun show(message: ToastMessage)` — called by screens
- Exposes `val messages: Flow<ToastMessage>` — consumed by overlay
- Provided via `LocalToastController = staticCompositionLocalOf<ToastController>`

### `ToastOverlay`
- Composable placed in the outer `Box` in `RecompApp`, positioned above the nav bar
- Collects from `LocalToastController.current.messages`
- Manages `currentToast: ToastMessage?` state
- Renders `AnimatedVisibility` with slide-up + fade-in entrance, fade-out exit
- Auto-dismisses after 3000ms via `LaunchedEffect`

### `ToastItem` (visual style: Tinted Pill)
- Pill shape (`CornerPill = 100dp`)
- Background tint per type:
  - Success: `rgba(139,92,246,0.22)` border `rgba(139,92,246,0.45)` glow-shadow violet
  - Error: `rgba(251,113,133,0.18)` border `rgba(251,113,133,0.4)` glow-shadow red
  - Info: `rgba(255,255,255,0.08)` border `rgba(255,255,255,0.15)` no glow
- Icon (✓ / ✕ / ℹ) + message text + optional action label (violet, tappable)
- Positioned centered, 8dp above the nav pill bottom edge

## Root Changes (`RecompApp.kt`)

- Remove `LocalSnackbarHostState` and `SnackbarHostState`
- Remove `snackbarHost = { SnackbarHost(snackbarHostState) }` from `Scaffold`
- Add `val toastController = remember { ToastController() }`
- Provide via `LocalToastController provides toastController`
- Add `ToastOverlay()` in the outer `Box` above the `LiquidBottomTabs` block

## Migrated Call Sites (3 files)

| File | Before | After |
|------|--------|-------|
| `BodyRecoveryScreen.kt` | `snackbarHostState.showSnackbar("Check-in saved")` | `toastController.show(ToastMessage("Check-in saved", ToastType.Success))` |
| `PlanScreen.kt` | `snackbarHostState.showSnackbar("Plan saved")` | `toastController.show(ToastMessage("Plan saved", ToastType.Success))` |
| `FoodLibraryScreen.kt` | `snackbarHostState.showSnackbar(message)` | `toastController.show(ToastMessage(message, ToastType.Success))` |

## Files Created/Modified

- **NEW** `ui/toast/ToastMessage.kt`
- **NEW** `ui/toast/ToastController.kt`
- **NEW** `ui/toast/ToastOverlay.kt`
- **MODIFIED** `ui/RecompApp.kt` — wire up controller, add overlay, remove snackbar
- **MODIFIED** `ui/today/BodyRecoveryScreen.kt` — migrate call site
- **MODIFIED** `ui/plan/PlanScreen.kt` — migrate call site
- **MODIFIED** `ui/foodlibrary/FoodLibraryScreen.kt` — migrate call site
