package com.zack.recomptracker.domain.insight

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightEngineTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    @Test
    fun `returns null when no detector fires`() {
        val end = LocalDate(2026, 6, 14)
        val calm = (0..13).map { offset ->
            val d = end.minus(13 - offset, DateTimeUnit.DAY)
            DayNutrition(d, 2200, 164.0, 320.0, 68.0, logged = true)
        }
        assertNull(InsightEngine.detectTopFact(calm, targets))
    }

    @Test
    fun `picks the highest-priority fact`() {
        // Weekend spike (weekday/weekend fires) AND a single derailment day → derailment has higher base priority.
        val end = LocalDate(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minus(13 - offset, DateTimeUnit.DAY)
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

    @Test
    fun `detectTopFacts returns ranked facts including the second place`() {
        // A weekend spike (weekday/weekend fires) AND one big derailment day → two distinct facts.
        val end = LocalDate(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minus(13 - offset, DateTimeUnit.DAY)
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val cals = when {
                offset == 13 -> 3700
                weekend -> 2700
                else -> 2200
            }
            DayNutrition(d, cals, 164.0, 320.0, 68.0, logged = true)
        }
        val facts = InsightEngine.detectTopFacts(list, targets, n = 2)
        assertEquals(2, facts.size)
        // Ranked highest-priority first, and the single-fact API agrees with the top of the list.
        assertEquals(InsightFactType.DERAILMENT_DAY, facts[0].type)
        assertEquals(InsightEngine.detectTopFact(list, targets), facts[0])
        assertTrue(facts[0].priority >= facts[1].priority)
        // The 2nd-place fact (which the old single-fact card discarded) is a different type.
        assertEquals(InsightFactType.WEEKDAY_WEEKEND, facts[1].type)
    }

    @Test
    fun `detectTopFacts returns empty when no pattern fires`() {
        val end = LocalDate(2026, 6, 14)
        val calm = (0..13).map { offset ->
            val d = end.minus(13 - offset, DateTimeUnit.DAY)
            DayNutrition(d, 2200, 164.0, 320.0, 68.0, logged = true)
        }
        assertTrue(InsightEngine.detectTopFacts(calm, targets, n = 2).isEmpty())
    }
}
