package com.zack.recomptracker.data.rebalance

import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceDefaults
import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlanMath
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate
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
    private val kToday = today.toKotlinLocalDate()
    private var idSeq = 0
    private val newId: () -> String = { "id-${idSeq++}" }
    private val nowIso: () -> String = { "2026-07-06T09:00:00Z" }

    private fun build(scenario: RebalanceDebugScenario, base: Int = 2500) =
        RebalanceDebugScenarios.build(scenario, today, base, newId, nowIso)

    @Test
    fun `PROGRESS_MID is day 2 of 4 via planDayInfo`() {
        val app = build(RebalanceDebugScenario.PROGRESS_MID)
        val info = EffectiveTargets.planDayInfo(kToday, app.state)
        assertNotNull(info)
        assertEquals(2, info!!.dayX)
        assertEquals(4, info.ofY)
    }

    @Test
    fun `PROGRESS_FINAL is day 4 of 4 via planDayInfo`() {
        val app = build(RebalanceDebugScenario.PROGRESS_FINAL)
        val info = EffectiveTargets.planDayInfo(kToday, app.state)
        assertNotNull(info)
        assertEquals(4, info!!.dayX)
        assertEquals(4, info.ofY)
    }

    @Test
    fun `PROGRESS_DAY0 has no plan-day info yet (starts tomorrow)`() {
        val app = build(RebalanceDebugScenario.PROGRESS_DAY0)
        // today < startDate, so no plan overrides today → the day-0 "starts tomorrow" branch.
        assertNull(EffectiveTargets.planDayInfo(kToday, app.state))
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

    // ── Dynamic-intensity scenarios (spec 2026-07-06 §13) ────────────────────────────────────────

    @Test
    fun `OFFER_LIGHT sets an offered plan at LIGHT intensity`() {
        val app = build(RebalanceDebugScenario.OFFER_LIGHT)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceStatus.OFFERED, plan!!.status)
        assertEquals(RebalanceIntensity.LIGHT, plan.intensity)
        assertFalse("a LIGHT offer is not partial", plan.partial)
        assertFalse(app.offerWindow.isNullOrEmpty())
    }

    @Test
    fun `OFFER_FULL sets an offered plan at FULL intensity`() {
        val app = build(RebalanceDebugScenario.OFFER_FULL)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceStatus.OFFERED, plan!!.status)
        assertEquals(RebalanceIntensity.FULL, plan.intensity)
        assertFalse("a FULL offer is not partial", plan.partial)
        assertFalse(app.offerWindow.isNullOrEmpty())
    }

    @Test
    fun `OFFER_LIGHT recovers less than OFFER_FULL for the same surplus`() {
        // Eyeball check that the two scenarios read as genuinely different intensities, not just a
        // different label on the same numbers.
        val light = build(RebalanceDebugScenario.OFFER_LIGHT).state.active!!
        val full = build(RebalanceDebugScenario.OFFER_FULL).state.active!!
        assertEquals(light.surplusKcal, full.surplusKcal)
        assertTrue(
            "LIGHT must recover less of the same surplus than FULL",
            light.recoveredKcal < full.recoveredKcal,
        )
    }

    @Test
    fun `OFFER_PARTIAL sets a partial 7-day plan for a large surplus`() {
        val app = build(RebalanceDebugScenario.OFFER_PARTIAL)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceStatus.OFFERED, plan!!.status)
        assertTrue("a big-surplus offer must be flagged partial", plan.partial)
        assertEquals(RebalanceDefaults.MAX_LENGTH_DAYS, plan.lengthDays)
        assertTrue("~3000 surplus per spec 13", plan.surplusKcal in 2500..3500)
        assertTrue(
            "a partial plan recovers less than its surplus",
            plan.recoveredKcal < plan.surplusKcal,
        )
        assertFalse(app.offerWindow.isNullOrEmpty())
    }

    @Test
    fun `REASSURANCE_NOTE sets a NO_ADJUSTMENT note with surplus below the reassurance band`() {
        val app = build(RebalanceDebugScenario.REASSURANCE_NOTE)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan!!.status)
        assertTrue(
            "must be below SMALL_SURPLUS_KCAL so the VM derives NoteKind.REASSURANCE",
            plan.surplusKcal < RebalanceDefaults.SMALL_SURPLUS_KCAL,
        )
        assertNull(app.endedNotice)
        assertNull(app.offerWindow)
    }

    @Test
    fun `RESUME_NOTE sets a NO_ADJUSTMENT note with surplus above the resume band`() {
        val app = build(RebalanceDebugScenario.RESUME_NOTE)
        val plan = app.state.active
        assertNotNull(plan)
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan!!.status)
        assertTrue(
            "must be above HUGE_SURPLUS_KCAL so the VM derives the resume NoteKind.NO_ADJUSTMENT",
            plan.surplusKcal > RebalanceDefaults.HUGE_SURPLUS_KCAL,
        )
        assertNull(app.endedNotice)
        assertNull(app.offerWindow)
    }

    @Test
    fun `every RebalanceDebugScenario is covered by build without throwing`() {
        // A cheap exhaustiveness guard: every enum entry must build cleanly for some base calorie
        // target, so a future scenario addition can't silently fall through an unhandled branch.
        RebalanceDebugScenario.entries.forEach { scenario ->
            build(scenario)
        }
    }
}
