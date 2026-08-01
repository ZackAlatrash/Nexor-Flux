package com.zack.recomptracker.domain.rebalance

import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.domain.plan.PlanTargets
import kotlinx.datetime.LocalDate

/**
 * Flattened snapshot the pure [RebalanceEngine] consumes (mirrors `AdjustmentInput`). All time-varying
 * data is pre-read by the coordinator into plain maps keyed by [LocalDate]; the engine reads no clock.
 *
 * The [FitnessGoal] enum lives in `data.preferences` but is a pure `@Serializable` data enum — the same
 * cross-reference `PlanCalculator` already makes from `domain/`.
 *
 * @property today the day the evaluation runs. The judged window is the trailing 7 days ending yesterday.
 * @property baseTargetsByDate base (permanent-plan) targets for the trailing 7 days ending yesterday.
 * @property eatenByDate eaten-only kcal per day (planned meals already excluded upstream).
 * @property mealCountByDate number of meal entries per day (the logged-day signal).
 * @property stepsByDate logged step counts per day.
 * @property baseStepGoal the user's permanent daily step goal; null = no step goal.
 * @property goal the user's fitness goal; null = treat as a cut.
 * @property mode the sticky Customize preference to size the first offer with.
 * @property existing the current persisted state (active plan + history) used for the re-trigger gates.
 */
data class RebalanceEvaluationInput(
    val today: LocalDate,
    val baseTargetsByDate: Map<LocalDate, PlanTargets>,
    val eatenByDate: Map<LocalDate, Int>,
    val mealCountByDate: Map<LocalDate, Int>,
    val stepsByDate: Map<LocalDate, Int>,
    val baseStepGoal: Int?,
    val goal: FitnessGoal?,
    val mode: RebalanceMode,
    val existing: RebalanceState,
)

/** Outcome of [RebalanceEngine.evaluate]. */
sealed class RebalanceDecision {
    /** Nothing to show — gates failed or the surplus doesn't warrant a card. Persists nothing. */
    data object Silent : RebalanceDecision()

    /** A supportive "no adjustment" note ([RebalanceStatus.NO_ADJUSTMENT], R=E=0). Occupies the card slot. */
    data class NoAdjustment(val plan: RebalancePlan) : RebalanceDecision()

    /** A concrete offer ([RebalanceStatus.OFFERED]) the user can start, decline, or customize. */
    data class Offer(val plan: RebalancePlan) : RebalanceDecision()
}

/**
 * Result of [RebalanceEngine.reconcile]. [ended] is non-null when the caller should surface a
 * graceful-early-end or completion note; null when the transition is silent (expiry, no-op).
 */
data class ReconcileResult(
    val state: RebalanceState,
    val ended: RebalancePlan? = null,
)
