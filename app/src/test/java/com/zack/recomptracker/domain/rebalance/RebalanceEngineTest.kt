package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure engine behaviour ([RebalanceEngine.evaluate]). All dates are plain [LocalDate]; `newId`/`nowIso`
 * are injected fakes so nothing reads the wall clock. `today` is a Wednesday (2026-07-08) so the trailing
 * window `[today-7, today-1]` = Wed 07-01 .. Tue 07-07; weekend-specific tests use Monday 2026-07-06 whose
 * window ends on the Sunday 07-05.
 */
class RebalanceEngineTest {

    private val today = LocalDate.of(2026, 7, 8)      // Wednesday
    private val yesterday = today.minusDays(1)         // Tuesday 07-07

    private fun targets(cal: Int) =
        PlanTargets(cal, 160, 300, 70, cal - 100, cal + 100)

    /** Fills every window day (7 days ending yesterday) with the same base target and on-target eaten. */
    private fun input(
        refToday: LocalDate = today,
        base: Int = 2500,
        eatenOverrides: Map<LocalDate, Int> = emptyMap(),
        mealCounts: Map<LocalDate, Int>? = null,
        steps: Map<LocalDate, Int> = emptyMap(),
        baseStepGoal: Int? = null,
        goal: FitnessGoal? = FitnessGoal.MODERATE_CUT,
        mode: RebalanceMode = RebalanceMode.BALANCED,
        existing: RebalanceState = RebalanceState(mode = mode),
    ): RebalanceEvaluationInput {
        val window = (1..7).map { refToday.minusDays(it.toLong()) }
        val baseTargets = window.associateWith { targets(base) }
        val eaten = window.associateWith { (eatenOverrides[it] ?: base) }
        val counts = mealCounts ?: window.associateWith { 3 }
        return RebalanceEvaluationInput(
            today = refToday,
            baseTargetsByDate = baseTargets,
            eatenByDate = eaten,
            mealCountByDate = counts,
            stepsByDate = steps,
            baseStepGoal = baseStepGoal,
            goal = goal,
            mode = mode,
            existing = existing,
        )
    }

    private var idSeq = 0
    private val newId: () -> String = { "id-${idSeq++}" }
    private val nowIso: () -> String = { "2026-07-08T09:00:00Z" }

    private fun evaluate(input: RebalanceEvaluationInput) = RebalanceEngine.evaluate(input, newId, nowIso)

