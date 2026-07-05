package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.domain.plan.PlanTargets
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** A plan day's position for the "Rebalance · Day X of Y" chip. */
data class PlanDayInfo(val dayX: Int, val ofY: Int, val plan: RebalancePlan)

/**
 * Pure resolver: base [PlanTargets] + rebalance windows -> the *effective* targets/step goal a
 * consumer should judge or display a given date against.
 *
 * A date is "in a plan window" when a plan overrides it (see [overridingPlan]): an ACTIVE/COMPLETED
 * plan covers `[startDate, endDate]`; an ENDED_EARLY plan covers only up to its recorded (rewritten)
 * end date. OFFERED/DECLINED/NO_ADJUSTMENT plans never override — their R/E are provisional or zero.
 *
 * Effective calories = `max(baseCalories - dailyCalorieReduction, MIN_EFFECTIVE_CAL)`; macros re-derive
 * per spec §5.7 (protein held from base, fat 25% of effective kcal, carbs the remainder ≥ 0); zone
 * re-centres to `effective ± ZONE_MARGIN`.
 */
object EffectiveTargets {

    /** Effective targets for [date]: reduced on a plan day, otherwise [base] unchanged. */
    fun resolve(base: PlanTargets, date: LocalDate, state: RebalanceState): PlanTargets {
        val plan = overridingPlan(date, state) ?: return base
        return reduce(base, plan.dailyCalorieReduction)
    }

    /** [resolve] applied to every entry of [baseByDate]. */
    fun resolveAll(
        baseByDate: Map<LocalDate, PlanTargets>,
        state: RebalanceState,
    ): Map<LocalDate, PlanTargets> = baseByDate.mapValues { (date, base) -> resolve(base, date, state) }

    /**
     * Lenient union of the base and effective calorie zones on a plan day (so a rebalance can never
     * break a streak the user would otherwise have kept); the base zone on any other day.
     */
    fun unionZone(base: PlanTargets, date: LocalDate, state: RebalanceState): IntRange {
        val plan = overridingPlan(date, state) ?: return base.zoneLowerBound..base.zoneUpperBound
        val eff = reduce(base, plan.dailyCalorieReduction)
        return minOf(base.zoneLowerBound, eff.zoneLowerBound)..maxOf(base.zoneUpperBound, eff.zoneUpperBound)
    }

    /**
     * The step goal to *display* on [date]: base goal + the plan's extra steps on a plan day, the base
     * goal otherwise. Null when the user has no step goal (nothing to boost). Judgment stays on the base
     * goal — this value is display/progress only.
     */
    fun effectiveStepGoal(baseGoal: Int?, date: LocalDate, state: RebalanceState): Int? {
        if (baseGoal == null) return null
        val plan = overridingPlan(date, state) ?: return baseGoal
        return baseGoal + plan.extraDailySteps
    }

    /** Day-X-of-Y info when [date] falls in a plan window, else null. */
    fun planDayInfo(date: LocalDate, state: RebalanceState): PlanDayInfo? {
        val plan = overridingPlan(date, state) ?: return null
        val start = LocalDate.parse(plan.startDateIso)
        val dayX = (ChronoUnit.DAYS.between(start, date) + 1).toInt()
        return PlanDayInfo(dayX = dayX, ofY = plan.lengthDays, plan = plan)
    }

    /**
     * The plan (if any) whose reduced targets apply to [date]. Checks the active plan first, then
     * history. Windows never overlap by construction, so the first match is authoritative.
     */
    private fun overridingPlan(date: LocalDate, state: RebalanceState): RebalancePlan? {
        state.active?.let { if (overrides(it, date)) return it }
        return state.history.firstOrNull { overrides(it, date) }
    }

    private fun overrides(plan: RebalancePlan, date: LocalDate): Boolean {
        val applies = when (plan.status) {
            RebalanceStatus.ACTIVE,
            RebalanceStatus.COMPLETED,
            RebalanceStatus.ENDED_EARLY,
            -> true
            RebalanceStatus.OFFERED,
            RebalanceStatus.DECLINED,
            RebalanceStatus.NO_ADJUSTMENT,
            -> false
        }
        if (!applies) return false
        val start = LocalDate.parse(plan.startDateIso)
        val end = LocalDate.parse(plan.endDateIso)
        return !date.isBefore(start) && !date.isAfter(end)
    }

    /** Applies a calorie reduction and re-derives macros + zone (spec §5.7). */
    private fun reduce(base: PlanTargets, reduction: Int): PlanTargets {
        val eff = (base.calories - reduction).coerceAtLeast(RebalanceDefaults.MIN_EFFECTIVE_CAL)
        val proteinG = base.proteinG
        val fatG = (0.25 * eff / 9.0).roundToInt()
        val carbsG = ((eff - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return PlanTargets(
            calories = eff,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            zoneLowerBound = eff - RebalanceDefaults.ZONE_MARGIN,
            zoneUpperBound = eff + RebalanceDefaults.ZONE_MARGIN,
        )
    }
}
