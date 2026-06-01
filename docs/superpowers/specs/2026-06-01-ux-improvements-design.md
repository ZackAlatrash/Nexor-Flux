# UX Improvements Design

**Date:** 2026-06-01  
**Scope:** Critical destructive-action protection, feedback & confirmation, flow friction  
**Out of scope:** Visual/pattern polish (MoreScreen ListItem refactor, duplicate browse-library action)

---

## 1. Shared Primitives

### 1.1 `ConfirmDialog` composable

New composable in `ui/component/Components.kt`.

```
ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String = "Delete",
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

- When `isDestructive = true`, the confirm button uses `MaterialTheme.colorScheme.error` text color.
- Dismiss button always reads "Cancel".
- All destructive-action confirmations across the app use this composable — no per-screen inline `AlertDialog`s for these cases.

### 1.2 Semantic `MessageText`

Extend the existing `MessageText` in `ui/component/Components.kt`:

```
enum class MessageKind { SUCCESS, ERROR, INFO }

fun MessageText(message: String?, kind: MessageKind = MessageKind.INFO, modifier: Modifier = Modifier)
```

- `SUCCESS` → `Color(0xFF34d399)` (green)
- `ERROR` → `MaterialTheme.colorScheme.error` (red)
- `INFO` → `MaterialTheme.colorScheme.primary` (blue — current behavior, default)

All existing call sites continue to compile unchanged (default `INFO`). Error cases that currently hard-code `colorScheme.error` inline are migrated to `MessageKind.ERROR`.

### 1.3 App-level `SnackbarHostState`

In `RecompApp.kt`:

- Create `val snackbarHostState = remember { SnackbarHostState() }`.
- Pass it to the `Scaffold`'s `snackbarHost` slot: `snackbarHost = { SnackbarHost(snackbarHostState) }`.
- Expose it via a `CompositionLocal`:

```kotlin
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}
```

- Wrap the `NavHost` call in `CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState)`.
- Any screen can then call `LocalSnackbarHostState.current.showSnackbar("...")` inside a `LaunchedEffect` or coroutine scope without prop-drilling.

---

## 2. Destructive-Action Confirmations

Each item below adds a boolean `showConfirm` state variable at the relevant composable level. The button's `onClick` sets `showConfirm = true`. The `ConfirmDialog` is shown when true and calls the real action on confirm.

### 2.1 Delete meal entry (`FoodScreen` / `SlotEntryRow`)

- Trigger: tapping the "Delete" `TextButton` in `SlotEntryRow`.
- Dialog title: `"Delete entry?"`
- Dialog body: `"Remove ${entry.name} from this slot?"`
- Confirm label: `"Delete"`, `isDestructive = true`.
- On confirm: `onDelete(entry.id)`.

### 2.2 Delete meal slot (`FoodScreen` / `EditModeSlotCard`)

- Trigger: tapping the "Delete" button in `EditModeSlotCard`.
- Dialog title: `"Delete slot?"`
- Dialog body: `"\"${slotWithEntries.slot.name}\" and its ${slotWithEntries.entries.size} entries will be removed."`
- Confirm label: `"Delete"`, `isDestructive = true`.
- On confirm: `onDelete()`.

### 2.3 Reset logs only (`SettingsScreen`)

- Trigger: tapping "Reset logs only".
- Dialog title: `"Reset logs?"`
- Dialog body: `"All food and body log entries will be deleted. Your plan, foods, and meals are kept."`
- Confirm label: `"Reset"`, `isDestructive = true`.
- On confirm: `viewModel::resetLogsOnly`.

### 2.4 Reset all local data (`SettingsScreen`)

- Trigger: tapping "Reset all local data".
- Dialog title: `"Delete everything?"`
- Dialog body: `"All data will be permanently deleted — logs, plan, foods, and meals. This cannot be undone."`
- Confirm label: `"Delete everything"`, `isDestructive = true`.
- On confirm: `viewModel::resetEverything`.

### 2.5 Remove NEVO catalog (`SettingsScreen`)

- Trigger: tapping "Remove NEVO catalog".
- Dialog title: `"Remove NEVO catalog?"`
- Dialog body: `"The imported NEVO foods will be removed. You can re-import the CSV at any time."`
- Confirm label: `"Remove"`, `isDestructive = true`.
- On confirm: `viewModel::removeNevoCatalog`.

### 2.6 Import backup (`SettingsScreen`)

- Trigger: tapping "Import JSON backup" (before the file picker opens).
- Dialog title: `"Import backup?"`
- Dialog body: `"This will replace all your current data with the contents of the backup file."`
- Confirm label: `"Import"`, `isDestructive = false` (not a red button — it's a deliberate action, just needs acknowledgement).
- On confirm: open the `importLauncher`.

---

## 3. Feedback & Confirmation

### 3.1 Barcode scanner — success overlay before navigation

Currently `BarcodeScannerScreen` navigates back immediately when `ScanState.Logged`.

`showSnackbar` is a suspend function that blocks until dismissed — using it before `onBack()` would delay navigation by 2–4 s. Instead, use a transient success state in the ViewModel:

**ViewModel change:** Add `ScanState.ShowingSuccess(message: String)` to `BarcodeScannerUiState`. After `confirmLog()` or `confirmLogAndSave()` persists the entry, set state to `ShowingSuccess("Added to $slotName")`, then after an 800 ms delay (`delay(800)`) set state to `ScanState.Logged`.

**Screen change:** Render `ScanState.ShowingSuccess` as a centered green overlay (similar to `ScanErrorOverlay` but with a checkmark icon and the message). The existing `LaunchedEffect(state.scanState)` already triggers `onBack()` on `ScanState.Logged` — no change needed there.

### 3.2 Food library — success Snackbar after logging

After `viewModel::confirmAmount` or `viewModel::logMeal` succeeds, show a Snackbar.

In `FoodLibraryViewModel`: emit a one-shot `loggedEvent: SharedFlow<String>` (`MutableSharedFlow<String>(replay = 0)`) containing the confirmation message (e.g., `"Added Chicken breast to Lunch"`).

In `FoodLibraryScreen`: collect `loggedEvent` in a `LaunchedEffect` and call `snackbarHostState.showSnackbar(message)`.

The `AmountSheet` dismisses as it does now; the snackbar appears underneath confirming success.

### 3.3 Plan save — Snackbar on success

In `PlanViewModel`: expose `savedEvent: SharedFlow<Unit>` (`MutableSharedFlow<Unit>(replay = 0)`). After `save()` persists, emit on `savedEvent`.

In `PlanScreen`: collect `savedEvent` in a `LaunchedEffect(Unit)` and call `snackbarHostState.showSnackbar("Plan saved")`.

Remove the current `MessageText(state.message)` from the Plan header (it was the only feedback mechanism).

### 3.4 Body check-in save — Snackbar on success

Same pattern as Plan save. `TodayViewModel` exposes `savedEvent: SharedFlow<Unit>` (`MutableSharedFlow<Unit>(replay = 0)`). `saveMetrics()` emits on it after persisting.

`BodyRecoveryScreen` collects it and calls `snackbarHostState.showSnackbar("Check-in saved")`.

The existing `state.message` on the form can remain for error cases (using `MessageKind.ERROR`).

### 3.5 `MessageText` error cases — migrate to `MessageKind.ERROR`

Audit all `MessageText` call sites that currently pass error strings:
- `BarcodeScannerScreen` (`ProductFoundSheet`) — already uses `colorScheme.error` inline; migrate to `MessageText(message, MessageKind.ERROR)`.
- `FoodLibraryScreen` (`AmountSheet`, `CreateFoodSheet`, `QuickAddSheet`) — `MessageText(state.message)` currently shows in blue even for errors; change to `MessageKind.ERROR` when the message represents a validation failure.
- `SettingsScreen` — `MessageText(state.message)` shown in the header; keep as `INFO` for success/status messages, use `ERROR` for failure.

To make the distinction: `FoodLibraryViewModel`, `BarcodeScannerViewModel`, and `SettingsViewModel` expose a `messageKind: MessageKind` alongside `message: String?` in their UI state, set appropriately when the message is assigned.

---

## 4. Flow Friction

### 4.1 Phase start date — `DatePickerDialog`

In `PlanScreen`, replace the `NumberField("Phase start date", ...)` with:

- A read-only `OutlinedTextField` showing the current date value.
- A trailing icon button (calendar icon) that opens Material 3's `DatePickerDialog`.
- On date selection, call `viewModel::updatePhaseStart` with the formatted `"YYYY-MM-DD"` string.

`PlanViewModel` continues to store the date as a string internally — no model changes needed.

### 4.2 Open Food Facts — debounced auto-search

Currently the OFF category requires pressing a Search icon button. Other categories filter reactively.

Change: in `FoodLibraryViewModel`, when `category == FoodCategory.OFF` and `query` changes, trigger `searchOff()` after a 600 ms debounce (using `debounce` on the query `StateFlow` or a `LaunchedEffect` with `delay`).

Remove the explicit Search icon button from the `OutlinedTextField`'s `trailingIcon` when category is OFF. The placeholder text changes to "Type to search Dutch products…" to set the right expectation.

The manual search is no longer needed; the debounce prevents a network call on every keystroke.

### 4.3 Barcode scanner — scanning guide reticle

In `BarcodeScannerScreen`, overlay a simple aiming box on the `CameraPreview`:

- A centered `Box` (e.g., 260 × 160 dp) with a 2 dp white border and rounded corners, positioned in the center of the camera preview.
- Below it, a small white caption: `"Point at a barcode"`.
- The reticle is purely decorative — it does not crop the scan area (ML Kit scans the full frame).
- Only shown when `scanState is ScanState.Scanning`.

### 4.4 Empty meal slot — improved empty state

In `LockedSlotCard`, replace the plain `Text("Empty — tap + Add", ...)` with a small centered column:

```
[+ icon, 20 dp, Secondary]
"No items yet"          ← 12 sp, Secondary
"Tap + Add to log food" ← 11 sp, Secondary
```

This gives the empty slot a slightly more intentional look without adding visual noise. No new component needed — inline in `LockedSlotCard`.

---

## Architecture Notes

- All `ConfirmDialog` state is local to the composable (`remember { mutableStateOf(false) }`) — no ViewModel changes needed for the confirmation gate itself.
- `SnackbarHostState` lives in `RecompApp.kt` and is passed down via `LocalSnackbarHostState`. ViewModels emit one-shot events (via `SharedFlow<String>` or `SharedFlow<Unit>` with `replay = 0`) for post-action messages; screens collect them in `LaunchedEffect(Unit)`.
- The barcode scanner does **not** use the Snackbar — it uses a transient `ScanState.ShowingSuccess` overlay rendered inside the scanner screen itself, followed by an 800 ms auto-transition to `ScanState.Logged` which triggers `onBack()`.
- No new screens. No navigation changes. No database migrations.
- The `MessageKind` change is additive and backwards-compatible — all existing `MessageText(message)` call sites compile unchanged.

---

## Files Changed

| File | Change |
|---|---|
| `ui/component/Components.kt` | Add `ConfirmDialog`, add `MessageKind` enum, update `MessageText` signature |
| `ui/RecompApp.kt` | Add `SnackbarHostState`, `LocalSnackbarHostState`, wire `Scaffold` |
| `ui/today/FoodScreen.kt` | Add confirm dialogs for delete entry and delete slot |
| `ui/settings/SettingsScreen.kt` | Add confirm dialogs for reset, remove NEVO, import backup |
| `ui/scanner/BarcodeScannerScreen.kt` | Add `ScanState.ShowingSuccess` overlay rendering, add scanner reticle overlay |
| `ui/foodlibrary/FoodLibraryScreen.kt` | Collect `loggedEvent` for Snackbar; remove OFF search button |
| `ui/foodlibrary/FoodLibraryViewModel.kt` | Add `loggedEvent: SharedFlow<String>`; add `messageKind`; add debounced OFF search |
| `ui/plan/PlanScreen.kt` | Replace date text field with `DatePickerDialog` trigger; collect `savedEvent` |
| `ui/plan/PlanViewModel.kt` | Add `savedEvent: SharedFlow<Unit>` |
| `ui/today/BodyRecoveryScreen.kt` | Collect `savedEvent` for Snackbar |
| `ui/today/TodayViewModel.kt` | Add `savedEvent: SharedFlow<Unit>` for body check-in |
| `ui/settings/SettingsViewModel.kt` | Add `messageKind` to UI state |
| `ui/scanner/BarcodeScannerViewModel.kt` | Add `ScanState.ShowingSuccess`, 800 ms auto-transition to `ScanState.Logged` |
