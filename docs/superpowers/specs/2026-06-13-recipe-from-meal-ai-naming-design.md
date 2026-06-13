# Recipe-from-Meal + AI Naming — Design Spec

**Date:** 2026-06-13
**Status:** Approved

---

## Overview

Two connected additions to the existing **Recipes** feature:

1. **Save-as-recipe from a meal** — from any meal slot on the food log, the user can enter a
   selection mode, tick specific food items, and save them as a reusable recipe. This complements
   the existing manual builder (Food Library → Recipes → "+ Create Recipe").
2. **AI recipe naming** — the Recipe Builder's name field gains an inline ✨ (AutoAwesome) button
   that generates a short, funny, "gym-bro / unhinged" recipe name (in the spirit of "Anabolic
   Oats") from the recipe's ingredients and macros. Tapping it again rerolls.

Neither changes how recipes are *used* (logging a recipe is unchanged) — only how they are *created*.

---

## User Flow

### Save-as-recipe from a meal
1. On the food log, a meal slot card (`LockedSlotCard`) with ≥1 entry shows a **⋯ overflow button**
   in its header (next to "＋ Add").
2. Tapping ⋯ opens a dropdown menu containing **Save as recipe**.
3. Selecting it puts **that one slot** into *recipe-selection mode*:
   - Each `SlotEntryRow` in the slot shows a checkbox (other action buttons hidden while selecting).
   - A bottom action bar appears on the slot: "**N selected**" · **Save as recipe** · **Cancel**.
   - Other slots remain in their normal state.
4. The user ticks the items they want. **Save as recipe** is disabled while 0 are selected.
5. Tapping **Save as recipe** navigates to the **Recipe Builder**, pre-filled with the selected
   items as ingredients (and an empty name). Cancel exits selection mode with no navigation.

### AI naming (Recipe Builder, both creation paths)
1. The "Recipe name" field has an inline trailing ✨ button (the app's `Icons.Default.AutoAwesome`,
   the same icon used for Coach).
2. Tapping ✨ generates a name from the current ingredients + macro totals and fills the field.
3. Tapping ✨ again **rerolls** — overwrites with a fresh name, no confirmation.
4. The user can freely edit the generated name by hand before saving.

---

## UI Design

### Meal slot card (selection affordances)
- **Overflow button:** a 28dp glass icon button (`⋯`) in the slot header, matching the existing
  header icon-button styling. Only shown for slots with ≥1 entry. Future slot actions ("Clear
  slot", etc.) can join the same menu.
- **Selection mode:** rows render a leading checkbox (violet accent when checked, per
  `LocalAppAccent`). The bottom action bar uses the accent-tinted surface; **Save as recipe** is a
  primary action, **Cancel** a muted secondary.

### Recipe Builder screen
- The screen's cards are upgraded from the current flat `CardSurface` look to the app's
  **liquid-glass** treatment (translucent white surface + accent glow), consistent with the Today
  card and Weekly Review pill. Applies to the name field container and the ingredient list.
- **AI name field states:**
  - **Empty / idle:** ghost ✨ button (tinted outline) inside the trailing edge of the field.
  - **Generating:** ✨ replaced by an animated dot indicator; field input disabled.
  - **Filled:** solid gradient ✨ button; tapping rerolls.
  - **AI unavailable:** ✨ button **hidden** entirely — the field looks like a plain text field.
  - **0 ingredients:** ✨ visible but **disabled** (nothing to name yet).

---

## Data Flow & Handoff

### MealEntry → RecipeIngredient mapping
`MealEntryEntity` and `RecipeIngredientEntity` share nearly identical fields. The conversion is a
pure, direct field map:

| RecipeIngredientEntity | from MealEntryEntity |
|---|---|
| `name` | `name` |
| `calories`, `proteinG`, `carbsG`, `fatG` | same |
| `amountGrams` | `amountGrams` |
| `basePer100Calories/ProteinG/CarbsG/FatG` | same |
| `entryServingName`, `entryServingGrams`, `loggedByServings` | same |
| `sortOrder` | list index of the selection |
| `id`, `recipeId` | `0` (assigned on save) |

This lives as a pure function in the **data layer** (e.g. a `MealToRecipe.kt` mapping file or a
`RecipeRepository` helper) — it references Room entities, so it cannot sit in the Android-import-free
`domain/` layer. The function itself is pure and unit-testable.

### Passing selected items into the Recipe Builder
Reuse the existing pattern: the Recipe Builder already accepts a single picked ingredient via a
`pickedIngredientJson` nav arg decoded into `RecipeIngredientEntity`. Add an optional
**`seedIngredientsJson`** nav arg carrying a JSON **array** of `RecipeIngredientEntity`. On first
composition (when `recipeId` is absent), the builder loads these as its initial ingredient list.
No new singleton/handoff store is introduced.

- `Routes.RecipeBuilder` gains an optional `seedIngredients` query parameter.
- `FoodLogViewModel` exposes the selected entries; `FoodScreen` serializes the mapped ingredients
  and triggers navigation.

---

## AI Namer Architecture

A small, isolated unit in the `ai/` layer, mirroring the existing local/cloud split used by
`RoutingInsightCoordinator`.

- **`RecipeNamer` interface:**
  - `suspend fun generate(ingredients: List<RecipeIngredientEntity>, totals: MacroTotals): Result<String>`
  - `val availability: StateFlow<Boolean>` — whether a name can currently be generated.
- **Local impl** reuses `GemmaInsightService` so it shares the engine's `inferenceLock` and never
  collides with insight-card or coach inference. It feeds a tight prompt from a new
  `RecipeNamePromptBuilder` and collects the streamed single-turn output into a final string.
- **Cloud impl** uses the cloud backend (same config/credentials path as the cloud insight
  coordinator).
- **Routing wrapper** selects local vs cloud using the **same effective-backend logic** as
  insights (CLOUD only when selected AND cloud config complete; otherwise LOCAL).
- **Availability** is derived: `true` when the effective backend is CLOUD-and-configured, OR LOCAL
  with the on-device model present/ready; otherwise `false`. The Recipe Builder ViewModel observes
  this to decide whether to show the ✨ button.

### Prompt & output handling
- **Prompt** (`RecipeNamePromptBuilder`): instruct the model to return exactly ONE short
  (2–4 word), funny, unhinged gym-bro recipe name — no quotes, no explanation, no list — given the
  ingredient names and macro totals (so it can lean into "Anabolic" for high protein, "Bulk" for
  high calories, etc.). Include 1–2 few-shot examples (e.g. "Anabolic Oats") to anchor the vibe.
  Written tightly because the on-device model is the 2B Gemma.
- **Sanitizer** (pure, unit-tested): take the first non-empty line, strip surrounding quotes/markup,
  trim, and cap length. Guards against the 2B model's known tendency to emit extra text.

---

## Edge Cases

| Case | Behaviour |
|---|---|
| AI unavailable (no cloud config AND no on-device model, or offline with cloud selected) | ✨ button hidden; field works as a plain text field. |
| Recipe has 0 ingredients | ✨ visible but disabled. |
| Generation in progress | ✨ shows dot animation; field input disabled; reroll ignored until done. |
| Generation fails / times out | Toast via the centralized toast system; name field left untouched; ✨ returns to idle. |
| Model emits extra prose / quotes / multiple lines | Sanitizer reduces to a single clean name. |
| User edits the generated name then rerolls | Reroll overwrites (by design — no confirm). |
| Selection mode + user navigates away / day changes | Selection mode is cleared. |

---

## Components Touched / Added

**Added**
- `data/` — pure `MealEntryEntity → RecipeIngredientEntity` mapper + its test.
- `ai/RecipeNamer.kt` — interface.
- `ai/` local + cloud `RecipeNamer` implementations + routing wrapper.
- `ai/RecipeNamePromptBuilder.kt` — prompt construction.
- Output sanitizer (pure function, with the namer or in domain).
- `StubRecipeNamer` (or fake) for tests.

**Modified**
- `ui/today/FoodScreen.kt` — overflow menu, selection-mode rows, bottom action bar.
- `ui/today/FoodLogViewModel.kt` — selection state (selected slot + entry IDs), toggle/clear,
  build-and-navigate.
- `ui/recipes/RecipeBuilderScreen.kt` — glass cards; AI ✨ name field with states.
- `ui/recipes/RecipeBuilderViewModel.kt` — `seedIngredientsJson` load; `RecipeNamer` integration
  (generate/reroll, availability, generating/error state).
- `ui/navigation/AppNavGraph.kt` — `seedIngredients` nav arg on `Routes.RecipeBuilder`.
- `core/AppContainer.kt` — construct/provide the `RecipeNamer` (routing wrapper).

---

## Testing

- **Mapping** `MealEntryEntity → RecipeIngredientEntity` — pure unit test (field fidelity, sortOrder).
- **Output sanitizer** — unit test (quotes, multiline, length cap, empty/garbage input).
- **`RecipeNamer` routing + availability** — unit test with fakes (mirrors `StubInsightCoordinator`).
- **`FoodLogViewModel` selection logic** — toggle, count, disable-when-empty, clear on cancel.
- **`RecipeBuilderViewModel`** — seed loading from JSON; availability gating; reroll overwrites;
  failure leaves name untouched.

---

## Out of Scope

- No change to how recipes are logged/consumed.
- No new AI backend, model, or credentials flow — reuses existing routing.
- No per-ingredient editing changes beyond what the builder already supports.
- No batch "save whole slot" shortcut (selection is always explicit).
