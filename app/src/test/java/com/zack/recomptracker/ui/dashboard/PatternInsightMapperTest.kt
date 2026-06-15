package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.insight.DayNutrition
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PatternInsightMapperTest {

    private val prefs = PlanPreferences(
        targetCalories = 2200, targetProteinG = 165, targetCarbsG = 320, targetFatG = 68,
        calorieZoneLowerBound = 2100, calorieZoneUpperBound = 2300,
    )

    @Test
    fun `returns null when no pattern fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val days = (0..13).map { offset ->
            DayNutrition(end.minusDays((13 - offset).toLong()), 2200, 164.0, 320.0, 68.0, logged = true)
        }
        assertNull(buildPatternInsightContext(days, prefs))
    }

    @Test
    fun `returns a context when a pattern fires`() {
        val end = LocalDate.of(2026, 6, 14)
        val days = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            DayNutrition(d, if (weekend) 2900 else 2200, 164.0, 320.0, 68.0, logged = true)
        }
        val ctx = buildPatternInsightContext(days, prefs)
        assertNotNull(ctx)
        assertNotNull(ctx!!.fact)
    }
}
