# Food-screen Meal-Suggestion Card (stage 3b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the Food screen's "Rest of Day" AI insight card with a deterministic meal-suggestion card (remaining gap → focus → portioned library picks + combo), gated on time-of-day + a real gap, with a "Get meal ideas" CTA that hands off to the coach chat.

**Architecture:** Reuse the pure 3a `MealSuggester`. Add a shared `suggestForDay` gap helper (DRY with `suggest_meals`) + a pure `MealSuggestionCardMapper`. `FoodLogViewModel` computes the card state from today's data + an injectable clock. A `TintedCard` renders it; the CTA seeds a web-forward coach handoff and navigates to the Coach tab.

**Tech Stack:** Kotlin, Compose, coroutines/Flow, JUnit + mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-07-02-meal-suggestion-card-design.md`
**Branch:** `redesign/ai-coaching`. **Run in the MAIN checkout** (worktree isolation branches from the wrong base here). Each implementer verifies base FIRST: `git branch --show-current` == `redesign/ai-coaching` AND `test -f app/src/main/java/com/zack/recomptracker/domain/food/MealSuggester.kt` (3a present). If not, STOP.

**Golden rules:** `git add` only each task's explicit files — never `git add -A` (unrelated TEMP DEBUG commits exist). Follow the design system (`AppType`, `TintedCard`, `LiquidActionButton`, `SectionLabel`, `LocalAppColors`/`LocalAppAccent`) — never hardcode `fontSize`/`fontWeight`/hex.

**Build/verify:** `./gradlew :app:compileDebugKotlin` · `./gradlew :app:testDebugUnitTest --tests "*MealSuggester*" --tests "*MealSuggestionCardMapper*" --tests "*SuggestMeals*" --tests "*FoodLogViewModel*" --tests "*AiCoachBoundaryTest*"` · full: `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail) · `./gradlew :app:assembleDebug`.

---

