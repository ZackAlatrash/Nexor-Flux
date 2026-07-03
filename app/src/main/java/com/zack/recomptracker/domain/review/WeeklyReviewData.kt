package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.insight.InsightFact

/**
 * Steps/activity inputs for the weekly check-in — the fourth domain (alongside nutrition, body,
 * and training). Derived deterministically from the daily step logs; null when steps aren't logged.
 */
data class WeeklyActivity(
    val avgSteps7: Int?,
    val avgStepsPrev7: Int?,
    val stepGoal: Int? = null,
)

/**
 * Training inputs for the weekly check-in — the cross-domain training signal (alongside nutrition,
 * body, and steps). Derived deterministically from the completed-session window; null when no
 * training was logged. Supporting narrative only: the verdict/numbers stay authoritative in
 * [AdjustmentResult]; the briefing may reference [summary] but must never derive a new number from it.
 */
data class WeeklyTraining(
    /** Completed training sessions this week (trailing 7 days). */
    val sessionsThisWeek: Int,
    /** The user's top lift by latest estimated 1RM, or null when no weighted set was logged. */
    val topLift: String?,
    /** Direction of the top lift's e1RM over its most recent points. */
    val topLiftTrend: SignalDirection,
    /** Deterministic, number-safe one-liner the briefing can quote verbatim. Never blank. */
    val summary: String,
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
    /** Training domain, when training was logged this window. Supporting cross-domain narrative only. */
    val training: WeeklyTraining? = null,
    /**
     * Deterministic Weekly Pattern Spotlight facts (top two from [com.zack.recomptracker.domain.insight.InsightEngine]),
     * highest-priority first. Rendered verbatim in the briefing (Phase 2D); empty when no pattern fires.
     */
    val patternFacts: List<InsightFact> = emptyList(),
)
