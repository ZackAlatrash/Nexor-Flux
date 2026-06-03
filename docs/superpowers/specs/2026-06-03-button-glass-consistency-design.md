# Button Glass Consistency — Design Spec
**Date:** 2026-06-03  
**Scope:** Audit and align all action buttons to the app's Liquid Glass design system

---

## Background

The app has a fully implemented Liquid Glass button system (`LiquidPrimaryButton`, `LiquidSecondaryButton`, `LiquidActionButton`, `LiquidGlassButton`, `LiquidStepButton`). Most screens already use it correctly, but four locations still use pre-glass patterns (solid fill box, ghost border box, Material3 `TextButton`).

## Out of Scope

- `AlertDialog` confirm/dismiss buttons — keep as Material3 `TextButton` (standard Android dialog pattern)
- `IconButton` back/calendar navigation — keep as Material3 `IconButton`
- Text links ("Reorder / Done") — keep as plain clickable text
- Navigation rows (`HistoryButton`, `MenuRow`) — keep as card/list-item nav pattern
- Filter chips / recent food chips — keep as styled `Box` chips
- `SegmentedButton` (scanner unit picker, food library filter) — keep as Material3 component

## Changes

### 1. FoodScreen — "＋ Add" slot button
**File:** `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`  
**Function:** `LockedSlotCard`

| | Before | After |
|---|---|---|
| Component | `Box` with `background(Violet500)` | `LiquidActionButton` |
| Style | Solid paint fill | Tinted glass pill |
| `isPrimary` | — | `true` |
| Text | "＋ Add" | "＋ Add" |

### 2. FoodScreen — "Add meal slot" full-width box
**File:** `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`  
**Location:** Bottom of main `LazyColumn`, between meal slots and bottom spacer

| | Before | After |
|---|---|---|
| Component | `Box` with ghost border + `clickable` | `LiquidSecondaryButton` |
| Style | Ghost/outline bordered box | Clear glass pill |
| Width | `fillMaxWidth` | `fillMaxWidth` (default) |
| Text | "+ Add meal slot" (as separate `Text` composables) | `text = "+ Add meal slot"` |

### 3. FoodsScreen — "Add" TextButton in SavedFoodRow
**File:** `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt`  
**Function:** `SavedFoodRow`

| | Before | After |
|---|---|---|
| Component | `TextButton` | `LiquidActionButton` |
| `isPrimary` | — | `true` |
| Text | "Add" | "Add" |

### 4. FoodsScreen — "Delete" TextButton in SavedFoodRow + SavedMealRow
**File:** `app/src/main/java/com/zack/recomptracker/ui/foods/FoodsScreen.kt`  
**Functions:** `SavedFoodRow`, `SavedMealRow`

| | Before | After |
|---|---|---|
| Component | `TextButton` | `LiquidActionButton` |
| `isPrimary` | — | `false` (clear glass, signals lower hierarchy) |
| Text | "Delete" | "Delete" |

## Style Hierarchy Reference

| Component | Use case | Tinted? | Width |
|---|---|---|---|
| `LiquidPrimaryButton` | Main CTA per form/screen | Violet | Full |
| `LiquidSecondaryButton` | Secondary/cancel actions | Clear | Full |
| `LiquidActionButton(isPrimary = true)` | Inline primary in list rows | Violet tint | Compact |
| `LiquidActionButton(isPrimary = false)` | Inline secondary/destructive | Clear | Compact |
| `LiquidGlassButton` | Icon+text flexible content | Custom | Any |
| `LiquidStepButton` | +/− stepper | Clear | 32dp |

## Unchanged — Correct Patterns

All `LiquidPrimaryButton`, `LiquidSecondaryButton`, `LiquidActionButton`, `LiquidGlassButton`, and `LiquidStepButton` usages in the following screens are already correct and require no changes:

- `BodyCheckInForm.kt`
- `PlanScreen.kt`
- `SettingsScreen.kt`
- `BarcodeScannerScreen.kt`
- `FoodLibraryScreen.kt`
- `Components.kt` (ScoreStepper)
