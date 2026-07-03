# Food-screen meal-suggestion card — stage 3b

**Date:** 2026-07-02
**Status:** Approved (blanket authorization — user asleep; decisions locked in prior brainstorm)
**Branch:** `redesign/ai-coaching`
**Part of:** feature #3 (meal suggestions). **Stage 3a** (done) = the pure `MealSuggester` engine
+ `suggest_meals` chat tool. **Stage 3b** (this spec) = the proactive Food-screen card that
replaces the "Rest of Day" AI insight card, reusing the 3a engine, gated on time-of-day + gap.

## Goal

On the Food screen, once enough of the day has passed and the user still has a meaningful macro
gap, show a **deterministic** card that tells them what to eat: the remaining gap, the focus macro,
2–3 portioned suggestions from their food library, and a combo when protein + carbs are both short.
A single CTA escalates to the coach chat (web-forward recipe ideas + offer-to-log) via the existing
handoff. The card's rendered content never depends on the LLM.

## Scope

**In scope (3b)**
- A pure mapper `MealSuggestionCardMapper` (`ui/today/`) that turns (plan target, eaten totals,
  library, fraction-of-day-elapsed) into an optional `MealSuggestionCardState` — null when the card
  should not show. Reuses the 3a `MealSuggester` engine and the **same** remaining-gap /
  proteinMetRatio computation as `suggest_meals`.
- A shared pure helper so the gap computation is **not duplicated** between the tool and the card
  (DRY): `MealSuggester.suggestForDay(target, eaten, library)` in `domain/food/`, consumed by both
  `CoachToolExecutor.suggestMeals` and the new mapper.
