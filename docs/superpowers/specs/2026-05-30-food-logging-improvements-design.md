# Food Logging Improvements - Design Spec
**Date:** 2026-05-30
**Scope:** Make food logging mimic the Samsung Health flow: an amount picker with a servings↔grams toggle, a +/- stepper, and a live macro preview; editable personal foods and editable logged entries; a Recents quick-access row; and a quick-add-calories one-off entry. Room remains the source of truth and the app stays local-first and offline.

---

## 1. Background

Today every food's macros are stored per 100 g but the `servingName` field is free text, so scaling is ambiguous (a food named "1 scoop" still scales as if per 100 g). Logging is grams-only through a "How many grams?" dialog. A logged `MealEntryEntity` keeps only final macros — there is no way to edit a saved food's macros, and no way to edit a logged entry (only delete and re-add).

This spec builds on the committed food-library-imports and four-tab-navigation work (Room at version 3, `catalog_foods` present, `FoodScreen` + `FoodLibraryScreen`).

---

## 2. Behavior Summary

| Feature | Behavior |
|---|---|
| Amount picker | Tapping a food opens a bottom sheet with a Servings↔Grams toggle, a +/- stepper (and editable value), and a live calorie/macro preview. Commits a scaled entry to the active slot. |
| Household servings | A personal food can carry an optional household serving (name + gram weight), e.g. "1 scoop = 30 g". Macros are always stored per 100 g. |
| Edit personal food | A personal food can be opened and its name, serving, household serving, calories, and macros changed. Updates the stored definition. |
| Edit logged entry | A logged entry can be reopened in the amount picker and its amount changed, recomputing macros. Quick-add and meal entries use a direct macro edit. |
| Recents | The Food Library shows recently logged library foods at the top for one-tap re-logging. |
| Quick add | A one-off entry of calories (required) plus optional macros and name, without creating a named food. |
| NEVO / catalog foods | Remain per-100 g, grams-only, read-only. No editing, no forking, no household serving. |
| Offline behavior | No backend, network sync, barcode scanning, or online search. |

---

## 3. Data Model

The Room database version advances from `3` to `4`. `MIGRATION_3_4` adds only nullable columns and preserves all existing user data.

### 3.1 `SavedFoodEntity` (personal foods)

Macros are **always per 100 g** (canonical base). Add an optional household serving:

```kotlin
@Serializable
@Entity(tableName = "saved_foods")
data class SavedFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servingName: String,            // legacy display label, retained for backup/JSON compatibility; no longer drives scaling
    val calories: Int,                  // per 100 g
    val proteinG: Double,               // per 100 g
    val carbsG: Double,                 // per 100 g
    val fatG: Double,                   // per 100 g
    val householdServingName: String? = null,   // e.g. "scoop", "slice"
    val householdServingGrams: Double? = null,   // e.g. 30.0
)
```

- `servingName` is no longer used to scale; it remains a free-text display label so existing rows, the full-app backup, and personal-food JSON stay compatible.
- A household serving is valid only when both `householdServingName` is non-blank and `householdServingGrams` is `>= 1`. Otherwise the food is grams-only.

### 3.2 `MealEntryEntity` (a logged item)

Snapshot the amount and per-100 g base onto the entry so it can be re-edited and re-scaled independently of the source food:

```kotlin
@Serializable
@Entity(tableName = "meal_entries", indices = [Index(value = ["date"])])
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String,               // "FOOD_LIBRARY" | "QUICK_ADD" | "SAVED" | existing values
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val slotId: Long? = null,
    val amountGrams: Double? = null,            // logged amount in grams (null for quick-add / meal entries)
    val basePer100Calories: Int? = null,        // per-100 g snapshot of the source food
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,       // household serving name at log time, so edit can default to servings mode
    val entryServingGrams: Double? = null,
)
```

**Architectural decision:** snapshot the base onto the entry rather than holding a foreign key to the food. The entry stays self-contained, survives food edits/deletion, and matches the offline/local-first design. The accepted trade-off is a few extra nullable columns and that an entry does not auto-update when the source food's definition changes later.

An entry is **amount-editable** when `amountGrams` and the four `basePer100*` values are all non-null. Otherwise (quick-add, saved-meal "Log all", legacy rows) it is **macro-editable** only.

---

## 4. Scaling (pure Kotlin)

All amount math lives in pure Kotlin so JVM tests cover it without Android.

```kotlin
data class FoodMacros(val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double)

// base = per 100 g; grams = chosen amount
fun scaleMacros(base: FoodMacros, grams: Double): FoodMacros
```

- `grams = servings * householdServingGrams` in Servings mode, or the entered value in Grams mode.
- `scale = grams / 100.0`; calories round to nearest Int; macros scale as Double.
- The live preview and the committed entry use the same function.
- Minimum amount is `1 g` (and `>= 1` serving when stepping servings; the value may also be typed).

---

## 5. Amount Picker (bottom sheet)

A bottom sheet, reused for both new logging and editing an entry.

Contents:
- Food name and a reference line: `1 {householdServingName} = {grams} g · {cal} kcal / 100 g` when a household serving exists, otherwise `{cal} kcal / 100 g`.
- A **Servings | Grams** segmented toggle.
- A **+/- stepper** with an editable numeric value. Servings step by 1 (min 1); grams are typed or stepped (min 1).
- A **live macro preview** (kcal, P, C, F) that updates on every change.
- An **Add to {slot}** (or **Save** when editing) button.

Defaults:
- New log, food has a household serving → **Servings** mode, value 1.
- New log, no household serving (e.g. NEVO) → **Grams** mode, value 100; the Servings toggle is hidden.
- Editing an entry → opens in the mode implied by `entryServingGrams` (servings if present, else grams) prefilled to the entry's amount.

