package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestMacros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedFoodSuggestionTest {

    /**
     * SavedFoodEntity macros are stored PER 100 g (see FoodScaling / basePer100Calories). The
     * MealSuggester engine treats a food's macros as "one serving" and [SuggestionFood.gramsPerServing]
     * as the grams of that serving. So the correct basis to hand the engine is 100 g — NOT the
     * household serving size — otherwise the gram label and the macros disagree for any food whose
     * household serving ≠ 100 g.
     */
    @Test fun `toSuggestionFood uses a per-100g basis regardless of household serving size`() {
        val whey = SavedFoodEntity(
            name = "Whey", servingName = "scoop", calories = 400,
            proteinG = 80.0, carbsG = 5.0, fatG = 5.0,
            householdServingName = "scoop", householdServingGrams = 30.0,
        )
        val sf = whey.toSuggestionFood()
        assertEquals(100.0, sf.gramsPerServing!!, 0.0)
        assertEquals(400, sf.calories)
        assertEquals(80.0, sf.proteinG, 0.0)
    }

    /** The engine's gram label must match the macros it reports: 200 kcal of whey ≈ 50 g, not 15 g. */
    @Test fun `portioned whey label matches its macros`() {
        val whey = SavedFoodEntity(
            name = "Whey", servingName = "scoop", calories = 400,
            proteinG = 80.0, carbsG = 0.0, fatG = 0.0, householdServingGrams = 30.0,
        ).toSuggestionFood()
        // 40 g protein gap, protein focus → fill ~half (20 g) → 0.5×100 g basis = 50 g whey = 200 kcal, 40 g P.
        val r = MealSuggester.suggest(SuggestMacros(700, 40.0, 0.0, 20.0), proteinMetRatio = 0.5, library = listOf(whey))
        val s = r.suggestions.single()
        assertEquals("≈50 g", s.amountLabel)
        assertEquals(200, s.calories)
        assertEquals(40.0, s.proteinG, 0.01)
        // Sanity: reported calories are consistent with the labelled grams (per-100g basis).
        assertTrue(s.calories in 195..205)
    }
}
