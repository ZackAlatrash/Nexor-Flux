package com.zack.recomptracker.domain.rebalance

import kotlinx.serialization.Serializable

/** Customize preference for how a plan splits its recovery between calories and steps. */
@Serializable
enum class RebalanceMode { EAT_LESS, BALANCED, MOVE_MORE }

/** Lifecycle status of a [RebalancePlan]. [NO_ADJUSTMENT] is a supportive note, not a plan. */
@Serializable
enum class RebalanceStatus { OFFERED, ACTIVE, COMPLETED, ENDED_EARLY, DECLINED, NO_ADJUSTMENT }

/**
 * A single weekly-rebalance offer/plan record, either the current [RebalanceState.active] entry
 * or a terminal record in [RebalanceState.history].
 *
 * @property id UUID.
 * @property triggerDateIso latest qualifying high day (weekend -> the Sunday).
 * @property startDateIso Day 1 (accept-day + 1).
 * @property endDateIso inclusive; start + lengthDays - 1.
 * @property lengthDays 2..5.
 * @property baseCalories base target at offer/accept (audit + derivation anchor).
 * @property dailyCalorieReduction R >= 0.
 * @property extraDailySteps E >= 0 (0 = calorie-only).
 * @property baseStepGoal null = user has no step goal.
 * @property recentAvgSteps 7-day avg at offer time — lets Customize recompute without re-reading data.
 * @property surplusKcal S the plan was sized against.
 * @property recoveredKcal D * (R + stepKcal(E)).
 * @property endedReason "completed" | "unrecoverable" | "plan_edited" | "expired" | "cancelled".
 */
@Serializable
data class RebalancePlan(
    val id: String,
    val triggerDateIso: String,
    val startDateIso: String,
    val endDateIso: String,
    val lengthDays: Int,
    val mode: RebalanceMode,
    val baseCalories: Int,
    val dailyCalorieReduction: Int,
    val extraDailySteps: Int,
    val baseStepGoal: Int?,
    val recentAvgSteps: Int?,
    val surplusKcal: Int,
    val recoveredKcal: Int,
    val status: RebalanceStatus,
    val createdAtIso: String,
    val decidedAtIso: String? = null,
    val endedReason: String? = null,
)

/** One bar in the offer's weekly-bars viz. Trailing-7 history days + the plan's lighter days ahead. */
data class RebalanceDayBar(
    val label: String,       // "Mon".."Sun"
    val valueKcal: Int,      // eaten (history days) or the reduced effective target (plan days)
    val targetKcal: Int,     // the base calorie target that day
    val isPlanDay: Boolean,  // false = trailing-7 history; true = a rebalance day ahead
    val isOver: Boolean,     // a history day that ended over its target (drove the offer)
)

/** Pure derived math over a [RebalancePlan]. The single home for the effective-target formula. */
object RebalancePlanMath {
    /**
     * The reduced daily calorie target while a plan is in effect: base minus the daily reduction,
     * floored at [RebalanceDefaults.MIN_EFFECTIVE_CAL]. This is the ONE formula — the coordinator's
     * weekly-bars viz, the dashboard ViewModel, the coach context, and the [EffectiveTargets] overlay
     * engine all delegate here so the floor is applied identically everywhere (see commit
     * "single effective formula").
     */
    fun effectiveCalories(baseCalories: Int, reduction: Int): Int =
        maxOf(baseCalories - reduction, RebalanceDefaults.MIN_EFFECTIVE_CAL)

    /** [effectiveCalories] for a whole [RebalancePlan]. */
    fun effectiveCalories(plan: RebalancePlan): Int =
        effectiveCalories(plan.baseCalories, plan.dailyCalorieReduction)
}

/**
 * Persisted weekly-rebalance state: at most one offered/active plan, a capped history of terminal
 * records, and the sticky Customize [mode] preference.
 *
 * `@Serializable` exists solely so [com.zack.recomptracker.domain.export.BackupPayload] can carry this
 * type directly (spec §9) via `kotlinx.serialization`. The DataStore-persisted round-trip still goes
 * through the hand-rolled [com.zack.recomptracker.data.rebalance.RebalanceSerialization] codec, which
 * this annotation does not touch or replace.
 */
@Serializable
data class RebalanceState(
    val active: RebalancePlan? = null,
    val history: List<RebalancePlan> = emptyList(),
    val mode: RebalanceMode = RebalanceMode.BALANCED,
) {
    companion object {
        const val HISTORY_CAP = 12
    }
}

/** All tunable constants for the weekly-rebalance engine (spec SS5). */
object RebalanceDefaults {
    const val MIN_LOGGED_DAYS_WINDOW = 4
    const val HIGH_DAY_ABS_KCAL = 400
    const val HIGH_DAY_PCT = 0.25
    const val WEEKEND_SURPLUS_KCAL = 600
    const val WEEKLY_IMPACT_MIN_KCAL = 50
    const val COOLDOWN_DAYS = 3
    const val RECOVERY_FRACTION = 0.75
    const val RECOVERY_FRACTION_RECOMP = 0.375
    const val RECOMP_MAX_LENGTH_DAYS = 3
    const val MAX_CAL_REDUCTION_PCT = 0.15
    const val MAX_CAL_REDUCTION_ABS = 300
    const val MIN_EFFECTIVE_CAL = 1200
    const val MAX_EXTRA_STEPS_PCT_OF_AVG = 0.25
    const val MAX_EXTRA_STEPS_ABS = 3000
    const val KCAL_PER_STEP = 0.04
    const val BALANCED_LEVER_FRACTION = 0.6
    const val UNRECOVERABLE_SLACK_KCAL = 75
    const val MIN_LENGTH_DAYS = 2
    const val MAX_LENGTH_DAYS = 5

    /**
     * Half-width of the effective calorie zone: zone = effective ± this. Mirrors
     * `PlanPreferences.CALORIE_ZONE_MARGIN` (100), duplicated here because that constant lives in
     * the data layer and pure `domain/` code must not depend on it.
     */
    const val ZONE_MARGIN = 100
}
