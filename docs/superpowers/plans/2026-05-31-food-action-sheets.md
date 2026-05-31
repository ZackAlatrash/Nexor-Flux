# Food Action Sheets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the buried inline create-food form and quick-add dialog with always-visible pinned action buttons that open `ModalBottomSheet` sliding panels.

**Architecture:** All changes are confined to `FoodLibraryScreen.kt`. No ViewModel changes are needed — all state flags (`showCreateFoodForm`, `showQuickAddDialog`, `editingFoodId`) and their handlers already exist. Two new private composables (`CreateFoodSheet`, `QuickAddSheet`) wrap the existing form content in `ModalBottomSheet`. A new pinned action row is inserted in the `LazyColumn` between the category chips and the food list.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 `ModalBottomSheet`, `rememberModalBottomSheetState`.

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt` | Add pinned row · Add `CreateFoodSheet` · Add `QuickAddSheet` · Remove inline form, AlertDialog, buried buttons |

---

### Task 1: Add pinned "New food / Quick add" action row

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

Context: The `LazyColumn` in `FoodLibraryScreen` currently has these items in order:
1. Header (back button + title)
2. Search field
3. Category chips (ends at line ~135)
4. Recents row (conditional)
5. Food list items
6. Meals list items
7. Bottom actions (buried "Create new food", "Quick add")

We insert a new item **after the category chips item** (after line 135, before the recents conditional).

- [ ] **Step 1: Add `BorderStroke` import**

In `FoodLibraryScreen.kt`, add this import alongside the existing imports:

```kotlin
import androidx.compose.foundation.BorderStroke
```

- [ ] **Step 2: Insert the pinned action row item in the `LazyColumn`**

Locate the category chips `item { LazyRow(...) }` block (ends around line 135 with the closing `}`). Immediately after its closing `}` and before the `if (state.recentFoods.isNotEmpty()...` block, insert:

```kotlin
        if (state.category != FoodCategory.NEVO) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::toggleCreateFoodForm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                        border = BorderStroke(1.5.dp, Blue),
                    ) {
                        Text("+ New food", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = viewModel::openQuickAdd,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text("⚡ Quick add", fontSize = 12.sp)
                    }
                }
            }
        }
