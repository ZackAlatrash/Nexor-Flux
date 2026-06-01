# UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect all destructive actions with confirmation dialogs, add success feedback via Snackbar/overlays, and reduce flow friction in three specific places.

**Architecture:** Shared `ConfirmDialog` composable and semantic `MessageText` live in `Components.kt`; an app-level `SnackbarHostState` is hoisted into `RecompApp.kt` and exposed via `LocalSnackbarHostState`; ViewModels emit one-shot `SharedFlow` events for post-action feedback that screens collect and forward to the Snackbar.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Room (no DB changes needed)

---

## File Map

| File | Change |
|---|---|
| `ui/component/Components.kt` | Add `ConfirmDialog`, `MessageKind` enum, update `MessageText` |
| `ui/RecompApp.kt` | Add `SnackbarHostState`, `LocalSnackbarHostState`, wire `Scaffold` |
| `ui/today/FoodScreen.kt` | Confirm dialogs for delete entry and delete slot |
| `ui/settings/SettingsScreen.kt` | Confirm dialogs for reset logs, reset all, remove NEVO, import backup |
| `ui/scanner/BarcodeScannerViewModel.kt` | Add `ScanState.ShowingSuccess`; 800 ms auto-transition |
| `ui/scanner/BarcodeScannerScreen.kt` | Render `ShowingSuccess` overlay; add reticle |
| `ui/foodlibrary/FoodLibraryViewModel.kt` | Add `loggedEvent: SharedFlow<String>`; debounced OFF search |
| `ui/foodlibrary/FoodLibraryScreen.kt` | Collect `loggedEvent`; remove OFF search button; update placeholder |
| `ui/plan/PlanViewModel.kt` | Add `savedEvent: SharedFlow<Unit>`; emit instead of success message |
| `ui/plan/PlanScreen.kt` | Collect `savedEvent`; replace date text field with DatePickerDialog |
| `ui/today/TodayViewModel.kt` | Add `savedEvent: SharedFlow<Unit>`; emit after `saveMetrics` |
| `ui/today/BodyRecoveryScreen.kt` | Collect `savedEvent` for Snackbar |
| `ui/settings/SettingsViewModel.kt` | Add `messageKind: MessageKind` to `SettingsUiState` |
| `ui/foodlibrary/FoodLibraryViewModel.kt` | Add `messageKind: MessageKind` to `FoodLibraryUiState` |
| `ui/scanner/BarcodeScannerViewModel.kt` | Add `messageKind: MessageKind` to `BarcodeScannerUiState` |

---

## Task 1: Shared primitives — `ConfirmDialog`, `MessageKind`, semantic `MessageText`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/component/Components.kt`

- [ ] **Step 1: Add `MessageKind` enum and update `MessageText` signature**

  Open `Components.kt`. After the existing imports, add the `MessageKind` enum and replace the existing `MessageText` with an overloaded version:

  ```kotlin
  enum class MessageKind { SUCCESS, ERROR, INFO }

  @Composable
  fun MessageText(message: String?, kind: MessageKind = MessageKind.INFO, modifier: Modifier = Modifier) {
      if (!message.isNullOrBlank()) {
          val color = when (kind) {
              MessageKind.SUCCESS -> Color(0xFF34d399)
              MessageKind.ERROR -> MaterialTheme.colorScheme.error
              MessageKind.INFO -> MaterialTheme.colorScheme.primary
          }
          Text(
              text = message,
              color = color,
              style = MaterialTheme.typography.bodyMedium,
              modifier = modifier,
          )
      }
  }
  ```

  Delete the old `MessageText` function (the one that takes only `message: String?`). The new function has a default parameter so all existing call sites (`MessageText(state.message)`) continue to compile unchanged.

  Add `androidx.compose.ui.graphics.Color` to the import if not already present.

- [ ] **Step 2: Add `ConfirmDialog` composable**

  Append to `Components.kt` after `MessageText`:

  ```kotlin
  @Composable
  fun ConfirmDialog(
      title: String,
      body: String,
      confirmLabel: String = "Delete",
      isDestructive: Boolean = true,
      onConfirm: () -> Unit,
      onDismiss: () -> Unit,
  ) {
      AlertDialog(
          onDismissRequest = onDismiss,
          title = { Text(title) },
          text = { Text(body) },
          confirmButton = {
              TextButton(onClick = onConfirm) {
                  Text(
                      text = confirmLabel,
                      color = if (isDestructive) MaterialTheme.colorScheme.error
                              else MaterialTheme.colorScheme.primary,
                  )
              }
          },
          dismissButton = {
              TextButton(onClick = onDismiss) { Text("Cancel") }
          },
      )
  }
  ```

  Add the following imports if not already present:
  ```kotlin
  import androidx.compose.material3.AlertDialog
  import androidx.compose.material3.TextButton
  ```

