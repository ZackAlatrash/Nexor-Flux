# Recipes Feature — Design Spec

**Date:** 2026-06-07
**Status:** Approved

---

## Overview

A **Recipe** is a named, reusable collection of food ingredients with per-ingredient amounts. The user creates a recipe once (e.g. "Rice Pudding" = Rice 200g + Full Fat Milk 150ml + Honey 20g) and can log it at any time with a single tap. Logging a recipe adds each ingredient as its own separate `MealEntryEntity` in the target meal slot — identical to logging each food individually.

---

## Feature Name

**Recipes.** Replaces the existing "Saved Meals" label in the Food Library's category chip row.

---

## User Flow

### Creating a recipe
1. User opens Food Library (from any meal slot's "+ Add" button, or standalone).
2. Taps the **Recipes** category chip → sees existing recipes + "+ Create Recipe" button.
3. Taps "+ Create Recipe" → navigates to **RecipeBuilderScreen** (no `recipeId`).
4. Enters a recipe name.
5. Taps "+ Add ingredient" → FoodLibraryScreen opens in **picker mode** (no slot context).
6. User searches/browses, taps a food → existing AmountSheet appears.
7. User sets grams/servings, taps "Add" → ingredient returned to RecipeBuilderScreen via `savedStateHandle`.
8. Repeat until all ingredients are added.
9. Taps **Save Recipe** → recipe persisted, navigate back.

### Editing a recipe
1. Tap ✎ on any recipe row in Food Library → RecipeBuilderScreen opens with `recipeId`.
2. Same screen as creation, pre-populated.
3. Tap an ingredient row → AmountSheet opens, amount editable.
4. Tap ✕ on ingredient → removed immediately.
5. Tap "+ Add ingredient" → same picker flow as creation.
6. Tap **Save Recipe** to persist changes, or **Delete** (top-right) to delete the recipe entirely.

### Logging a recipe
1. Tap **+** on a recipe row in Food Library (Recipes tab).
2. `RecipeRepository.logRecipe()` creates one `MealEntryEntity` per ingredient in the target slot for the selected date.
3. Toast appears: "Rice Pudding added to Breakfast (3 items)".

### Saving a slot as a recipe (upgraded "Save slot as meal")
1. In Food Library, "Save slot as meal" button (bottom of screen when a slot is active) is renamed **"Save slot as recipe"**.
2. Dialog asks for recipe name (pre-filled with slot name, unchanged behaviour).
3. On confirm: a `RecipeEntity` is created and one `RecipeIngredientEntity` is created per slot entry (preserving per-100g base data and amounts where available).
4. Legacy `SavedMealEntity` is no longer created. Existing legacy meals remain visible below recipes in the UI.

---

## Data Model

### New entities

**`RecipeEntity`** — `recipes` table
```
id:         Long   PK autoGenerate
name:       String
```

**`RecipeIngredientEntity`** — `recipe_ingredients` table
```
id:                  Long    PK autoGenerate
recipeId:            Long    FK → recipes.id
name:                String
sortOrder:           Int
calories:            Int     (scaled to stored amount)
proteinG:            Double
carbsG:              Double
fatG:                Double
amountGrams:         Double? (null for flat-macro-only ingredients)
basePer100Calories:  Int?
basePer100ProteinG:  Double?
basePer100CarbsG:    Double?
basePer100FatG:      Double?
entryServingName:    String?
entryServingGrams:   Double?
loggedByServings:    Boolean  default false
```

`sortOrder` preserves insertion order.  `amountGrams` nullable for quick-add style ingredients.  `basePer100*` mirrors `MealEntryEntity` fields so the existing AmountSheet can edit amounts without new logic.

### Domain model

```kotlin
data class RecipeWithIngredients(
    val recipe: RecipeEntity,
    val ingredients: List<RecipeIngredientEntity>,
) {
    val totalCalories: Int get() = ingredients.sumOf { it.calories }
    val totalProteinG: Double get() = ingredients.sumOf { it.proteinG }
    val totalCarbsG: Double get() = ingredients.sumOf { it.carbsG }
    val totalFatG: Double get() = ingredients.sumOf { it.fatG }
}
```

### Room migration: 6 → 7

```sql
CREATE TABLE IF NOT EXISTS recipes (
    id   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    recipeId            INTEGER NOT NULL,
    name                TEXT NOT NULL,
    sortOrder           INTEGER NOT NULL,
    calories            INTEGER NOT NULL,
    proteinG            REAL NOT NULL,
    carbsG              REAL NOT NULL,
    fatG                REAL NOT NULL,
    amountGrams         REAL,
    basePer100Calories  INTEGER,
    basePer100ProteinG  REAL,
    basePer100CarbsG    REAL,
    basePer100FatG      REAL,
    entryServingName    TEXT,
    entryServingGrams   REAL,
    loggedByServings    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId
    ON recipe_ingredients (recipeId);
```

Legacy `saved_meals` table is left untouched. No data migration required.

---

## New Files

| File | Purpose |
|------|---------|
| `data/local/entity/RecipeEntity.kt` | Room entity |
| `data/local/entity/RecipeIngredientEntity.kt` | Room entity |
| `data/local/dao/RecipeDao.kt` | All recipe + ingredient queries |
| `data/repository/RecipeRepository.kt` | Business logic: CRUD + logRecipe() |
| `ui/recipes/RecipeBuilderScreen.kt` | Create/edit recipe screen |
| `ui/recipes/RecipeBuilderViewModel.kt` | State + actions for builder |

---

## Modified Files

| File | Change |
|------|--------|
| `data/local/RecompDatabase.kt` | Version 6→7, add entities + DAOs + MIGRATION_6_7 |
| `core/AppContainer.kt` | Add `recipeRepository`; add `RecipeBuilderViewModel` to factory |
| `ui/navigation/AppNavGraph.kt` | Add `Routes.RecipeBuilder` route |
| `ui/foodlibrary/FoodLibraryViewModel.kt` | Add recipe flows; `logRecipe()`; picker mode result handler; upgrade `confirmSaveMeal()` |
| `ui/foodlibrary/FoodLibraryScreen.kt` | Recipes section in MEALS tab; recipe rows with ✎/+ buttons; picker mode `onConfirm` callback; pass `pickerMode` to barcode scanner nav; observe `scanned_food` from savedStateHandle |
| `ui/scanner/BarcodeScannerScreen.kt` | Picker mode branch: on scan success, write food to savedStateHandle and navigate back instead of logging |
| `ui/scanner/BarcodeScannerViewModel.kt` | Picker mode flag; `onPickerScanSuccess()` that emits food without logging |
| `domain/food/MealEntryTypes.kt` | Add `const val RECIPE = "RECIPE"` |

---

## RecipeDao

```kotlin
interface RecipeDao {
    fun observeAllWithIngredients(): Flow<List<RecipeWithIngredients>>
    suspend fun getAllWithIngredients(): List<RecipeWithIngredients>
    suspend fun getWithIngredients(recipeId: Long): RecipeWithIngredients?
    suspend fun insertRecipe(recipe: RecipeEntity): Long
    suspend fun updateRecipe(recipe: RecipeEntity)
    suspend fun deleteRecipeById(id: Long)
    suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long
    suspend fun updateIngredient(ingredient: RecipeIngredientEntity)
    suspend fun deleteIngredientById(id: Long)
    suspend fun deleteIngredientsByRecipeId(recipeId: Long)
    suspend fun replaceIngredients(recipeId: Long, ingredients: List<RecipeIngredientEntity>)
}
```

Implemented as a `@Dao` with `@Transaction` on the combined queries. `replaceIngredients` deletes all existing ingredients for the recipe then inserts the new list — used on save.

---

## RecipeRepository

Key methods:
- `observeAll(): Flow<List<RecipeWithIngredients>>`
- `getById(id: Long): RecipeWithIngredients?`
- `saveRecipe(name: String, ingredients: List<RecipeIngredientEntity>): Long` — insert new
- `updateRecipe(recipeId: Long, name: String, ingredients: List<RecipeIngredientEntity>)`
- `deleteRecipe(recipeId: Long)`

Logging a recipe is **not** in `RecipeRepository` — it is handled by `FoodLibraryViewModel.logRecipe()` which iterates ingredients and calls `logRepository.addMealToSlot()` for each. This avoids a cross-repository dependency.

---

## RecipeBuilderViewModel

State:
```kotlin
data class RecipeBuilderUiState(
    val recipeId: Long? = null,           // null = new recipe
    val name: String = "",
    val ingredients: List<RecipeIngredientEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
)
```

Key actions:
- `init(recipeId: Long?)` — load existing recipe or start fresh
- `onNameChanged(v: String)`
- `addIngredient(ingredient: RecipeIngredientEntity)` — called when picker returns a result
- `removeIngredientAt(index: Int)` — removes by list index (draft is an ordered list; no DB ID needed until save)
- `editIngredientAt(index: Int, updated: RecipeIngredientEntity)` — updates amount in draft
- `save()` — calls `recipeRepository.saveRecipe()` or `updateRecipe()`, then navigates back
- `delete()` — calls `recipeRepository.deleteRecipe()`, navigates back

**Draft ingredient identification:** the builder holds a `List<RecipeIngredientEntity>` as the in-memory draft. Before saving, ingredients have `id = 0`. Operations use list index — no stable DB id needed until `save()` persists the full list via `recipeDao.replaceIngredients()`.

`addIngredient` reads the result from `savedStateHandle["picked_ingredient"]` (set by FoodLibraryScreen when picker mode confirms). The ViewModel observes this key via a `LaunchedEffect` in the Screen, or directly in `init` via `savedStateHandle.getStateFlow()`.

---

## FoodLibraryScreen — Picker Mode

**New parameter:** `pickerMode: Boolean` (default `false`).

When `pickerMode = true`:
- Top bar title changes to "Add Ingredient"
- The existing camera/scan button remains visible and navigates to `BarcodeScannerScreen` with `pickerMode = true`
- Confirming an amount in AmountSheet calls `onIngredientPicked(RecipeIngredientEntity)` instead of `logRepository.addMealToSlot()`
- `onIngredientPicked` writes the result to `navController.previousBackStackEntry?.savedStateHandle?.set("picked_ingredient", ingredient)` then navigates back

When `pickerMode = false` (default): behaviour unchanged.

### Barcode scan in picker mode

`Routes.barcodeScanner()` gains a `pickerMode: Boolean` parameter. When `pickerMode = true`:

- `BarcodeScannerScreen` scans a food and, instead of logging it, writes it to `navController.previousBackStackEntry?.savedStateHandle?.set("scanned_food", food)` and navigates back to `FoodLibraryScreen`.
- `FoodLibraryScreen` (in picker mode) observes `savedStateHandle["scanned_food"]` and immediately opens the AmountSheet for the scanned food — same path as tapping a food from the list.
- The user sets the amount → confirms → ingredient returned to RecipeBuilderScreen via `"picked_ingredient"`.

This means the full flow "scan barcode → set amount → add to recipe" works with no new screens, just the same picker mode flag threaded through two levels.

**Modified files for this addition:**
- `ui/navigation/AppNavGraph.kt` — `pickerMode` added to the barcode scanner route arguments
- `ui/scanner/BarcodeScannerScreen.kt` / `BarcodeScannerViewModel.kt` — picker mode branch on scan success
- `ui/foodlibrary/FoodLibraryScreen.kt` — pass `pickerMode` to barcode scanner nav call; observe `scanned_food` from savedStateHandle

---

## Navigation

New route: `Routes.RecipeBuilder = "recipe_builder?recipeId={recipeId}"`

```kotlin
// Navigate to create
navController.navigate(Routes.RecipeBuilder)   // no recipeId

// Navigate to edit
navController.navigate("recipe_builder?recipeId=$id")

// Navigate to ingredient picker from RecipeBuilder
navController.navigate(Routes.FoodLibrary + "?pickerMode=true")
```

RecipeBuilderScreen reads `savedStateHandle["picked_ingredient"]` as a `LaunchedEffect` to receive ingredients from the picker.

---

## MEALS Tab Changes in FoodLibraryScreen

1. Category chip label "Saved Meals" → **"Recipes"**.
2. When Recipes or All category is active, show:
   - "+ Create Recipe" primary button (navigates to RecipeBuilder with no `recipeId`)
   - Recipes section: `GlassRecipeRow` for each `RecipeWithIngredients` — name, macro summary, ingredient count, ✎ (edit) and + (log) buttons
   - Legacy saved meals section below (faded, existing `GlassMealRow` unchanged)
3. `GlassRecipeRow` mirrors `GlassFoodRow` visually with a purple "Recipe" badge.
4. `FoodLibraryViewModel` gains:
   - `allRecipes: List<RecipeWithIngredients>` in state (observed from `RecipeRepository`)
   - `filteredRecipes` computed field (same query filter as foods)
   - `logRecipe(recipe: RecipeWithIngredients)` method
   - `confirmSaveMeal()` upgraded to create a `RecipeEntity` instead of `SavedMealEntity`

---

## Logging Behaviour

Tapping **+** on a recipe row calls `FoodLibraryViewModel.logRecipe(recipe)`:

```kotlin
fun logRecipe(recipe: RecipeWithIngredients) {
    viewModelScope.launch {
        recipe.ingredients.forEach { ingredient ->
            logRepository.addMealToSlot(
                input = MealEntryInput(
                    date = logDate,
                    mealType = MealEntryTypes.RECIPE,
                    name = ingredient.name,
                    calories = ingredient.calories,
                    proteinG = ingredient.proteinG,
                    carbsG = ingredient.carbsG,
                    fatG = ingredient.fatG,
                    amountGrams = ingredient.amountGrams,
                    basePer100Calories = ingredient.basePer100Calories,
                    basePer100ProteinG = ingredient.basePer100ProteinG,
                    basePer100CarbsG = ingredient.basePer100CarbsG,
                    basePer100FatG = ingredient.basePer100FatG,
                    entryServingName = ingredient.entryServingName,
                    entryServingGrams = ingredient.entryServingGrams,
                    loggedByServings = ingredient.loggedByServings,
                ),
                slotId = _uiState.value.slotId,
            )
        }
        val label = _uiState.value.slotId?.let { _uiState.value.slotName } ?: "log"
        _loggedEvent.emit("${recipe.recipe.name} added to $label (${recipe.ingredients.size} items)")
    }
}
```

Each ingredient becomes a `MealEntryEntity` with `mealType = MealEntryTypes.RECIPE`. Ingredients with `basePer100Calories` set are adjustable via the edit-amount flow; flat-macro ingredients fall back to `MacroEditDialog`.

---

## Legacy SavedMeals

- `saved_meals` table and `SavedMealDao` are left untouched.
- Existing `SavedMealEntity` rows remain displayed in the Food Library under a "Legacy Saved Meals" section (below recipes), visually faded.
- `GlassMealRow` and `logMeal()` in `FoodLibraryViewModel` remain unchanged — they still log a single combined entry.
- No new saved meals are created after this feature ships ("Save slot as meal" now creates a recipe). Legacy meals accumulate no further entries and will naturally disappear as users replace them with recipes.

---

## Scope — What Is NOT Included

- Scaling a recipe by number of servings (e.g. "log half the recipe") — not in V1.
- Sharing or exporting recipes — not in V1.
- Reordering ingredients within a recipe — not in V1.
- AI coach tool for logging recipes — not in V1.
