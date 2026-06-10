package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.preferences.PlanPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightMapperTest {

    private val target = PlanPreferences(
        targetCalories = 2200,
        targetProteinG = 165,
        calorieZoneLowerBound = 2100,
        calorieZoneUpperBound = 2300,
    )

    @Test
    fun `maps totals and target into context`() {
        val ctx = buildRestOfDayInsightContext(
            totals = MacroTotals(calories = 1420, proteinG = 102.0),
            target = target,
            mealsLoggedCount = 2,
        )
        assertEquals(1420, ctx.caloriesConsumed)
        assertEquals(2200, ctx.targetCalories)
        assertEquals(2100, ctx.calorieZoneLowerBound)
        assertEquals(2300, ctx.calorieZoneUpperBound)
        assertEquals(102.0, ctx.proteinConsumedG, 0.001)
        assertEquals(165, ctx.proteinTargetG)
        assertEquals(2, ctx.mealsLoggedCount)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `zero meals is insufficient`() {
        val ctx = buildRestOfDayInsightContext(MacroTotals(), target, mealsLoggedCount = 0)
        assertFalse(ctx.hasSufficientData)
    }
}
