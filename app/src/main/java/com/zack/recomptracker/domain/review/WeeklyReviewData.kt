package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult

/**
 * Steps/activity inputs for the weekly check-in — the fourth domain (alongside nutrition, body,
 * and training). Derived deterministically from the daily step logs; null when steps aren't logged.
 */
data class WeeklyActivity(
    val avgSteps7: Int?,
    val avgStepsPrev7: Int?,
    val stepGoal: Int? = null,
)

/** One deterministic per-signal row (no AI prose yet). */
data class SignalSkeleton(
    val id: String,            // "weight" | "waist" | "adherence" | "strength" | "recovery"
    val label: String,
    val value: String,
    val direction: SignalDirection,
)

/**
 * Everything the briefing needs that is computed, not generated. The model is handed this and
 * may only add prose on top of it.
 */
data class WeeklyReviewData(
    val weekStart: String,
    val phase: BriefingPhase,
    val daysLogged: Int,
    val input: AdjustmentInput,
    val result: AdjustmentResult,
    val verdictLabel: String,
    val signals: List<SignalSkeleton>,
    val currentTargetCalories: Int,
    /** Non-null only in FULL phase with an INCREASE/REDUCE verdict. */
    val applyTargetCalories: Int?,
    /** Steps/activity domain, when steps were logged this window. Feeds the signature + a signal row. */
    val activity: WeeklyActivity? = null,
)
