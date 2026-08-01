package com.zack.recomptracker.domain.adherence

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorPerDayTest {
    private val calc = AdherenceCalculator()
    private val d0 = LocalDate.parse("2026-06-01")

    @Test fun `each day is graded against its own target`() {
        val days = listOf(
            NutritionDay(d0, calories = 2500, targetCalories = 2500),
            NutritionDay(d0.plus(1, DateTimeUnit.DAY), calories = 2200, targetCalories = 2200),
        )
        assertEquals(100.0, calc.calculate(days), 0.001)
    }

    @Test fun `unlogged days are excluded`() {
        val days = listOf(
            NutritionDay(d0, calories = 0, targetCalories = 2500),
            NutritionDay(d0.plus(1, DateTimeUnit.DAY), calories = 2200, targetCalories = 2200),
        )
        assertEquals(100.0, calc.calculate(days), 0.001)
    }
}
