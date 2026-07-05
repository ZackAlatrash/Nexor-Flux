package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.plan.PlanTargets
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pure weekly-rebalance engine. All time-varying data arrives in [RebalanceEvaluationInput]; ids and
 * timestamps are injected (`newId`/`nowIso`) so no wall-clock is read here. Every constant comes from
 * [RebalanceDefaults].
 *
 * The window judged is the trailing 7 days ending **yesterday** (`today` is never judged). `over(d)`
 * uses the positive-part surplus convention (`max(eaten − base, 0)`), matching
 * `PatternDetectors.detectDerailmentDay`.
 *
 * **Steps-unavailable rule (spec §5.5 line 147, §5.6):** when `recentAvgSteps == null` OR
 * `baseStepGoal == null` the plan is *calorie-only* for **every** mode — the per-day capacity is the
 * full `calCap` and `E` is always 0. The 0.6/0.6 BALANCED split only applies when steps are available.
 * This is what makes the mandatory worked example (BALANCED, no steps) size at `perDayCap = calCap = 300`.
 */
object RebalanceEngine {

    // ── Public API ────────────────────────────────────────────────────────────────────

    fun evaluate(
        input: RebalanceEvaluationInput,
        newId: () -> String,
        nowIso: () -> String,
    ): RebalanceDecision {
        // Gate 1: never offer while one is offered/active.
        if (input.existing.active != null) return RebalanceDecision.Silent

        val yesterday = input.today.minusDays(1)
        val window: List<LocalDate> = (1..7).map { input.today.minusDays(it.toLong()) }

        // Gate 2: data trust — ≥ MIN_LOGGED_DAYS_WINDOW of the 7 window days logged.
        val loggedDays = window.filter { isLogged(input, it) }
        if (loggedDays.size < RebalanceDefaults.MIN_LOGGED_DAYS_WINDOW) return RebalanceDecision.Silent

        // High days + weekend.
        val highDays = window.filter { isLogged(input, it) && isHigh(input, it) }
        val weekendSunday = qualifyingWeekendSunday(input, window)

        // Gate 3: no qualifying trigger at all.
        if (highDays.isEmpty() && weekendSunday == null) return RebalanceDecision.Silent

        // Latest qualifying high day (weekend → its Sunday); also the plan's triggerDate.
        val triggerDate = (highDays + listOfNotNull(weekendSunday)).max()

        // Gate 4: cooldown + new-event against the most recent terminal record.
        val mostRecent = input.existing.history.maxByOrNull { referenceDate(it) }
        if (mostRecent != null) {
            val daysSince = java.time.temporal.ChronoUnit.DAYS.between(referenceDate(mostRecent), input.today)
            if (daysSince < RebalanceDefaults.COOLDOWN_DAYS) return RebalanceDecision.Silent
            if (!triggerDate.isAfter(isoDate(mostRecent.triggerDateIso))) return RebalanceDecision.Silent
        }

        // Surplus over the whole window (positive part, logged days only).
        val surplus = loggedDays.sumOf { over(input, it) }

        // Gate 5: weekly-impact test (integer-safe: S ≥ 50 * 7).
        if (surplus < RebalanceDefaults.WEEKLY_IMPACT_MIN_KCAL * 7) return RebalanceDecision.Silent

        // Gate 6: goal branches. Bulk goals never offer.
        if (input.goal.isBulk()) return RebalanceDecision.Silent

        val baseCalories = baseCaloriesFor(input, yesterday, window)
        val recentAvgSteps = ActivitySummary.averageDailySteps(input.stepsByDate, yesterday, 7)

        val sizing = size(
            surplus = surplus,
            baseCalories = baseCalories,
            recentAvgSteps = recentAvgSteps,
            baseStepGoal = input.baseStepGoal,
            goal = input.goal,
            mode = input.mode,
        )

        val now = nowIso()
        if (sizing == null) {
            // Too-large surplus → a self-consistent NO_ADJUSTMENT note (start = end = triggerDate).
            return RebalanceDecision.NoAdjustment(
                RebalancePlan(
                    id = newId(),
                    triggerDateIso = triggerDate.toString(),
                    startDateIso = triggerDate.toString(),
                    endDateIso = triggerDate.toString(),
                    lengthDays = 0,
                    mode = input.mode,
                    baseCalories = baseCalories,
                    dailyCalorieReduction = 0,
                    extraDailySteps = 0,
                    baseStepGoal = input.baseStepGoal,
                    recentAvgSteps = recentAvgSteps,
                    surplusKcal = surplus,
                    recoveredKcal = 0,
                    status = RebalanceStatus.NO_ADJUSTMENT,
                    createdAtIso = now,
                ),
            )
        }

        // Provisional window at offer time; accept (Task 5) re-stamps start/end.
        val start = input.today.plusDays(1)
        val end = start.plusDays((sizing.days - 1).toLong())
        return RebalanceDecision.Offer(
            RebalancePlan(
                id = newId(),
                triggerDateIso = triggerDate.toString(),
                startDateIso = start.toString(),
                endDateIso = end.toString(),
                lengthDays = sizing.days,
                mode = input.mode,
                baseCalories = baseCalories,
                dailyCalorieReduction = sizing.reduction,
                extraDailySteps = sizing.extraSteps,
                baseStepGoal = input.baseStepGoal,
                recentAvgSteps = recentAvgSteps,
                surplusKcal = surplus,
                recoveredKcal = sizing.recovered,
                status = RebalanceStatus.OFFERED,
                createdAtIso = now,
            ),
        )
    }

