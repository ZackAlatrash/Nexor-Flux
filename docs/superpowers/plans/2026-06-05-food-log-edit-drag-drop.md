# Food Log Edit Button & Drag-and-Drop Reordering — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain "Reorder" text button in the Food Log screen with a liquid glass "Edit" button, and replace the up/down arrow reordering with smooth drag-and-drop via a visible drag handle.

**Architecture:** Add `sh.calvin.reorderable` as the only new dependency. Wire its `rememberReorderableLazyListState` into the existing `LazyColumn` in `FoodContent`. Pass a `Modifier.draggableHandle()` from inside the `ReorderableItem` scope down into a redesigned `EditModeSlotCard` that shows a frosted glass drag handle instead of arrows. The existing `onReorderSlots(List<Long>)` ViewModel callback is unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, `sh.calvin.reorderable:reorderable:2.4.3`, Material Icons Extended (already in deps)

---

## File Map

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `reorderable` version + library alias |
| `app/build.gradle.kts` | Add `implementation(libs.reorderable)` |
| `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` | Edit button, reorderable LazyColumn, redesigned EditModeSlotCard |

No ViewModel, repository, or database changes.

---

## Task 1: Create branch + add dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feature/food-log-edit-drag-drop
```

Expected: `Switched to a new branch 'feature/food-log-edit-drag-drop'`

- [ ] **Step 2: Add version entry to libs.versions.toml**

Open `gradle/libs.versions.toml`. In the `[versions]` section, add:

```toml
reorderable = "2.4.3"
```

In the `[libraries]` section, add:

```toml
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

> **Note:** Verify the latest stable version at https://github.com/Calvin-LL/Reorderable/releases before committing. Replace `2.4.3` with the latest if newer.

- [ ] **Step 3: Add implementation dependency in build.gradle.kts**

Open `app/build.gradle.kts`. In the `dependencies { }` block, add alongside the other `implementation(libs.*)` lines:

```kotlin
implementation(libs.reorderable)
```

- [ ] **Step 4: Sync and verify the build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` with no errors. If Gradle cannot resolve `sh.calvin.reorderable:reorderable:2.4.3`, check the version number at Maven Central and update `libs.versions.toml`.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add sh.calvin.reorderable dependency for drag-and-drop"
```

---

## Task 2: Update the "Edit" button

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt` (meals header row only)

- [ ] **Step 1: Replace the plain Text button with LiquidActionButton**

In `FoodScreen.kt`, find the meals header `Row` inside `FoodContent` (around line 161–183). It currently ends with:

```kotlin
Text(
    text = if (state.slotsEditMode) "Done" else "Reorder",
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    color = Color(0xB38B5CF6),
    modifier = Modifier.clickable(onClick = actions.onToggleEditMode),
)
```

Replace that `Text` with:

```kotlin
LiquidActionButton(
    text = if (state.slotsEditMode) "Done" else "Edit",
    onClick = actions.onToggleEditMode,
    isPrimary = false,
    small = true,
)
```

`LiquidActionButton` is already imported on line 67. No new import needed.

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food-log): replace Reorder text button with LiquidActionButton Edit"
```

---

## Task 3: Implement drag-and-drop reordering

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`

This task has three logical sub-parts: (a) update imports, (b) wire the reorderable `LazyColumn` in `FoodContent`, (c) rewrite `EditModeSlotCard`.

### 3a — Update imports

- [ ] **Step 1: Remove unused imports (arrow icons, IconButton)**

In `FoodScreen.kt`, remove these three lines from the imports:

```kotlin
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.IconButton
```

- [ ] **Step 2: Add new imports**

Add these imports in `FoodScreen.kt` alongside the existing import block:

```kotlin
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
```

> **Note:** `Modifier.draggableHandle()` is a member extension of `ReorderableItemScope` and is available automatically within the `ReorderableItem { }` lambda — no separate import needed.

### 3b — Wire reorderable LazyColumn in FoodContent

- [ ] **Step 3: Add reorderable state before the Box in FoodContent**

In `FoodContent`, after the `var newSlotName by remember...` line and before the `Box(modifier = modifier.fillMaxSize())` line, add:

```kotlin
val currentSlots by rememberUpdatedState(state.slots)
val currentOnReorderSlots by rememberUpdatedState(actions.onReorderSlots)

