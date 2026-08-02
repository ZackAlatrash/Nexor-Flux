package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanTargets
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure engine behaviour ([RebalanceEngine.evaluate]). All dates are plain [LocalDate]; `newId`/`nowIso`
 * are injected fakes so nothing reads the wall clock. `today` is a Wednesday (2026-07-08) so the trailing
 * window `[today-7, today-1]` = Wed 07-01 .. Tue 07-07; weekend-specific tests use Monday 2026-07-06 whose
 * window ends on the Sunday 07-05.
 */
class RebalanceEngineTest {

    private val today = LocalDate(2026, 7, 8)      // Wednesday
    private val yesterday = today.minus(1, DateTimeUnit.DAY)         // Tuesday 07-07

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
        val window = (1..7).map { refToday.minus(it, DateTimeUnit.DAY) }
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
        // Fresh offers default to STANDARD intensity and are fully (not partially) recoverable here.
        assertEquals(RebalanceIntensity.STANDARD, plan.intensity)
        assertFalse(plan.partial)
        assertEquals(yesterday.toString(), plan.triggerDateIso)
        // Provisional window: start = today+1, end = start + D - 1.
        assertEquals(today.plus(1, DateTimeUnit.DAY).toString(), plan.startDateIso)
        assertEquals(today.plus(2, DateTimeUnit.DAY).toString(), plan.endDateIso)
    }

    @Test
    fun `weekend Sat and Sun over 600 triggers`() {
        // refToday = Monday 2026-07-06; window ends Sun 07-05, Sat 07-04.
        val monday = LocalDate(2026, 7, 6)
        val sat = LocalDate(2026, 7, 4)
        val sun = LocalDate(2026, 7, 5)
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
    fun `surplus above the resume band yields the resume NO_ADJUSTMENT note`() {
        // Surplus > HUGE_SURPLUS_KCAL (4000): even Light can't sensibly claw it back → a supportive
        // resume note, NOT a plan. eaten +4500 on yesterday → S = 4500 > 4000.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 7000)))
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan.status)
        assertEquals(0, plan.dailyCalorieReduction)
        assertEquals(0, plan.extraDailySteps)
        assertEquals(0, plan.lengthDays)
        // The note MUST carry the real surplus so the VM tells resume (>500) from reassurance (<500).
        assertEquals(4500, plan.surplusKcal)
        // Self-consistent record: start = end = triggerDate.
        assertEquals(plan.triggerDateIso, plan.startDateIso)
        assertEquals(plan.triggerDateIso, plan.endDateIso)
    }

    @Test
    fun `surplus below the reassurance band yields the reassurance NO_ADJUSTMENT note carrying the surplus`() {
        // A HIGH day (over ≥ 400) but a small weekly surplus < SMALL_SURPLUS_KCAL (500) → reassurance
        // note, not a micro-plan. eaten +450 on yesterday → over 450 (HIGH), S = 450 (< 500), impact
        // 450/7 ≈ 64 ≥ 50 passes the weekly-impact gate, so only the band gate suppresses the plan.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 2950)))
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan.status)
        assertEquals(0, plan.dailyCalorieReduction)
        assertEquals(0, plan.lengthDays)
        // Surplus stamped so the VM derives NoteKind.REASSURANCE (< 500) rather than the resume note.
        assertEquals(450, plan.surplusKcal)
    }

    @Test
    fun `surplus exactly at the band boundaries lands in the offer band, not a note`() {
        // The band gates are strict (< 500 / > 4000), so exactly 500 and exactly 4000 are OFFERS (spec §6).
        // S = 500: eaten +500 (over ≥ 400 = HIGH), not < 500 → offer.
        val at500 = evaluate(input(eatenOverrides = mapOf(yesterday to 3000)))
        assertTrue("surplus == 500 is an offer, not the reassurance note", at500 is RebalanceDecision.Offer)
        assertEquals(500, (at500 as RebalanceDecision.Offer).plan.surplusKcal)
        // S = 4000: not > 4000 → offer (partial, since 4000 overflows 7 days at max daily cut).
        val at4000 = evaluate(input(eatenOverrides = mapOf(yesterday to 6500)))
        assertTrue("surplus == 4000 is an offer, not the resume note", at4000 is RebalanceDecision.Offer)
        assertEquals(4000, (at4000 as RebalanceDecision.Offer).plan.surplusKcal)
    }

    @Test
    fun `a surplus too large to fully recover in seven days yields a partial plan, not a note`() {
        // The blowout the feature was built for. base 2500 calorie-only → perDayCap 300, 7×300 = 2100.
        // S = 3500 (in-band, < 4000): STANDARD target = round(2625) > 2100, so the recovery is capped
        // at the 7-day feasible amount and the plan is PARTIAL — an honest offer, never NO_ADJUSTMENT.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 6000)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(RebalanceStatus.OFFERED, plan.status)
        assertEquals(3500, plan.surplusKcal)
        assertTrue("a 7-day-overflow surplus must be flagged partial", plan.partial)
        assertEquals(7, plan.lengthDays)
        // Recovers only the feasible amount, strictly less than the STANDARD target (round(3500*0.75)).
        val standardTarget = Math.round(3500 * 0.75).toInt()
        assertTrue(
            "partial recovered ${plan.recoveredKcal} must be < target $standardTarget",
            plan.recoveredKcal < standardTarget,
        )
        assertEquals(2100, plan.recoveredKcal) // 7 × 300
    }

    @Test
    fun `an in-band surplus needing seven days is offered (old five-day cap would have bowed out)`() {
        // S = 2668: STANDARD target = round(2001) ≈ 2001. perDayCap 300. Smallest D with D×300 ≥ 2001
        // is 7. Under the old MAX_LENGTH_DAYS = 5 (5×300 = 1500 < 2001) this was NO_ADJUSTMENT; now it
        // is a full (non-partial) 7-day offer.
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 2500 + 2668)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(7, plan.lengthDays)
        assertFalse("2001 ≤ 7×300 fits, so not partial", plan.partial)
        assertEquals(RebalanceIntensity.STANDARD, plan.intensity)
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
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 20000 }
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
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 9000 }
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
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 9000 }
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
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 12000 }
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
    fun `move more with negligible step history falls back to a calorie plan`() {
        // Steps are technically available (data + goal both present) but the average is tiny:
        // recentAvgSteps = 100 -> stepsCap = round500(0.25*100) = round500(25) = 0, so MOVE_MORE's
        // stepsCap > 0 branch fails and falls back to the calorie-only lever, same as the
        // no-steps-available worked example: S=600, targetRecover=450, calorie lever cap 300 -> D=2,
        // perDay=225, R=round10(225)=230; E stays 0.
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 100 }
        val decision = evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S=600
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(0, plan.extraDailySteps)
        assertEquals(2, plan.lengthDays)
        assertEquals(230, plan.dailyCalorieReduction)
        assertTrue("dailyCalorieReduction must be > 0", plan.dailyCalorieReduction > 0)
    }

    @Test
    fun `MOVE_MORE maxes steps then covers the remainder with calories when steps alone fall short`() {
        // steps avg 6000 -> stepsCap = round500(1500) = 1500, stepKcal = 60/day. Over maxDays 7 that is
        // 420 kcal, short of targetRecover 450 (S = 600). Steps alone can't cover it, so MOVE_MORE maxes
        // steps AND cuts calories for the remainder — a distinct, feasible plan, not a null that would
        // make `customize` fall back to the previous mode's numbers.
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 6000 }
        val plan = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S = 600
                steps = stepsMap,
                baseStepGoal = 9000,
                mode = RebalanceMode.MOVE_MORE,
            ),
        ) as RebalanceDecision.Offer).plan
        assertTrue("MOVE_MORE should still add steps", plan.extraDailySteps > 0)
        assertTrue("MOVE_MORE should also cut calories when steps fall short", plan.dailyCalorieReduction > 0)
    }

    @Test
    fun `MOVE_MORE and BALANCED give different adjustments when steps alone fall short`() {
        // Regression guard for the on-device report "Balanced and Move more give the same adjustments":
        // in the band where steps alone can't cover the surplus (avg 6000 → 60 kcal/day × 7 = 420 < 450),
        // the two modes must still differ, and Move more must lean harder on steps than the balanced split.
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 6000 }
        fun planFor(mode: RebalanceMode) = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S = 600
                steps = stepsMap,
                baseStepGoal = 9000,
                mode = mode,
            ),
        ) as RebalanceDecision.Offer).plan
        val balanced = planFor(RebalanceMode.BALANCED)
        val moveMore = planFor(RebalanceMode.MOVE_MORE)
        assertTrue(
            "Move more must differ from Balanced (R ${moveMore.dailyCalorieReduction} vs " +
                "${balanced.dailyCalorieReduction}, E ${moveMore.extraDailySteps} vs ${balanced.extraDailySteps})",
            moveMore.dailyCalorieReduction != balanced.dailyCalorieReduction ||
                moveMore.extraDailySteps != balanced.extraDailySteps,
        )
        assertTrue("Move more should use more steps than Balanced", moveMore.extraDailySteps > balanced.extraDailySteps)
    }

    @Test
    fun `customize to MOVE_MORE changes the adjustment even when steps alone fall short`() {
        // The exact on-device path: a BALANCED offer, then tapping "Move more". customize must recompute
        // to a genuinely different plan rather than returning the offer with only its mode label flipped.
        // avg 6000 keeps steps short of the target over 7 days so the two-tier remainder branch is live.
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 6000 }
        val offer = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S = 600
                steps = stepsMap,
                baseStepGoal = 9000,
                mode = RebalanceMode.BALANCED,
            ),
        ) as RebalanceDecision.Offer).plan
        val moved = RebalanceEngine.customize(offer, RebalanceMode.MOVE_MORE, RebalanceIntensity.STANDARD)
        assertEquals(RebalanceMode.MOVE_MORE, moved.mode)
        assertTrue(
            "customize(MOVE_MORE) must change the adjustment, not just the label",
            moved.extraDailySteps != offer.extraDailySteps ||
                moved.dailyCalorieReduction != offer.dailyCalorieReduction,
        )
        assertTrue("MOVE_MORE should use more steps than the balanced offer", moved.extraDailySteps > offer.extraDailySteps)
    }

    @Test
    fun `BALANCED split uses both levers`() {
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 12000 }
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
    fun `recomp is no longer halved and uses the same plan as a cut`() {
        // Recomp special-casing (0.375 fraction, 3-day cap) is dropped (spec §7): a Recomp offer must
        // now be byte-for-byte identical to a MODERATE_CUT offer for the same surplus, both sized at the
        // shared STANDARD fraction and 7-day cap.
        val recomp = (evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), goal = FitnessGoal.RECOMP),
        ) as RebalanceDecision.Offer).plan
        val cut = (evaluate(
            input(eatenOverrides = mapOf(yesterday to 3100), goal = FitnessGoal.MODERATE_CUT),
        ) as RebalanceDecision.Offer).plan
        assertEquals(cut.lengthDays, recomp.lengthDays)
        assertEquals(cut.dailyCalorieReduction, recomp.dailyCalorieReduction)
        assertEquals(cut.recoveredKcal, recomp.recoveredKcal)
        // The old recomp cap was 3 days; the shared 7-day worked example is a 2-day / 230 kcal plan.
        assertEquals(2, recomp.lengthDays)
        assertEquals(230, recomp.dailyCalorieReduction)
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
            decidedAtIso = today.minus(10, DateTimeUnit.DAY).toString() + "T09:00:00Z", endedReason = "expired",
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
        val window = (1..7).map { today.minus(it, DateTimeUnit.DAY) }
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
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 12000 }
        val offer = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100),
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.EAT_LESS,
            ),
        ) as RebalanceDecision.Offer).plan
        assertEquals(0, offer.extraDailySteps)

        val moved = RebalanceEngine.customize(offer, RebalanceMode.MOVE_MORE, RebalanceIntensity.STANDARD)
        assertEquals(RebalanceMode.MOVE_MORE, moved.mode)
        assertEquals(RebalanceStatus.OFFERED, moved.status)
        assertEquals(offer.surplusKcal, moved.surplusKcal)     // facts unchanged
        assertEquals(offer.baseCalories, moved.baseCalories)
        assertTrue("MOVE_MORE recompute should use steps", moved.extraDailySteps > 0)
        assertEquals(0, moved.dailyCalorieReduction)
    }

    @Test
    fun `intensity presets give distinct plans for the same surplus`() {
        // Same S = 600, base 2500 calorie-only (perDayCap 300). Light/Standard/Full scale targetRecover
        // (300 / 450 / 600) → distinct daily reductions and recovered kcal, all within the 300 cap.
        fun sizeAt(intensity: RebalanceIntensity): RebalancePlan {
            val offer = (evaluate(
                input(eatenOverrides = mapOf(yesterday to 3100)),
            ) as RebalanceDecision.Offer).plan
            return RebalanceEngine.customize(offer, RebalanceMode.EAT_LESS, intensity)
        }
        val light = sizeAt(RebalanceIntensity.LIGHT)
        val standard = sizeAt(RebalanceIntensity.STANDARD)
        val full = sizeAt(RebalanceIntensity.FULL)

        assertEquals(RebalanceIntensity.LIGHT, light.intensity)
        assertEquals(150, light.dailyCalorieReduction)    // target 300 / D 2
        assertEquals(300, light.recoveredKcal)
        assertEquals(230, standard.dailyCalorieReduction) // target 450 / D 2, round-half-up
        assertEquals(460, standard.recoveredKcal)
        assertEquals(300, full.dailyCalorieReduction)     // target 600 / D 2
        assertEquals(600, full.recoveredKcal)
        // Recovery is strictly monotonic in intensity.
        assertTrue(light.recoveredKcal < standard.recoveredKcal)
        assertTrue(standard.recoveredKcal < full.recoveredKcal)
    }

    @Test
    fun `customize with mode and intensity recomputes the plan`() {
        // A BALANCED/STANDARD offer, then customize to MOVE_MORE + FULL: both dials compose and the
        // plan changes (more recovered at FULL, steps-led under MOVE_MORE with a generous step history).
        val stepsMap = (1..7).associate { today.minus(it, DateTimeUnit.DAY) to 12000 }
        val offer = (evaluate(
            input(
                eatenOverrides = mapOf(yesterday to 3100), // S = 600
                steps = stepsMap,
                baseStepGoal = 8000,
                mode = RebalanceMode.BALANCED,
            ),
        ) as RebalanceDecision.Offer).plan

        val customized = RebalanceEngine.customize(offer, RebalanceMode.MOVE_MORE, RebalanceIntensity.FULL)
        assertEquals(RebalanceMode.MOVE_MORE, customized.mode)
        assertEquals(RebalanceIntensity.FULL, customized.intensity)
        assertEquals(RebalanceStatus.OFFERED, customized.status)
        // FULL recovers ~all of the surplus vs STANDARD's ~75%, so recovered climbs.
        assertTrue(
            "FULL should recover more than the STANDARD offer (${customized.recoveredKcal} vs ${offer.recoveredKcal})",
            customized.recoveredKcal > offer.recoveredKcal,
        )
    }

    @Test
    fun `customize to FULL can turn a fully-recoverable offer into a partial one`() {
        // S = 2800: STANDARD target round(2100) == 7×300 feasible → fully recoverable (not partial).
        // Bumping to FULL raises the target to 2800 > 2100 feasible → the plan becomes PARTIAL.
        val offer = (evaluate(input(eatenOverrides = mapOf(yesterday to 5300))) as RebalanceDecision.Offer).plan
        assertEquals(2800, offer.surplusKcal)
        assertFalse("STANDARD offer for S=2800 recovers fully", offer.partial)

        val full = RebalanceEngine.customize(offer, offer.mode, RebalanceIntensity.FULL)
        assertEquals(RebalanceIntensity.FULL, full.intensity)
        assertTrue("FULL on a 2800 surplus overflows 7 days → partial", full.partial)
        assertTrue("still an honest offer, never a note", full.status == RebalanceStatus.OFFERED)
    }

    // ── exact boundaries ────────────────────────────────────────────────────────────

    @Test
    fun `single day over of exactly 400 triggers`() {
        // over == 400 fires the HIGH-day trigger, but S = 400 < SMALL_SURPLUS_KCAL (500), so the band
        // gate routes it to the reassurance note. The stamped surplus proves the trigger + surplus math
        // ran (it is not Silent).
        val decision = evaluate(input(eatenOverrides = mapOf(yesterday to 2900)))
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(400, plan.surplusKcal)
    }

    @Test
    fun `weekend surplus of exactly 600 triggers`() {
        // +300 each: neither day is individually HIGH (< 400 abs, < 625 pct) — only the weekend rule fires.
        val monday = LocalDate(2026, 7, 6)
        val sat = LocalDate(2026, 7, 4)
        val sun = LocalDate(2026, 7, 5)
        val decision = evaluate(
            input(refToday = monday, eatenOverrides = mapOf(sat to 2800, sun to 2800)), // 300 + 300 == 600
        )
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(sun.toString(), plan.triggerDateIso)
        assertEquals(600, plan.surplusKcal)
    }

    @Test
    fun `surplus of exactly 350 passes the weekly impact gate`() {
        // base 1400, eaten 1750 -> over 350: HIGH via pct (350 >= 0.25*1400) and S == 50*7 exactly, so
        // it clears the weekly-impact gate (not Silent). S = 350 < SMALL_SURPLUS_KCAL (500) then routes
        // to the reassurance note; the stamped surplus proves the gate + surplus math passed.
        val decision = evaluate(input(base = 1400, eatenOverrides = mapOf(yesterday to 1750)))
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(350, plan.surplusKcal)
    }

    @Test
    fun `cooldown of exactly three days does not block`() {
        val declined = declinedRecord(decidedOn = today.minus(3, DateTimeUnit.DAY), trigger = LocalDate(2026, 6, 30))
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
        val declined = declinedRecord(decidedOn = today.minus(2, DateTimeUnit.DAY), trigger = LocalDate(2026, 6, 30))
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
    fun `low base sizes against the floored lever and still hits the recovery target`() {
        // base 1300, eaten 2000 -> over 700 (HIGH), S = 700 (≥ 500 so in-band), STANDARD targetRecover =
        // round(525) = 525. calCap = min(round10(195)=200, 300) = 200 but floorCap = 1300 - 1200 = 100,
        // so the calorie lever (and perDayCap) is 100 -> D = 6 (smallest with D*100 >= 525),
        // perDay = ceil(525/6) = 88, round10 -> 90 -> R = 90, recovered = 6*90 = 540 (>= target; the
        // lever is honoured and never exceeds floorCap 100).
        val decision = evaluate(input(base = 1300, eatenOverrides = mapOf(yesterday to 2000)))
        val plan = (decision as RebalanceDecision.Offer).plan
        assertEquals(6, plan.lengthDays)
        assertEquals(90, plan.dailyCalorieReduction)
        assertTrue("R=${plan.dailyCalorieReduction} must be <= floorCap 100", plan.dailyCalorieReduction <= 100)
        assertEquals(plan.lengthDays * plan.dailyCalorieReduction, plan.recoveredKcal)
        assertEquals(540, plan.recoveredKcal)
    }

    @Test
    fun `base at or below the effective floor yields the no-adjustment note instead of a zero offer`() {
        // base 1150 <= MIN_EFFECTIVE_CAL -> floorCap = 0 -> calorie lever 0; no steps -> perDayCap = 0,
        // so `size` returns null (the only in-band no-plan case, spec §5) and the decision routes to the
        // supportive NO_ADJUSTMENT note rather than a meaningless Offer with R=0/E=0/recovered=0.
        // eaten 1650 -> over 500 (HIGH via abs), S = 500 (≥ 500 so past the reassurance gate).
        val decision = evaluate(input(base = 1150, eatenOverrides = mapOf(yesterday to 1650)))
        assertTrue(decision is RebalanceDecision.NoAdjustment)
        val plan = (decision as RebalanceDecision.NoAdjustment).plan
        assertEquals(RebalanceStatus.NO_ADJUSTMENT, plan.status)
        assertEquals(0, plan.dailyCalorieReduction)
        assertEquals(0, plan.extraDailySteps)
    }

    private fun declinedRecord(decidedOn: LocalDate, trigger: LocalDate) = RebalancePlan(
        id = "d", triggerDateIso = trigger.toString(), startDateIso = trigger.plus(1, DateTimeUnit.DAY).toString(),
        endDateIso = trigger.plus(3, DateTimeUnit.DAY).toString(), lengthDays = 3, mode = RebalanceMode.BALANCED,
        baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
        baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
        status = RebalanceStatus.DECLINED, createdAtIso = decidedOn.toString() + "T09:00:00Z",
        decidedAtIso = decidedOn.toString() + "T09:00:00Z", endedReason = "expired",
    )
}
