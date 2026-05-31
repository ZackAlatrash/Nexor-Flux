# Food Action Sheets Design

**Date:** 2026-05-31  
**Status:** Approved

---

## Problem

The "Create new food", "Edit food", and "Quick add calories" actions in `FoodLibraryScreen` are buried at the bottom of a scrollable list. Users must scroll past all food items to reach them. The create/edit form expands inline inside the scroll list, which is disorienting. The quick-add uses an `AlertDialog`, which is inconsistent with the rest of the logging UX.

---

## Goal

Make all three food-action entry points immediately visible on screen (no scrolling required), and present each action as a `ModalBottomSheet` — the same sliding-panel pattern used by the food-logging amount picker (`AmountSheet`).

---

## Approved Design

### 1. Pinned action row

Two side-by-side outlined buttons inserted as a **non-lazy item just below the category filter chips** in the `LazyColumn`:

| Button | Label | Icon | Action |
|---|---|---|---|
| Primary (blue border) | `+ New food` | none | opens `CreateFoodSheet` |
| Secondary (grey border) | `⚡ Quick add` | none | opens `QuickAddSheet` |

These buttons appear immediately when the screen loads — no scrolling needed. They are part of the scroll content (not a sticky overlay), which is sufficient since they sit above the food list.

### 2. CreateFoodSheet (`ModalBottomSheet`)

Opened by:
- Tapping **"+ New food"** in the pinned row → empty form, title "New food"
- Tapping **"Edit"** on a food row → pre-filled form, title "Edit: [food name]"

**Fields (top to bottom):**
1. Name — full-width `OutlinedTextField`
2. Serving label (e.g. "100g, 1 scoop") — full-width `OutlinedTextField`
3. Row: Calories (kcal) · Protein (g)
4. Row: Carbs (g) · Fat (g)
5. Row: Serving name (opt.) · Serving grams (opt., g)
6. Full-width confirm button: "Save food" (create) or "Update food" (edit)

The sheet reuses all existing ViewModel state (`newFoodName`, `newFoodCalories`, etc.) and the `saveNewFood()` / edit logic already present in `FoodLibraryViewModel`. No ViewModel changes are needed.

Dismissing the sheet without saving discards unsaved input (same as the current Cancel behaviour).

### 3. QuickAddSheet (`ModalBottomSheet`)

Opened by tapping **"⚡ Quick add"** in the pinned row.

**Fields (top to bottom):**
1. Name — optional, full-width `OutlinedTextField`, placeholder "e.g. Snack bar"
2. Calories — full-width `NumberField`, suffix "kcal"
3. Row: Protein · Carbs · Fat (each g)
4. Full-width "Add" button

Reuses all existing ViewModel state (`quickAddName`, `quickAddCalories`, etc.) and `confirmQuickAdd()`. No ViewModel changes needed.

---

## What Is Removed

| Removed | Replaced by |
|---|---|
| `+ Create new food` `OutlinedButton` at bottom of list | Pinned `+ New food` button at top |
| `CreateFoodForm` composable expanding inline in list | `CreateFoodSheet` ModalBottomSheet |
| `AlertDialog` for quick add | `QuickAddSheet` ModalBottomSheet |

The "Save current slot as meal" button at the bottom of the list is **not affected** — it stays as-is.

The `"Quick add calories"` `OutlinedButton` at the bottom of the list is **removed**.

---

## Architecture

All changes are confined to `FoodLibraryScreen.kt`. The ViewModel (`FoodLibraryViewModel.kt`) requires **no changes** — all state flags (`showCreateFoodForm`, `showQuickAddDialog`, `editingFoodId`) and their handlers are reused as-is.

**Files changed:**
- `app/src/main/java/com/zack/recomptracker/ui/foodlibrary/FoodLibraryScreen.kt`
  - Add `CreateFoodSheet` private composable (wraps existing `CreateFoodForm` content in `ModalBottomSheet`)
  - Add `QuickAddSheet` private composable (wraps existing quick-add fields in `ModalBottomSheet`)
  - Add pinned action row `item` in `LazyColumn` (below category chips, above food list)
  - Remove inline `CreateFoodForm` expansion and its toggle button from the bottom `item`
  - Remove `AlertDialog` for quick add (replaced by `QuickAddSheet`)
  - Remove `"Quick add calories"` button from the bottom `item`

**Files unchanged:**
- `FoodLibraryViewModel.kt` — no logic changes
- `FoodLibraryUiStateTest.kt` — no test changes needed

---

## Visual Spec

The approved mockup is saved in `.superpowers/brainstorm/` for reference. Key visual details:

- **Pinned row**: `+ New food` has blue border (`0xFF3b82f6`) matching the rest of the UI; `⚡ Quick add` has grey border (`Secondary`)
- **Sheets**: use the same `ModalBottomSheet` setup as `AmountSheet` — `rememberModalBottomSheetState()`, horizontal padding 20dp, bottom padding 28dp, `Arrangement.spacedBy(16.dp)` between sections
- **Sheet titles**: bold 16sp for create/edit, regular weight subtitle in grey for supporting text
- **Confirm button**: full-width, blue `containerColor` for save actions; grey for quick-add "Add" button
- **Edit sheet title**: "Edit: [food name]" when `editingFoodId != null`

---

## Out of Scope

- Swipe-to-edit or long-press on food rows (Edit button stays as a text button in the row)
- Any changes to the food logging `AmountSheet`
- Any changes to the "Save current slot as meal" flow
- NEVO tab (action row is hidden when `category == FoodCategory.NEVO`, matching existing behaviour)
