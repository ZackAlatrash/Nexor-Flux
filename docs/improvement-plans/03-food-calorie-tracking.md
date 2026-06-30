# 03 — Food / Calorie & Macro Tracking

The food log is the app's strongest, highest-traffic surface. This plan is about **polish and
closing wiring gaps**, not a rewrite. The logging model (Room meal entries + `planned` flag,
per-100g scaling, slot-based day view) works and should be left intact.

Every finding below was verified against the live tree; file paths and line numbers are cited.
Lines are approximate to the tree at time of writing.

---

## 1. Current state & problems

**What works well (verified — do not touch):**

- The **recipe seed-from-selection** path is sound end-to-end. `FoodScreen.onCreateRecipeFromSelection`
  (`ui/today/FoodScreen.kt:104`, invoked at `:142` with `viewModel.selectedRecipeIngredients()`)
  → JSON + Base64-URL encode (`ui/navigation/AppNavGraph.kt:212-217`) → nav arg `seedIngredients`
  (`AppNavGraph.kt:119`, `:606-610`) → decoded in `RecipeBuilderViewModel.decodeSeed`
  (`ui/recipes/RecipeBuilderViewModel.kt:78-96`). The seed **does** reach the builder. The only
  empty-arrival cases are legitimate (nothing selected) or a corrupt arg, which
  `decodeSeed`'s `catch (_: Exception)` at `RecipeBuilderViewModel.kt:93` swallows silently —
  a malformed seed degrades to an empty builder with no user-visible error (minor).
- **`FoodCatalogRepository` is NOT dead** — injected at `ui/foodlibrary/FoodLibraryViewModel.kt:229`
  and used in the `combine` at `:272` (`observeCatalogFoods()` drives NEVO catalog search). The
  audit's "injected-but-unused" suspicion is **false**.
- **`showCreateFoodForm` + `newFood*` state is NOT dead** — fully wired: toggle at
  `FoodLibraryScreen.kt:211`, sheet at `:418`/`:935-986`, fields bound at `:952-981`, save at
  `:986` → `FoodLibraryViewModel.saveNewFood():620`. The audit's "dead state" suspicion is **false**.
- **Adjustment-threshold invalidation is robust, NOT stale** — `DashboardViewModel` rebuilds its
  cached `AdjustmentEngine` reactively: the `init` `combine` includes `planRepository.preferences`
  (`ui/dashboard/DashboardViewModel.kt:233`), and `buildState` re-creates the engine on value change
  (`if (thresholds != cachedEngineThresholds) … AdjustmentEngine(thresholds)`,
  `DashboardViewModel.kt:295-303`). The cache exists only to avoid re-instantiating per meal-add;
  edits take effect on the next emission (300 ms debounce). The audit's "stale engine" suspicion is
  **false**. (Keep an eye on it if the caching is ever refactored, but no action needed now.)

**Real problems (verified):**

1. **`FoodScreen.kt` is a 1363-line monolith** (`ui/today/FoodScreen.kt`) holding ~12 composables:
   `FoodScreen`/`FoodContent` (orchestration), `FoodScreenHeader`/`DayNavButton`,
   `ReconcilePlannedBanner`, `RestOfDayReveal`, `StalePlannedHint`, `NutritionStrip`/`MacroProgressItem`,
   `LockedSlotCard`, `SlotEntryRow`, `MacroEditDialog`, `EditModeSlotCard`, plus `calorieStatus`/
   colour helpers. It mixes day navigation, the nutrition summary, slot rows, edit mode, recipe
   selection, and the rest-of-day insight in one file.

