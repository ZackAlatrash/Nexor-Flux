# Recipe Ingredient Amount Editing — Design Spec

**Date:** 2026-06-13
**Status:** Approved

---

## Overview

While building or editing a recipe in the **Recipe Builder**, the user can tap an ingredient row
to adjust its amount. Tapping opens a bottom sheet — the same control set used when adding a food —
where the user changes grams (or servings) and the ingredient's macros rescale live. Saving updates
that ingredient; the recipe's overall totals follow automatically.

For ingredients that lack a per-100g base (e.g. a manually-logged item pulled in from a meal, which
can't be rescaled by amount), the same sheet instead lets the user edit the raw macros (kcal/P/C/F)
directly.

This extends the existing Recipes feature (`docs/superpowers/specs/2026-06-07-recipes-design.md` and
`2026-06-13-recipe-from-meal-ai-naming-design.md`). The `RecipeBuilderViewModel` already has an
`editIngredientAt(index, updated)` method; this spec adds the editor state, actions, and UI that
drive it.

---

## User Flow

1. In the Recipe Builder, the user taps an ingredient row.
2. A bottom sheet opens for that ingredient:
   - **Scalable ingredient** (`amountGrams != null && basePer100Calories != null`): a Servings/Grams
     stepper with a live kcal/P/C/F preview. The Servings option appears only when the ingredient
     has serving info (`entryServingGrams != null`); otherwise grams only.
   - **Non-scalable ingredient** (no per-100g base): four editable macro fields (kcal/P/C/F).
3. The user adjusts the value(s); the preview/fields update live.
4. **Save** → the ingredient row updates (macros + amount), the sheet closes, and the bottom-of-screen
   recipe totals reflect the change. **Cancel/dismiss** → no change.

---

## Architecture & Components

### 1. Shared amount controls (extraction)

`AmountStepper` and `AmountPreviewStat` are currently `private` composables in
`ui/foodlibrary/FoodLibraryScreen.kt`, and `AmountMode` (`SERVINGS`/`GRAMS`) is declared in
`ui/foodlibrary/FoodLibraryViewModel.kt`. Move these three into a shared location under `ui/component`
(e.g. `ui/component/AmountControls.kt`) as public declarations, so both the add-food sheet and the new
recipe-ingredient editor use identical controls.

- `FoodLibraryScreen.kt` / `FoodLibraryViewModel.kt` update their references/imports (mechanical; no
  behavior change).
- This is a targeted extraction in service of this feature, not a broad refactor. The scaling math
  (`FoodScaling.scale`) already lives in the shared `domain/food` layer and is reused as-is.

### 2. `RecipeBuilderViewModel` — editor state + actions

Add an editor sub-state to `RecipeBuilderUiState`:

```
val ingredientEditor: IngredientEditorState? = null   // null = closed
```

`IngredientEditorState` holds:
- `index: Int` — which ingredient is being edited.
- `name: String` — ingredient name (for the sheet header).
- `scalable: Boolean` — true when the ingredient has per-100g base + amount.
- `hasServings: Boolean` — true when `entryServingGrams != null` (controls Servings toggle visibility).
- Scalable inputs: `mode: AmountMode`, `gramsInput: String`, `servingsInput: String`.
- Raw inputs: `caloriesInput: String`, `proteinInput: String`, `carbsInput: String`, `fatInput: String`.
- `preview: FoodMacros?` — live scaled macros for the scalable mode.

Actions:
- `startEditingIngredient(index)` — reads the ingredient, decides `scalable`, seeds inputs from current
  values (grams from `amountGrams`, servings from `amountGrams / entryServingGrams`, or raw macros),
  computes the initial preview, opens the sheet.
- `onEditorAmountModeChanged(mode)`, `onEditorGramsChanged(text)`, `stepEditorGrams(delta)`,
  `onEditorServingsChanged(text)`, `stepEditorServings(delta)` — update scalable inputs and recompute
  `preview` via `FoodScaling.scale(base, grams)` (servings → grams via `entryServingGrams`).
- `onEditorMacroChanged(field, text)` — update a raw macro input.
- `confirmIngredientEdit()` — build the updated `RecipeIngredientEntity`:
  - **Scalable:** `grams` = resolved grams (clamped to `FoodScaling.MIN_GRAMS`); macros from
    `FoodScaling.scale(base, grams)`; set `amountGrams = grams`, `loggedByServings = (mode == SERVINGS)`.
    `basePer100*` and serving fields unchanged.
  - **Raw:** macros parsed from the four inputs (blank → 0); `amountGrams` and base fields unchanged.
  - Calls the existing `editIngredientAt(index, updated)`, then clears `ingredientEditor`.
- `cancelIngredientEdit()` — clears `ingredientEditor` with no change.

### 3. `RecipeBuilderScreen` — UI

- `IngredientRow` becomes tappable → `viewModel.startEditingIngredient(index)`. (Keep the existing ✕
  remove button working; the row tap is separate from the remove tap.)
- Add an `IngredientAmountSheet` (a `ModalBottomSheet`) shown when `state.ingredientEditor != null`:
  - **Scalable:** Servings/Grams `SingleChoiceSegmentedButtonRow` (Servings shown only when
    `hasServings`), the shared `AmountStepper`, and a row of `AmountPreviewStat` for kcal/P/C/F.
  - **Raw:** four labeled macro text fields.
  - A primary **Save** button (`viewModel::confirmIngredientEdit`); dismiss → `cancelIngredientEdit`.

---

## Data Flow

```
tap row
  → viewModel.startEditingIngredient(index)   // VM decides scalable vs raw from ingredient fields
  → sheet renders mode; user edits → VM recomputes preview
  → Save → viewModel.confirmIngredientEdit()
      → builds updated RecipeIngredientEntity (rescaled or raw)
      → editIngredientAt(index, updated)       // existing method
      → ingredientEditor = null (sheet closes)
  → recipe totals (already derived from ingredients) update automatically
```

---

## Edge Cases

| Case | Behaviour |
|---|---|
| Grams blank / below minimum | Clamp to `FoodScaling.MIN_GRAMS`; Save disabled while the field is empty/invalid. |
| Servings mode but `entryServingGrams` null | Servings toggle not shown; grams-only for that ingredient. |
| Raw macro field blank | Treated as `0`. |
| Non-scalable ingredient | Editor opens in raw-macro mode (no grams stepper). |
| Dismiss without Save | No change to the ingredient. |
| Editing then removing the same ingredient | Remove (✕) still works; if the editor is open for a removed index, Save is a no-op guard (index out of range → ignore). |

---

## Components Touched / Added

**Added**
- `ui/component/AmountControls.kt` — public `AmountStepper`, `AmountPreviewStat`, `AmountMode` (moved
  from FoodLibrary).
- `IngredientEditorState` (in `RecipeBuilderViewModel.kt` or a small sibling file).
- `IngredientAmountSheet` composable (in `RecipeBuilderScreen.kt`).

**Modified**
- `ui/foodlibrary/FoodLibraryScreen.kt`, `ui/foodlibrary/FoodLibraryViewModel.kt` — reference the moved
  controls/enum (mechanical).
- `ui/recipes/RecipeBuilderViewModel.kt` — editor state + actions.
- `ui/recipes/RecipeBuilderScreen.kt` — tappable rows + the editor sheet.

**Reused as-is**
- `domain/food/FoodScaling.kt` (`scale`, `MIN_GRAMS`, `SERVING_STEP`), `FoodMacros`.
- `RecipeBuilderViewModel.editIngredientAt`.

---

## Testing

`RecipeBuilderViewModel` unit tests (pure, no Android):
- `startEditingIngredient` on a scalable ingredient opens grams mode (and servings available when it
  has serving info), seeded from current amount.
- Grams edit rescales macros correctly (e.g. a 200g ingredient edited to 100g halves its macros).
- Servings→grams conversion uses `entryServingGrams`.
- `startEditingIngredient` on a non-scalable ingredient opens raw-macro mode.
- Raw-macro edit updates macros as typed; blanks → 0.
- `confirmIngredientEdit` updates the ingredient via `editIngredientAt` and closes the editor;
  `cancelIngredientEdit` closes without change.
- Grams below `MIN_GRAMS` clamps.

`FoodScaling.scale` is already unit-tested; not re-tested here.

---

## Out of Scope

- No change to how recipes are saved, logged, or consumed.
- No new scaling math (reuse `FoodScaling`).
- No reordering of ingredients.
- No changes to the add-food AmountSheet behaviour (only the shared controls' location changes).
