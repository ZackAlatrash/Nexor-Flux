package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.domain.plan.PlanTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Daily reconcile ([RebalanceEngine.reconcile]) — pure, runs before evaluation at app open. */
class RebalanceReconcileTest {

    private fun targets(cal: Int) =
        PlanTargets(cal, 160, 300, 70, cal - 100, cal + 100)

    private fun activePlan(
        start: String,
        end: String,
        r: Int = 200,
        e: Int = 0,
    ) = RebalancePlan(
        id = "p", triggerDateIso = "2026-07-01", startDateIso = start, endDateIso = end,
        lengthDays = 3, mode = RebalanceMode.EAT_LESS, baseCalories = 2500,
        dailyCalorieReduction = r, extraDailySteps = e, baseStepGoal = null, recentAvgSteps = null,
        surplusKcal = 600, recoveredKcal = 600, status = RebalanceStatus.ACTIVE,
        createdAtIso = "2026-07-01T09:00:00Z", decidedAtIso = "2026-07-01T09:00:00Z",
    )

    /** Window of on-target eaten days so reconcile sees no fresh surplus unless overridden. */
    private fun window(today: LocalDate, base: Int = 2500, overrides: Map<LocalDate, Int> = emptyMap()):
        Pair<Map<LocalDate, PlanTargets>, Map<LocalDate, Int>> {
        val days = (1..7).map { today.minusDays(it.toLong()) }
        return days.associateWith { targets(base) } to days.associateWith { (overrides[it] ?: base) }
    }

    @Test
    fun `active plan past its end becomes COMPLETED in history`() {
        val today = LocalDate.of(2026, 7, 8)
        val plan = activePlan(start = "2026-07-04", end = "2026-07-06") // today > end
        val (bt, eaten) = window(today)
        val result = RebalanceEngine.reconcile(RebalanceState(active = plan), today, bt, eaten)

        assertNull(result.state.active)
        assertEquals(1, result.state.history.size)
        val done = result.state.history.first()
        assertEquals(RebalanceStatus.COMPLETED, done.status)
        assertEquals("completed", done.endedReason)
        assertNotNull("completion returns the ended plan for the note", result.ended)
        assertEquals(RebalanceStatus.COMPLETED, result.ended!!.status)
    }

    @Test
    fun `active plan that is clearly unrecoverable ends early`() {
        val today = LocalDate.of(2026, 7, 8)
        // Plan still running (end in the future). A large fresh surplus vs base makes it unrecoverable.
        val plan = activePlan(start = "2026-07-06", end = "2026-07-10", r = 100, e = 0)
        // S_now large: yesterday +3000. remainingDays = today..end inclusive = 07-08..07-10 = 3.
        // (3000 - 3*100)/7 = 385.7 > 75 -> unrecoverable.
        val (bt, eaten) = window(today, overrides = mapOf(today.minusDays(1) to 5500))
        val result = RebalanceEngine.reconcile(RebalanceState(active = plan), today, bt, eaten)

        assertNull(result.state.active)
        val ended = result.state.history.first()
        assertEquals(RebalanceStatus.ENDED_EARLY, ended.status)
        assertEquals("unrecoverable", ended.endedReason)
        // endDate rewritten to yesterday (the last day it was actually in effect).
        assertEquals(today.minusDays(1).toString(), ended.endDateIso)
        assertNotNull(result.ended)
    }

    @Test
    fun `reconcile never increases R or E`() {
        val today = LocalDate.of(2026, 7, 8)
        val plan = activePlan(start = "2026-07-06", end = "2026-07-10", r = 150, e = 500)
        // On-track window: no early end. Plan stays ACTIVE, untouched.
        val (bt, eaten) = window(today)
        val result = RebalanceEngine.reconcile(RebalanceState(active = plan), today, bt, eaten)

        val stillActive = result.state.active
        assertNotNull(stillActive)
        assertEquals(150, stillActive!!.dailyCalorieReduction)
        assertEquals(500, stillActive.extraDailySteps)
        assertNull(result.ended)
    }

    @Test
    fun `stale offer expires to DECLINED expired`() {
        val today = LocalDate.of(2026, 7, 8)
        val offer = RebalancePlan(
            id = "o", triggerDateIso = "2026-07-06", startDateIso = "2026-07-08",
            endDateIso = "2026-07-10", lengthDays = 3, mode = RebalanceMode.BALANCED,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.OFFERED,
            createdAtIso = "2026-07-07T09:00:00Z", // created yesterday -> today > createdAt date
        )
        val (bt, eaten) = window(today)
        val result = RebalanceEngine.reconcile(RebalanceState(active = offer), today, bt, eaten)

        assertNull(result.state.active)
        val expired = result.state.history.first()
        assertEquals(RebalanceStatus.DECLINED, expired.status)
        assertEquals("expired", expired.endedReason)
        assertNull("an expired offer produces no note", result.ended)
    }

    @Test
    fun `NO_ADJUSTMENT note expires to history without a note`() {
        val today = LocalDate.of(2026, 7, 8)
        val note = RebalancePlan(
            id = "n", triggerDateIso = "2026-07-06", startDateIso = "2026-07-06",
            endDateIso = "2026-07-06", lengthDays = 0, mode = RebalanceMode.BALANCED,
            baseCalories = 2500, dailyCalorieReduction = 0, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 5000, recoveredKcal = 0,
            status = RebalanceStatus.NO_ADJUSTMENT,
            createdAtIso = "2026-07-07T09:00:00Z",
        )
        val (bt, eaten) = window(today)
        val result = RebalanceEngine.reconcile(RebalanceState(active = note), today, bt, eaten)

        assertNull(result.state.active)
        val moved = result.state.history.first()
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, moved.status)
        assertNull(result.ended)
    }
}