- [ ] **Step 3: Build to verify no regressions**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/component/Components.kt
  git commit -m "feat: add ConfirmDialog and semantic MessageText with MessageKind"
  ```

---

## Task 2: App-level `SnackbarHostState` and `LocalSnackbarHostState`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt`

- [ ] **Step 1: Add imports and `LocalSnackbarHostState`**

  In `RecompApp.kt`, add these imports:

  ```kotlin
  import androidx.compose.material3.SnackbarHost
  import androidx.compose.material3.SnackbarHostState
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.staticCompositionLocalOf
  ```

  Below the existing `val LocalAppContainer = ...` line, add:

  ```kotlin
  val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
      error("No SnackbarHostState provided")
  }
  ```

- [ ] **Step 2: Wire `SnackbarHostState` into `RecompApp`**

  Inside `RecompApp`, add `val snackbarHostState = remember { SnackbarHostState() }` right after `val navController = rememberNavController()`.

  Then update the `Scaffold` call to include the snackbar host:

  ```kotlin
  Scaffold(
      modifier = Modifier.fillMaxSize(),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = { /* existing code unchanged */ },
  ) { padding ->
      CompositionLocalProvider(
          LocalAppContainer provides container,
          LocalSnackbarHostState provides snackbarHostState,
      ) {
          AppNavGraph(
              navController = navController,
              modifier = Modifier.padding(padding),
          )
      }
  }
  ```

  Also move the outer `CompositionLocalProvider(LocalAppContainer provides container)` wrapper so it wraps the entire `RecompTrackerTheme` block, OR nest the two providers as shown above. The cleanest approach: move both providers inside the single `CompositionLocalProvider` call above, removing the outer wrapper.

  The full updated `RecompApp` function body:

  ```kotlin
  @Composable
  fun RecompApp(container: AppContainer) {
      RecompTrackerTheme {
          val navController = rememberNavController()
          val snackbarHostState = remember { SnackbarHostState() }
          val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
          Scaffold(
              modifier = Modifier.fillMaxSize(),
              snackbarHost = { SnackbarHost(snackbarHostState) },
              bottomBar = {
                  NavigationBar(
                      containerColor = NavBarBg,
                      tonalElevation = 0.dp,
                  ) {
                      TopLevelDestination.entries.forEach { destination ->
                          val selected = currentRoute == destination.route
                          NavigationBarItem(
                              selected = selected,
                              onClick = {
                                  navController.navigate(destination.route) {
                                      popUpTo(TopLevelDestination.Home.route) {
                                          saveState = true
                                      }
                                      launchSingleTop = true
                                      restoreState = true
                                  }
                              },
                              icon = {
                                  Icon(
                                      imageVector = destination.icon,
                                      contentDescription = destination.label,
                                  )
                              },
                              label = {
                                  Text(
                                      text = destination.label,
                                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                  )
                              },
                              colors = NavigationBarItemDefaults.colors(
                                  indicatorColor = NavPillBg,
                                  selectedIconColor = NavBlue,
                                  selectedTextColor = NavBlue,
                                  unselectedIconColor = NavInactive,
                                  unselectedTextColor = NavInactive,
                              ),
                          )
                      }
                  }
              },
          ) { padding ->
              CompositionLocalProvider(
                  LocalAppContainer provides container,
                  LocalSnackbarHostState provides snackbarHostState,
              ) {
                  AppNavGraph(
                      navController = navController,
                      modifier = Modifier.padding(padding),
                  )
              }
          }
      }
  }
  ```

- [ ] **Step 3: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/RecompApp.kt
  git commit -m "feat: add app-level SnackbarHostState via LocalSnackbarHostState"
  ```

---

## Task 3: Delete meal entry confirmation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Add confirmation state to `SlotEntryRow`**

  In `FoodScreen.kt`, find the `SlotEntryRow` private composable. Add `var showDeleteConfirm by remember { mutableStateOf(false) }` at the top of the function.

  Change the "Delete" TextButton's `onClick` from calling `onDelete(entry.id)` directly to setting `showDeleteConfirm = true`:

  ```kotlin
  TextButton(onClick = { showDeleteConfirm = true }) {
      Text("Delete", color = DangerText, fontSize = 11.sp)
  }
  ```

- [ ] **Step 2: Show `ConfirmDialog` when state is true**

  Append to `SlotEntryRow`, after the `if (showMacroEdit)` block:

  ```kotlin
  if (showDeleteConfirm) {
      ConfirmDialog(
          title = "Delete entry?",
          body = "Remove \"${entry.name}\" from this slot?",
          confirmLabel = "Delete",
          isDestructive = true,
          onConfirm = {
              onDelete(entry.id)
              showDeleteConfirm = false
          },
          onDismiss = { showDeleteConfirm = false },
      )
  }
  ```

  Add the `ConfirmDialog` import:
  ```kotlin
  import com.zack.recomptracker.ui.component.ConfirmDialog
  ```

- [ ] **Step 3: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
  git commit -m "feat: confirm before deleting meal entry"
  ```

