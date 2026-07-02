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

    @Test fun `drops a food whose smallest half-serving exceeds remaining calories`() {
        val r = MealSuggester.suggest(
            SuggestMacros(100, 40.0, 0.0, 20.0), proteinMetRatio = 0.5,
            library = listOf(food("Steak", 300, 30.0, 0.0, 20.0)),  // 0.5 serving = 150 kcal > 100 left
        )
        assertTrue(r.suggestions.none { it.name == "Steak" })
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
        assertEquals(SuggestionFocus.NONE, r.focus)
    }
}
