package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InsightEngineTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    @Test
    fun `returns null when no detector fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val calm = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            DayNutrition(d, 2200, 164.0, 320.0, 68.0, logged = true)
        }
        assertNull(InsightEngine.detectTopFact(calm, targets))
    }

    @Test
    fun `picks the highest-priority fact`() {
        // Weekend spike (weekday/weekend fires) AND a single derailment day → derailment has higher base priority.
        val end = LocalDate.of(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            // last 7 days carry one big spike for derailment
            val cals = when {
                offset == 13 -> 3700
                weekend -> 2700
                else -> 2200
            }
            DayNutrition(d, cals, 164.0, 320.0, 68.0, logged = true)
        }
        val fact = InsightEngine.detectTopFact(list, targets)
        assertEquals(InsightFactType.DERAILMENT_DAY, fact?.type)
    }
}