---

## Task 4: Delete meal slot confirmation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Add confirmation state to `EditModeSlotCard`**

  In `EditModeSlotCard`, add `var showDeleteConfirm by remember { mutableStateOf(false) }` at the top of the function.

  Change the Delete button's `onClick` from `onDelete` to `{ showDeleteConfirm = true }`:

  ```kotlin
  Button(
      onClick = { showDeleteConfirm = true },
      colors = ButtonDefaults.buttonColors(containerColor = DangerBg),
  ) { Text("Delete", fontSize = 11.sp, color = DangerText) }
  ```

- [ ] **Step 2: Show `ConfirmDialog`**

  Append after the `if (showRename)` block:

  ```kotlin
  if (showDeleteConfirm) {
      val entryCount = slotWithEntries.entries.size
      val bodyText = if (entryCount > 0)
          "\"${slotWithEntries.slot.name}\" and its $entryCount ${if (entryCount == 1) "entry" else "entries"} will be removed."
      else
          "\"${slotWithEntries.slot.name}\" will be removed."
      ConfirmDialog(
          title = "Delete slot?",
          body = bodyText,
          confirmLabel = "Delete",
          isDestructive = true,
          onConfirm = {
              onDelete()
              showDeleteConfirm = false
          },
          onDismiss = { showDeleteConfirm = false },
      )
  }
  ```

- [ ] **Step 3: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
  git commit -m "feat: confirm before deleting meal slot"
  ```

---

## Task 5: Settings destructive confirmations

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add confirmation state variables**

  In `SettingsScreen`, add four `remember` state variables at the top of the composable (after the launcher declarations):

  ```kotlin
  var showResetLogsConfirm by remember { mutableStateOf(false) }
  var showResetAllConfirm by remember { mutableStateOf(false) }
  var showRemoveNevoConfirm by remember { mutableStateOf(false) }
  var showImportConfirm by remember { mutableStateOf(false) }
  ```

- [ ] **Step 2: Guard reset and remove-NEVO buttons**

  In the Reset section, change `onClick` for both buttons:

  ```kotlin
  SectionCard("Reset") {
      OutlinedButton(
          onClick = { showResetLogsConfirm = true },
          enabled = !state.busy,
          modifier = Modifier.fillMaxWidth(),
      ) {
          Text("Reset logs only")
      }
      OutlinedButton(
          onClick = { showResetAllConfirm = true },
          enabled = !state.busy,
          modifier = Modifier.fillMaxWidth(),
      ) {
          Text("Reset all local data")
      }
  }
  ```

  In the NEVO section, change the Remove button's onClick:

  ```kotlin
  OutlinedButton(
      onClick = { showRemoveNevoConfirm = true },
      enabled = !state.busy,
      modifier = Modifier.fillMaxWidth(),
  ) {
      Text("Remove NEVO catalog")
  }
  ```

  For the import backup button, change its onClick:

  ```kotlin
  OutlinedButton(
      onClick = { showImportConfirm = true },
      enabled = !state.busy,
      modifier = Modifier.fillMaxWidth(),
  ) {
      Text("Import JSON backup")
  }
  ```

- [ ] **Step 3: Add the four `ConfirmDialog` calls**

  Before the closing brace of `SettingsScreen`, add:

  ```kotlin
  if (showResetLogsConfirm) {
      ConfirmDialog(
          title = "Reset logs?",
          body = "All food and body log entries will be deleted. Your plan, foods, and meals are kept.",
          confirmLabel = "Reset",
          isDestructive = true,
          onConfirm = { viewModel.resetLogsOnly(); showResetLogsConfirm = false },
          onDismiss = { showResetLogsConfirm = false },
      )
  }

  if (showResetAllConfirm) {
      ConfirmDialog(
          title = "Delete everything?",
          body = "All data will be permanently deleted — logs, plan, foods, and meals. This cannot be undone.",
          confirmLabel = "Delete everything",
          isDestructive = true,
          onConfirm = { viewModel.resetEverything(); showResetAllConfirm = false },
          onDismiss = { showResetAllConfirm = false },
      )
  }

  if (showRemoveNevoConfirm) {
      ConfirmDialog(
          title = "Remove NEVO catalog?",
          body = "The imported NEVO foods will be removed. You can re-import the CSV at any time.",
          confirmLabel = "Remove",
          isDestructive = true,
          onConfirm = { viewModel.removeNevoCatalog(); showRemoveNevoConfirm = false },
          onDismiss = { showRemoveNevoConfirm = false },
      )
  }

  if (showImportConfirm) {
      ConfirmDialog(
          title = "Import backup?",
          body = "This will replace all your current data with the contents of the backup file.",
          confirmLabel = "Import",
          isDestructive = false,
          onConfirm = {
              importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
              showImportConfirm = false
          },
          onDismiss = { showImportConfirm = false },
      )
  }
  ```

  Add the import:
  ```kotlin
  import com.zack.recomptracker.ui.component.ConfirmDialog
  ```

- [ ] **Step 4: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
  git commit -m "feat: add confirmation dialogs for destructive settings actions"
  ```