2. **Pervasive design-system violations in `FoodScreen.kt`.** Bare `fontSize`/`fontWeight` instead of
   `AppType` at lines 452-453, 458, 504, 507, 654, 685-686, 771-774, 855-856, 974, 1097 (and more);
   hardcoded `Color(0x…)` hex at 575-576, 584-585, 593-594, 602-603; raw `.background().border()`
   cards instead of the card family at 416-417, 443-444, 498-499, 833-834, 868-869; a Material
   `AlertDialog` (`:333`) and `OutlinedTextField` (`:337`) for the add-slot dialog; text-glyph
   affordance (`🗓`) at `:504`. These contradict `docs/design-system.md` ("Never hardcode
   `fontSize`/`fontWeight`", "one card family", "two header tiers").

3. **Recents exclude recipes and quick-adds.** The recents query is `WHERE mealType = 'FOOD_LIBRARY'`
   (`data/local/dao/MealEntryDao.kt:30`) and `RecentFoods.fromEntries` re-filters
   `mealType == FOOD_LIBRARY && basePer100Calories != null`
   (`domain/food/RecentFoods.kt:12-17`). `MealEntryTypes` are string tags
   `FOOD_LIBRARY`/`QUICK_ADD`/`RECIPE` (`domain/food/MealEntryTypes.kt:5-7`). A frequently-logged
   recipe or quick-add **never** surfaces as a recent.

4. **`postponeMeal` is +1 day only.** `FoodLogViewModel.postponeMeal()` hard-codes
   `_selectedDate.value.plusDays(1)` (`ui/today/FoodLogViewModel.kt:180-185`), even though day
   navigation spans ±30 days (`NAV_WINDOW_DAYS = 30L`, `:244`; clamp at `selectDate():158-163`).
   The repository's `moveMealToDate(id, date, planned)` already accepts any date — only the VM
   restricts it.

5. **Quick-add-sourced ingredients are non-scalable in recipes.** `RecipeIngredientEntity.basePer100*`
   are nullable (`data/local/entity/RecipeIngredientEntity.kt:32-35`). Scaling requires them:
   `RecipeBuilderViewModel.basePer100()` returns null when `basePer100Calories == null`
   (`:229-237`), and `startEditingIngredient` only enters the scalable branch when
   `amountGrams != null && base != null` (`:122`); otherwise the ingredient can only have raw
   macros edited, never rescaled by grams (`FoodScaling.scale` needs a per-100g base,
   `domain/food/FoodScaling.kt:20`). The **builder has no inline-macro entry path** — every
   ingredient enters via the food picker, which *does* populate basePer100
   (`FoodLibraryViewModel.kt:411-425`). The real leak is upstream: **quick-add** logging
   (`FoodLibraryViewModel.kt:724-736`) and **saved-meal** logging (`logMeal`, `:494-504`) call
   `addMealToSlot` without basePer100 args (default null, `data/repository/LogModels.kt:42-45`).
   When such an entry is later turned into a recipe ingredient — via Save-Meal-as-recipe
   (`FoodLibraryViewModel.kt:680-695`) or `MealEntryEntity.toRecipeIngredient`
   (`data/repository/MealToRecipe.kt:11-29`, maps basePer100 1:1) — the null propagates and the
   ingredient is permanently non-scalable. So the audit's literal framing ("entered with inline
   macros in the builder") is slightly off, but the **consequence is real**.

6. **Three ad-hoc "return a value from a screen" channels.** The barcode picker returns a
   `SavedFoodEntity` as a JSON string keyed `"scanned_food"` through
   `previousBackStackEntry.savedStateHandle` (encode at `ui/scanner/BarcodeScannerScreen.kt:90-93`,
   write at `AppNavGraph.kt:593-595`, read/clear at `AppNavGraph.kt:528-529`/`:544-545`). The recipe
   ingredient picker does the same with `"picked_ingredient"` (`AppNavGraph.kt:548`, `:615-623`), and
   the recipe seed uses Base64-in-route (finding 1). Three separate hand-serialized, magic-string
   channels for one conceptual operation — architectural debt, not a bug.

7. **Rest-of-day insight is today-only.** `restOfDayInsightContext` is built only when
   `day.date == today` (`FoodLogViewModel.kt:121-123`); on any other selected date it is null and
   the reveal is hidden (`RestOfDayReveal` early-returns on `!available`, `FoodScreen.kt:480`). This
   is correct for the *rest-of-day* projection but means the food screen offers no insight at all
   when reviewing/planning another day.

---

## 2. UX improvements

1. **Recents should include recipes and quick-adds.** Broaden the recents source so a frequently
   logged recipe or quick-add can re-appear. Either widen the DAO query
   (`MealEntryDao.kt:30`) to include `RECIPE`/`QUICK_ADD`, or add a parallel "recent recipes" row.
   Keep the `basePer100 != null` gate **only** for the amount-editable FOOD_LIBRARY row (recipes/
   quick-adds re-log as-is, no scaling). Update `RecentFoods.fromEntries` (`RecentFoods.kt:12-17`)
   to segment by type rather than hard-exclude.

2. **Arbitrary-date postpone.** Replace the fixed `+1 day` in `postponeMeal`
   (`FoodLogViewModel.kt:180`) with a date picker (a `GlassBottomSheet` with a compact calendar, or
   reuse the day-nav range ±30). The repo call `moveMealToDate(id, target, planned = target.isAfter(today))`
   already supports any date — only pass the chosen date. Keep "Tomorrow" as the one-tap default.

3. **Clearer planned-vs-eaten distinction.** The `ReconcilePlannedBanner`
   (`FoodScreen.kt:433-468`) and per-row "Planned" pill (`:1097`) exist, but the nutrition strip
   shows eaten totals only. Surface a subtle "+ N kcal planned" affordance in `NutritionStrip`
   (`FoodScreen.kt:531`) using `state.plannedTotals` (already in `FoodLogUiState`) so the user sees
   the eaten/planned split without scrolling. Use a muted/tinted style, not a second progress bar.

4. **Quicker logging.** The recents row already gives one-tap re-log. Two cheap additions:
   (a) make a recents chip long-press offer "log again with same amount" vs "edit amount";
   (b) consider a "repeat yesterday's <slot>" action on an empty slot (data already available via
   `logRepository.observeDay`).

5. **Surface the silent seed-decode failure** (finding 1): if `decodeSeed` returns null but a seed
   arg was present, show a one-line toast in the builder rather than an empty screen
   (`RecipeBuilderViewModel.kt:88-96`).

---

## 3. UI improvements

All against `docs/design-system.md`.

1. **Kill the bare `fontSize`/`fontWeight` in `FoodScreen.kt`.** Map each to an `AppType` token:
   captions (`:458`, `:507`, `:685`, `:771`, `:855`, `:974`) → `AppType.metaLabel`/`AppType.label`;
   the banner title (`:452`) → `AppType.cardTitle` or `cardSubtitle`; tile numbers → `statValue`.
   Pass colour separately via `appColors.<token>`.

2. **Replace hardcoded hex** (`:575-576`, `:584-585`, `:593-594`, `:602-603`) with semantic colour
   tokens. The calorie-day status palette (green/red zone colours) should live as named tokens in
   `ui/theme/AppColors.kt` (or reuse `ErrorRed` / a success token) rather than inline `Color(0x…)`.

3. **Convert raw `.background().border()` cards to the card family.** `ReconcilePlannedBanner`
   (`:443-444`), `StalePlannedHint` (`:498-499`), `LockedSlotCard` (`:833-834`), the empty-state
   row (`:868-869`) → `FrostedCard`/`NeutralCard`/`TintedCard`. The accent-tinted banner is a good
   `TintedCard` candidate; the stale hint is a `NeutralCard`.

4. **Replace the add-slot `AlertDialog` + `OutlinedTextField`** (`FoodScreen.kt:333-337`) with a
   `GlassBottomSheet` + `GlassInputField`, matching the rest of the app's sheets.

5. **Swap the `🗓` text glyph** (`:504`) for a Material icon (per the design system: emoji only as
   genuine content, not affordance). It's decorative here so a small tinted `Icon` is cleaner.

6. **Header tier check.** Confirm `FoodScreenHeader` (`:360`) uses tier-1 `ScreenHeader` (Food Log is
   a tab destination) with the date as `subtitle` per the day-scoped rule — keep, don't invent a
   bespoke header.

---

## 4. Data / model improvements

1. **Populate `basePer100*` at quick-add / saved-meal logging.** When a quick-add or saved-meal entry
   carries enough info to derive a per-100g base, compute and store it so downstream recipe
   conversion stays scalable. Touch points: `FoodLibraryViewModel` quick-add (`:724-736`) and
   `logMeal` (`:494-504`), passing basePer100 args through `addMealToSlot`
   (`LogModels.kt:42-45`). For genuinely amount-less quick-adds (a raw kcal figure with no grams),
   leave basePer100 null but make the non-scalable state explicit in the recipe editor UI ("fixed
   amount — re-enter macros to change").

2. **Broaden the recents query** (finding 3 / UX 1): change `MealEntryDao.observeFoodLibraryEntries`
   (`:30`) or add a sibling query so `RECIPE`/`QUICK_ADD` can be surfaced, and refactor
   `RecentFoods.fromEntries` to keep the `basePer100 != null` gate scoped to the scalable
   FOOD_LIBRARY row only.

3. **No dead-state/dead-repo cleanup needed** — finding verification showed `FoodCatalogRepository`,
   `showCreateFoodForm`, and the `newFood*` fields are all live (see §1). Drop these from the cleanup
   list; the audit hypotheses were false.

4. **Threshold invalidation needs no change** — verified robust (see §1). If anyone later refactors
   `DashboardViewModel`'s engine cache (`:220`, `:295-303`), preserve the value-comparison
   invalidation.

---

## 5. AI opportunities (respect 2B constraints in `docs/ai-coach.md`)

The on-device model is Gemma 4 2B with limited tool iterations; insight cards are single-turn
streaming (`GemmaInsightCoordinator.generateExplanation`), and the doctrine is "never add a tool for
data that's static or session-invariant."

1. **Make rest-of-day insight available on the selected day, not just today** (finding 7). The
   insight is a single-turn projection over `day.totals` vs `dayTarget`; today vs. another date is
   just which numbers feed `buildRestOfDayInsightContext` (`FoodLogViewModel.kt:121-123`). For a
   **future** selected day, reframe it as a "plan check" ("your planned meals land you at X kcal");
   for a **past** day, either suppress or reframe as a recap. Gate strictly on
   `hasSufficientData` (already checked at `:79`) to avoid empty generations on sparse days. This is
   a context-builder change, not a new tool — cheap and on-doctrine.

2. **Smarter food suggestions as a *deterministic* pre-filter, AI only for phrasing.** Don't ask the
   2B model to pick foods (it has no library access without a tool call and will burn iterations).
   Instead: compute the macro gap (remaining protein/carbs/fat to target) in domain code, rank
   recents/library foods that close the gap, and optionally pass the top 2-3 to a single-turn
   insight for a one-line nudge ("You're 40 g protein short — your usual chicken & rice covers it").
   Keep the ranking in `domain/food` (pure Kotlin), reuse the existing `FoodScaling` math, and feed
   the model only the final shortlist as text.

3. **Do not** add a coach tool for recents or library scanning — that data is local and a tool call
   risks the iteration cap (per `docs/ai-coach.md`). Keep food selection deterministic.

---

## 6. Quick wins

- Convert the 4 raw `.background().border()` cards in `FoodScreen.kt` to `FrostedCard`/`NeutralCard`/
  `TintedCard` (`:443`, `:498`, `:833`, `:868`).
- Replace hardcoded `Color(0x…)` calorie-status hex with tokens (`:575-603`).
- Map all bare `fontSize`/`fontWeight` in `FoodScreen.kt` to `AppType`.
- Swap the `🗓` glyph (`:504`) for a Material icon.
- Add the seed-decode-failure toast in `RecipeBuilderViewModel` (`:88-96`).
- Show "+ N kcal planned" in `NutritionStrip` using existing `state.plannedTotals`.

## 7. Medium improvements

- **Arbitrary-date postpone**: date-picker sheet wired into `postponeMeal`
  (`FoodLogViewModel.kt:180`), keeping "Tomorrow" as default.
- **Recents include recipes/quick-adds**: DAO query + `RecentFoods.fromEntries` segmentation
  (`MealEntryDao.kt:30`, `RecentFoods.kt:12`).
- **Populate basePer100 at quick-add/saved-meal logging** so recipe conversion stays scalable
  (`FoodLibraryViewModel.kt:494-504`, `:724-736`).
- **Replace the add-slot `AlertDialog`** with a `GlassBottomSheet` (`FoodScreen.kt:333`).
- **Rest-of-day insight on the selected day** (reframed future/past) — context-builder change in
  `FoodLogViewModel` (`:121-123`).

## 8. Bigger refactors

1. **Decompose `FoodScreen.kt` (1363 lines).** Suggested split, keeping `FoodScreen`/`FoodContent`
   as the thin orchestrator in `ui/today/`:
   - `FoodDayHeader.kt` — `FoodScreenHeader` + `DayNavButton` (`:360-431`).
   - `FoodNutritionStrip.kt` — `NutritionStrip` + `MacroProgressItem` + `calorieStatus`/colour helpers
     (`:531-804`, `:513-528`).
   - `FoodSlotCard.kt` — `LockedSlotCard`, `SlotEntryRow`, `EditModeSlotCard`, `MacroEditDialog`
     (`:806-1361`).
   - `FoodPlannedBanners.kt` — `ReconcilePlannedBanner`, `StalePlannedHint`, `RestOfDayReveal`
     (`:433-511`).
   Do the design-system token migration (§3) *as part of* each extraction so each new file lands
   already conformant — don't migrate in place then split.

2. **Unify the "return a value from a screen" channels** (finding 6). Introduce one small typed
   nav-result helper (a sealed result keyed by a single constant, or a shared
   `NavResult<T>` extension over `savedStateHandle`) and route the barcode `SavedFoodEntity`
   (`BarcodeScannerScreen.kt:90`), the recipe `picked_ingredient` (`AppNavGraph.kt:548`), and ideally
   the recipe seed through it. This is a navigation-architecture change — schedule it after the
   `FoodScreen` decomposition so the two refactors don't collide.

## 9. What to avoid for now

- **Do not rebuild the logging model.** Room meal entries, the `planned` flag, slot-based day view,
  and per-100g `FoodScaling` all work and are well-tested. No schema migration is needed for any item
  in this plan except (optionally) backfilling basePer100, which can be additive.
- **Do not "fix" the dead-state / dead-repo / threshold items** — they were verified live/robust.
- **Do not give the 2B coach a food-search/recents tool** — keep food selection deterministic to
  respect the iteration cap (`docs/ai-coach.md`).
- **Do not over-engineer the nav-result unification before** the `FoodScreen` split lands; sequencing
  matters.

## 10. Suggested implementation order

1. **Quick wins (§6)** — design-system token migration in `FoodScreen.kt`, card-family swaps, hex →
   tokens, glyph → icon, planned-kcal in the strip, seed-decode toast. Low risk, immediate polish.
2. **Recents include recipes/quick-adds (§7)** — DAO + `RecentFoods` change; high user value, small
   surface.
3. **Arbitrary-date postpone (§7)** — VM + a date-picker sheet.
4. **basePer100 backfill at logging (§7)** — quick-add / saved-meal logging populates basePer100;
   keeps recipe conversion scalable. Pairs naturally with #2.
5. **Rest-of-day insight on selected day (§7)** — context-builder change; reframe future/past.
6. **Decompose `FoodScreen.kt` (§8.1)** — fold the remaining §3 token migration into each extracted
   file.
7. **Unify nav-result channels (§8.2)** — after the decomposition, retire the three magic-string
   channels.
8. **Deterministic food-suggestion ranking + optional one-line AI nudge (§5.2)** — last, builds on
   the cleaner food layer.
