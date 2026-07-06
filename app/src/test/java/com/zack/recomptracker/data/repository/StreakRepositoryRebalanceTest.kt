package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Streak semantics under an active rebalance (spec §6): the calorie streak judges against the lenient
 * UNION of the base and effective zones (a rebalance can only widen it), and the steps streak keeps
 * judging against the BASE step goal (a temporary boost is display-only). Both are unit-tested through
 * the pure [buildStreaks] with a hand-built [RebalanceState] — no store, no coroutines.
 */
class StreakRepositoryRebalanceTest {

    private val calc = StreakCalculator()

    // One baseline plan version covering all test dates: target 2500, zone 2400..2600.
    private val versions = listOf(
        PlanVersion(LocalDate.parse("1970-01-01"), PlanTargets(2500, 160, 300, 70, 2400, 2600)),
    )

    private fun log(date: LocalDate, steps: Int? = null) =
        DailyLogEntity(date = date.toString(), steps = steps)

    /** An ACTIVE plan covering [start]..[end] that reduces calories by [r] and boosts steps by [e]. */
    private fun activePlan(start: LocalDate, end: LocalDate, r: Int, e: Int, baseStepGoal: Int?) =
        RebalancePlan(
            id = "p",
            triggerDateIso = start.minusDays(1).toString(),
            startDateIso = start.toString(),
            endDateIso = end.toString(),
            lengthDays = 3,
            mode = RebalanceMode.BALANCED,
            baseCalories = 2500,
            dailyCalorieReduction = r,
            extraDailySteps = e,
            baseStepGoal = baseStepGoal,
            recentAvgSteps = if (baseStepGoal != null) 9000 else null,
            surplusKcal = 600,
            recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE,
            createdAtIso = "2026-06-21T09:00:00Z",
            decidedAtIso = "2026-06-21T09:00:00Z",
        )

    @Test
    fun `union zone never breaks a kept calorie streak during rebalance`() {
        // A rebalance reduces the effective target (effective zone ~2150..2350), but the day was eaten
        // at 2500 — inside the BASE zone (2400..2600). The union keeps 2500 in-zone, so the streak holds.
        val today = LocalDate.of(2026, 6, 23)
        val planDay = today.minusDays(1) // 06-22, inside the active window
        val plan = activePlan(
            start = today.minusDays(2), end = today, r = 300, e = 0, baseStepGoal = null,
        )
        val streaks = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(planDay to 2500),
            completedSessionDates = emptyList(),
            versions = versions,
            dailyStepGoal = null,
            today = today,
            calculator = calc,
            rebalanceState = RebalanceState(active = plan),
        )
        // The plan day (yesterday, index 5 of the 7-day strip) counts in-zone despite the reduced target.
        assertTrue(
            "A base-zone day during an active rebalance must still count in-zone",
            streaks.calorie.last7.dropLast(1).last(),
        )
    }

    @Test
    fun `steps streak uses base goal not boosted goal`() {
        // Base step goal 8000; the active plan boosts the DISPLAY goal by +2000 (→10000). The day logged
        // 9000 steps: it meets the base goal but NOT base+extra. The streak must judge against base, so
        // 9000 qualifies and the streak holds.
        val today = LocalDate.of(2026, 6, 23)
        val planDay = today.minusDays(1)
        val plan = activePlan(
            start = today.minusDays(2), end = today, r = 0, e = 2000, baseStepGoal = 8000,
        )
        val streaks = buildStreaks(
            dailyLogs = listOf(log(planDay, steps = 9000)),
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = emptyList(),
            versions = versions,
            dailyStepGoal = 8000,
            today = today,
            calculator = calc,
            rebalanceState = RebalanceState(active = plan),
        )
        // 9000 ≥ base 8000 → the plan day counts as a step hit even though it's under the boosted 10000.
        assertTrue(
            "A day meeting the base step goal must count even under an active step boost",
            streaks.steps.last7.dropLast(1).last(),
        )
    }
}