---

## Task 6: Scanner `ShowingSuccess` overlay + reticle

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt`

- [ ] **Step 1: Add `ScanState.ShowingSuccess` to the sealed class**

  In `BarcodeScannerViewModel.kt`, add the new state to the `ScanState` sealed class:

  ```kotlin
  sealed class ScanState {
      object Scanning : ScanState()
      object Loading : ScanState()
      data class ProductFound(
          val product: BarcodeProduct,
          val amountGrams: String = "100",
      ) : ScanState()
      object NotFound : ScanState()
      object NetworkError : ScanState()
      data class ShowingSuccess(val message: String) : ScanState()
      object Logged : ScanState()
  }
  ```

- [ ] **Step 2: Emit `ShowingSuccess` then `Logged` after confirming**

  In `confirmLog()`, replace the final `_uiState.update` line:

  ```kotlin
  // BEFORE:
  _uiState.update { it.copy(scanState = ScanState.Logged, message = "${product.name} logged.") }

  // AFTER:
  val slotLabel = state.slotName.ifBlank { "log" }
  _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Added to $slotLabel")) }
  delay(800)
  _uiState.update { it.copy(scanState = ScanState.Logged) }
  ```

  Do the same replacement in `confirmLogAndSave()`:

  ```kotlin
  // BEFORE:
  _uiState.update { it.copy(scanState = ScanState.Logged, message = "${product.name} logged and saved.") }

  // AFTER:
  val slotLabel = state.slotName.ifBlank { "log" }
  _uiState.update { it.copy(scanState = ScanState.ShowingSuccess("Saved & added to $slotLabel")) }
  delay(800)
  _uiState.update { it.copy(scanState = ScanState.Logged) }
  ```

  Add the import:
  ```kotlin
  import kotlinx.coroutines.delay
  ```

- [ ] **Step 3: Build ViewModel to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Render `ShowingSuccess` overlay in `BarcodeScannerScreen`**

  In `BarcodeScannerScreen.kt`, in the `when (val scanState = state.scanState)` block, add a branch for `ShowingSuccess`:

  ```kotlin
  is ScanState.ShowingSuccess -> {
      Box(
          Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.7f)),
          contentAlignment = Alignment.Center,
      ) {
          Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.padding(24.dp),
          ) {
              Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = null,
                  tint = Color(0xFF34d399),
                  modifier = Modifier.size(48.dp),
              )
              Text(
                  scanState.message,
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 16.sp,
              )
          }
      }
  }
  ```

  Add imports:
  ```kotlin
  import androidx.compose.material.icons.filled.CheckCircle
  import androidx.compose.foundation.layout.size
  import androidx.compose.material3.Icon
  ```

- [ ] **Step 5: Add scanning guide reticle**

  In `BarcodeScannerScreen`, inside the main `Box`, add the reticle overlay after the `CameraPreview` / black background block and before the back button — only shown when `scanState is ScanState.Scanning`:

  ```kotlin
  if (state.scanState is ScanState.Scanning) {
      Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
          Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              Box(
                  modifier = Modifier
                      .size(width = 260.dp, height = 160.dp)
                      .border(
                          width = 2.dp,
                          color = Color.White.copy(alpha = 0.8f),
                          shape = RoundedCornerShape(8.dp),
                      ),
              )
              Text(
                  "Point at a barcode",
                  color = Color.White.copy(alpha = 0.8f),
                  fontSize = 12.sp,
              )
          }
      }
  }
  ```

  Add imports:
  ```kotlin
  import androidx.compose.foundation.border
  import androidx.compose.foundation.shape.RoundedCornerShape
  import androidx.compose.foundation.layout.size
  ```

- [ ] **Step 6: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt
  git commit -m "feat: scanner shows success overlay before navigating back, adds reticle guide"
  ```

---

## Task 7: Food library logged Snackbar

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Add `loggedEvent` SharedFlow to `FoodLibraryViewModel`**

  In `FoodLibraryViewModel.kt`, add the backing field after `private val _uiState`:

  ```kotlin
  private val _loggedEvent = MutableSharedFlow<String>(replay = 0)
  val loggedEvent: SharedFlow<String> = _loggedEvent
  ```

  Add imports:
  ```kotlin
  import kotlinx.coroutines.flow.MutableSharedFlow
  import kotlinx.coroutines.flow.SharedFlow
  ```

