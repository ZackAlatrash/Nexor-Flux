# iOS Phase 3a — Food Library, Recipe Builder, and the logging loop

**Date:** 2026-08-03 · **Status:** approved, ready for an implementation plan
**Repo:** everything lands in `~/Desktop/RecompTracker-IOS`. No Android code changes.

## Goal

**Logging becomes real.** Phase 2 can only log a meal by typing its macros; 3a lets you log from
your own food library, build recipes, and log those.

**Acceptance:** open Food Log, tap **+ Add** on a slot, find a saved food, set an amount in servings
or grams, and log it — with the day's totals recomputing from the observation. Then build a recipe
from two ingredients and log a portion of it.

## Why Phase 3 is split

Phase 3 as roadmapped is 16 screens, 12 ViewModels and ~11,500 LOC of Android — roughly seven times
Phase 2. It splits into three sub-phases, each ending in something the user can do:

| | | Outcome |
|---|---|---|
| **3a** | Food Library, Recipe Builder, three Food Log changes | log from your library |
| **3b** | Dashboard, Body/Recovery, check-in, streaks, charts, rebalance surfaces | see how you are doing |
| **3c** | Plan, Profile, Onboarding, Progress, Body history/edit, More, Appearance, Usage, Developer | set the app up from scratch |

This spec covers **3a only**. Each sub-phase gets its own spec and plan.

## Screenshots are a gate, not a habit

Phase 2's most expensive mistakes came from building UI off the Kotlin alone — the week strip was a
bar chart, not dots; the calorie card turns green on a zone hit. **The plan opens with a screenshot
list and a screen without one does not get built**, the same way Phase 1b made the backup fixture a
blocking prerequisite.

Nine screenshots are in the iOS repo's `screenshots/`, renamed by content. The six states they do
not cover were resolved by **reading the composables** — which is enough, because the design
language is now established and what remained was structure rather than appearance.

## Decisions taken in this session

| | Decision | Why |
|---|---|---|
| **D20** | **NEVO is not built on iOS.** No tab, no catalogue query, no CSV importer. | Its only fill mechanisms are the CSV import (v1.1 per D4) and a backup restore, so on a fresh install the tab is permanently empty behind a message pointing at a Settings screen that will not exist. `catalog_foods` and the backup's `catalogFoods` key **stay untouched** so Phase 1a's schema parity and Phase 1b's round-trip both hold; iOS simply never reads the table. |
| **D21** | **Open Food Facts moves to Phase 4**, with the barcode scanner. | It is a live network search, and the scanner is its natural companion. Removing it also removes 3a's only network path. |
| **D22** | **Pickers are sheets with completion closures.** Settles the open reverse-result convention. | Android's `FoodLibraryScreen(onIngredientPicked:)` is *already* a closure; the JSON encoding around it exists only because nav arguments must be strings. All four `savedStateHandle` flows are outside 3a (three are Train/v1.1, one is the Phase 4 scanner), so 3a needs no general mechanism. |
| **D23** | **Dismissal uses the environment's `dismiss` action**, not a `navigateBack` flag on the model. | `RecipeBuilderViewModel` publishes `navigateBack: StateFlow<Bool>` because Compose gives it no upward signal. Every Phase 3 form screen would otherwise grow the same field. |

These belong in `docs/ios-port/decisions.md` when the plan lands. D22 also closes the
*"Replacement for the 4 `savedStateHandle` reverse-result flows"* item — and corrects it: **three**
are Train-only, not two, the fourth being the scanner.

## Scope

**In:** Food Library with four filter chips (All · Proteins · Carbs · Recipes), search over personal
foods and recipes, Recents, and the three action buttons · the amount sheet with servings/grams ·
the food editor (new and edit) · quick add · the recipe portion sheet · the meal-impact strip ·
Recipe Builder with its two-mode ingredient editor · and three Food Log changes: **+ Add** opens the
Library, slot selection seeds a recipe, and the reconcile banner.

