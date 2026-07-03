package com.zack.recomptracker.domain.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealImpactTest {

    // Baseline plan used across cases.
    private val targetCalories = 2000
    private val targetProtein = 150
    private val targetCarbs = 200

    @Test
    fun projectsPostLogPercentsForEachMacro() {
        // Eaten: 100g protein, 100g carbs. Add: 20g protein, 20g carbs.
        // protein 120/150 = 80%, carbs 120/200 = 60%.
        val result = MealImpact.compute(
            eatenCalories = 800, eatenProtein = 100.0, eatenCarbs = 100.0,
            targetCalories = targetCalories, targetProtein = targetProtein, targetCarbs = targetCarbs,
            addCalories = 200, addProtein = 20.0, addCarbs = 20.0,
        )!!
        assertEquals(80, result.protein.percent)
        assertEquals(60, result.carbs.percent)
        assertFalse(result.protein.over)
        assertFalse(result.carbs.over)
    }

    @Test
    fun overCarbHint_whenCarbsPushedPastTarget() {
        // carbs 190 + 30 = 220 / 200 = 110% (> 105%), calories still in range.
        val result = MealImpact.compute(
            eatenCalories = 1000, eatenProtein = 140.0, eatenCarbs = 190.0,
            targetCalories = targetCalories, targetProtein = targetProtein, targetCarbs = targetCarbs,
            addCalories = 200, addProtein = 5.0, addCarbs = 30.0,
        )!!
        assertTrue(result.carbs.over)
        assertTrue(result.hint.contains("carbs", ignoreCase = true))
    }

    @Test
    fun overCalorieHint_takesPrecedenceOverCarbs() {
        // calories 1900 + 300 = 2200 / 2000 = 110% (> 105%) → calorie hint first.
        val result = MealImpact.compute(
            eatenCalories = 1900, eatenProtein = 140.0, eatenCarbs = 190.0,
            targetCalories = targetCalories, targetProtein = targetProtein, targetCarbs = targetCarbs,
            addCalories = 300, addProtein = 5.0, addCarbs = 30.0,
        )!!
        assertTrue(result.calories.over)
        assertTrue(result.hint.contains("calorie", ignoreCase = true))
    }

    @Test
    fun proteinGapHint_whenProteinStillLow() {
        // protein 40 + 10 = 50 / 150 = 33% (< 85%), nothing over.
        val result = MealImpact.compute(
            eatenCalories = 500, eatenProtein = 40.0, eatenCarbs = 50.0,
            targetCalories = targetCalories, targetProtein = targetProtein, targetCarbs = targetCarbs,
            addCalories = 100, addProtein = 10.0, addCarbs = 10.0,
        )!!
        assertFalse(result.calories.over)
        assertFalse(result.carbs.over)
        assertTrue(result.hint.contains("protein", ignoreCase = true))
    }

    @Test
    fun fitsYourDay_whenBalancedAndNothingOver() {
        // protein 130 + 10 = 140 / 150 = 93% (>= 85%), carbs & calories comfortably under.
        val result = MealImpact.compute(
            eatenCalories = 1000, eatenProtein = 130.0, eatenCarbs = 100.0,
            targetCalories = targetCalories, targetProtein = targetProtein, targetCarbs = targetCarbs,
            addCalories = 100, addProtein = 10.0, addCarbs = 20.0,
        )!!
        assertFalse(result.calories.over)
        assertFalse(result.carbs.over)
        assertEquals("Fits your day.", result.hint)
    }

    @Test
    fun hiddenWhenNoTargets_returnsNull() {
        val result = MealImpact.compute(
            eatenCalories = 500, eatenProtein = 40.0, eatenCarbs = 50.0,
            targetCalories = 0, targetProtein = 0, targetCarbs = 0,
            addCalories = 100, addProtein = 10.0, addCarbs = 10.0,
        )
        assertNull(result)
    }

    @Test
    fun perMacroPercentIsZero_whenThatTargetIsMissingButOthersExist() {
        // No protein target, but calorie + carb targets present → still computes, protein % = 0.
        val result = MealImpact.compute(
            eatenCalories = 500, eatenProtein = 40.0, eatenCarbs = 50.0,
            targetCalories = targetCalories, targetProtein = 0, targetCarbs = targetCarbs,
            addCalories = 100, addProtein = 10.0, addCarbs = 10.0,
        )!!
        assertEquals(0, result.protein.percent)
        assertFalse(result.protein.over)
        // With no protein target, the protein-gap branch is skipped → neutral line.
        assertEquals("Fits your day.", result.hint)
    }
}
