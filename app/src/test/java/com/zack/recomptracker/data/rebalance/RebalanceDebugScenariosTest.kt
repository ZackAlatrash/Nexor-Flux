package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-builder checks for the developer phase-tester scenarios. The load-bearing assertions are that the
 * PROGRESS scenarios feed [EffectiveTargets.planDayInfo] the exact day-X-of-Y the redesign expects
 * (dayX = `between(startDate, today) + 1`, ofY = `lengthDays`), so what the tester sees on-device is the
 * intended day, and that each phase sets the three UI sources the `RebalanceViewModel` reads.
 */
class RebalanceDebugScenariosTest {

    private val today: LocalDate = LocalDate.of(2026, 7, 6)
    private var idSeq = 0
    private val newId: () -> String = { "id-${idSeq++}" }
    private val nowIso: () -> String = { "2026-07-06T09:00:00Z" }

    private fun build(scenario: RebalanceDebugScenario, base: Int = 2500) =
        RebalanceDebugScenarios.build(scenario, today, base, newId, nowIso)

    @Test
    fun `PROGRESS_MID is day 2 of 4 via planDayInfo`() {
        val app = build(RebalanceDebugScenario.PROGRESS_MID)
        val info = EffectiveTargets.planDayInfo(today, app.state)
        assertNotNull(info)
        assertEquals(2, info!!.dayX)
        assertEquals(4, info.ofY)
    }

    @Test
    fun `PROGRESS_FINAL is day 4 of 4 via planDayInfo`() {
        val app = build(RebalanceDebugScenario.PROGRESS_FINAL)
        val info = EffectiveTargets.planDayInfo(today, app.state)
        assertNotNull(info)
        assertEquals(4, info!!.dayX)
        assertEquals(4, info.ofY)
    }

    @Test
    fun `PROGRESS_DAY0 has no plan-day info yet (starts tomorrow)`() {
        val app = build(RebalanceDebugScenario.PROGRESS_DAY0)
        // today < startDate, so no plan overrides today → the day-0 "starts tomorrow" branch.
        assertNull(EffectiveTargets.planDayInfo(today, app.state))
        assertEquals(RebalanceStatus.ACTIVE, app.state.active?.status)
    }

    @Test
    fun `OFFER scenarios set an offered plan plus a weekly-bars window`() {
        val app = build(RebalanceDebugScenario.OFFER_BALANCED)
        assertEquals(RebalanceStatus.OFFERED, app.state.active?.status)
        assertEquals(RebalanceMode.BALANCED, app.state.active?.mode)
        assertNull(app.endedNotice)
        // 7 trailing history bars + lengthDays (4) plan bars.
        assertEquals(11, app.offerWindow?.size)
        assertEquals(2, app.offerWindow?.count { it.isOver })
        assertEquals(4, app.offerWindow?.count { it.isPlanDay })
    }

    @Test
    fun `MOVE_MORE offer window plan days keep the base target when reduction is zero`() {
        val base = 2400
        val app = build(RebalanceDebugScenario.OFFER_MOVE_MORE, base = base)
        val planDays = app.offerWindow.orEmpty().filter { it.isPlanDay }
        val expected = RebalancePlanMath.effectiveCalories(base, reduction = 0)
        assertTrue(planDays.isNotEmpty())
        assertTrue(planDays.all { it.valueKcal == expected })
        assertEquals(base, expected)
    }

    @Test
    fun `COMPLETION and GRACEFUL_END surface an ended notice and no active plan`() {
        val completed = build(RebalanceDebugScenario.COMPLETION)
        assertNull(completed.state.active)
        assertEquals(RebalanceStatus.COMPLETED, completed.endedNotice?.status)

        val graceful = build(RebalanceDebugScenario.GRACEFUL_END)
        assertNull(graceful.state.active)
        assertEquals(RebalanceStatus.ENDED_EARLY, graceful.endedNotice?.status)
        assertEquals("unrecoverable", graceful.endedNotice?.endedReason)
    }

    @Test
    fun `NO_ADJUSTMENT sets a note with no window and no ended notice`() {
        val app = build(RebalanceDebugScenario.NO_ADJUSTMENT)
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, app.state.active?.status)
        assertNull(app.endedNotice)
        assertNull(app.offerWindow)
    }

    @Test
    fun `CLEAR wipes every forced source`() {
        val app = build(RebalanceDebugScenario.CLEAR)
        assertNull(app.state.active)
        assertTrue(app.state.history.isEmpty())
        assertNull(app.endedNotice)
        assertNull(app.offerWindow)
    }

    @Test
    fun `EAT_LESS offer is calorie-only`() {
        val app = build(RebalanceDebugScenario.OFFER_EAT_LESS)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceMode.EAT_LESS, plan!!.mode)
        assertEquals(0, plan.extraDailySteps)
        assertEquals(300, plan.dailyCalorieReduction)
        assertFalse(app.offerWindow.isNullOrEmpty())
    }
}
