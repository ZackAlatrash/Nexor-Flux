package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PatternDetectorsTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    /** 14 consecutive days ending [end]; [calsFor] maps each date to its calories. proteins/etc default to target. */
    private fun days(
        end: LocalDate = LocalDate.of(2026, 6, 14),
        logged: (LocalDate) -> Boolean = { true },
        calsFor: (LocalDate) -> Int,
        proteinFor: (LocalDate) -> Double = { targets.proteinG.toDouble() },
    ): List<DayNutrition> = (0..13).map { offset ->
        val d = end.minusDays((13 - offset).toLong())
        DayNutrition(d, calsFor(d), proteinFor(d), targets.carbsG.toDouble(), targets.fatG.toDouble(), logged(d))
    }

    private fun isWeekend(d: LocalDate) =
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    @Test
    fun `weekday-weekend fires when weekend calories are much higher`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2800 else 2200 }),
            targets,
        )
        assertEquals(InsightFactType.WEEKDAY_WEEKEND, fact?.type)
        assertTrue(fact!!.statement.contains("2800"))
        assertTrue(fact.statement.contains("2200"))
        assertTrue(fact.statement.contains("+600"))
    }

    @Test
    fun `weekday-weekend does not fire when gap is small`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2250 else 2200 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `weekday-weekend needs enough weekend days`() {
        // Only weekdays logged → fewer than 2 weekend samples.
        val fact = detectWeekdayWeekend(
            days(logged = { !isWeekend(it) }, calsFor = { 2800 }),
            targets,
        )
        assertNull(fact)
    }
}
