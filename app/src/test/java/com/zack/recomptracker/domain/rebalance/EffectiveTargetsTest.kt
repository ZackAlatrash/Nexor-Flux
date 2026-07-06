package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.domain.plan.PlanTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure resolver ([EffectiveTargets]) — base [PlanTargets] + rebalance windows -> effective values. */
class EffectiveTargetsTest {

    private fun targets(cal: Int) =
        PlanTargets(cal, 160, 300, 70, cal - 100, cal + 100)

    private fun plan(
        start: String,
        end: String,
        status: RebalanceStatus,
        r: Int = 250,
        e: Int = 0,
        baseStepGoal: Int? = null,
    ) = RebalancePlan(
        id = "p", triggerDateIso = start, startDateIso = start, endDateIso = end,
        lengthDays = 3, mode = RebalanceMode.EAT_LESS, baseCalories = 2500,
        dailyCalorieReduction = r, extraDailySteps = e, baseStepGoal = baseStepGoal,
        recentAvgSteps = if (baseStepGoal != null) 9000 else null, surplusKcal = 600,
        recoveredKcal = 600, status = status, createdAtIso = "2026-07-01T09:00:00Z",
        decidedAtIso = "2026-07-01T09:00:00Z",
    )

    @Test
    fun `active plan day reduces the target and re-centres the zone`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val base = targets(2500)
        val eff = EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 9), state)
        assertEquals(2250, eff.calories)               // 2500 - 250
        assertEquals(2150, eff.zoneLowerBound)          // 2250 - 100
        assertEquals(2350, eff.zoneUpperBound)          // 2250 + 100
    }

    @Test
    fun `historical completed day is still reduced after revert`() {
        val state = RebalanceState(
            history = listOf(plan("2026-06-20", "2026-06-22", RebalanceStatus.COMPLETED, r = 250)),
        )
        val base = targets(2500)
        val eff = EffectiveTargets.resolve(base, LocalDate.of(2026, 6, 21), state)
        assertEquals(2250, eff.calories)
    }

    @Test
    fun `a date outside any window resolves to base`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val base = targets(2500)
        val eff = EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 15), state)
        assertEquals(base, eff)
    }

    @Test
    fun `ENDED_EARLY only overrides through its recorded end date`() {
        // Plan started 07-08, but ENDED_EARLY with endDate rewritten to 07-09.
        val state = RebalanceState(
            history = listOf(plan("2026-07-08", "2026-07-09", RebalanceStatus.ENDED_EARLY, r = 250)),
        )
        val base = targets(2500)
        // 07-09 is within [start, end] -> reduced.
        assertEquals(2250, EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 9), state).calories)
        // 07-10 is past the ended date -> base.
        assertEquals(2500, EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 10), state).calories)
    }

    @Test
    fun `macro scaling holds protein caps fat at 25 percent and keeps carbs non negative`() {
        // effective 2250: fat = round(0.25*2250/9) = round(62.5) = 63 (Kotlin half-up);
        // carbs = round((2250 - 160*4 - 63*9)/4) = round((2250 - 640 - 567)/4) = round(1043/4)=round(260.75)=261.
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val base = targets(2500)
        val eff = EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 9), state)
        assertEquals(160, eff.proteinG)  // held from base
        assertEquals(63, eff.fatG)
        assertEquals(261, eff.carbsG)
        assertTrue(eff.carbsG >= 0)
    }

    @Test
    fun `effective step goal only applies on plan days`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 0, e = 1500, baseStepGoal = 8000),
        )
        // On a plan day: base goal + extra.
        assertEquals(9500, EffectiveTargets.effectiveStepGoal(8000, LocalDate.of(2026, 7, 9), state))
        // Off a plan day: base goal untouched.
        assertEquals(8000, EffectiveTargets.effectiveStepGoal(8000, LocalDate.of(2026, 7, 20), state))
    }

    @Test
    fun `union zone widens but never narrows`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val base = targets(2500) // zone [2400, 2600]; effective zone [2150, 2350]
        val union = EffectiveTargets.unionZone(base, LocalDate.of(2026, 7, 9), state)
        assertEquals(2150, union.first)   // min(2400, 2150)
        assertEquals(2600, union.last)    // max(2600, 2350)

        // Off a plan day the union is just the base zone.
        val offDay = EffectiveTargets.unionZone(base, LocalDate.of(2026, 7, 20), state)
        assertEquals(2400, offDay.first)
        assertEquals(2600, offDay.last)
    }

    @Test
    fun `planDayInfo reports day X of Y on plan days and null off them`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val info = EffectiveTargets.planDayInfo(LocalDate.of(2026, 7, 9), state)!!
        assertEquals(2, info.dayX)  // 07-09 is day 2 of 3
        assertEquals(3, info.ofY)
        assertNull(EffectiveTargets.planDayInfo(LocalDate.of(2026, 7, 20), state))
    }

    @Test
    fun `offered plan never overrides a date`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.OFFERED, r = 250),
        )
        val base = targets(2500)
        assertEquals(base, EffectiveTargets.resolve(base, LocalDate.of(2026, 7, 9), state))
    }

    @Test
    fun `resolveAll maps each date through resolve`() {
        val state = RebalanceState(
            active = plan("2026-07-08", "2026-07-10", RebalanceStatus.ACTIVE, r = 250),
        )
        val d1 = LocalDate.of(2026, 7, 9)   // plan day
        val d2 = LocalDate.of(2026, 7, 20)  // off day
        val map = EffectiveTargets.resolveAll(mapOf(d1 to targets(2500), d2 to targets(2500)), state)
        assertEquals(2250, map.getValue(d1).calories)
        assertEquals(2500, map.getValue(d2).calories)
    }
}
