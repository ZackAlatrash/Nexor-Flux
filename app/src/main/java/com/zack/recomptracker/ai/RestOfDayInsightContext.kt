package com.zack.recomptracker.ai

import kotlin.math.roundToInt

data class RestOfDayInsightContext(
    val caloriesConsumed: Int,
    val targetCalories: Int,
    val calorieZoneLowerBound: Int,
    val calorieZoneUpperBound: Int,
    val proteinConsumedG: Double,
    val proteinTargetG: Int,
    val mealsLoggedCount: Int,
    /** 0.0-1.0 of the eating day elapsed, so the card can judge pace. */
    val fractionOfDayElapsed: Double? = null,
) {
    val hasSufficientData: Boolean
        get() = mealsLoggedCount >= 1

    fun key(): String =
        "$caloriesConsumed|${proteinConsumedG.roundToInt()}|$mealsLoggedCount"
}