**Out:** NEVO (D20) · Open Food Facts and the camera button (D21, Phase 4) · the ✨ recipe namer
(Phase 5, gated on `aiAvailable`) · everything in 3b and 3c.

**Two scope calls that are not arbitrary:**

*The reconcile banner is pulled forward from Phase 2's deferred list.* Once the Library can log onto
a future date it **creates planned entries** (`planned = logDate > today`). Without the banner a user
could create a plan with no way to confirm it. Confirm-all and confirm-one only; postpone and the
stale-plan nudge stay deferred.

*Slot selection is pulled forward for the same reason.* It is the second entry point into Recipe
Builder — Android seeds the builder from a Food Log slot — so without it the builder ships half-built.

*One Phase 2 defect is fixed here rather than left.* `FoodLogModel.today` is frozen at `init`;
Android advances it through `dateProvider.todayFlow()`. A tab left open across midnight keeps a stale
"today", which breaks `isToday`, the week strip's right-hand end, and the ±30-day clamp. 3a makes it
materially worse — planned entries are keyed on *future* dates, so a stale today mis-classifies a
plan as eaten. Small fix, and it belongs with the feature that raises the stakes.

## Architecture

```
Features/
  FoodLibrary/
    FoodLibraryModel.swift        data, filtering, actions
    FoodLibraryScreen.swift       composition root
    LibraryRow.swift              one row, three badges
    AmountSheet.swift             + AmountDraft
    FoodEditorSheet.swift         + FoodDraft — new and edit
    QuickAddSheet.swift           moved from FoodLog/
    RecipeAmountSheet.swift       + PortionDraft
    MealImpactStrip.swift
  RecipeBuilder/
    RecipeBuilderModel.swift
    RecipeBuilderScreen.swift
    IngredientEditorSheet.swift   + IngredientDraft — two modes
  FoodLog/
    ReconcileBanner.swift         new
    (FoodLogModel, FoodLogScreen, SlotCard — modified)
Persistence/Queries/
    SavedFoodQueries.swift        new
    RecipeQueries.swift           new
```

**One model, drafts as separate value types.** Android's `FoodLibraryUiState` is 53 fields covering
the screen *and* four sheets. The **data** — foods, recipes, filters, the impact context — is
genuinely one screen's; the **drafts** are genuinely independent, each with its own `validated()`.
Phase 2 proved that half: `QuickAddDraft.validated()` is nine tests that never present a sheet, and
it is the single definition both the confirm button and the write path use.

Rejected: a model per sheet (every sheet needs the library's impact context, so it fragments what is
cohesive) and one flat model (reproduces the 53-field problem in a language without `copy()`).

**One row view, not three.** Android's food, meal and recipe rows are structurally identical — name,
optional badge, `XP YC ZF`, `N kcal`, optional pencil, `+`. They differ only in badge text (a source
label, `"Meal"`, `"Recipe"`) and whether editing is offered.

**Saved meals render but cannot be created.** `SavedMealDao` has an insert that nothing in the live
UI calls — rows arrive only from old data or a restore. Included read-only so historical data stays
reachable; no creation path, because Android has none either. (The method that looks like it creates
them, `confirmSaveMeal()`, actually saves a **recipe** — its own success message says so. The name is
a leftover from before recipes existed.)

**`QuickAddSheet` moves out of `FoodLog/` and gets two corrections the screenshots caught.** Its
**name becomes optional** — Android's subtitle is literally *"Log calories without creating a food"* —
and its three macro fields sit in one row rather than stacked. Phase 2 built it blind and required a
name; that is a real behavioural divergence, not a style difference.

**Search covers personal foods, saved meals and recipes — nothing else.** With NEVO and Open Food
Facts out, the two catalogue-backed sources are gone, which also removes two of the three empty
states: only *"No foods found."* survives. The NEVO empty state told users to import a CSV via a
Settings screen that will not exist in v1, so losing it is a gain.

**Category predicates port verbatim, asymmetry included:**

