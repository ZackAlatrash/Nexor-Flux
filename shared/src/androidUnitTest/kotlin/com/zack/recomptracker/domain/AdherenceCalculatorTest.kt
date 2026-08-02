package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Test

class AdherenceCalculatorTest {
    private val calculator = AdherenceCalculator()
    private val start = LocalDate(2026, 5, 1)

    // --- calculate: adherence quality (graded average over LOGGED days only) ---

    @Test
    fun calculateReturnsZeroWhenNoLoggedDays() {
        val days = listOf(NutritionDay(start, calories = 0, targetCalories = 2550))
        assertEquals(0.0, calculator.calculate(days), 0.001)
    }

    @Test
    fun calculateReturnsZeroWhenTargetInvalid() {
        val days = listOf(NutritionDay(start, calories = 2550, targetCalories = 0))
        assertEquals(0.0, calculator.calculate(days), 0.001)
    }

    @Test
    fun calculateIsOneHundredWhenAllLoggedDaysOnTarget() {
        val days = listOf(
            NutritionDay(start, calories = 2550, targetCalories = 2550),
            NutritionDay(start.plus(1, DateTimeUnit.DAY), calories = 2550, targetCalories = 2550),
        )
        assertEquals(100.0, calculator.calculate(days), 0.001)
    }

    @Test
    fun calculateAveragesGradedScoresOverLoggedDaysOnly() {
        // Day 1: exactly on target -> 100
        // Day 2: 20% over (3060) -> 80
        // Day 3: not logged -> excluded from the average entirely
        val days = listOf(
            NutritionDay(start, calories = 2550, targetCalories = 2550),
            NutritionDay(start.plus(1, DateTimeUnit.DAY), calories = 3060, targetCalories = 2550),
            NutritionDay(start.plus(2, DateTimeUnit.DAY), calories = 0, targetCalories = 2550),
        )
        // (100 + 80) / 2 logged days = 90
        assertEquals(90.0, calculator.calculate(days), 0.001)
    }

    @Test
    fun calculateDeDuplicatesByDate() {
        val days = listOf(
            NutritionDay(start, calories = 2550, targetCalories = 2550),
            NutritionDay(start, calories = 0, targetCalories = 2550),
        )
        assertEquals(100.0, calculator.calculate(days), 0.001)
    }

    // --- loggingConsistency: logged days / expected days ---

    @Test
    fun loggingConsistencyZeroWhenNoExpectedDays() {
        assertEquals(0.0, calculator.loggingConsistency(emptyList(), expectedDays = 0), 0.001)
    }

    @Test
    fun loggingConsistencyCountsLoggedDaysOverExpected() {
        val days = listOf(
            NutritionDay(start, calories = 2550, targetCalories = 2550),
            NutritionDay(start.plus(1, DateTimeUnit.DAY), calories = 2500, targetCalories = 2550),
        )
        assertEquals(28.571, calculator.loggingConsistency(days, expectedDays = 7), 0.01)
    }

    @Test
    fun loggingConsistencyIgnoresZeroCalorieDays() {
        val days = listOf(
            NutritionDay(start, calories = 2550, targetCalories = 2550),
            NutritionDay(start.plus(1, DateTimeUnit.DAY), calories = 0, targetCalories = 2550),
        )
        assertEquals(50.0, calculator.loggingConsistency(days, expectedDays = 2), 0.001)
    }

    // --- dailyAdherencePercent: per-day graded primitive ---

    @Test
    fun dailyAdherenceIsHundredOnTarget() {
        assertEquals(100.0, calculator.dailyAdherencePercent(2550, 2550), 0.001)
    }

    @Test
    fun dailyAdherenceGradesDeviation() {
        // 10% off -> 90
        assertEquals(90.0, calculator.dailyAdherencePercent(2805, 2550), 0.001)
    }

    @Test
    fun dailyAdherenceZeroWhenNotLogged() {
        assertEquals(0.0, calculator.dailyAdherencePercent(0, 2550), 0.001)
    }
}
