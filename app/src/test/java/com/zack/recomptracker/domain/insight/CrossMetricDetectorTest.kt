package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CrossMetricDetectorTest {

    private val base = LocalDate.of(2026, 6, 1)
    private val proteinTarget = 165

    private fun day(offset: Int, proteinG: Double, logged: Boolean = true) = DayNutrition(
        date = base.plusDays(offset.toLong()),
        calories = if (logged) 2000 else 0,
        proteinG = proteinG,
        carbsG = 200.0,
        fatG = 60.0,
        logged = logged,
    )

    @Test
    fun `surfaces the link when hunger is clearly lower on protein-hit days`() {
        // 3 hit days (hunger ~3) vs 3 miss days (hunger ~7)
        val days = listOf(
            day(0, 170.0), day(1, 175.0), day(2, 168.0),
            day(3, 90.0), day(4, 100.0), day(5, 95.0),
        )
        val hunger = mapOf(
            base.plusDays(0) to 3, base.plusDays(1) to 2, base.plusDays(2) to 4,
            base.plusDays(3) to 7, base.plusDays(4) to 8, base.plusDays(5) to 6,
        )
        val fact = CrossMetricDetector.detectProteinHungerLink(days, hunger, proteinTarget)!!
        assertEquals(InsightFactType.CROSS_METRIC, fact.type)
        assertTrue("statement should mention protein", fact.statement.contains("protein", ignoreCase = true))
        assertTrue("statement should mention hunger", fact.statement.contains("hunger", ignoreCase = true))
        assertTrue("priority should be positive", fact.priority > 0)
    }

    @Test
    fun `returns null when hunger barely differs`() {
        val days = listOf(day(0, 170.0), day(1, 175.0), day(2, 90.0), day(3, 95.0))
        val hunger = mapOf(
            base.plusDays(0) to 5, base.plusDays(1) to 5,
            base.plusDays(2) to 5, base.plusDays(3) to 6,
        )
        assertNull(CrossMetricDetector.detectProteinHungerLink(days, hunger, proteinTarget))
    }

    @Test
    fun `returns null without enough days in each group`() {
        // only 1 hit day
        val days = listOf(day(0, 170.0), day(1, 90.0), day(2, 95.0))
        val hunger = mapOf(base.plusDays(0) to 3, base.plusDays(1) to 7, base.plusDays(2) to 8)
        assertNull(CrossMetricDetector.detectProteinHungerLink(days, hunger, proteinTarget))
    }

    @Test
    fun `ignores unlogged days and days without a hunger score`() {
        val days = listOf(
            day(0, 170.0), day(1, 175.0), day(2, 168.0),
            day(3, 90.0), day(4, 100.0), day(5, 0.0, logged = false),
        )
        val hunger = mapOf(
            base.plusDays(0) to 3, base.plusDays(1) to 2, base.plusDays(2) to 4,
            base.plusDays(3) to 7, base.plusDays(4) to 8,
            // day 5 unlogged and no hunger
        )
        val fact = CrossMetricDetector.detectProteinHungerLink(days, hunger, proteinTarget)
        assertTrue("should still detect from the valid days", fact != null)
    }
}
