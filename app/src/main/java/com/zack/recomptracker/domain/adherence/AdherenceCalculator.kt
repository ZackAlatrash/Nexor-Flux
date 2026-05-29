package com.zack.recomptracker.domain.adherence

import java.time.LocalDate
import kotlin.math.abs

data class NutritionDay(
    val date: LocalDate,
    val calories: Int,
)

class AdherenceCalculator {
    fun calculate(
        days: List<NutritionDay>,
        targetCalories: Int,
        expectedDays: Int,
        tolerancePercent: Double = 0.10,
    ): Double {
        if (expectedDays <= 0 || targetCalories <= 0) return 0.0

        val lowerBound = targetCalories * (1.0 - tolerancePercent)
        val upperBound = targetCalories * (1.0 + tolerancePercent)
        val adherentDays = days
            .distinctBy { it.date }
            .count { it.calories > 0 && it.calories.toDouble() in lowerBound..upperBound }

        return (adherentDays.toDouble() / expectedDays.toDouble()) * 100.0
    }

    fun dailyAdherencePercent(calories: Int, targetCalories: Int): Double {
        if (calories <= 0 || targetCalories <= 0) return 0.0
        val delta = abs(calories - targetCalories).toDouble() / targetCalories.toDouble()
        return (100.0 - (delta * 100.0)).coerceIn(0.0, 100.0)
    }
}