```

- [ ] **Step 3: Build to confirm no compilation errors**

```
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt && git commit -m "feat: add pinned New food / Quick add action row to food library"
```

---

### Task 2: Add `CreateFoodSheet` and wire it up

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

Context: `FoodLibraryUiState.showCreateFoodForm` is already `true` when either `toggleCreateFoodForm()` (for new food) or `openEditFood(food)` (for edit) is called. `state.editingFoodId` is non-null when editing. `saveNewFood()` sets `showCreateFoodForm = false` on success, and sets `message` on validation failure (sheet stays open). Dismissing the sheet should call `toggleCreateFoodForm()` which sets `showCreateFoodForm = false` and resets all form fields.

- [ ] **Step 1: Add the `CreateFoodSheet` private composable**

Add this composable at the bottom of the file, after `AmountPreviewStat`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFoodSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = viewModel::toggleCreateFoodForm,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (state.editingFoodId != null) "Edit: ${state.newFoodName}" else "New food",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text("Macros are per 100 g", color = Secondary, fontSize = 11.sp)
            }
            OutlinedTextField(
                value = state.newFoodName,
                onValueChange = viewModel::onNewFoodNameChanged,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.newFoodServing,
                onValueChange = viewModel::onNewFoodServingChanged,
                label = { Text("Serving (e.g. 100g, 1 scoop)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Calories", state.newFoodCalories, viewModel::onNewFoodCaloriesChanged, Modifier.weight(1f), "kcal")
                NumberField("Protein", state.newFoodProtein, viewModel::onNewFoodProteinChanged, Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Carbs", state.newFoodCarbs, viewModel::onNewFoodCarbsChanged, Modifier.weight(1f), "g")
                NumberField("Fat", state.newFoodFat, viewModel::onNewFoodFatChanged, Modifier.weight(1f), "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.newFoodServingName,
                    onValueChange = viewModel::onNewFoodServingNameChanged,
                    label = { Text("Serving name (opt.)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumberField("Serving grams", state.newFoodServingGrams, viewModel::onNewFoodServingGramsChanged, Modifier.weight(1f), "g")
            }
            MessageText(state.message)
            Button(
                onClick = viewModel::saveNewFood,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
            ) {
                Text(
                    if (state.editingFoodId != null) "Update food" else "Save food",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Wire `CreateFoodSheet` into `FoodLibraryScreen`**

Locate the block after the `LazyColumn` closing brace in `FoodLibraryScreen`. It currently looks like:

```kotlin
    if (state.showAmountSheet && state.pendingFood != null) {
        AmountSheet(state = state, viewModel = viewModel)
    }

    if (state.showSaveMealDialog) {
```

Add the new sheet call between `AmountSheet` and the save-meal dialog:

```kotlin
    if (state.showAmountSheet && state.pendingFood != null) {
        AmountSheet(state = state, viewModel = viewModel)
    }

    if (state.showCreateFoodForm) {
        CreateFoodSheet(state = state, viewModel = viewModel)
    }

    if (state.showSaveMealDialog) {
```

- [ ] **Step 3: Build to confirm no compilation errors**

```
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt && git commit -m "feat: add CreateFoodSheet bottom sheet for new/edit food"
```

---

### Task 3: Add `QuickAddSheet` and replace the `AlertDialog`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

Context: The quick-add `AlertDialog` is shown when `state.showQuickAddDialog` is true (lines 244–269). We replace it with a `ModalBottomSheet`. `dismissQuickAdd()` sets `showQuickAddDialog = false`. `confirmQuickAdd()` sets `showQuickAddDialog = false` on success, or sets `message` on validation failure (sheet stays open).

- [ ] **Step 1: Add the `QuickAddSheet` private composable**

Add this composable at the bottom of the file, after `CreateFoodSheet`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissQuickAdd,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚡ Quick add", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Log calories without creating a food", color = Secondary, fontSize = 11.sp)
            }
            OutlinedTextField(
                value = state.quickAddName,
                onValueChange = viewModel::onQuickAddNameChanged,
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Calories", state.quickAddCalories, viewModel::onQuickAddCaloriesChanged, suffix = "kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Protein", state.quickAddProtein, viewModel::onQuickAddProteinChanged, Modifier.weight(1f), "g")
                NumberField("Carbs", state.quickAddCarbs, viewModel::onQuickAddCarbsChanged, Modifier.weight(1f), "g")
                NumberField("Fat", state.quickAddFat, viewModel::onQuickAddFatChanged, Modifier.weight(1f), "g")
            }
            MessageText(state.message)
            Button(
                onClick = viewModel::confirmQuickAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4b5563)),
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

- [ ] **Step 2: Replace the `AlertDialog` with `QuickAddSheet`**

Locate the `if (state.showQuickAddDialog)` block (currently an `AlertDialog`, lines 244–269). Replace the entire block:

```kotlin
    if (state.showQuickAddDialog) {
        QuickAddSheet(state = state, viewModel = viewModel)
    }
```

- [ ] **Step 3: Build to confirm no compilation errors**

```
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt && git commit -m "feat: add QuickAddSheet bottom sheet, replace AlertDialog"
```

---

### Task 4: Remove buried bottom actions and dead `CreateFoodForm` composable

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`

Context: Now that the pinned row handles "+ New food" and "⚡ Quick add", the bottom `item` block (lines 185–216) contains the now-redundant "+ Create new food" toggle button, the `CreateFoodForm` inline expansion, and the "Quick add calories" button. These must be removed. The "Save current slot as meal" button in the same block must be kept but moved to its own `item`. The `CreateFoodForm` private composable (lines 329–374) is now dead code and must be deleted.

- [ ] **Step 1: Replace the bottom `item` block**

Locate this block in the `LazyColumn`:

```kotlin
        if (state.category != FoodCategory.NEVO) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (slotId != null) {
                        OutlinedButton(
                            onClick = viewModel::openSaveMealDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenStar),
                        ) {
                            Text("Save current slot as meal")
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::toggleCreateFoodForm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text(if (state.showCreateFoodForm) "Cancel" else "+ Create new food")
                    }
                    if (state.showCreateFoodForm) {
                        CreateFoodForm(state = state, viewModel = viewModel)
                    }
                    OutlinedButton(
                        onClick = viewModel::openQuickAdd,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Secondary),
                    ) {
                        Text("Quick add calories")
                    }
                }
            }
        }
```

Replace it with:

```kotlin
        if (slotId != null) {
            item {
                OutlinedButton(
                    onClick = viewModel::openSaveMealDialog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenStar),
                ) {
                    Text("Save current slot as meal")
                }
            }
        }
```

- [ ] **Step 2: Delete the `CreateFoodForm` composable**

Delete the entire `CreateFoodForm` composable (currently around lines 329–374):

```kotlin
@Composable
private fun CreateFoodForm(state: FoodLibraryUiState, viewModel: FoodLibraryViewModel) {
    SectionCard {
        ...
    }
}
```

Delete from `@Composable` through the final closing `}`.

- [ ] **Step 3: Remove unused `AlertDialog` import if the compiler warns**

After removing the `AlertDialog` quick-add usage, check whether `AlertDialog` is still used elsewhere (it is — in `showSaveMealDialog`). So leave the import as-is.

- [ ] **Step 4: Build and run all unit tests**

```
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && ./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
cd "/Users/zackalatrash/Desktop/Personal Dietitian" && git add app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt && git commit -m "refactor: remove buried food actions, delete dead CreateFoodForm composable"
```

---

## Self-Review

**Spec coverage:**
- ✅ Pinned `+ New food` / `⚡ Quick add` row (Task 1)
- ✅ `CreateFoodSheet` ModalBottomSheet, shows for new and edit, "Edit: [name]" title (Task 2)
- ✅ `QuickAddSheet` replaces AlertDialog (Task 3)
- ✅ Inline form, buried buttons, dead composable removed (Task 4)
- ✅ "Save current slot as meal" preserved (Task 4)
- ✅ NEVO tab: pinned row hidden (Task 1 wraps in `if (state.category != FoodCategory.NEVO)`)
- ✅ ViewModel unchanged — no ViewModel tasks needed
- ✅ Edit button on food rows unchanged — triggers `openEditFood()` → `showCreateFoodForm = true` → `CreateFoodSheet` appears

**Placeholder scan:** None found. All code is complete.

**Type consistency:**
- `viewModel::toggleCreateFoodForm` — used in pinned row (Task 1) and as `onDismissRequest` in `CreateFoodSheet` (Task 2). Consistent.
- `state.showCreateFoodForm` — gates `CreateFoodSheet` in screen (Task 2). Consistent with ViewModel.
- `state.showQuickAddDialog` — gates `QuickAddSheet` in screen (Task 3). Consistent with ViewModel.
