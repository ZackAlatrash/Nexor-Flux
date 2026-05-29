package com.zack.recomptracker.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class MacroTotals(
    val calories: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: MacroTotals) = MacroTotals(
        calories = calories + other.calories,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )

    val calculatedCalories: Int
        get() = ((proteinG * 4.0) + (carbsG * 4.0) + (fatG * 9.0)).roundToInt()
}