```swift
case .proteins: protein >= carbs && protein >= fat
case .carbs:    carbs >= protein && carbs >  fat   // > not >=, deliberately
```

A food with equal carbs and fat is **not** carbs. Pinned by test.

**`isDirty` is not ported.** Android sets it in five places and never reads it — there is no discard
confirmation. Recipe Builder is a pushed screen on both platforms, so iOS's interactive pop is the
equivalent of Android's back button and is no more dangerous. Revisit only if the builder ever
becomes a sheet, where swipe-dismiss would make loss materially easier.

## Behaviour that must survive

**Servings mode is conditional.** The toggle appears only when the food has
`householdServingGrams >= 1.0` **and** a non-blank serving name. Opening defaults to Servings when
that holds, Grams otherwise. On **edit**, the mode is re-derived from `loggedByServings` plus the
presence of serving grams/name, and the servings count is back-computed as
`amountGrams ÷ servingGrams`.

**Recents are reconstructed, not queried.** They come from `meal_entries` via the `basePer100*`
columns — which is what those columns are for — so a recent food carries the serving it was logged
with.

**The impact strip is today-only.** `viewingToday && !pickerMode`. On a past or future day it is
hidden, because projecting onto today's remaining plan would be misleading.

**Future dates create plans, not eaten entries**, and the confirmation wording changes with them:
*"Planned X for Wed, Aug 5"* versus *"Added X to Breakfast"*.

**The ingredient editor has two bodies**, keyed on whether the ingredient is scalable: a stepper when
it has per-100g bases, four editable macro fields when it does not.

**Already in `:shared`, not reimplemented:** `MealImpact` (the strip's arithmetic) and `FoodScaling`
(every serving/gram conversion), both golden-tested on Kotlin/Native. The largest single piece of 3a
is already done.

## Data flow

**Reads.** Five GRDB observations — saved foods, saved meals, recipes, recent entries, today's
totals — plus a one-shot read of plan targets.

**Filtering is a stored property recomputed on change, never a computed property.** A computed one
would re-filter the whole library on every render pass. It runs on the main actor, and **dropping
NEVO is why that is now safe**: Android pushes filtering to `Dispatchers.Default` because it filters
a catalogue of thousands, which 3a does not build. If it ever feels slow, the fix is a debounce on
the query, not a dispatcher.

**Writes** follow Phase 2's rule — write to GRDB, let the observation recompute, never update state
by hand on the write path. Entries carry full provenance: `basePer100*`, serving name and grams,
`loggedByServings`, and `planned`.

**The `JSONStore` change-stream debt comes due in 3c, not 3a.** Plan targets are still read once,
safely, because nothing in 3a can change them. **3c must add an `AsyncStream` to `JSONStore` before
it ships Plan or Settings**, or every screen reading targets will show a stale value.

## Error handling

Per-sheet and inline, matching Android's messages verbatim: an invalid amount
(*"Enter a valid amount (min 1g)."*), a missing entry on edit
(*"Couldn't find that entry to update."*), and an empty slot on save-as-recipe
(*"No foods in slot to save."*). A failed save keeps the sheet open with what the user typed.

## Testing

Four `validated()` suites with no UI. The category predicates including the asymmetry. The
servings/grams derivation on edit, both directions. Planned-versus-eaten by date. The recipe
round-trip through `Transactions.replaceIngredients` (built in Phase 1a). Recents reconstruction
from `basePer100*`. And the impact strip's eligibility gate — the one most likely to regress
silently, because a wrong answer just means a missing strip rather than a wrong number.

**No snapshot tests.** Glass renders differently across OS versions and device configurations, so
they fail for reasons that are not regressions.

**Needs visual check:** the library list at real length · the amount sheet in both modes · the impact
strip's three tones · the recipe editor's two bodies · the reconcile banner.

## Rollback

Additive in the iOS repo on its own branch, except for three modified Food Log files. Nothing in
`Persistence/` changes apart from two new query files. The Android repo receives documentation only.
