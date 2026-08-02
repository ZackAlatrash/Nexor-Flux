package com.zack.recomptracker.domain.plan

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanHistoryTest {
    private fun targets(cal: Int) = PlanTargets(
        calories = cal, proteinG = 1, carbsG = 1, fatG = 1,
        zoneLowerBound = cal - 100, zoneUpperBound = cal + 100,
    )
    private fun version(date: String, cal: Int) = PlanVersion(LocalDate.parse(date), targets(cal))

    @Test fun `planOn returns the version effective on the date`() {
        val versions = listOf(version("2026-01-01", 2500), version("2026-06-01", 2200))
        assertEquals(2500, PlanHistory.planOn(versions, LocalDate.parse("2026-03-15")).calories)
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2026-06-01")).calories)
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2026-12-31")).calories)
    }

    @Test fun `planOn clamps to earliest version for dates before history`() {
        val versions = listOf(version("2026-06-01", 2200))
        assertEquals(2200, PlanHistory.planOn(versions, LocalDate.parse("2020-01-01")).calories)
    }

    @Test fun `planOn with unsorted input still resolves correctly`() {
        val versions = listOf(version("2026-06-01", 2200), version("2026-01-01", 2500))
        assertEquals(2500, PlanHistory.planOn(versions, LocalDate.parse("2026-02-01")).calories)
    }

    @Test fun `resolve maps each requested date to its version`() {
        val versions = listOf(version("2026-01-01", 2500), version("2026-06-01", 2200))
        val dates = listOf(LocalDate.parse("2026-05-31"), LocalDate.parse("2026-06-02"))
        val map = PlanHistory.resolve(versions, dates)
        assertEquals(2500, map.getValue(dates[0]).calories)
        assertEquals(2200, map.getValue(dates[1]).calories)
    }

    @Test fun `planOnOrFallback returns fallback when history empty, else resolves`() {
        val fb = targets(1999)
        assertEquals(1999, PlanHistory.planOnOrFallback(emptyList(), LocalDate.parse("2026-06-01"), fb).calories)
        val versions = listOf(version("2026-01-01", 2500))
        assertEquals(2500, PlanHistory.planOnOrFallback(versions, LocalDate.parse("2026-06-01"), fb).calories)
    }

    @Test fun `targetsChanged detects calorie, macro and zone differences only`() {
        val a = targets(2500)
        assertFalse(PlanHistory.targetsChanged(a, a.copy()))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(calories = 2400)))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(proteinG = 200)))
        assertTrue(PlanHistory.targetsChanged(a, a.copy(zoneLowerBound = 0)))
    }
}