- [ ] **Step 2: Emit on `loggedEvent` after successful logs**

  In `confirmAmount()`, in the **new entry** branch, change the `_uiState.update` to also emit:

  ```kotlin
  // After logRepository.addMealToSlot(...)
  val slotLabel = if (state.slotId != null) state.slotName else "log"
  _uiState.update { it.copy(showAmountSheet = false, pendingFood = null, message = null) }
  _loggedEvent.emit("Added ${food.name} to $slotLabel")
  ```

  In the **edit entry** branch, emit:
  ```kotlin
  _uiState.update { it.copy(showAmountSheet = false, pendingFood = null, editingEntryId = null, message = null) }
  _loggedEvent.emit("${food.name} updated")
  ```

  In `logMeal()`, replace the `_uiState.update { it.copy(message = ...) }` line:
  ```kotlin
  _uiState.update { it.copy(message = null) }
  val slotLabel = if (_uiState.value.slotId != null) _uiState.value.slotName else "log"
  _loggedEvent.emit("${meal.name} added to $slotLabel")
  ```

  In `confirmQuickAdd()`, replace success update:
  ```kotlin
  _uiState.update { it.copy(showQuickAddDialog = false, message = null) }
  _loggedEvent.emit("Quick add logged")
  ```

  Note: `_loggedEvent.emit(...)` is a suspend call — it's already inside `viewModelScope.launch { }` in each of these methods, so no extra launch is needed.

- [ ] **Step 3: Collect `loggedEvent` in `FoodLibraryScreen` and show Snackbar**

  In `FoodLibraryScreen.kt`, add at the top of the composable (after the `LaunchedEffect(Unit)` and state collection):

  ```kotlin
  val snackbarHostState = LocalSnackbarHostState.current
  LaunchedEffect(Unit) {
      viewModel.loggedEvent.collect { message ->
          snackbarHostState.showSnackbar(message)
      }
  }
  ```

  Add imports:
  ```kotlin
  import com.zack.recomptracker.ui.LocalSnackbarHostState
  import androidx.compose.runtime.LaunchedEffect
  ```

- [ ] **Step 4: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
  git commit -m "feat: show Snackbar confirmation after logging food from library"
  ```

---

## Task 8: Plan save Snackbar

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`

- [ ] **Step 1: Add `savedEvent` to `PlanViewModel`**

  In `PlanViewModel.kt`, add after `val uiState`:

  ```kotlin
  private val _savedEvent = MutableSharedFlow<Unit>(replay = 0)
  val savedEvent: SharedFlow<Unit> = _savedEvent
  ```

  Add imports:
  ```kotlin
  import kotlinx.coroutines.flow.MutableSharedFlow
  import kotlinx.coroutines.flow.SharedFlow
  ```

- [ ] **Step 2: Emit on save success instead of setting message**

  In `PlanViewModel.save()`, replace:
  ```kotlin
  _uiState.value = preferences.toUiState(message = "Plan saved.")
  ```
  with:
  ```kotlin
  _uiState.value = preferences.toUiState()
  _savedEvent.emit(Unit)
  ```

- [ ] **Step 3: Collect `savedEvent` in `PlanScreen` and show Snackbar**

  In `PlanScreen.kt`, add at the top of the composable (after `val state by viewModel...`):

  ```kotlin
  val snackbarHostState = LocalSnackbarHostState.current
  LaunchedEffect(Unit) {
      viewModel.savedEvent.collect {
          snackbarHostState.showSnackbar("Plan saved")
      }
  }
  ```

  Remove the `MessageText(state.message)` from the header section (it was the only success feedback mechanism; errors still show via `state.message` but you can keep a `MessageText(state.message, MessageKind.ERROR)` call for validation error messages):

  In the header `item`:
  ```kotlin
  item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Text("Targets and review thresholds")
          MessageText(state.message, MessageKind.ERROR)
      }
  }
  ```

  Add imports:
  ```kotlin
  import com.zack.recomptracker.ui.LocalSnackbarHostState
  import com.zack.recomptracker.ui.component.MessageKind
  import androidx.compose.runtime.LaunchedEffect
  ```

- [ ] **Step 4: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt
  git commit -m "feat: show Snackbar after saving plan, surface validation errors in red"
  ```

---

## Task 9: Body check-in save Snackbar

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt`