    fun reconcile(
        state: RebalanceState,
        today: LocalDate,
        baseTargetsByDate: Map<LocalDate, PlanTargets>,
        eatenByDate: Map<LocalDate, Int>,
    ): ReconcileResult {
        val active = state.active ?: return ReconcileResult(state)

        when (active.status) {
            RebalanceStatus.ACTIVE -> {
                val endDate = isoDate(active.endDateIso)
                // (1) Past its end → COMPLETED.
                if (today.isAfter(endDate)) {
                    val done = active.copy(status = RebalanceStatus.COMPLETED, endedReason = "completed")
                    return ReconcileResult(archive(state, done), ended = done)
                }
                // (2) Clearly unrecoverable → ENDED_EARLY, endDate rewritten to the last effective day.
                if (isUnrecoverable(active, today, baseTargetsByDate, eatenByDate)) {
                    val start = isoDate(active.startDateIso)
                    // Last day it was actually in effect: the day before `today`, clamped ≥ startDate.
                    val lastEffective = maxOf(today.minusDays(1), start)
                    val ended = active.copy(
                        status = RebalanceStatus.ENDED_EARLY,
                        endDateIso = lastEffective.toString(),
                        endedReason = "unrecoverable",
                    )
                    return ReconcileResult(archive(state, ended), ended = ended)
                }
                // Still running and recoverable — untouched. R/E never change here.
                return ReconcileResult(state)
            }

            RebalanceStatus.OFFERED, RebalanceStatus.NO_ADJUSTMENT -> {
                // (3) Lived its one day → history. OFFERED becomes DECLINED("expired"); a note stays a note.
                val createdDate = isoDate(active.createdAtIso)
                if (today.isAfter(createdDate)) {
                    val moved = if (active.status == RebalanceStatus.OFFERED) {
                        active.copy(status = RebalanceStatus.DECLINED, endedReason = "expired")
                    } else {
                        active
                    }
                    return ReconcileResult(archive(state, moved), ended = null)
                }
                return ReconcileResult(state)
            }

            else -> return ReconcileResult(state)
        }
    }

    /**
     * Recomputes an OFFERED plan's R/E/D/recovered for [newMode] from the facts stored on the offer
     * (`surplusKcal`, `recentAvgSteps`, `baseCalories`, `baseStepGoal`). No data re-read; OFFERED only.
     * The goal is NOT stored on the plan, so the caller must pass the user's current [goal] — it keeps
     * the re-size goal-aware (RECOMP's halved fraction and 3-day length cap, spec §5.6). Null goal →
     * full cut behavior, matching [evaluate].
     */
    fun customize(offer: RebalancePlan, newMode: RebalanceMode, goal: FitnessGoal?): RebalancePlan {
        val sizing = size(
            surplus = offer.surplusKcal,
            baseCalories = offer.baseCalories,
            recentAvgSteps = offer.recentAvgSteps,
            baseStepGoal = offer.baseStepGoal,
            goal = goal,
            mode = newMode,
        ) ?: return offer.copy(mode = newMode) // too-large under this mode: keep facts, just switch mode

        val start = isoDate(offer.startDateIso)
        val end = start.plusDays((sizing.days - 1).toLong())
        return offer.copy(
            mode = newMode,
            lengthDays = sizing.days,
            endDateIso = end.toString(),
            dailyCalorieReduction = sizing.reduction,
            extraDailySteps = sizing.extraSteps,
            recoveredKcal = sizing.recovered,
            status = RebalanceStatus.OFFERED,
        )
    }

    // ── Trigger helpers ─────────────────────────────────────────────────────────────

    private fun isLogged(input: RebalanceEvaluationInput, d: LocalDate): Boolean =
        (input.mealCountByDate[d] ?: 0) >= 1

    private fun over(input: RebalanceEvaluationInput, d: LocalDate): Int {
        val base = input.baseTargetsByDate[d]?.calories ?: return 0
        val eaten = input.eatenByDate[d] ?: return 0
        return (eaten - base).coerceAtLeast(0)
    }

