# Coach Meal Suggestions (stage 3a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A deterministic engine + `suggest_meals` chat tool that tells the user what to eat to hit their remaining macros (protein→carbs focus, portioned suggestions + combos from the food library).

**Architecture:** A pure `MealSuggester` (`domain/food`) computes the gap, focus macro, ranked portioned suggestions, and combos. `CoachToolExecutor.suggest_meals` feeds it plan targets − eaten totals + the food library and returns JSON. Prompt guidance tells the coach to present picks (library exact; web/knowledge approximate, preference-filtered) and offer to log.

**Tech Stack:** Kotlin, coroutines/Flow, JUnit + mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-07-02-coach-meal-suggestions-design.md`
**Branch:** `redesign/ai-coaching`. **Run in the MAIN checkout** (worktree isolation branches from the wrong base here). Each implementer verifies base FIRST: `git branch --show-current` == `redesign/ai-coaching` AND `grep -c "\"remember\"" app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt` ≥ 1 (prior features present). If not, STOP.

---

## File Structure
- `domain/food/MealSuggester.kt` — NEW: pure engine + DTOs (Task 1).
- `ai/CoachToolExecutor.kt` — `suggest_meals` method + dispatch (Task 2).
- `ai/CoachTools.kt` — `SUGGESTION_TOOL_SCHEMAS`, append to `CLOUD_COACH_TOOL_SCHEMAS` (NOT `COACH_WRITE_TOOLS`) (Task 2).
- `ai/CoachToolsAdapter.kt` — prompt guidance; `ai/CloudCoachCoordinator.kt` — status label (Task 3).
- Tests: `domain/food/MealSuggesterTest.kt`, `ai/CoachToolExecutorSuggestMealsTest.kt`.

**Build/verify:** `./gradlew :app:compileDebugKotlin` · `./gradlew :app:testDebugUnitTest --tests "*MealSuggester*" --tests "*SuggestMeals*" --tests "*AiCoachBoundaryTest*"` · full: `./gradlew :app:testDebugUnitTest` (only `InsightHarnessTest` may fail) · `./gradlew :app:assembleDebug`.

---

## Task 1: Pure `MealSuggester` engine

**Files:**
- Create: `app/src/main/java/com/zack/recomptracker/domain/food/MealSuggester.kt`
- Test: `app/src/test/java/com/zack/recomptracker/domain/food/MealSuggesterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSuggesterTest {
    private fun food(name: String, cal: Int, p: Double, c: Double, f: Double, grams: Double? = 100.0) =
        SuggestionFood(name, "serving", grams, cal, p, c, f)

    @Test fun `focus is protein when protein under 85 percent met with a gap`() {
        val r = MealSuggester.suggest(
            SuggestMacros(700, 40.0, 60.0, 20.0), proteinMetRatio = 0.5,
            library = listOf(food("Chicken", 165, 31.0, 0.0, 4.0)),
        )
        assertEquals(SuggestionFocus.PROTEIN, r.focus)
    }

    @Test fun `focus flips to carbs once protein is basically met`() {
        val r = MealSuggester.suggest(
            SuggestMacros(500, 8.0, 80.0, 20.0), proteinMetRatio = 0.95,
            library = listOf(food("Rice", 130, 2.7, 28.0, 0.3)),
        )
        assertEquals(SuggestionFocus.CARBS, r.focus)
    }

    @Test fun `focus is none when on target`() {
        val r = MealSuggester.suggest(SuggestMacros(0, 0.0, 0.0, 0.0), proteinMetRatio = 1.0, library = emptyList())
        assertEquals(SuggestionFocus.NONE, r.focus)
        assertTrue(r.suggestions.isEmpty())
    }

    @Test fun `ranks the higher protein-density food first`() {
        val r = MealSuggester.suggest(
            SuggestMacros(1200, 60.0, 0.0, 20.0), proteinMetRatio = 0.4,
            library = listOf(food("Rice", 130, 2.7, 28.0, 0.3), food("Chicken", 165, 31.0, 0.0, 4.0)),
        )
        assertEquals("Chicken", r.suggestions.first().name)
    }

    @Test fun `portions a single food to about half the protein gap in grams`() {
        val r = MealSuggester.suggest(
            SuggestMacros(700, 40.0, 0.0, 20.0), proteinMetRatio = 0.5,
            library = listOf(food("Chicken", 165, 31.0, 0.0, 4.0, grams = 100.0)),
        )
        val s = r.suggestions.single()
        // fill = 0.5*40 = 20 g protein; 20/31 = 0.645 servings → rounded to 0.5 → 50 g, 15.5 g protein, 83 kcal.
        assertEquals("≈50 g", s.amountLabel)
        assertEquals(15.5, s.proteinG, 0.01)
        assertEquals(83, s.calories)
    }

    @Test fun `never suggests more calories than remain`() {
        val r = MealSuggester.suggest(
            SuggestMacros(100, 200.0, 0.0, 20.0), proteinMetRatio = 0.2,
            library = listOf(food("Chicken", 165, 31.0, 0.0, 4.0)),
        )
        assertTrue(r.suggestions.all { it.calories <= 100 })
    }

    @Test fun `builds a protein-plus-carb combo when both are short, within calories`() {
        val r = MealSuggester.suggest(
            SuggestMacros(900, 50.0, 80.0, 20.0), proteinMetRatio = 0.3,
            library = listOf(food("Chicken", 165, 31.0, 0.0, 4.0), food("Rice", 130, 2.7, 28.0, 0.3)),
        )
        assertEquals(SuggestionFocus.PROTEIN, r.focus)
        assertTrue(r.combos.isNotEmpty())
        val combo = r.combos.first()
        assertEquals(2, combo.items.size)
        assertTrue(combo.calories <= 900)
    }

    @Test fun `library thin when no food contributes the focus macro`() {
        val r = MealSuggester.suggest(
            SuggestMacros(700, 40.0, 0.0, 20.0), proteinMetRatio = 0.5,
            library = listOf(food("Lettuce", 10, 0.0, 2.0, 0.0)),  // no protein
        )
        assertTrue(r.libraryThin)
        assertTrue(r.suggestions.isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggesterTest*"`
Expected: FAIL — `MealSuggester` / its DTOs don't exist.

- [ ] **Step 3: Implement the engine**

```kotlin
package com.zack.recomptracker.domain.food

import kotlin.math.roundToInt

enum class SuggestionFocus { PROTEIN, CARBS, CALORIES, NONE }

data class SuggestMacros(val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** A library food; macros are for one [servingLabel] serving. */
data class SuggestionFood(
    val name: String,
    val servingLabel: String,
    val gramsPerServing: Double?,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

data class MealSuggestion(
    val name: String, val amountLabel: String,
    val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double,
)

data class ComboItem(val name: String, val amountLabel: String)
data class MealCombo(
    val items: List<ComboItem>,
    val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double,
)

data class SuggestionResult(
    val remaining: SuggestMacros,
    val focus: SuggestionFocus,
    val suggestions: List<MealSuggestion>,
    val combos: List<MealCombo>,
    val libraryThin: Boolean,
)

/** Pure meal-suggestion engine. Deterministic; every number here, none from the LLM. */
object MealSuggester {
    private const val PROTEIN_MET = 0.85
    private const val MIN_PROTEIN_GAP = 5.0
    private const val MIN_CARB_GAP = 10.0
    private const val MIN_CAL_GAP = 100
    private const val FILL_FRACTION = 0.5
    private const val MAX_SUGGESTIONS = 5

    fun suggest(remaining: SuggestMacros, proteinMetRatio: Double, library: List<SuggestionFood>): SuggestionResult {
        val focus = when {
            proteinMetRatio < PROTEIN_MET && remaining.proteinG >= MIN_PROTEIN_GAP -> SuggestionFocus.PROTEIN
            remaining.carbsG >= MIN_CARB_GAP && remaining.calories > 0 -> SuggestionFocus.CARBS
            remaining.calories >= MIN_CAL_GAP -> SuggestionFocus.CALORIES
            else -> SuggestionFocus.NONE
        }
        if (focus == SuggestionFocus.NONE) {
            return SuggestionResult(remaining, focus, emptyList(), emptyList(), library.isEmpty())
        }
        fun perServingFocus(f: SuggestionFood): Double = when (focus) {
            SuggestionFocus.PROTEIN -> f.proteinG
            SuggestionFocus.CARBS -> f.carbsG
            else -> f.calories.toDouble()
        }
        val remainingFocus: Double = when (focus) {
            SuggestionFocus.PROTEIN -> remaining.proteinG
            SuggestionFocus.CARBS -> remaining.carbsG
            else -> remaining.calories.toDouble()
        }
        val ranked = library
            .filter { perServingFocus(it) > 0.0 && it.calories > 0 }
            .sortedByDescending { perServingFocus(it) / it.calories.toDouble() }
        val suggestions = ranked.mapNotNull { portion(it, perServingFocus(it), remainingFocus, remaining.calories) }
            .take(MAX_SUGGESTIONS)

        val combos = if (focus == SuggestionFocus.PROTEIN && remaining.carbsG >= MIN_CARB_GAP)
            buildCombo(library, remaining) else emptyList()

        return SuggestionResult(remaining, focus, suggestions, combos, libraryThin = suggestions.isEmpty())
    }

    /** Portion [food] to ~half of [remainingFocus] of its focus macro, capped to fit [remainingCalories]. */
    private fun portion(food: SuggestionFood, perServingFocus: Double, remainingFocus: Double, remainingCalories: Int): MealSuggestion? {
        if (perServingFocus <= 0.0 || food.calories <= 0) return null
        var servings = (FILL_FRACTION * remainingFocus) / perServingFocus
        if (remainingCalories > 0) servings = minOf(servings, remainingCalories.toDouble() / food.calories)
        servings = (servings * 2.0).roundToInt() / 2.0
        if (servings < 0.5) servings = 0.5
        return MealSuggestion(
            name = food.name,
            amountLabel = amountLabel(food, servings),
            calories = (food.calories * servings).roundToInt(),
            proteinG = round1(food.proteinG * servings),
            carbsG = round1(food.carbsG * servings),
            fatG = round1(food.fatG * servings),
        )
    }

    private fun amountLabel(food: SuggestionFood, servings: Double): String {
        val grams = food.gramsPerServing
        return if (grams != null && grams > 0) "≈${(grams * servings).roundToInt()} g"
        else "≈${tidy(servings)} × ${food.servingLabel}"
    }

    private fun buildCombo(library: List<SuggestionFood>, remaining: SuggestMacros): List<MealCombo> {
        val protein = library.filter { it.proteinG > 0 && it.calories > 0 }
            .maxByOrNull { it.proteinG / it.calories.toDouble() } ?: return emptyList()
        val carb = library.filter { it.carbsG > 0 && it.calories > 0 && it.name != protein.name }
            .maxByOrNull { it.carbsG / it.calories.toDouble() } ?: return emptyList()
        val pPick = portion(protein, protein.proteinG, remaining.proteinG, remaining.calories) ?: return emptyList()
        val calLeft = (remaining.calories - pPick.calories).coerceAtLeast(0)
        val cPick = portion(carb, carb.carbsG, remaining.carbsG, calLeft) ?: return emptyList()
        return listOf(
            MealCombo(
                items = listOf(ComboItem(pPick.name, pPick.amountLabel), ComboItem(cPick.name, cPick.amountLabel)),
                calories = pPick.calories + cPick.calories,
                proteinG = round1(pPick.proteinG + cPick.proteinG),
                carbsG = round1(pPick.carbsG + cPick.carbsG),
                fatG = round1(pPick.fatG + cPick.fatG),
            ),
        )
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun tidy(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*MealSuggesterTest*"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/domain/food/MealSuggester.kt app/src/test/java/com/zack/recomptracker/domain/food/MealSuggesterTest.kt
git commit -m "feat(coach): MealSuggester engine — remaining-macro gap, focus, portions, combos"
```

---

## Task 2: `suggest_meals` tool

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt`
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt`
- Test: `app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorSuggestMealsTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoachToolExecutorSuggestMealsTest {
    private val fixedDate = LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun food(name: String, cal: Int, p: Double, c: Double, f: Double) =
        SavedFoodEntity(name = name, servingName = "serving", calories = cal, proteinG = p, carbsG = c, fatG = f,
            householdServingGrams = 100.0)

    private fun executor(log: LogRepository, plan: PlanRepository) =
        CoachToolExecutor(logRepository = log, planRepository = plan, dateProvider = dateProvider)

    @Test fun `suggest_meals returns focus protein and a suggestion when protein is short`() = runTest {
        val log = mock<LogRepository>()
        val plan = mock<PlanRepository>()
        whenever(plan.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2000, targetProteinG = 150, targetCarbsG = 200, targetFatG = 60)))
        whenever(log.getDay(fixedDate)).thenReturn(
            DayLog(fixedDate, null, emptyList(), MacroTotals(calories = 1000, proteinG = 50.0, carbsG = 120.0, fatG = 30.0)),
        )
        whenever(log.getSavedFoods()).thenReturn(listOf(food("Chicken", 165, 31.0, 0.0, 4.0)))

        val json = executor(log, plan).execute("suggest_meals", emptyMap())
        assertTrue(json.contains("\"focus\":\"protein\""))
        assertTrue(json.contains("Chicken"))
        assertTrue(json.contains("remaining"))
    }

    @Test fun `suggest_meals reports library_thin when the library is empty`() = runTest {
        val log = mock<LogRepository>()
        val plan = mock<PlanRepository>()
        whenever(plan.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2000, targetProteinG = 150, targetCarbsG = 200, targetFatG = 60)))
        whenever(log.getDay(fixedDate)).thenReturn(DayLog(fixedDate, null, emptyList(), MacroTotals(calories = 1000, proteinG = 50.0, carbsG = 120.0, fatG = 30.0)))
        whenever(log.getSavedFoods()).thenReturn(emptyList())

        val json = executor(log, plan).execute("suggest_meals", emptyMap())
        assertTrue(json.contains("\"library_thin\":true"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorSuggestMealsTest*"`
Expected: FAIL — `suggest_meals` returns `unknown tool`.

- [ ] **Step 3: Implement the tool**

In `CoachToolExecutor.kt` add imports:
```kotlin
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestMacros
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import com.zack.recomptracker.domain.food.SuggestionResult
```
(`kotlinx.coroutines.flow.first` is already imported.)

Add the dispatch entry in `execute(...)`:
```kotlin
        "suggest_meals" -> suggestMeals(args)
```

Implement (reuse `esc()`):
```kotlin
    private suspend fun suggestMeals(args: Map<String, String>): String {
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val prefs = planRepository.preferences.first()
        val eaten = logRepository.getDay(date).totals
        val remaining = SuggestMacros(
            calories = (prefs.targetCalories - eaten.calories).coerceAtLeast(0),
            proteinG = (prefs.targetProteinG - eaten.proteinG).coerceAtLeast(0.0),
            carbsG = (prefs.targetCarbsG - eaten.carbsG).coerceAtLeast(0.0),
            fatG = (prefs.targetFatG - eaten.fatG).coerceAtLeast(0.0),
        )
        val proteinMetRatio = if (prefs.targetProteinG > 0) eaten.proteinG / prefs.targetProteinG else 1.0
        val library = logRepository.getSavedFoods().map {
            SuggestionFood(
                name = it.name, servingLabel = it.servingName, gramsPerServing = it.householdServingGrams,
                calories = it.calories, proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG,
            )
        }
        return serializeSuggestions(MealSuggester.suggest(remaining, proteinMetRatio, library))
    }

    private fun serializeSuggestions(r: SuggestionResult): String {
        val focus = when (r.focus) {
            SuggestionFocus.PROTEIN -> "protein"; SuggestionFocus.CARBS -> "carbs"
            SuggestionFocus.CALORIES -> "calories"; SuggestionFocus.NONE -> "none"
        }
        val rem = """{"calories":${r.remaining.calories},"protein_g":${r.remaining.proteinG},"carbs_g":${r.remaining.carbsG},"fat_g":${r.remaining.fatG}}"""
        if (r.focus == SuggestionFocus.NONE) {
            return """{"remaining":$rem,"focus":"none","message":"on target"}"""
        }
        val sugg = r.suggestions.joinToString(",") { s ->
            """{"name":"${s.name.esc()}","amount":"${s.amountLabel.esc()}","calories":${s.calories},"protein_g":${s.proteinG},"carbs_g":${s.carbsG},"fat_g":${s.fatG},"exact":true}"""
        }
        val combos = r.combos.joinToString(",") { c ->
            val items = c.items.joinToString(",") { """{"name":"${it.name.esc()}","amount":"${it.amountLabel.esc()}"}""" }
            """{"items":[$items],"calories":${c.calories},"protein_g":${c.proteinG},"carbs_g":${c.carbsG},"fat_g":${c.fatG}}"""
        }
        return """{"remaining":$rem,"focus":"$focus","suggestions":[$sugg],"combos":[$combos],"library_thin":${r.libraryThin}}"""
    }
```

- [ ] **Step 4: Add the schema in `CoachTools.kt` (NOT in COACH_WRITE_TOOLS)**

```kotlin
/** Cloud-coach meal-suggestion tool. Read-only — not in COACH_WRITE_TOOLS. */
val SUGGESTION_TOOL_SCHEMAS: List<String> = listOf(
    """{"name":"suggest_meals","description":"Get what the user could eat to hit their remaining macros for the day. Returns remaining calories/macros, the focus macro (protein until it's ~met, then carbs), portioned suggestions from their food library (exact macros), and combos. Use for 'what should I eat', 'how do I hit my protein', 'I have calories left'.","parameters":{"type":"object","properties":{"date":{"type":"string","description":"Optional ISO date; omit for today"}},"required":[]}}""",
)
```

Append to the cloud list only:
```kotlin
val CLOUD_COACH_TOOL_SCHEMAS: List<String> = COACH_TOOL_SCHEMAS + SEARCH_WEB_TOOL_SCHEMA + ROUTINE_TOOL_SCHEMAS + MEAL_EDIT_TOOL_SCHEMAS + MEMORY_TOOL_SCHEMAS + SUGGESTION_TOOL_SCHEMAS
```

Do NOT touch `COACH_WRITE_TOOLS`.

- [ ] **Step 5: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "*CoachToolExecutorSuggestMealsTest*"` (Expected: 2 pass) and `./gradlew :app:compileDebugKotlin` (Expected: SUCCESS).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolExecutor.kt app/src/main/java/com/zack/recomptracker/ai/CoachTools.kt app/src/test/java/com/zack/recomptracker/ai/CoachToolExecutorSuggestMealsTest.kt
git commit -m "feat(coach): suggest_meals tool over the MealSuggester engine"
```

---

## Task 3: Prompt guidance + status label

**Files:**
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt` (`COACH_PROMPT_GUIDELINES`)
- Modify: `app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt` (`toolStatusText`)

- [ ] **Step 1: Append prompt guidance**

Add to `COACH_PROMPT_GUIDELINES` (match the `"- …\n" +` format):
```
- When the user asks what to eat, or how to hit their remaining macros/protein, call suggest_meals and recommend 2-3 concrete options WITH amounts. Library items (exact:true) have exact macros — state them confidently; for web (search_web) or your own recipe ideas, say the macros are approximate and cite the source. If library_thin is true or they want variety, use search_web or your knowledge for a recipe that fits the remaining macros, respecting the user's memory (diet, allergies, dislikes). After they pick one, offer to log it with log_meal.
```

- [ ] **Step 2: Add the status label**

In `CloudCoachCoordinator.toolStatusText`, add:
```kotlin
        "suggest_meals" -> "Planning your meals…"
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/zack/recomptracker/ai/CoachToolsAdapter.kt app/src/main/java/com/zack/recomptracker/ai/CloudCoachCoordinator.kt
git commit -m "feat(coach): system-prompt routing + status label for suggest_meals"
```

---

## Task 4: Full verification

- [ ] **Step 1: Boundary + full suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: only `InsightHarnessTest` fails (known network test); `AiCoachBoundaryTest` and all else green.

- [ ] **Step 2: APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device (user)**

In chat: *"what should I eat to hit my protein?"* → the coach suggests portioned options from the library (and web/knowledge if configured), respecting coach memory, and offers to log a pick.

---

## Notes for the implementer
- `suggest_meals` is READ-ONLY — do NOT add it to `COACH_WRITE_TOOLS` (no confirm dialog).
- Reuse the existing private `esc()`; tool returns a JSON string matching the existing contract.
- Plan targets come from `planRepository.preferences.first()` (`PlanPreferences.targetCalories/targetProteinG/targetCarbsG/targetFatG`; protein/carbs/fat are `Int`). Eaten from `logRepository.getDay(date).totals` (`MacroTotals`).
- The engine is pure (`domain/food`) — no Android imports; keeps `AiCoachBoundaryTest` green.
- `git add` only each task's files by explicit path — never `git add -A` (unrelated TEMP DEBUG commits exist).
