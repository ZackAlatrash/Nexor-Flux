package com.zack.recomptracker.domain.food

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodScalingTest {
    private val base = FoodMacros(calories = 120, proteinG = 24.0, carbsG = 3.0, fatG = 2.0)

    @Test
    fun scalesPer100gByGrams() {
        val result = FoodScaling.scale(base, grams = 60.0)
        assertEquals(72, result.calories)
        assertEquals(14.4, result.proteinG, 0.001)
        assertEquals(1.8, result.carbsG, 0.001)
        assertEquals(1.2, result.fatG, 0.001)
    }

    @Test
    fun roundsCaloriesToNearestInt() {
        // 120 kcal/100g * 33g = 39.6 -> 40
        assertEquals(40, FoodScaling.scale(base, grams = 33.0).calories)
    }

    @Test
    fun convertsServingsToGrams() {
        assertEquals(90.0, FoodScaling.gramsForServings(servings = 3.0, servingGrams = 30.0), 0.001)
    }

    @Test
    fun clampsGramsToMinimum() {
        assertEquals(FoodScaling.MIN_GRAMS, FoodScaling.normalizeGrams(0.0), 0.001)
        assertEquals(FoodScaling.MIN_GRAMS, FoodScaling.normalizeGrams(-5.0), 0.001)
        assertEquals(250.0, FoodScaling.normalizeGrams(250.0), 0.001)
    }
}