- `FoodLogViewModel`: expose `mealSuggestion: MealSuggestionCardState?` in `FoodLogUiState`
  (computed from today's data + an injectable clock), and an `askCoachForMeals()` path.
- `MealSuggestionCard` composable (`ui/today/FoodScreen.kt`) using the design system (`TintedCard`,
  `AppType`, `LiquidActionButton`, `SectionLabel`), placed exactly where `RestOfDayReveal` sat.
- Remove the now-orphaned Food-screen rest-of-day glue: the `RestOfDayReveal` composable, the VM
  members `restOfDayInsightState` / `onRestOfDayInsightVisible` / `retryRestOfDayInsight` /
  `restOfDayInsightContext`, and `RestOfDayInsightMapper.kt` + `RestOfDayInsightMapperTest.kt`.
- Wire `AppNavGraph`: a new `onAskCoachForMeals` lambda (seed a web-forward handoff via
  `CoachHandoffStore`, `coachCoordinator.clearHistory()`, navigate to the Coach tab) — mirrors the
  weekly-review `startCoachHandoff` pattern.

**Out of scope / explicitly deferred**
- **Direct in-card "log this portion"** — logging a portioned suggestion into a specific meal slot
  needs a slot-picker/prefilled-add flow; deferred to keep this change low-risk. Logging is still
  offered, via the coach (the seeded chat runs `suggest_meals` + `log_meal` with confirmation).
- The shared insight **engine** for `REST_OF_DAY` (InsightRequest/InsightKind/RestOfDayInsightContext,
  the prompt builder, both coordinators, and their tests) stays **untouched** — it is cross-cutting
  and tested. Only the Food-screen's *use* of it and the Food-screen-specific mapper are removed.
- No change to `MealSuggester`'s core `suggest(...)` behaviour, `suggest_meals` JSON contract, or
  `search_web` / `log_meal`.

## Data sources (all already available)

- **Plan target for today:** `planRepository.observePlanOn(today)` overlaid on
  `planRepository.preferences` (the VM already builds `dayTarget` this way). Fields:
  `PlanPreferences.targetCalories/targetProteinG/targetCarbsG/targetFatG` (all `Int`).
- **Eaten totals today:** `logRepository.observeDay(today).totals` → `MacroTotals(calories: Int,
  proteinG/carbsG/fatG: Double)` — eaten (not planned).
- **Food library:** `logRepository.observeSavedFoods()` → `List<SavedFoodEntity>`
  (name, servingName, calories, proteinG, carbsG, fatG, householdServingGrams).
- **Time of day:** no clock exists in the app for hours (only `DateProvider.today()`). Inject a
  `clock: () -> java.time.LocalTime = { java.time.LocalTime.now() }` into `FoodLogViewModel`
  (mirrors the `now = { LocalDateTime.now() }` lambda already used for `CoachPushEmitter` in
  `AppContainer`). The mapper receives a pure `fractionOfDayElapsed: Double`.

## Shared gap computation (DRY) — `domain/food/`

Add to `MealSuggester` (pure):

```kotlin
data class MacroTargets(val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

/** Remaining-gap + proteinMetRatio derivation shared by suggest_meals and the Food-screen card. */
fun suggestForDay(target: MacroTargets, eaten: MacroTotals, library: List<SuggestionFood>): SuggestionResult {
    val remaining = SuggestMacros(
        calories = (target.calories - eaten.calories).coerceAtLeast(0),
        proteinG = (target.proteinG - eaten.proteinG).coerceAtLeast(0.0),
        carbsG   = (target.carbsG   - eaten.carbsG).coerceAtLeast(0.0),
        fatG     = (target.fatG     - eaten.fatG).coerceAtLeast(0.0),
    )
    val proteinMetRatio = if (target.proteinG > 0) eaten.proteinG / target.proteinG else 1.0
    return suggest(remaining, proteinMetRatio, library)
}
```

(`MacroTotals` is `core.model`, a pure data class — importing it into `domain/food` keeps domain
Android-free, so `AiCoachBoundaryTest` stays green.) `CoachToolExecutor.suggestMeals` is refactored
to build `MacroTargets` from `PlanPreferences` and call `suggestForDay(...)`, dropping its inline
subtraction/ratio (behaviour identical; existing `CoachToolExecutorSuggestMealsTest` must stay green).

A pure entity→DTO mapper removes the second duplication:
`fun SavedFoodEntity.toSuggestionFood(): SuggestionFood` in a small `data/.../SavedFoodSuggestion.kt`
(data → domain is allowed), used by both the tool and the VM.

## Pure card mapper — `ui/today/MealSuggestionCardMapper.kt`

```kotlin
data class MealSuggestionCardState(
    val focus: SuggestionFocus,
    val headline: String,                 // e.g. "≈740 kcal · 42 g protein to go"
    val suggestions: List<MealSuggestion>,// top 3 from the engine
    val combo: MealCombo?,                // first combo if any
    val libraryThin: Boolean,             // true → prompt to ask the coach for ideas
    val coachSeed: String,                // web-forward handoff text for the CTA
)

object MealSuggestionCardMapper {
    const val DAY_FRACTION_GATE = 0.5     // "half the (eating) day elapsed"
    fun build(
        target: MealSuggester.MacroTargets,
        eaten: MacroTotals,
        library: List<SuggestionFood>,
        fractionOfDayElapsed: Double,
    ): MealSuggestionCardState?           // null unless gate passes AND focus != NONE
}
```

**Gate:** return null unless `fractionOfDayElapsed >= DAY_FRACTION_GATE`. Run
`MealSuggester.suggestForDay(...)`; if `focus == NONE` (on target) return null. Otherwise build the
state: `headline` from the remaining gap + focus; `suggestions` = `result.suggestions.take(3)`;
`combo` = `result.combos.firstOrNull()`; `libraryThin` = `result.libraryThin`; `coachSeed` = a short
web-forward instruction embedding the numeric gap (so the coach opens straight into
`suggest_meals` + `search_web`, respecting memory, offering to log).

**Fraction of day:** a pure helper `eatingDayFraction(now: LocalTime, startHour = 8, endHour = 22)`
= `((now.toSecondOfDay() - start) / (end - start)).coerceIn(0.0, 1.0)`. Half ⇒ ~15:00.

## ViewModel wiring — `FoodLogViewModel`

- Add ctor param `clock: () -> LocalTime = { LocalTime.now() }`.
- A dedicated `viewModelScope.launch` combining `observeDay(today)`, `preferences`,
  `observePlanOn(today)`, `observeSavedFoods()` → builds `MacroTargets` (same day-target overlay as
  the main block), maps the library via `toSuggestionFood()`, computes `eatingDayFraction(clock())`,
  calls `MealSuggestionCardMapper.build(...)`, and pushes the result into
  `FoodLogUiState.mealSuggestion`. (Separate from the main state collect → the existing state
  machine is untouched; lowers risk.)
- Add `mealSuggestion: MealSuggestionCardState? = null` to `FoodLogUiState`.
- Remove the four rest-of-day members and the `restOfDayInsightContext` assignment in the main
  block. The `aiInsightCoordinator` ctor dep stays (still used for the other insight kinds? No —
  the Food VM only used REST_OF_DAY. **Keep** the param but it becomes unused → instead drop the
  `aiInsightCoordinator` ctor param from `FoodLogViewModel` and its `AppContainer` construction,
  since REST_OF_DAY was its only consumer here). The shared coordinator object in `AppContainer`
  is unchanged and still used by Dashboard/Today/Progress.
- Add `askCoachSeed(): String?` → returns `uiState.value.mealSuggestion?.coachSeed`.

The card only renders for **today** — the mapper always computes for today; the screen shows it only
when `state.isToday`.

## Card UI — `MealSuggestionCard` (`ui/today/FoodScreen.kt`)

`TintedCard` (AI-feature glass). Contents:
- `SectionLabel("What to eat next")` + a small 🍽 content glyph (emoji as content, not affordance).
- Headline (`AppType.cardTitle`, `textPrimary`) = `state.headline`.
- Up to 3 suggestion rows (`AppType.body` name + `AppType.label`/`metaLabel` for
  `amount · key-macro · kcal`), e.g. "Chicken breast · ≈150 g · 46 g protein · 248 kcal".
- If `combo != null`, one combo row ("Chicken + Rice · 61 g protein · 470 kcal").
- If `libraryThin`, replace rows with a one-liner: "Your library is thin on {focus} — ask the coach
  for ideas."
- One `LiquidActionButton(text = "Get meal ideas", isPrimary = true, small = true)` →
  `onAskCoachForMeals()`.

Replace the `item { RestOfDayReveal(...) }` block (FoodScreen ~line 259-266) with
`if (state.isToday) state.mealSuggestion?.let { item { MealSuggestionCard(it, onAskCoachForMeals) } }`.
Delete the `RestOfDayReveal` composable and the `restOfDay*` params from `FoodScreenContent`.

## Nav wiring — `AppNavGraph`

Add `onAskCoachForMeals: () -> Unit` to the Food destination. Implementation (mirrors weekly review):
```kotlin
onAskCoachForMeals = {
    foodLogViewModel.askCoachSeed()?.let { seed ->
        container.coachHandoffStore.set(seed)
        container.coachCoordinator.clearHistory()
    }
    navController.navigate(TopLevelDestination.Coach.route) { launchSingleTop = true; /* same opts as onOpenCoach */ }
}
```

## Architecture / boundary

- `MealSuggester` stays pure (`domain/food`); adding `MacroTargets` + `suggestForDay` + importing
  `core.model.MacroTotals` keeps it Android-free. `MealSuggestionCardMapper` is pure Kotlin in
  `ui/today` (no Compose, no Android) — unit-testable. `AiCoachBoundaryTest` stays green.
- Deterministic thesis preserved: the card's every number comes from the engine; the LLM is only
  reached when the user taps "Get meal ideas".
- DRY: one gap computation (`suggestForDay`) and one entity→DTO mapper (`toSuggestionFood`) shared
  by the tool and the card.

## Testing

- `MealSuggesterTest` (extend): `suggestForDay` computes remaining = target − eaten (clamped) and
  proteinMetRatio, delegating to `suggest`; protein-met ratio 0 target → 1.0.
- `MealSuggestionCardMapperTest` (new): null before the day-fraction gate; null when focus == NONE
  (on target) even after the gate; non-null with headline + ≤3 suggestions + combo when protein &
  carbs both short and time gate passed; `libraryThin` true when the library yields nothing;
  `eatingDayFraction` boundaries (08:00→0.0, 15:00→~0.5, 22:00→1.0, clamped outside).
- `CoachToolExecutorSuggestMealsTest` (unchanged) must stay green after the `suggestForDay` refactor.
- Full suite: only the known `InsightHarnessTest` network test may fail; `AiCoachBoundaryTest` green.

## Rollout

Subagent-driven TDD in the MAIN checkout on `redesign/ai-coaching` (worktree isolation branches
from the wrong base here — main tree + base sanity check). Review pass. On-device (deferred to the
user): after ~15:00 with a real macro gap, the Food screen shows the card with portioned picks;
"Get meal ideas" opens the coach mid-suggestion. Direct in-card logging is a documented follow-up.
