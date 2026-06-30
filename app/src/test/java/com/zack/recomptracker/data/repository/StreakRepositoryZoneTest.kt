package com.zack.recomptracker.data.repository

import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRepositoryZoneTest {
    @Test fun `a past in-zone day stays a hit after the zone is lowered later`() {
        val today = LocalDate.parse("2026-06-30")
        val pastDay = today.minusDays(1)
        val versions = listOf(
            PlanVersion(LocalDate.parse("2026-01-01"), PlanTargets(2500, 1, 1, 1, 2400, 2600)),
            PlanVersion(today, PlanTargets(2000, 1, 1, 1, 1900, 2100)),
        )
        val streaks = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(pastDay to 2500),
            completedSessionDates = emptyList(),
            versions = versions,
            dailyStepGoal = null,
            today = today,
            calculator = StreakCalculator(),
        )
        assertTrue(streaks.calorie.last7.dropLast(1).last())
    }
}