## Task 1: Shared gap helper (`suggestForDay`) + entity mapper, refactor `suggest_meals`

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/domain/food/MealSuggester.kt`
- Create: `app/src/main/java/com/zack/recomptracker/data/repository/SavedFoodSuggestion.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` (`suggestMeals`)
- Test: `app/src/test/java/com/zack/recomptracker/domain/food/MealSuggesterTest.kt` (extend)

- [ ] **Step 1: Add failing tests** to `MealSuggesterTest.kt` (append inside the class):

```kotlin
    @Test fun `suggestForDay derives remaining as target minus eaten, clamped`() {
        val r = MealSuggester.suggestForDay(
            target = MealSuggester.MacroTargets(2000, 150, 200, 60),
            eaten = com.zack.recomptracker.core.model.MacroTotals(1000, 50.0, 120.0, 30.0),
            library = listOf(food("Chicken", 165, 31.0, 0.0, 4.0)),
        )
        assertEquals(1000, r.remaining.calories)
        assertEquals(100.0, r.remaining.proteinG, 0.01)
        assertEquals(SuggestionFocus.PROTEIN, r.focus)  // 50/150 = 0.33 < 0.85
    }

    @Test fun `suggestForDay clamps negative gaps to zero and treats zero protein target as met`() {
        val r = MealSuggester.suggestForDay(
            target = MealSuggester.MacroTargets(1000, 0, 0, 0),
            eaten = com.zack.recomptracker.core.model.MacroTotals(1500, 80.0, 0.0, 0.0),
            library = emptyList(),
        )
        assertEquals(0, r.remaining.calories)
        assertEquals(0.0, r.remaining.proteinG, 0.01)
        // proteinMetRatio = 1.0 (zero target) → not protein focus; no calorie gap → NONE
        assertEquals(SuggestionFocus.NONE, r.focus)
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggesterTest*"`
Expected: FAIL — `MacroTargets` / `suggestForDay` don't exist.

- [ ] **Step 3: Implement in `MealSuggester.kt`**

Add the import at the top (with the existing imports):
```kotlin
import com.zack.recomptracker.core.model.MacroTotals
```
Add inside `object MealSuggester` (after the `suggest(...)` function):
```kotlin
    /** Plan targets for a day (all whole numbers, as stored in PlanPreferences). */
    data class MacroTargets(val calories: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

    /**
     * Shared day-level entry point: derives the remaining-macro gap (target − eaten, clamped ≥0)
     * and proteinMetRatio, then delegates to [suggest]. Used by both the suggest_meals coach tool
     * and the Food-screen suggestion card so the gap math lives in exactly one place.
     */
    fun suggestForDay(target: MacroTargets, eaten: MacroTotals, library: List<SuggestionFood>): SuggestionResult {
        val remaining = SuggestMacros(
            calories = (target.calories - eaten.calories).coerceAtLeast(0),
            proteinG = (target.proteinG - eaten.proteinG).coerceAtLeast(0.0),
            carbsG = (target.carbsG - eaten.carbsG).coerceAtLeast(0.0),
            fatG = (target.fatG - eaten.fatG).coerceAtLeast(0.0),
        )
        val proteinMetRatio = if (target.proteinG > 0) eaten.proteinG / target.proteinG else 1.0
        return suggest(remaining, proteinMetRatio, library)
    }
```

- [ ] **Step 4: Create the entity→DTO mapper** `SavedFoodSuggestion.kt`:

```kotlin
package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.food.SuggestionFood

/** Maps a saved food to the pure [SuggestionFood] DTO the MealSuggester engine consumes. */
fun SavedFoodEntity.toSuggestionFood(): SuggestionFood = SuggestionFood(
    name = name,
    servingLabel = servingName,
    gramsPerServing = householdServingGrams,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
```

- [ ] **Step 5: Refactor `CoachToolExecutor.suggestMeals`** to use the shared helpers.

Replace the body of `suggestMeals` (the `remaining`/`proteinMetRatio`/`library` block) so it reads:
```kotlin
    private suspend fun suggestMeals(args: Map<String, String>): String {
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val prefs = planRepository.preferences.first()
        val eaten = logRepository.getDay(date).totals
        val target = MealSuggester.MacroTargets(
            calories = prefs.targetCalories, proteinG = prefs.targetProteinG,
            carbsG = prefs.targetCarbsG, fatG = prefs.targetFatG,
        )
        val library = logRepository.getSavedFoods().map { it.toSuggestionFood() }
        return serializeSuggestions(MealSuggester.suggestForDay(target, eaten, library))
    }
```
Update imports in `CoachToolExecutor.kt`: keep `MealSuggester`, `SuggestionFocus`, `SuggestionResult`; remove now-unused `SuggestMacros`/`SuggestionFood` imports **only if** nothing else in the file uses them (grep first); add `import com.zack.recomptracker.data.repository.toSuggestionFood`.

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggesterTest*" --tests "*CoachToolExecutorSuggestMealsTest*"` (all pass — the tool test is unchanged and must stay green) and `./gradlew :app:compileDebugKotlin`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/food/MealSuggester.kt app/src/main/java/com/zack/recomptracker/data/repository/SavedFoodSuggestion.kt app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/test/java/com/zack/recomptracker/domain/food/MealSuggesterTest.kt
git commit -m "refactor(coach): shared suggestForDay gap helper + SavedFood→SuggestionFood mapper"
```

---

## Task 2: Pure `MealSuggestionCardMapper` + `eatingDayFraction`

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/ui/today/MealSuggestionCardMapper.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ui/today/MealSuggestionCardMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSuggestionCardMapperTest {
    private val target = MealSuggester.MacroTargets(2000, 150, 200, 60)
    private fun food(name: String, cal: Int, p: Double, c: Double, f: Double) =
        SuggestionFood(name, "serving", 100.0, cal, p, c, f)

    @Test fun `eatingDayFraction is 0 at start, ~0_5 at mid, 1 at end, clamped`() {
        assertEquals(0.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(8, 0)), 0.001)
        assertEquals(0.5, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(15, 0)), 0.001)
        assertEquals(1.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(22, 0)), 0.001)
        assertEquals(0.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(6, 0)), 0.001)
        assertEquals(1.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(23, 30)), 0.001)
    }

    @Test fun `null before the day-fraction gate even with a gap`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(500, 20.0, 40.0, 10.0),
            listOf(food("Chicken", 165, 31.0, 0.0, 4.0)), fractionOfDayElapsed = 0.3,
        )
        assertNull(s)
    }

    @Test fun `null when on target after the gate`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(2000, 150.0, 200.0, 60.0), emptyList(), fractionOfDayElapsed = 0.9,
        )
        assertNull(s)
    }

    @Test fun `builds card with focus, headline, capped suggestions past the gate`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(600, 30.0, 60.0, 15.0),
            listOf(
                food("Chicken", 165, 31.0, 0.0, 4.0), food("Whey", 120, 24.0, 3.0, 2.0),
                food("Cod", 90, 20.0, 0.0, 1.0), food("Tofu", 145, 15.0, 3.0, 8.0),
            ),
            fractionOfDayElapsed = 0.6,
        )
        assertNotNull(s)
        assertEquals(SuggestionFocus.PROTEIN, s!!.focus)
        assertTrue(s.headline.isNotBlank())
        assertTrue(s.suggestions.size <= 3)
        assertTrue(s.coachSeed.contains("protein", ignoreCase = true))
    }

    @Test fun `libraryThin true when nothing contributes the focus macro`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(600, 30.0, 60.0, 15.0),
            listOf(food("Lettuce", 10, 0.0, 2.0, 0.0)), fractionOfDayElapsed = 0.7,
        )
        assertNotNull(s)
        assertTrue(s!!.libraryThin)
        assertTrue(s.suggestions.isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggestionCardMapperTest*"`
Expected: FAIL — mapper doesn't exist.

- [ ] **Step 3: Implement `MealSuggestionCardMapper.kt`**

```kotlin
package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.food.MealCombo
import com.zack.recomptracker.domain.food.MealSuggestion
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import java.time.LocalTime
import kotlin.math.roundToInt

/** Rendered state for the Food-screen meal-suggestion card. Every number is engine-computed. */
data class MealSuggestionCardState(
    val focus: SuggestionFocus,
    val headline: String,
    val suggestions: List<MealSuggestion>,
    val combo: MealCombo?,
    val libraryThin: Boolean,
    val coachSeed: String,
)

/**
 * Pure mapper: turns today's plan target, eaten totals, library, and how much of the eating day has
 * elapsed into an optional card state. Returns null when the card should not show (too early, or the
 * user is on target). Deterministic — reuses [MealSuggester.suggestForDay]; no Android/Compose.
 */
object MealSuggestionCardMapper {
    const val DAY_FRACTION_GATE = 0.5
    private const val START_HOUR = 8
    private const val END_HOUR = 22

    /** Fraction of the [START_HOUR, END_HOUR] eating window elapsed at [now], clamped to [0,1]. */
    fun eatingDayFraction(now: LocalTime): Double {
        val start = START_HOUR * 3600
        val end = END_HOUR * 3600
        return ((now.toSecondOfDay() - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)
    }

    fun build(
        target: MealSuggester.MacroTargets,
        eaten: MacroTotals,
        library: List<SuggestionFood>,
        fractionOfDayElapsed: Double,
    ): MealSuggestionCardState? {
        if (fractionOfDayElapsed < DAY_FRACTION_GATE) return null
        val r = MealSuggester.suggestForDay(target, eaten, library)
        if (r.focus == SuggestionFocus.NONE) return null
        return MealSuggestionCardState(
            focus = r.focus,
            headline = headline(r.remaining.calories, r.focus, r.remaining.proteinG, r.remaining.carbsG),
            suggestions = r.suggestions.take(3),
            combo = r.combos.firstOrNull(),
            libraryThin = r.libraryThin,
            coachSeed = coachSeed(r.remaining.calories, r.focus, r.remaining.proteinG, r.remaining.carbsG),
        )
    }

    private fun headline(cal: Int, focus: SuggestionFocus, proteinG: Double, carbsG: Double): String = when (focus) {
        SuggestionFocus.PROTEIN -> "≈$cal kcal · ${proteinG.roundToInt()} g protein to go"
        SuggestionFocus.CARBS -> "≈$cal kcal · ${carbsG.roundToInt()} g carbs to go"
        else -> "≈$cal kcal left today"
    }

    private fun coachSeed(cal: Int, focus: SuggestionFocus, proteinG: Double, carbsG: Double): String {
        val gap = when (focus) {
            SuggestionFocus.PROTEIN -> "about $cal kcal and ${proteinG.roundToInt()} g of protein"
            SuggestionFocus.CARBS -> "about $cal kcal and ${carbsG.roundToInt()} g of carbs"
            else -> "about $cal kcal"
        }
        return "The user tapped 'Get meal ideas' on the Food screen. They still need $gap today. " +
            "Call suggest_meals for their exact library fits AND search_web for recipe ideas that fit " +
            "the remaining macros — combine both, respect their memory (diet/allergies/dislikes), give " +
            "2-3 concrete options with amounts, and offer to log the one they pick."
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggestionCardMapperTest*"` (Expected: 5 pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/MealSuggestionCardMapper.kt app/src/test/java/com/zack/recomptracker/ui/today/MealSuggestionCardMapperTest.kt
git commit -m "feat(food): pure MealSuggestionCardMapper — time-gated deterministic card state"
```

---

## Task 3: `FoodLogViewModel` wiring — card state, clock, drop rest-of-day glue

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/core/AppContainer.kt` (FoodLogViewModel construction)
- Modify: `app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt`

- [ ] **Step 1: Write the failing test** — append to `FoodLogViewModelTest.kt` (uses its existing harness; the VM ctor loses `aiCoordinator` and gains `clock`):

```kotlin
    @Test fun `exposes a meal suggestion for today past the day-fraction gate with a protein gap`() = runTest {
        // Arrange: big protein gap today + a protein-dense library food; clock at 15:00 (fraction ~0.5).
        savedFoods.value = listOf(
            com.zack.recomptracker.data.local.entity.SavedFoodEntity(
                name = "Chicken", servingName = "serving", calories = 165,
                proteinG = 31.0, carbsG = 0.0, fatG = 4.0, householdServingGrams = 100.0,
            ),
        )
        // (day totals in the test harness default to a large remaining gap; see existing setup)
        val vm = buildVm()
        advanceUntilIdle()
        val card = vm.uiState.value.mealSuggestion
        assertNotNull(card)
        assertEquals(com.zack.recomptracker.domain.food.SuggestionFocus.PROTEIN, card!!.focus)
    }
```

> NOTE to implementer: adapt to the existing `FoodLogViewModelTest` fixtures — it already mocks
> `logRepo`/`planRepo`. Add a `savedFoods` MutableStateFlow backing `observeSavedFoods()`, ensure
> `observeDay(today)` returns totals with a real gap, and set the injected clock to 15:00. If the
> existing harness makes "today past the gate with a gap" awkward, assert on the mapper-equivalent
> inputs instead — but DO cover that `mealSuggestion` becomes non-null. Keep all existing tests green.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*FoodLogViewModelTest*"`
Expected: FAIL — `mealSuggestion` / new ctor shape don't exist.

- [ ] **Step 3: Edit `FoodLogViewModel.kt`**

(a) Imports: remove `AiInsightCoordinator`, `AiInsightState`, `InsightKind`, `InsightRequest`, `RestOfDayInsightContext`; add:
```kotlin
import com.zack.recomptracker.data.repository.observeSavedFoods // if not already importable; else use logRepository.observeSavedFoods()
import com.zack.recomptracker.data.repository.toSuggestionFood
import com.zack.recomptracker.domain.food.MealSuggester
import java.time.LocalTime
```
(b) Constructor — drop `aiInsightCoordinator`, add `clock`:
```kotlin
class FoodLogViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val clock: () -> LocalTime = { LocalTime.now() },
) : ViewModel() {
```
(c) Delete `restOfDayInsightState`, `onRestOfDayInsightVisible()`, `retryRestOfDayInsight()`.
(d) In `FoodLogUiState`: remove `restOfDayInsightContext`; add `val mealSuggestion: MealSuggestionCardState? = null`. Remove the `restOfDayInsightContext = …` assignment in the main `_uiState.update`.
(e) Add `fun askCoachSeed(): String? = _uiState.value.mealSuggestion?.coachSeed`.
(f) Add a dedicated collector in `init` (computes the card for TODAY only):
```kotlin
        viewModelScope.launch {
            combine(
                logRepository.observeDay(today),
                planRepository.preferences,
                planRepository.observePlanOn(today),
                logRepository.observeSavedFoods(),
            ) { day, prefs, dayPlan, foods ->
                val target = MealSuggester.MacroTargets(
                    calories = dayPlan.calories, proteinG = dayPlan.proteinG,
                    carbsG = dayPlan.carbsG, fatG = dayPlan.fatG,
                )
                MealSuggestionCardMapper.build(
                    target = target,
                    eaten = day.totals,
                    library = foods.map { it.toSuggestionFood() },
                    fractionOfDayElapsed = MealSuggestionCardMapper.eatingDayFraction(clock()),
                )
            }.collect { card -> _uiState.update { it.copy(mealSuggestion = card) } }
        }
```
(Note: `dayPlan` is the `observePlanOn` day-target type already used in the main block — reuse its `.calories/.proteinG/.carbsG/.fatG` fields.)

- [ ] **Step 4: Update `AppContainer.kt`** FoodLogViewModel construction (drop the coordinator arg; the clock uses its default):
```kotlin
            FoodLogViewModel::class.java -> FoodLogViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
            )
```

- [ ] **Step 5: Update `FoodLogViewModelTest.kt`** — remove the `aiCoordinator` field + `StubInsightCoordinator` import; change `buildVm()` to `FoodLogViewModel(logRepo, planRepo, dateProvider) { LocalTime.of(15, 0) }` (inject a fixed 15:00 clock so the gate passes). Add the `savedFoods` flow used by `observeSavedFoods()`.

- [ ] **Step 6: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*FoodLogViewModelTest*"` (all pass) and `./gradlew :app:compileDebugKotlin`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodLogViewModel.kt app/src/main/java/com/zack/recomptracker/core/AppContainer.kt app/src/test/java/com/zack/recomptracker/ui/today/FoodLogViewModelTest.kt
git commit -m "feat(food): FoodLogViewModel exposes deterministic meal-suggestion card state"
```

---

## Task 4: `MealSuggestionCard` UI + remove `RestOfDayReveal` + delete orphaned mapper

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt`
- Delete: `app/src/main/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapper.kt`
- Delete: `app/src/test/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapperTest.kt`

- [ ] **Step 1: Replace the card usage in `FoodScreenContent`.** Swap the `item { RestOfDayReveal(...) }` block (~line 259-266) with:
```kotlin
                if (state.isToday) {
                    state.mealSuggestion?.let { suggestion ->
                        item { MealSuggestionCard(state = suggestion, onAskCoach = onAskCoachForMeals) }
                    }
                }
```

- [ ] **Step 2: Update `FoodScreen` params.** Remove `restOfDayInsightState`, `restOfDayAvailable`, `onRevealRestOfDay`, `onRetryRestOfDay` from both the outer `FoodScreen(...)` composable and inner `FoodScreenContent(...)`; remove the two `viewModel.restOfDayInsightState` collection + the `restOfDay*` wiring at the call site (~line 107, 134-137). Add param `onAskCoachForMeals: () -> Unit = {}` to both, wire it at the outer composable from a hoisted callback (passed down from AppNavGraph in Task 5). Remove the now-unused imports (`GeneratedInsightCard`, `AiInsightState`, `InsightCardVariant` — only if unused elsewhere in the file; grep first).

- [ ] **Step 3: Delete the `RestOfDayReveal` composable** (~line 473-489) and replace with the new card:
```kotlin
// ── Meal suggestion ──────────────────────────────────────────────────────────

@Composable
private fun MealSuggestionCard(
    state: MealSuggestionCardState,
    onAskCoach: () -> Unit,
) {
    val appColors = LocalAppColors.current
    TintedCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("🍽", style = AppType.label)
                SectionLabel(text = "What to eat next")
            }
            Text(state.headline, style = AppType.cardTitle, color = appColors.textPrimary)
            if (state.libraryThin) {
                Text(
                    "Your library is thin here — ask the coach for ideas that fit.",
                    style = AppType.cardSubtitle, color = appColors.textMuted,
                )
            } else {
                state.suggestions.forEach { s ->
                    Text(
                        "${s.name} · ${s.amountLabel} · ${macroLine(state.focus, s.proteinG, s.carbsG)} · ${s.calories} kcal",
                        style = AppType.body, color = appColors.textSecondary,
                    )
                }
                state.combo?.let { c ->
                    Text(
                        "Combo: ${c.items.joinToString(" + ") { it.name }} · ${c.proteinG.roundToInt()} g P · ${c.calories} kcal",
                        style = AppType.cardSubtitle, color = appColors.textMuted,
                    )
                }
            }
            LiquidActionButton(
                text = "Get meal ideas",
                onClick = onAskCoach,
                isPrimary = true,
                small = true,
            )
        }
    }
}

private fun macroLine(focus: com.zack.recomptracker.domain.food.SuggestionFocus, proteinG: Double, carbsG: Double): String =
    when (focus) {
        com.zack.recomptracker.domain.food.SuggestionFocus.CARBS -> "${carbsG.roundToInt()} g carbs"
        else -> "${proteinG.roundToInt()} g protein"
    }
```
Add imports as needed: `TintedCard`, `SectionLabel`, `AppType`, `LiquidActionButton`, `LocalAppColors`, `Spacing`, `kotlin.math.roundToInt`, and `MealSuggestionCardState` (same package — no import).

- [ ] **Step 4: Delete** `RestOfDayInsightMapper.kt` and `RestOfDayInsightMapperTest.kt`:
```bash
git rm app/src/main/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapper.kt app/src/test/java/com/zack/recomptracker/ui/today/RestOfDayInsightMapperTest.kt
```

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS). Fix any dangling refs to removed params/imports.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/today/FoodScreen.kt
git commit -m "feat(food): MealSuggestionCard replaces the Rest-of-Day insight card"
```

---

## Task 5: `AppNavGraph` — `onAskCoachForMeals` handoff + Coach navigation

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt`

- [ ] **Step 1: Hoist the Food VM + wire the CTA.** In the `Routes.Food` composable (~line 211), change the inline `viewModel<FoodLogViewModel>(factory = factory)` to a hoisted val and pass `onAskCoachForMeals`:
```kotlin
        composable(route = Routes.Food, enterTransition = { tabEnter }, exitTransition = { tabExit }) {
            val foodLogViewModel = viewModel<FoodLogViewModel>(factory = factory)
            val appContainer = com.zack.recomptracker.ui.LocalAppContainer.current
            FoodScreen(
                viewModel = foodLogViewModel,
                onAddToSlot = { slotId, slotName, date -> /* unchanged */ },
                onBrowseLibrary = { navController.navigate(Routes.FoodLibrary) },
                onEditEntryAmount = { slotId, slotName, entryId, date -> /* unchanged */ },
                onCreateRecipeFromSelection = { ingredients -> /* unchanged */ },
                onAskCoachForMeals = {
                    foodLogViewModel.askCoachSeed()?.let { seed ->
                        appContainer.coachHandoffStore.set(seed)
                        appContainer.coachCoordinator.clearHistory()
                    }
                    navController.navigate(TopLevelDestination.Coach.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
```
(Keep the existing `onAddToSlot`/`onEditEntryAmount`/`onCreateRecipeFromSelection` bodies exactly as they were — only add the hoisted val + `onAskCoachForMeals`.) `LocalAppContainer` is imported in `AppNavGraph.kt` already (used for `factory`); `coachHandoffStore` and `coachCoordinator` are public vals on `AppContainer`.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ui/navigation/AppNavGraph.kt
git commit -m "feat(food): 'Get meal ideas' seeds a web-forward coach handoff + opens chat"
```

---

## Task 6: Full verification

- [ ] **Step 1: Targeted + full suite**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggester*" --tests "*MealSuggestionCardMapper*" --tests "*SuggestMeals*" --tests "*FoodLogViewModel*" --tests "*AiCoachBoundaryTest*"` (all green), then full `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail — known network test).

- [ ] **Step 2: APK**

Run: `./gradlew :app:assembleDebug` (Expected: BUILD SUCCESSFUL).

- [ ] **Step 3: On-device (user, deferred)**

On today's Food screen after ~15:00 with a real macro gap: the card shows the gap + portioned picks (+ combo); "Get meal ideas" opens the coach chat mid-suggestion. Before the gate or when on target, the card is absent.

---

## Notes for the implementer
- Deterministic thesis: the card's numbers all come from `MealSuggester`; the LLM is reached only via the CTA.
- DRY: `suggestForDay` and `toSuggestionFood` are the single sources for gap math and entity mapping.
- Do NOT touch the shared `REST_OF_DAY` insight engine (InsightRequest/InsightKind/RestOfDayInsightContext/prompt builder/coordinators/their tests) — only the Food-screen usage + `RestOfDayInsightMapper` are removed.
- `git add` only each task's explicit files — never `git add -A`.
- Follow the design system; `AiCoachBoundaryTest` must stay green (engine + mapper are pure).
