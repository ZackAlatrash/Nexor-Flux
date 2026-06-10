package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightContextTest {

    private fun ctx(
        caloriesConsumed: Int = 1420,
        targetCalories: Int = 2200,
        calorieZoneLowerBound: Int = 2100,
        calorieZoneUpperBound: Int = 2300,
        proteinConsumedG: Double = 102.0,
        proteinTargetG: Int = 165,
        mealsLoggedCount: Int = 2,
    ) = RestOfDayInsightContext(
        caloriesConsumed, targetCalories, calorieZoneLowerBound,
        calorieZoneUpperBound, proteinConsumedG, proteinTargetG, mealsLoggedCount,
    )

    @Test
    fun `sufficient when at least one meal logged`() {
        assertTrue(ctx(mealsLoggedCount = 1).hasSufficientData)
    }

    @Test
    fun `insufficient when no meals logged`() {
        assertFalse(ctx(mealsLoggedCount = 0).hasSufficientData)
    }

    @Test
    fun `key changes when calories consumed changes`() {
        assertTrue(ctx(caloriesConsumed = 1420).key() != ctx(caloriesConsumed = 1800).key())
    }

    @Test
    fun `key stable for identical inputs`() {
        assertEquals(ctx().key(), ctx().key())
    }
}