    /** Raw (signed) overage for the HIGH-day test; positive part is used everywhere else. */
    private fun rawOver(input: RebalanceEvaluationInput, d: LocalDate): Int {
        val base = input.baseTargetsByDate[d]?.calories ?: return 0
        val eaten = input.eatenByDate[d] ?: return 0
        return eaten - base
    }

    private fun isHigh(input: RebalanceEvaluationInput, d: LocalDate): Boolean {
        val base = input.baseTargetsByDate[d]?.calories ?: return false
        val ov = rawOver(input, d)
        return ov >= RebalanceDefaults.HIGH_DAY_ABS_KCAL || ov >= RebalanceDefaults.HIGH_DAY_PCT * base
    }

    /**
     * The Sunday of the most recent completed Sat+Sun (both < today, both logged) whose positive-part
     * surplus sum ≥ WEEKEND_SURPLUS_KCAL, or null. Only the most-recent weekend pair in the window is
     * considered.
     */
    private fun qualifyingWeekendSunday(
        input: RebalanceEvaluationInput,
        window: List<LocalDate>,
    ): LocalDate? {
        val sunday = window.filter { it.dayOfWeek == DayOfWeek.SUNDAY }.maxOrNull() ?: return null
        val saturday = sunday.minusDays(1)
        if (saturday !in window) return null
        if (!isLogged(input, saturday) || !isLogged(input, sunday)) return null
        val sum = over(input, saturday) + over(input, sunday)
        return if (sum >= RebalanceDefaults.WEEKEND_SURPLUS_KCAL) sunday else null
    }

    private fun baseCaloriesFor(
        input: RebalanceEvaluationInput,
        yesterday: LocalDate,
        window: List<LocalDate>,
    ): Int {
        input.baseTargetsByDate[yesterday]?.let { return it.calories }
        // Fall back to the most recent available target in the window.
        return window.sortedDescending()
            .firstNotNullOfOrNull { input.baseTargetsByDate[it]?.calories }
            ?: 0
    }

    private fun referenceDate(plan: RebalancePlan): LocalDate = when (plan.status) {
        RebalanceStatus.COMPLETED, RebalanceStatus.ENDED_EARLY -> isoDate(plan.endDateIso)
        else -> isoDate(plan.decidedAtIso ?: plan.createdAtIso)
    }

    // ── Sizing (spec §5.5) ───────────────────────────────────────────────────────────

    private data class Sizing(val days: Int, val reduction: Int, val extraSteps: Int, val recovered: Int)

    /**
     * Sizes a plan, or returns null when the surplus is too large to recover within the allowed length.
     * Raw math is kept through the cap computation; only the FINAL R and E are rounded.
     */
    private fun size(
        surplus: Int,
        baseCalories: Int,
        recentAvgSteps: Int?,
        baseStepGoal: Int?,
        goal: FitnessGoal?,
        mode: RebalanceMode,
    ): Sizing? {
        val fraction = if (goal == FitnessGoal.RECOMP) {
            RebalanceDefaults.RECOVERY_FRACTION_RECOMP
        } else {
            RebalanceDefaults.RECOVERY_FRACTION
        }
        val maxDays = if (goal == FitnessGoal.RECOMP) {
            RebalanceDefaults.RECOMP_MAX_LENGTH_DAYS
        } else {
            RebalanceDefaults.MAX_LENGTH_DAYS
        }
        val targetRecover = (surplus * fraction).roundToInt()

        val calCap = minOf(
            round10(RebalanceDefaults.MAX_CAL_REDUCTION_PCT * baseCalories),
            RebalanceDefaults.MAX_CAL_REDUCTION_ABS,
        )
        val stepsAvailable = recentAvgSteps != null && baseStepGoal != null
        val stepsCap = if (stepsAvailable) {
            round500(RebalanceDefaults.MAX_EXTRA_STEPS_PCT_OF_AVG * recentAvgSteps!!)
                .coerceIn(0, RebalanceDefaults.MAX_EXTRA_STEPS_ABS)
        } else {
            0
        }

        // Per-day capacity (raw kcal) by mode. Steps unavailable → calorie-only for every mode.
        val perDayCap: Double = when {
            !stepsAvailable -> calCap.toDouble()
            mode == RebalanceMode.EAT_LESS -> calCap.toDouble()
            mode == RebalanceMode.MOVE_MORE ->
                if (stepsCap > 0) stepKcalRaw(stepsCap) else calCap.toDouble() // fall back to EAT_LESS
            else -> // BALANCED
                RebalanceDefaults.BALANCED_LEVER_FRACTION * calCap +
                    stepKcalRaw(RebalanceDefaults.BALANCED_LEVER_FRACTION * stepsCap)
        }

        if (perDayCap <= 0.0 || maxDays * perDayCap < targetRecover) return null

        // Smallest D in [MIN_LENGTH_DAYS, maxDays] with D * perDayCap ≥ targetRecover.
        val days = (RebalanceDefaults.MIN_LENGTH_DAYS..maxDays)
            .first { it * perDayCap >= targetRecover }
        val perDay = ceil(targetRecover.toDouble() / days)

        // Split into raw R/E by mode, then round + cap.
        val (rRaw, eRaw) = when {
            !stepsAvailable -> perDay to 0.0
            mode == RebalanceMode.EAT_LESS -> perDay to 0.0
            mode == RebalanceMode.MOVE_MORE ->
                if (stepsCap > 0) 0.0 to (perDay / RebalanceDefaults.KCAL_PER_STEP) else perDay to 0.0
            else -> { // BALANCED: calories first (up to their 0.6 lever), remainder into steps
                val calLever = RebalanceDefaults.BALANCED_LEVER_FRACTION * calCap
                val r = minOf(perDay, calLever)
                val remainderKcal = perDay - r
                r to (remainderKcal / RebalanceDefaults.KCAL_PER_STEP)
            }
        }

        // R is also clamped so the effective target never dips below MIN_EFFECTIVE_CAL: this keeps
        // recoveredKcal (the audit number) consistent with the resolver's floored effective target on
        // very low base plans. The resolver keeps its own floor as belt-and-braces.
        val floorCap = (baseCalories - RebalanceDefaults.MIN_EFFECTIVE_CAL).coerceAtLeast(0)
        val reduction = round10(rRaw).coerceIn(0, minOf(calCap, floorCap))
        val extraSteps = round100(eRaw).coerceIn(0, stepsCap)
        val recovered = days * (reduction + stepKcal(extraSteps))
        return Sizing(days = days, reduction = reduction, extraSteps = extraSteps, recovered = recovered)
    }

