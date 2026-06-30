package com.zack.recomptracker.domain.activity

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivitySummaryTest {

    private val today = LocalDate.of(2026, 6, 30)

    @Test
    fun `workoutDays unions sessions and trained logs, de-duplicating`() {
        val sessions = listOf(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30))
        val trained = listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 28))
        assertEquals(
            setOf(LocalDate.of(2026, 6, 28), LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30)),
            ActivitySummary.workoutDays(sessions, trained),
        )
    }

    @Test
    fun `weekly training frequency averages distinct days over the window`() {
        // 8 training days across the trailing 4 weeks -> 2.0 per week.
        val days = (0 until 8).map { today.minusDays(it * 2L) }.toSet()
        assertEquals(2.0, ActivitySummary.weeklyTrainingFrequency(days, today, weeks = 4), 0.0001)
    }

    @Test
    fun `weekly training frequency ignores days outside the window`() {
        val inside = today.minusDays(3)
        val outside = today.minusDays(40) // beyond 4 weeks
        assertEquals(
            0.25, // 1 day / 4 weeks
            ActivitySummary.weeklyTrainingFrequency(setOf(inside, outside), today, weeks = 4),
            0.0001,
        )
    }

    @Test
    fun `weekly training frequency is zero with no training days`() {
        assertEquals(0.0, ActivitySummary.weeklyTrainingFrequency(emptySet(), today), 0.0001)
    }

    @Test
    fun `average steps means the trailing window and rounds`() {
        val steps = mapOf(
            today to 10_000,
            today.minusDays(1) to 5_000,
            today.minusDays(2) to 6_001, // sum 21001 / 3 = 7000.33 -> 7000
        )
        assertEquals(7000, ActivitySummary.averageDailySteps(steps, today, days = 7))
    }

    @Test
    fun `average steps excludes days outside the window`() {
        val steps = mapOf(
            today to 9_000,
            today.minusDays(10) to 1_000, // outside a 7-day window
        )
        assertEquals(9_000, ActivitySummary.averageDailySteps(steps, today, days = 7))
    }

    @Test
    fun `average steps is null when nothing logged in window`() {
        assertNull(ActivitySummary.averageDailySteps(emptyMap(), today))
        assertNull(
            ActivitySummary.averageDailySteps(mapOf(today.minusDays(30) to 8000), today, days = 7),
        )
    }
}