Committing writes a `MealEntryEntity` with the scaled macros plus the amount and base snapshot (Section 3.2). Dismissing writes nothing.

---

## 6. Logging Flow

1. From a slot's **+ Add**, open the Food Library (existing navigation).
2. The library shows, top to bottom: a **Recents** row, personal foods, then search results (personal first, NEVO when a query is present — unchanged from the imports spec).
3. Tapping a food (personal, NEVO, or a Recents item) opens the amount picker.
4. Adjust amount → **Add to {slot}** commits the entry and returns to the library with a confirmation message.

Saved meals keep the existing **Log all** behavior (composite, logged at their stored absolute macros, not amount-scaled).

---

## 7. Editing

### 7.1 Personal food
- Personal-food rows in the Food Library gain an edit affordance (overflow / edit icon).
- It opens the create-food form, prefilled, extended with optional **household serving name** and **grams** fields.
- Saving updates the existing `SavedFoodEntity`. Catalog/NEVO rows have no edit affordance.

### 7.2 Logged entry
- Tapping a logged entry in the day's log opens an editor (today only Delete exists).
- **Amount-editable** entries open the bottom-sheet picker prefilled; changing the amount recomputes and updates the entry.
- **Macro-editable** entries (quick-add, saved-meal, legacy) open a direct macro edit (name + calories + P/C/F).
- Delete remains available.

---

## 8. Recents

- A **Recents** section at the top of the Food Library: the most recently logged distinct library foods (`mealType = "FOOD_LIBRARY"`), most recent first, capped at ~8.
- Derived from existing `meal_entries` history through a new DAO query and repository method; no new storage.
- Each Recents item carries its base snapshot, so tapping it opens the amount picker exactly like selecting the food.

**Known trade-off (snapshot-on-entry):** A Recents item is reconstructed from the *logged entry's* snapshot, and the household serving (`entryServingName`/`entryServingGrams`) is only recorded when the food was logged in **Servings** mode. So a food that has a household serving but was last logged in **Grams** mode appears in Recents as grams-only (the Servings toggle is hidden) until it is next logged in Servings mode. This is an accepted consequence of snapshotting the entry rather than holding a reference to the food. A future enhancement could fall back to the current personal-food definition (matched by name) to restore the serving.

---

## 9. Quick Add

- A **Quick add** action in the Food Library opens a small form: **Calories (required)**, optional **Protein / Carbs / Fat**, optional **name** (defaults to `"Quick add"`).
- Commits a `MealEntryEntity` with `mealType = "QUICK_ADD"`, `amountGrams = null`, and no base snapshot, into the active slot.
- Editing a quick-add entry uses the direct macro edit path (Section 7.2).

---

## 10. Dependency Wiring

Continue manual wiring in `AppContainer` (no Hilt, no networking):
- New DAO query for recents and any new `MealEntryEntity` reads/updates.
- `LogRepository` gains: update a logged entry, observe recents, update a saved food, quick-add.
- `FoodLibraryViewModel` gains amount-picker, recents, edit-food, and quick-add state and actions.
- `TodayViewModel` / `FoodScreen` gain the edit-entry entry point.

---

## 11. Error Handling

| Scenario | Behavior |
|---|---|
| Amount below 1 g (or below 1 serving via stepper) | Block commit; show an inline validation message. |
| Non-numeric amount typed | Treat as invalid; keep the previous valid value and show a message. |
| Editing a food into invalid macros / blank name | Block save; show a validation message. |
| Quick add with no calories | Block save; calories are required. |
| Editing an entry that has no base snapshot | Use the direct macro editor rather than the scaling picker. |
| NEVO food logged | Grams-only picker; never written back as a personal food. |

---

## 12. Testing

### JVM tests
- `scaleMacros` for servings and grams, including rounding and the 1 g floor.
- Servings↔grams conversion via `householdServingGrams`.
- Recents derivation (distinct, most-recent-first, cap, FOOD_LIBRARY only).
- Household-serving validity (both fields required, grams `>= 1`).
- Quick-add validation (calories required, optional macros/name).

### Instrumented Room tests
- `MIGRATION_3_4` adds the new nullable columns and preserves existing `saved_foods` and `meal_entries`.
- Logged-entry amount edit round-trip (re-scale from snapshot).
- Saved-food edit persists household serving.

### UI-state tests
- Picker mode toggle, stepper bounds, preview recompute.
- NEVO opens grams-only with the Servings toggle hidden.
- Edit-entry routes amount-editable vs macro-editable correctly.

### Build verification
```bash
./gradlew test
./gradlew assembleDebug
./gradlew connectedAndroidTest
```
`connectedAndroidTest` is required when an emulator or device is available.

---

## 13. Out Of Scope

- Barcode scanning and online/photo food recognition.
- Editing or forking NEVO/catalog foods.
- Multiple named servings per food (only one optional household serving).
- Auto-updating logged entries when a source food's definition changes.
- Any backend, account, sync, or networking.

---

## 14. Implementation Order

1. Add the pure-Kotlin `scaleMacros` / `FoodMacros` and recents-derivation logic with JVM tests.
2. Add the `SavedFoodEntity` and `MealEntryEntity` columns, `MIGRATION_3_4`, DAO queries, and repository methods with Room tests.
3. Build the bottom-sheet amount picker (new-log path) with the servings↔grams toggle, stepper, and live preview.
4. Add the Recents row to the Food Library.
5. Add Quick add.
6. Add edit-personal-food (form + household serving) and edit-logged-entry (picker reuse + macro fallback).
7. Run JVM tests, debug build, and available instrumented tests.