- [ ] **Step 1: Add `savedEvent` to `TodayViewModel`**

  In `TodayViewModel.kt`, add after `val uiState`:

  ```kotlin
  private val _savedEvent = MutableSharedFlow<Unit>(replay = 0)
  val savedEvent: SharedFlow<Unit> = _savedEvent
  ```

  Add imports:
  ```kotlin
  import kotlinx.coroutines.flow.MutableSharedFlow
  import kotlinx.coroutines.flow.SharedFlow
  ```

- [ ] **Step 2: Emit on save success**

  In `TodayViewModel.saveMetrics()`, replace the final `_uiState.update` line:
  ```kotlin
  // BEFORE:
  _uiState.update { it.copy(metricsDirty = false, message = "Metrics saved.") }

  // AFTER:
  _uiState.update { it.copy(metricsDirty = false, message = null) }
  _savedEvent.emit(Unit)
  ```

- [ ] **Step 3: Collect `savedEvent` in `BodyRecoveryScreen`**

  In `BodyRecoveryScreen.kt`, add at the top of the composable (after `val state by viewModel...`):

  ```kotlin
  val snackbarHostState = LocalSnackbarHostState.current
  LaunchedEffect(Unit) {
      viewModel.savedEvent.collect {
          snackbarHostState.showSnackbar("Check-in saved")
      }
  }
  ```

  Add imports:
  ```kotlin
  import com.zack.recomptracker.ui.LocalSnackbarHostState
  import androidx.compose.runtime.LaunchedEffect
  ```

- [ ] **Step 4: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/today/TodayViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/today/BodyRecoveryScreen.kt
  git commit -m "feat: show Snackbar after saving body check-in"
  ```

---

## Task 10: `MessageKind` error migration

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add `messageKind` to `FoodLibraryUiState`**

  In `FoodLibraryViewModel.kt`, add to `FoodLibraryUiState`:
  ```kotlin
  val messageKind: MessageKind = MessageKind.INFO,
  ```

  Add import:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageKind
  ```

  Everywhere in `FoodLibraryViewModel` where a validation error message is set (these are calls where `message = "..."` represents a failure), add `messageKind = MessageKind.ERROR`:
  - In `confirmAmount()`: `message = "Enter a valid amount (min ${FoodScaling.MIN_GRAMS.toInt()}g)."` → add `messageKind = MessageKind.ERROR`
  - In `saveNewFood()`: `message = "Fill in all fields with valid numbers."` → add `messageKind = MessageKind.ERROR`
  - In `confirmSaveMeal()`: `message = "No foods in slot to save."` → add `messageKind = MessageKind.ERROR`
  - In `confirmQuickAdd()`: `message = "Enter a calorie amount."` → add `messageKind = MessageKind.ERROR`
  - In `confirmAmount()` edit branch: `message = "Couldn't find that entry to update."` → add `messageKind = MessageKind.ERROR`

  For success messages that remain in state (like "Food saved to library.", "Food updated.", "Meal saved."), add `messageKind = MessageKind.SUCCESS`. The OFF no-results message (`"No products found for '$q'."`) stays as `INFO`.

- [ ] **Step 2: Update `MessageText` calls in `FoodLibraryScreen`**

  In `FoodLibraryScreen.kt`, find all `MessageText(state.message)` calls (in `AmountSheet`, `CreateFoodSheet`, `QuickAddSheet`). Replace each with:
  ```kotlin
  MessageText(state.message, state.messageKind)
  ```

  Add import:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageKind
  ```

- [ ] **Step 3: Add `messageKind` to `BarcodeScannerUiState`**

  In `BarcodeScannerViewModel.kt`, add to `BarcodeScannerUiState`:
  ```kotlin
  val messageKind: MessageKind = MessageKind.ERROR,
  ```

  The `message` field in `BarcodeScannerUiState` is only ever set for validation errors (`"Enter a valid amount (min 1g)."`), so the default `MessageKind.ERROR` is always correct here. No updates needed in the ViewModel body.

  Add import:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageKind
  ```

- [ ] **Step 4: Replace inline error `Text` in `BarcodeScannerScreen`**

  In `ProductFoundSheet`, replace:
  ```kotlin
  if (message != null) {
      Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
  }
  ```
  with:
  ```kotlin
  MessageText(message, MessageKind.ERROR)
  ```

  Add imports:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageText
  import com.zack.recomptracker.ui.component.MessageKind
  ```

- [ ] **Step 5: Add `messageKind` to `SettingsUiState`**

  In `SettingsViewModel.kt`, add to `SettingsUiState`:
  ```kotlin
  val messageKind: MessageKind = MessageKind.INFO,
  ```

  Add import:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageKind
  ```

  The `runBusy` private helper (line ~375 in `SettingsViewModel.kt`) is:
  ```kotlin
  private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
      _uiState.update { it.copy(busy = true, message = null) }
      runCatching { block() }
          .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage) } }
          .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.") } }
  }
  ```

  Update it to also set `messageKind`:
  ```kotlin
  private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
      _uiState.update { it.copy(busy = true, message = null, messageKind = MessageKind.INFO) }
      runCatching { block() }
          .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage, messageKind = MessageKind.SUCCESS) } }
          .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.", messageKind = MessageKind.ERROR) } }
  }
  ```

  For the `importNevoFromUri` function which uses `runCatching` directly (not `runBusy`), add `messageKind`:
  - `.onSuccess`: add `messageKind = MessageKind.SUCCESS`
  - `.onFailure`: add `messageKind = MessageKind.ERROR`

  If a `runBusyWithSummary` helper also exists in the file, apply the same pattern to it.

