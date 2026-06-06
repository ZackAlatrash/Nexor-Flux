# Food Log — Edit Button & Drag-and-Drop Reordering

**Date:** 2026-06-05
**Branch:** `feature/food-log-edit-drag-drop`

## Overview

Two changes to the Food Log screen:
1. Replace the plain "Reorder" text button with a liquid glass "Edit" button.
2. Replace the up/down arrow reordering in edit mode with drag-and-drop via a visible drag handle.

## Files Affected

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `sh.calvin.reorderable` version + library alias |
| `app/build.gradle.kts` | Add `reorderable` implementation dependency |
| `app/.../today/FoodScreen.kt` | Edit button, LazyColumn reorderable wiring, EditModeSlotCard redesign |

No ViewModel changes required — `onReorderSlots(List<Long>)` is already the right interface.

## Part 1 — "Edit" Button

**Location:** Meals header row in `FoodContent` (`FoodScreen.kt` ~line 160).

**Before:** `Text("Reorder" / "Done")` with violet color and `.clickable`.

**After:** `LiquidActionButton(text = "Edit" / "Done", isPrimary = false, small = true, onClick = actions.onToggleEditMode)`.

- `isPrimary=false` → clear frosted glass pill, white text at 85% opacity — no violet
- `small=true` → 32dp height, fits cleanly in the header row alongside "MEALS" label
- Toggle text: "Edit" in normal mode, "Done" in edit mode (same logic as before)

## Part 2 — Drag-and-Drop Reordering

### Library

`sh.calvin.reorderable` — the standard Compose LazyColumn drag-and-drop library.
- Version: `2.4.3` (latest stable as of 2026-06)
- Maven: `sh.calvin.reorderable:reorderable:2.4.3`
- Provides: `rememberReorderableLazyListState`, `ReorderableItem`, `draggableHandle()`

### LazyColumn wiring (`FoodContent`)

```
val reorderState = rememberReorderableLazyListState(
    onMove = { from, to ->
        val ids = state.slots.map { it.slot.id }.toMutableList()
        ids.add(to.index - headerItemCount, ids.removeAt(from.index - headerItemCount))
        actions.onReorderSlots(ids)
    }
)
LazyColumn(state = reorderState.listState, ...) {
    // header items (WeekCalorieStrip, NutritionStrip, Meals header) come first
    items(state.slots, key = { it.slot.id }) { slotWithEntries ->
        ReorderableItem(reorderState, key = slotWithEntries.slot.id) { isDragging ->
            if (state.slotsEditMode) {
                EditModeSlotCard(
                    ...,
                    isDragging = isDragging,
                    dragHandleModifier = Modifier.draggableHandle(),
                )
            } else {
                LockedSlotCard(...)
            }
        }
    }
}
```

Note: `onMove` fires on every item swap during drag (real-time), so the ViewModel list updates live. `onReorderSlots` is already a pure reorder (no DB write on each intermediate swap — check ViewModel to confirm or debounce if needed).

### EditModeSlotCard redesign

**Remove:** Up/down `IconButton` arrows (the `Column { IconButton... IconButton... }` on the left).

**Add:** Drag handle on the left — a small frosted glass capsule with a drag grid icon.

```
// Drag handle — left side of the card
Box(
    modifier = dragHandleModifier   // .draggableHandle() passed in
        .size(width = 32.dp, height = 44.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0x14FFFFFF))  // subtle frosted surface
        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(8.dp)),
    contentAlignment = Alignment.Center,
) {
    Icon(
        imageVector = Icons.Default.DragHandle,  // or custom grid dots
        contentDescription = "Drag to reorder",
        tint = Color(0x47FFFFFF),   // TextMuted — subtle
        modifier = Modifier.size(18.dp),
    )
}
```

**Card lift effect:** When `isDragging = true`, apply `graphicsLayer { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 8.dp.toPx() }` to give visual lift feedback.

**Retained:** Rename and Delete action buttons on the right (unchanged).

### Header item count

The `LazyColumn` has 3 header items before slots (WeekCalorieStrip, NutritionStrip, Meals header row). The `onMove` callback must offset indices by 3 (or use a named constant `SLOT_LIST_HEADER_COUNT = 3`) to convert LazyColumn indices to slot list indices.

## Out of Scope

- Haptic feedback on drag start (library provides this by default via `draggableHandle`)
- Animated item entry/exit (not needed for this feature)
- Any ViewModel/data layer changes

## Success Criteria

- Tapping "Edit" shows the frosted glass pill; text toggles Edit/Done
- In edit mode, cards show a drag handle icon on the left (no up/down arrows)
- Press and hold the drag handle to initiate drag; cards animate into new positions in real time (no long-press required — the handle itself is the drag affordance)
- Releasing the handle commits the reorder via `onReorderSlots`
- In normal mode, cards look identical to today (no regression)