    // ── Rounding + kcal helpers ──────────────────────────────────────────────────────

    private fun round10(x: Double): Int = (x / 10.0).roundToInt() * 10
    private fun round100(x: Double): Int = (x / 100.0).roundToInt() * 100
    private fun round500(x: Double): Int = (x / 500.0).roundToInt() * 500

    private fun stepKcal(steps: Int): Int = (steps * RebalanceDefaults.KCAL_PER_STEP).roundToInt()
    private fun stepKcalRaw(steps: Number): Double = steps.toDouble() * RebalanceDefaults.KCAL_PER_STEP

    // ── Reconcile helpers ────────────────────────────────────────────────────────────

    private fun isUnrecoverable(
        plan: RebalancePlan,
        today: LocalDate,
        baseTargetsByDate: Map<LocalDate, PlanTargets>,
        eatenByDate: Map<LocalDate, Int>,
    ): Boolean {
        // S_now: positive-part surplus vs BASE over the trailing 7 days ending yesterday.
        val window = (1..7).map { today.minusDays(it.toLong()) }
        val sNow = window.sumOf { d ->
            val base = baseTargetsByDate[d]?.calories ?: return@sumOf 0
            val eaten = eatenByDate[d] ?: return@sumOf 0
            (eaten - base).coerceAtLeast(0)
        }
        val start = isoDate(plan.startDateIso)
        val end = isoDate(plan.endDateIso)
        // Remaining days from max(today, start) through end inclusive.
        val from = maxOf(today, start)
        val remainingDays = if (from.isAfter(end)) 0 else
            (java.time.temporal.ChronoUnit.DAYS.between(from, end) + 1).toInt()
        val perDayRecover = plan.dailyCalorieReduction + stepKcal(plan.extraDailySteps)
        val slack = (sNow - remainingDays * perDayRecover) / 7.0
        return slack > RebalanceDefaults.UNRECOVERABLE_SLACK_KCAL
    }

    /** Moves [terminal] into history (newest first, capped), clearing the active slot. */
    private fun archive(state: RebalanceState, terminal: RebalancePlan): RebalanceState {
        val history = (listOf(terminal) + state.history)
            .sortedByDescending { isoDateTimeKey(it.createdAtIso) }
            .take(RebalanceState.HISTORY_CAP)
        return state.copy(active = null, history = history)
    }

    // ── ISO helpers ──────────────────────────────────────────────────────────────────

    private fun isoDate(s: String): LocalDate =
        runCatching { LocalDate.parse(s.substring(0, 10)) }.getOrDefault(LocalDate.MIN)

    /** Sort key for capping history newest-first by creation instant (lexicographic ISO-8601 works). */
    private fun isoDateTimeKey(s: String): String = s

    private fun FitnessGoal?.isBulk(): Boolean = this == FitnessGoal.LEAN_BULK ||
        this == FitnessGoal.MODERATE_BULK ||
        this == FitnessGoal.AGGRESSIVE_BULK
}