- [ ] **Step 6: Update `MessageText` in `SettingsScreen`**

  In `SettingsScreen.kt`, the `MessageText(state.message)` in the header item:
  ```kotlin
  MessageText(state.message, state.messageKind)
  ```

  Add import:
  ```kotlin
  import com.zack.recomptracker.ui.component.MessageKind
  ```

- [ ] **Step 7: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
  git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/scanner/BarcodeScannerScreen.kt
  git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/settings/SettingsScreen.kt
  git commit -m "feat: semantic MessageText colors — errors in red, success in green"
  ```

---

## Task 11: Phase start `DatePickerDialog`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt`

- [ ] **Step 1: Add `@OptIn` and imports**

  At the top of `PlanScreen.kt`, add the opt-in annotation to the file or to the `PlanScreen` composable:
  ```kotlin
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PlanScreen(viewModel: PlanViewModel) { ... }
  ```

  Add imports:
  ```kotlin
  import androidx.compose.material3.DatePicker
  import androidx.compose.material3.DatePickerDialog
  import androidx.compose.material3.ExperimentalMaterial3Api
  import androidx.compose.material3.rememberDatePickerState
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.CalendarToday
  import androidx.compose.material3.Icon
  import androidx.compose.material3.IconButton
  import java.time.Instant
  import java.time.ZoneOffset
  ```

- [ ] **Step 2: Add date picker state and show/hide flag**

  Inside `PlanScreen`, add after `val state by viewModel...`:

  ```kotlin
  var showDatePicker by remember { mutableStateOf(false) }
  val initialDateMillis = remember(state.maintenancePhaseStartDate) {
      state.maintenancePhaseStartDate.takeIf { it.isNotBlank() }?.let {
          runCatching {
              java.time.LocalDate.parse(it)
                  .atStartOfDay(ZoneOffset.UTC)
                  .toInstant()
                  .toEpochMilli()
          }.getOrNull()
      }
  }
  val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
  ```

- [ ] **Step 3: Replace the date `NumberField` with a read-only field + calendar icon**

  Find in the "Review rules" `SectionCard`:
  ```kotlin
  NumberField("Phase start date", state.maintenancePhaseStartDate, viewModel::updatePhaseStart, suffix = "YYYY-MM-DD")
  ```

  Replace with:
  ```kotlin
  OutlinedTextField(
      value = state.maintenancePhaseStartDate,
      onValueChange = {},
      label = { Text("Phase start date") },
      readOnly = true,
      trailingIcon = {
          IconButton(onClick = { showDatePicker = true }) {
              Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
          }
      },
      modifier = Modifier.fillMaxWidth(),
  )
  ```

  Add import:
  ```kotlin
  import androidx.compose.material3.OutlinedTextField
  ```

- [ ] **Step 4: Show the `DatePickerDialog`**

  Before the closing brace of `PlanScreen`, add:

  ```kotlin
  if (showDatePicker) {
      DatePickerDialog(
          onDismissRequest = { showDatePicker = false },
          confirmButton = {
              TextButton(onClick = {
                  val millis = datePickerState.selectedDateMillis
                  if (millis != null) {
                      val picked = Instant.ofEpochMilli(millis)
                          .atZone(ZoneOffset.UTC)
                          .toLocalDate()
                          .toString()
                      viewModel.updatePhaseStart(picked)
                  }
                  showDatePicker = false
              }) { Text("OK") }
          },
          dismissButton = {
              TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
          },
      ) {
          DatePicker(state = datePickerState)
      }
  }
  ```

  Add import:
  ```kotlin
  import androidx.compose.material3.TextButton
  ```

