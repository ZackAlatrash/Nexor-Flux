package com.zack.recomptracker.domain.food

import kotlin.math.roundToInt

/** Macros expressed for a specific amount of food. As a per-100g base, all four are "per 100 g". */
data class FoodMacros(
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

object FoodScaling {
    const val MIN_GRAMS: Double = 1.0
    const val MIN_SERVINGS: Double = 0.1
    const val SERVING_STEP: Double = 0.5
    const val DEFAULT_SERVING_GRAMS: Double = 100.0

    /** Scales a per-100g base to [grams]. Calories round to the nearest Int; macros stay Double. */
    fun scale(basePer100: FoodMacros, grams: Double): FoodMacros {
        val factor = grams / 100.0
        return FoodMacros(
            calories = (basePer100.calories * factor).roundToInt(),
            proteinG = basePer100.proteinG * factor,
            carbsG = basePer100.carbsG * factor,
            fatG = basePer100.fatG * factor,
        )
    }

    fun gramsForServings(servings: Double, servingGrams: Double): Double = servings * servingGrams
}