    // ── The mandatory worked example ────────────────────────────────────────────────
    @Test
    fun `single high day sizes an offer at about 75 percent of surplus`() {
        val decision = evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100)), // over = 600, S = 600
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(600, plan.surplusKcal)
        assertEquals(RebalanceStatus.OFFERED, plan.status)
        // targetRecover = round(600 * 0.75) = 450; calCap = min(round10(375)=380, 300) = 300;
        // no steps -> calorie-only, perDayCap = calCap = 300; D = 2 (2*300 >= 450); perDay = 225;
        // R = round10(225) = 230 (<= 300); E = 0; recovered = 2*(230+0) = 460.
        assertEquals(2, plan.lengthDays)
        assertEquals(230, plan.dailyCalorieReduction)
        assertEquals(0, plan.extraDailySteps)
        assertEquals(460, plan.recoveredKcal)
        assertEquals(2500, plan.baseCalories)
        assertEquals(yesterday.toString(), plan.triggerDateIso)
        // Provisional window: start = today+1, end = start + D - 1.
        assertEquals(today.plusDays(1).toString(), plan.startDateIso)
        assertEquals(today.plusDays(2).toString(), plan.endDateIso)
    }

    @Test
    fun `weekend Sat and Sun over 600 triggers`() {
        // refToday = Monday 2026-07-06; window ends Sun 07-05, Sat 07-04.
        val monday = LocalDate.of(2026, 7, 6)
        val sat = LocalDate.of(2026, 7, 4)
        val sun = LocalDate.of(2026, 7, 5)
        val decision = evaluate(
            input(
                refToday = monday,
                eatenOverrides = mapOf(sat to 2900, sun to 2900), // each +400 -> sum 800 >= 600
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        // Trigger date is the Sunday.
        assertEquals(sun.toString(), plan.triggerDateIso)
        assertEquals(800, plan.surplusKcal)
    }

    @Test
    fun `300 over day below both thresholds does not trigger`() {
        // over = 300: < 400 abs and < 0.25*2500 (=625). Not HIGH. No weekend. -> Silent.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 2800)))
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `high day fully offset elsewhere with weekly impact under 50 does not trigger`() {
        // One +400 day, one -400 day: S(positive part) = 400 -> 400/7 = 57 >= 50, would offer.
        // To get impact < 50 we need S < 350. A single 400 over is HIGH but S=400 -> impact 57.
        // Use a 400-over day offset so S stays 400? positive-part ignores the deficit day, so S=400.
        // Instead: make the only high day exactly at the abs threshold but shrink base so pct dominates?
        // Simplest: a day that is HIGH by pct on a small base, tiny surplus. base 1200, eaten 1500 -> over 300,
        // HIGH by pct (>=0.25*1200=300), S = 300 -> impact 300/7 = 42.9 < 50 -> Silent.
        val decision = evaluate(
            input(base = 1200, eatenOverrides = mapOf(yesterday to 1500)),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `too large surplus yields NO_ADJUSTMENT`() {
        // Huge surplus that even 5 days at max capacity can't recover.
        // base 2500 -> calCap = 300 (calorie-only). 5*300 = 1500 max recover.
        // targetRecover must exceed 1500 -> S*0.75 > 1500 -> S > 2000. Put +2500 on yesterday.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 5000)))
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan.status)
        assertEquals(0, plan.dailyCalorieReduction)
        assertEquals(0, plan.extraDailySteps)
        assertEquals(0, plan.lengthDays)
        // Self-consistent record: start = end = triggerDate.
        assertEquals(plan.triggerDateIso, plan.startDateIso)
        assertEquals(plan.triggerDateIso, plan.endDateIso)
    }

    @Test
    fun `reduction capped at min of 15 percent and 300`() {
        // base 3000 -> 0.15*3000 = 450 -> round10 = 450 -> min(450,300) = 300. R must never exceed 300.
        // Large surplus so perDay would exceed cap: S = 2000 (yesterday +2000), targetRecover=1500,
        // D=5 -> perDay=300 -> R=300 (capped, not above 300).
        val decision = evaluate(input(base = 3000, eatenOverrides = mapOf(yesterday to 5000)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertTrue("R=${plan.dailyCalorieReduction} must be <= 300", plan.dailyCalorieReduction <= 300)
        assertEquals(300, plan.dailyCalorieReduction)
    }

    @Test
    fun `steps capped at min of 25 percent of avg and 3000`() {
        // MOVE_MORE with a large step average -> stepsCap clamped to 3000 (raw 0.25*20000 = 5000).
        // Without the clamp, perDayCap would be stepKcal(5000)=200 and E would round to 3800 (> 3000);
        // the clamp to 3000 keeps perDayCap at stepKcal(3000)=120 so E can never exceed 3000.
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 20000 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S=600, recoverable at 120 kcal/day
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertTrue("E=${plan.extraDailySteps} must be <= 3000", plan.extraDailySteps <= 3000)
    }

    @Test
    fun `null step goal produces a calorie only plan`() {
        // Steps data present but no step goal -> calorie-only.
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 9000 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = stepsMap,
                baseStepGoal = null,
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(0, plan.extraDailySteps)
        assertTrue(plan.dailyCalorieReduction > 0)
    }

    @Test
    fun `null step data produces a calorie only plan`() {
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = emptyMap(),
                baseStepGoal = 8000,
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(0, plan.extraDailySteps)
        assertTrue(plan.dailyCalorieReduction > 0)
    }

    @Test
    fun `EAT_LESS split is calorie only`() {
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 9000 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.EAT_LESS,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(0, plan.extraDailySteps)
        assertTrue(plan.dailyCalorieReduction > 0)
    }

    @Test
    fun `MOVE_MORE split leads with steps`() {
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 12000 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S=600, targetRecover=450
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertTrue("MOVE_MORE should use steps", plan.extraDailySteps > 0)
        assertEquals(0, plan.dailyCalorieReduction)
    }

    @Test
    fun `BALANCED split uses both levers`() {
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 12000 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.BALANCED,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertTrue("BALANCED should reduce calories", plan.dailyCalorieReduction > 0)
        assertTrue("BALANCED should add steps", plan.extraDailySteps > 0)
    }

    @Test
    fun `bulk goal is silent`() {
        val decision = evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), goal = FitnessGoal.LEAN_BULK),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `recomp halves recovery and caps length at three days`() {
        // RECOMP: fraction 0.375, D <= 3. Big surplus so D would otherwise be 5.
        // base 2500 calorie-only, calCap 300. S = 3000 -> targetRecover = round(3000*0.375) = 1125.
        // Dmax = 3 -> 3*300 = 900 < 1125 -> too large -> NO_ADJUSTMENT (proves the D<=3 cap bites).
        val bigSurplus = evaluate(
            input(eatenOverrides = mapOf(yesterday to 5500), goal = FitnessGoal.RECOMP),
        )
        assertTrue(bigSurplus is RebalanceDecision.NoAdjustment)

        // A recoverable recomp surplus: S = 600 -> targetRecover = round(225) = 225.
        // calorie-only perDayCap 300 -> D = 2 (2*300 >= 225 and it's the smallest). length <= 3.
        val small = evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), goal = FitnessGoal.RECOMP),
        )
        val plan = (small as RebalanceDecision.Offer).plan
        assertTrue("recomp length must be <= 3", plan.lengthDays <= 3)
        assertEquals(600, plan.surplusKcal)
    }

    @Test
    fun `active plan is silent`() {
        val active = RebalancePlan(
            id = "a", triggerDateIso = "2026-07-01", startDateIso = "2026-07-02",
            endDateIso = "2026-07-04", lengthDays = 3, mode = RebalanceMode.BALANCED,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-07-01T09:00:00Z",
            decidedAtIso = "2026-07-01T09:00:00Z",
        )
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                existing = RebalanceState(active = active),
            ),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `cooldown under three days is silent`() {
        // A DECLINED record decided yesterday (1 day ago) -> cooldown active.
        val declined = RebalancePlan(
            id = "d", triggerDateIso = "2026-07-02", startDateIso = "2026-07-03",
            endDateIso = "2026-07-05", lengthDays = 3, mode = RebalanceMode.BALANCED,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.DECLINED, createdAtIso = "2026-07-06T09:00:00Z",
            decidedAtIso = yesterday.toString() + "T09:00:00Z", endedReason = "expired",
        )
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                existing = RebalanceState(history = listOf(declined)),
            ),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `same trigger day never re-offers after decline`() {
        // A terminal record whose triggerDate == our latest high day -> new-event fails -> Silent,
        // even though cooldown has elapsed (decided 10 days ago).
        val declined = RebalancePlan(
            id = "d", triggerDateIso = yesterday.toString(), startDateIso = "2026-06-25",
            endDateIso = "2026-06-27", lengthDays = 3, mode = RebalanceMode.BALANCED,
            baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
            baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.DECLINED, createdAtIso = "2026-06-24T09:00:00Z",
            decidedAtIso = today.minusDays(10).toString() + "T09:00:00Z", endedReason = "expired",
        )
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                existing = RebalanceState(history = listOf(declined)),
            ),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `fewer than four logged days is silent`() {
        // Only 3 of the 7 window days have a meal entry.
        val window = (1..7).map { today.minusDays(it.toLong()) }
        val counts = window.mapIndexed { i, d -> d to if (i < 3) 2 else 0 }.toMap()
        val decision = evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), mealCounts = counts),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `today is excluded from the surplus`() {
        // Put a giant overage on TODAY; the window ends yesterday so it must be ignored -> Silent.
        val decision = evaluate(
            input(eatenOverrides = mapOf(today to 6000)),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    @Test
    fun `null goal is treated as a cut`() {
        val decision = evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), goal = null),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(230, plan.dailyCalorieReduction) // identical to the MODERATE_CUT worked example
    }

    // ── customize ───────────────────────────────────────────────────────────────────
    @Test
    fun `customize recomputes an offer for a new mode from stored facts`() {
        val stepsMap = (1..7).associate { today.minusDays(it.toLong()) to 12000 }
        val offer = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.EAT_LESS,
            ),
        ) as RebalanceDecision.Offer).plan
        assertEquals(0, offer.extraDailySteps)

        val moved = RebalanceEngine.customize(offer, RebalanceMode.MOVE_MORE, FitnessGoal.MODERATE_CUT)
        assertEquals(RebalanceMode.MOVE_MORE, moved.mode)
        assertEquals(RebalanceStatus.OFFERED, moved.status)
        assertEquals(offer.surplusKcal, moved.surplusKcal)     // facts unchanged
        assertEquals(offer.baseCalories, moved.baseCalories)
        assertTrue("MOVE_MORE recompute should use steps", moved.extraDailySteps > 0)
        assertEquals(0, moved.dailyCalorieReduction)
    }

    @Test
    fun `customize on a recomp offer keeps the halved fraction and 3-day cap`() {
        // RECOMP, S = 2000 (yesterday +2000): targetRecover = round(2000*0.375) = 750; calorie-only
        // perDayCap = 300; recomp maxDays = 3 -> D = 3 (2*300 < 750 <= 3*300); perDay = 250; R = 250.
        val offer = (evaluate(
            input(eatenOverrides = mapOf(yesterday to 4500), goal = FitnessGoal.RECOMP),
        ) as RebalanceDecision.Offer).plan
        assertEquals(3, offer.lengthDays)
        assertEquals(250, offer.dailyCalorieReduction)

        // Re-sizing for a new mode must keep the recomp fraction and cap. A cut re-size (fraction 0.75,
        // maxDays 5) would instead give targetRecover = 1500 -> D = 5, R = 300.
        val customized = RebalanceEngine.customize(offer, RebalanceMode.EAT_LESS, FitnessGoal.RECOMP)
        assertEquals(RebalanceMode.EAT_LESS, customized.mode)
        assertTrue("recomp customize length must stay <= 3", customized.lengthDays <= 3)
        assertEquals(3, customized.lengthDays)
        assertEquals(250, customized.dailyCalorieReduction)
        assertEquals(750, customized.recoveredKcal) // 3 * (250 + 0)
    }

    // ── exact boundaries ────────────────────────────────────────────────────────────

    @Test
    fun `single day over of exactly 400 triggers`() {
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 2900))) // over == 400 exactly
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(400, plan.surplusKcal)
    }

    @Test
    fun `weekend surplus of exactly 600 triggers`() {
        // +300 each: neither day is individually HIGH (< 400 abs, < 625 pct) — only the weekend rule fires.
        val monday = LocalDate.of(2026, 7, 6)
        val sat = LocalDate.of(2026, 7, 4)
        val sun = LocalDate.of(2026, 7, 5)
        val decision = evaluate(
            input(refToday = monday, eatenOverrides = mapOf(sat to 2800, sun to 2800)), // 300 + 300 == 600
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(sun.toString(), plan.triggerDateIso)
        assertEquals(600, plan.surplusKcal)
    }

    @Test
    fun `surplus of exactly 350 passes the weekly impact gate`() {
        // base 1400, eaten 1750 -> over 350: HIGH via pct (350 >= 0.25*1400) and S == 50*7 exactly.
        val decision = evaluate(input(base = 1400, eatenOverrides = mapOf(yesterday to 1750)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(350, plan.surplusKcal)
    }

    @Test
    fun `cooldown of exactly three days does not block`() {
        val declined = declinedRecord(decidedOn = today.minusDays(3), trigger = LocalDate.of(2026, 6, 30))
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                existing = RebalanceState(history = listOf(declined)),
            ),
        )
        assertTrue("3 days since the decision is outside the cooldown", decision is RebalanceDecision.Offer)
    }

    @Test
    fun `cooldown of two days blocks`() {
        val declined = declinedRecord(decidedOn = today.minusDays(2), trigger = LocalDate.of(2026, 6, 30))
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                existing = RebalanceState(history = listOf(declined)),
            ),
        )
        assertTrue(decision is RebalanceDecision.Silent)
    }

    // ── low-base clamp ──────────────────────────────────────────────────────────────

    @Test
    fun `low base clamps reduction so recovered kcal matches the floored target`() {
        // base 1300, eaten 1750 -> over 450 (HIGH), S = 450, targetRecover = round(337.5) = 338;
        // calCap = min(round10(195)=200, 300) = 200 -> D = 2, perDay = 169, round10 -> 170; but the
        // 1200-kcal floor allows only 1300 - 1200 = 100 of reduction -> R = 100, recovered = 2*100 = 200.
        val decision = evaluate(input(base = 1300, eatenOverrides = mapOf(yesterday to 1750)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertTrue("R=${plan.dailyCalorieReduction} must be <= 100", plan.dailyCalorieReduction <= 100)
        assertEquals(100, plan.dailyCalorieReduction)
        assertEquals(plan.lengthDays * plan.dailyCalorieReduction, plan.recoveredKcal)
        assertEquals(200, plan.recoveredKcal)
    }

    private fun declinedRecord(decidedOn: LocalDate, trigger: LocalDate) = RebalancePlan(
        id = "d", triggerDateIso = trigger.toString(), startDateIso = trigger.plusDays(1).toString(),
        endDateIso = trigger.plusDays(3).toString(), lengthDays = 3, mode = RebalanceMode.BALANCED,
        baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
        baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
        status = RebalanceStatus.DECLINED, createdAtIso = decidedOn.toString() + "T09:00:00Z",
        decidedAtIso = decidedOn.toString() + "T09:00:00Z", endedReason = "expired",
    )
}