- [ ] **Step 5: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/plan/PlanScreen.kt
  git commit -m "feat: replace phase start date text field with DatePickerDialog"
  ```

---

## Task 12: OFF debounced auto-search

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

- [ ] **Step 1: Add `offSearchJob` and `triggerOffSearch` to `FoodLibraryViewModel`**

  In `FoodLibraryViewModel.kt`, add a Job field inside the class body (below `private var initialized`):
  ```kotlin
  private var offSearchJob: kotlinx.coroutines.Job? = null
  ```

  Add a private helper:
  ```kotlin
  private fun triggerOffSearch() {
      offSearchJob?.cancel()
      val q = _uiState.value.query.trim()
      if (q.isBlank()) {
          _uiState.update { it.copy(offSearchResults = emptyList(), offSearchLoading = false, message = null) }
          return
      }
      offSearchJob = viewModelScope.launch {
          kotlinx.coroutines.delay(600)
          searchOff()
      }
  }
  ```

- [ ] **Step 2: Call `triggerOffSearch` from `onQueryChanged` and `onCategoryChanged`**

  Replace `onQueryChanged`:
  ```kotlin
  fun onQueryChanged(q: String) {
      _uiState.update { it.copy(query = q, message = null) }
      if (_uiState.value.category == FoodCategory.OFF) {
          triggerOffSearch()
      }
  }
  ```

  Replace `onCategoryChanged`:
  ```kotlin
  fun onCategoryChanged(c: FoodCategory) {
      _uiState.update { it.copy(category = c) }
      if (c == FoodCategory.OFF && _uiState.value.query.isNotBlank()) {
          triggerOffSearch()
      }
  }
  ```

- [ ] **Step 3: Remove the search button and update placeholder in `FoodLibraryScreen`**

  In `FoodLibraryScreen.kt`, find the `OutlinedTextField` for search. Change:
  - `trailingIcon`: remove the entire `if (state.category == FoodCategory.OFF) { ... } else null` expression; set `trailingIcon = null` or simply remove the parameter.
  - Update the OFF placeholder: change `"Search Dutch products…"` to `"Type to search Dutch products…"`.

  The updated `OutlinedTextField`:
  ```kotlin
  OutlinedTextField(
      value = state.query,
      onValueChange = viewModel::onQueryChanged,
      placeholder = {
          Text(
              when (state.category) {
                  FoodCategory.NEVO -> "Search NEVO foods…"
                  FoodCategory.OFF -> "Type to search Dutch products…"
                  else -> "Search saved foods…"
              }
          )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
  )
  ```

  Remove unused imports (`Icons.Default.Search` if no longer used elsewhere in the file).

- [ ] **Step 4: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryViewModel.kt
  git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt
  git commit -m "feat: OFF tab auto-searches as you type with 600ms debounce"
  ```

---

## Task 13: Improved empty meal slot state

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

- [ ] **Step 1: Replace the plain empty-state text in `LockedSlotCard`**

  In `FoodScreen.kt`, find in `LockedSlotCard`:
  ```kotlin
  if (slotWithEntries.entries.isEmpty()) {
      Text("Empty — tap + Add", fontSize = 12.sp, color = Color(0xFF444444))
  }
  ```

  Replace with:
  ```kotlin
  if (slotWithEntries.entries.isEmpty()) {
      Column(
          modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
          Icon(
              imageVector = Icons.Default.Add,
              contentDescription = null,
              tint = Secondary,
              modifier = Modifier.size(20.dp),
          )
          Text("No items yet", fontSize = 12.sp, color = Secondary)
          Text("Tap + Add to log food", fontSize = 11.sp, color = Secondary)
      }
  }
  ```

  Add import if not present:
  ```kotlin
  import androidx.compose.foundation.layout.size
  ```

  (`Icons.Default.Add`, `Secondary`, `Column`, `Arrangement`, `Alignment` are already imported/declared in `FoodScreen.kt`.)

- [ ] **Step 2: Build to verify**

  ```bash
  ./gradlew :app:assembleDebug
  ```

  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run full unit test suite**

  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

  Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
  git commit -m "feat: improve empty meal slot state with icon and hint text"
  ```

---

## Self-Review Checklist

After writing the plan, verifying spec coverage:

| Spec requirement | Task |
|---|---|
| `ConfirmDialog` composable | Task 1 |
| Semantic `MessageText` with `MessageKind` | Task 1 |
| App-level `SnackbarHostState` + `LocalSnackbarHostState` | Task 2 |
| Delete meal entry confirmation | Task 3 |
| Delete meal slot confirmation | Task 4 |
| Reset logs confirmation | Task 5 |
| Reset all data confirmation | Task 5 |
| Remove NEVO confirmation | Task 5 |
| Import backup confirmation | Task 5 |
| Scanner `ShowingSuccess` overlay | Task 6 |
| Scanner reticle | Task 6 |
| Food library logged Snackbar | Task 7 |
| Plan save Snackbar | Task 8 |
| Body check-in save Snackbar | Task 9 |
| `messageKind` in 3 ViewModels + `MessageText` call sites | Task 10 |
| Phase start `DatePickerDialog` | Task 11 |
| OFF debounced auto-search | Task 12 |
| Empty meal slot improved state | Task 13 |