val reorderState = rememberReorderableLazyListState(
    onMove = { from, to ->
        val fromKey = from.key as Long
        val toKey   = to.key   as Long
        val ids     = currentSlots.map { it.slot.id }.toMutableList()
        val fromIdx = ids.indexOf(fromKey)
        val toIdx   = ids.indexOf(toKey)
        if (fromIdx >= 0 && toIdx >= 0) {
            ids.add(toIdx, ids.removeAt(fromIdx))
            currentOnReorderSlots(ids)
        }
    }
)
```

`rememberUpdatedState` ensures the lambda always reads the latest `state.slots` and `actions.onReorderSlots`, even if the composable recomposes between drag frames.

- [ ] **Step 4: Add `state = reorderState.listState` to LazyColumn**

Find the `LazyColumn(` call in `FoodContent`. Change it from:

```kotlin
LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
```

to:

```kotlin
LazyColumn(
    state = reorderState.listState,
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
```

- [ ] **Step 5: Wrap slot items in ReorderableItem**

Find the `items(state.slots, key = { it.slot.id }) { slotWithEntries ->` block inside the `LazyColumn`. Replace the entire block (from `items(` to its closing `}`) with:

```kotlin
items(state.slots, key = { it.slot.id }) { slotWithEntries ->
    ReorderableItem(reorderState, key = slotWithEntries.slot.id) { isDragging ->
        if (state.slotsEditMode) {
            EditModeSlotCard(
                slotWithEntries     = slotWithEntries,
                isDragging          = isDragging,
                dragHandleModifier  = Modifier.draggableHandle(),
                onRename            = { actions.onRenameSlot(slotWithEntries.slot.id, it) },
                onDelete            = { actions.onDeleteSlot(slotWithEntries.slot.id) },
            )
        } else {
            LockedSlotCard(
                slotWithEntries   = slotWithEntries,
                onAddClick        = { onAddToSlot(slotWithEntries.slot.id, slotWithEntries.slot.name) },
                onDeleteEntry     = actions.onDeleteMeal,
                onEditEntryAmount = { entryId ->
                    onEditEntryAmount(slotWithEntries.slot.id, slotWithEntries.slot.name, entryId)
                },
                onEditMacros      = actions.onEditMacros,
            )
        }
    }
}
```

`Modifier.draggableHandle()` is a member of `ReorderableItemScope` (the lambda receiver), so it compiles inside `ReorderableItem { ... }` without an extra import.

### 3c — Rewrite EditModeSlotCard

- [ ] **Step 6: Replace the EditModeSlotCard function entirely**

Find the `@Composable private fun EditModeSlotCard(` function (around line 663) and replace the **entire function** (from its annotation to its closing `}` including the two dialogs at the bottom) with the following:

```kotlin
@Composable
private fun EditModeSlotCard(
    slotWithEntries: MealSlotWithEntries,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showRename       by remember { mutableStateOf(false) }
    var renameValue      by remember(slotWithEntries.slot.id) { mutableStateOf(slotWithEntries.slot.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue    = if (isDragging) 1.02f else 1f,
        animationSpec  = tween(durationMillis = 150),
        label          = "dragScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX          = cardScale
                scaleY          = cardScale
                shadowElevation = if (isDragging) 24f else 0f
            }
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Drag handle ───────────────────────────────────────────────────────
        Box(
            modifier = dragHandleModifier
                .size(width = 32.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint               = Color(0x47FFFFFF),
                modifier           = Modifier.size(18.dp),
            )
        }

        // ── Slot info ─────────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = slotWithEntries.slot.name,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
            )
            Text(
                text     = "${slotWithEntries.entries.size} items · ${slotWithEntries.totals.calories} kcal",
                fontSize = 11.sp,
                color    = TextMuted,
            )
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1F8B5CF6))
                    .border(1.dp, Color(0x338B5CF6), RoundedCornerShape(8.dp))
                    .clickable { showRename = true; renameValue = slotWithEntries.slot.name }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Rename", fontSize = 11.sp, color = Violet400)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x1AFB7185))
                    .border(1.dp, Color(0x33FB7185), RoundedCornerShape(8.dp))
                    .clickable { showDeleteConfirm = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Delete", fontSize = 11.sp, color = ErrorRed)
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title   = { Text("Rename slot") },
            text    = {
                OutlinedTextField(
                    value         = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine    = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(renameValue); showRename = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            },
        )
    }
    if (showDeleteConfirm) {
        val entryCount = slotWithEntries.entries.size
        val bodyText   = if (entryCount > 0)
            "\"${slotWithEntries.slot.name}\" and its $entryCount ${if (entryCount == 1) "entry" else "entries"} will be removed."
        else
            "\"${slotWithEntries.slot.name}\" will be removed."
        ConfirmDialog(
            title          = "Delete slot?",
            body           = bodyText,
            confirmLabel   = "Delete",
            isDestructive  = true,
            onConfirm      = { onDelete(); showDeleteConfirm = false },
            onDismiss      = { showDeleteConfirm = false },
        )
    }
}
```

- [ ] **Step 7: Verify build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` with no errors.

Common issues and fixes:
- `Unresolved reference: rememberReorderableLazyListState` → check the library version resolved correctly; run `./gradlew :app:dependencies | grep reorderable` to confirm
- `Unresolved reference: draggableHandle` → ensure you're calling it inside the `ReorderableItem { isDragging -> ... }` lambda (it's a member of `ReorderableItemScope`)
- `Unresolved reference: DragHandle` → verify `compose.material.icons.extended` is in deps (it already is per `build.gradle.kts`)
- `Unresolved reference: graphicsLayer` → confirm `import androidx.compose.ui.graphics.graphicsLayer` was added

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food-log): drag-and-drop meal slot reordering with glass drag handle"
```

---

## Task 4: Manual smoke test

> No automated UI tests exist for this screen. Verify behavior by running the app.

- [ ] **Step 1: Build and install the debug APK**

```bash
./gradlew :app:installDebug
```

Expected: `BUILD SUCCESSFUL`, app installs on connected device/emulator.

- [ ] **Step 2: Verify the Edit button**

1. Open the app → go to the Food Log tab
2. Find the "MEALS" header row
3. Confirm the right side shows a frosted glass pill labelled **"Edit"** (not a plain text link)
4. Tap it → pill text changes to **"Done"**, meal cards switch to edit mode
5. Tap "Done" → returns to normal view

- [ ] **Step 3: Verify drag-and-drop**

1. With at least 2 meal slots present, tap **"Edit"**
2. Each edit-mode card shows a ≡ drag handle icon on the left (frosted glass box, muted white)
3. Press and hold the drag handle on any card → card lifts slightly (scale + shadow)
4. Drag up or down → cards swap in real time with a smooth animation
5. Release → reordered position persists (ViewModel updated)
6. Tap **"Done"** → locked cards appear in the new order
7. Navigate away and back → order is preserved (persisted via ViewModel/DB)

- [ ] **Step 4: Regression check**

1. In normal mode (Edit not tapped): locked slot cards look identical to before, no drag handles visible
2. "＋ Add" buttons in each slot card still work
3. "+ Add meal slot" button still works
4. Delete and Rename in edit mode still work correctly

- [ ] **Step 5: Commit smoke test results (no code change needed)**

If all checks pass, no extra commit is needed. The feature is complete on `feature/food-log-edit-drag-drop`.
