package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorTest {
    private val calculator = AdherenceCalculator()

    @Test
    fun noExpectedDaysReturnsZero() {
        assertEquals(0.0, calculator.calculate(emptyList(), targetCalories = 2550, expectedDays = 0), 0.001)
    }

    @Test
    fun countsOnlyLoggedDaysInsideCalorieTolerance() {
        val start = LocalDate.of(2026, 5, 1)
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 2700),
            NutritionDay(start.plusDays(2), calories = 3200),
            NutritionDay(start.plusDays(3), calories = 0),
        )

        val adherence = calculator.calculate(days, targetCalories = 2550, expectedDays = 4)

        assertEquals(50.0, adherence, 0.001)
    }

    @Test
    fun missingDaysLowerAdherence() {
        val start = LocalDate.of(2026, 5, 1)
        val days = listOf(
            NutritionDay(start, calories = 2550),
            NutritionDay(start.plusDays(1), calories = 2500),
        )

        val adherence = calculator.calculate(days, targetCalories = 2550, expectedDays = 7)

        assertEquals(28.571, adherence, 0.01)
    }
}
