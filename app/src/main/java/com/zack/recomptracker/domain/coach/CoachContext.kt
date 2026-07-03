package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import java.time.LocalDate

/**
 * The single canonical snapshot of all coaching-relevant user data — the one input to the
 * [CoachSignalEngine] (`docs/ai-redesign/08-technical-architecture.md` §6). Built once per run
 * (suspend one-shot) by `data/coach/CoachContextBuilder`, which maps Room entities / DataStore /
 * domain derivations into these PURE value types so `domain/coach` stays Android- and
 * data-layer-free and every detector is unit-testable with plain objects.
 */
data class CoachContext(
    val asOf: LocalDate,
    val plan: PlanContext,
    val profile: ProfileContext,
    val nutrition: NutritionContext,
    val body: BodyContext,
    val training: TrainingContext,
    val streaks: StreaksContext,
    val history: HistoryContext,
)

/** A single dated numeric reading (weight, waist, sleep, …). Null value = not logged that day. */
data class MetricPoint(val date: LocalDate, val value: Double)

// ── Plan ───────────────────────────────────────────────────────────────────────
data class PlanContext(
    val targetCalories: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int,
    val targetFatG: Int,
    val zoneLowerBound: Int,
    val zoneUpperBound: Int,
    val maintenancePhaseStartDate: LocalDate? = null,
    /** Weeks since the current phase started, when known (drives phase-aware coaching). */
    val weeksSincePhaseStart: Int? = null,
)

// ── Profile ────────────────────────────────────────────────────────────────────
data class ProfileContext(
    val goal: String? = null,
    val sex: String? = null,
    val ageYears: Int? = null,
    val heightCm: Int? = null,
    val activityLevel: String? = null,
    val weeklyGymSessions: Int? = null,
    val dailyStepGoal: Int? = null,
)

// ── Nutrition ──────────────────────────────────────────────────────────────────
data class NutritionContext(
    val todayEaten: MacroTotals,
    val todayPlanned: MacroTotals,
    val todayMealsLogged: Int,
    /** Eaten macros per day over the window (planned excluded). */
    val eatenByDate: Map<LocalDate, MacroTotals>,
    val loggedDaysInWindow: Int,
    /** Graded per-day closeness over logged days, 0..100. Null if no logged days. */
    val adherencePercent: Double? = null,
    val loggingConsistencyPercent: Double? = null,
    /** Planned meals for today or the past that were never confirmed eaten. */
    val unconfirmedPlannedCount: Int = 0,
)

// ── Body / recovery / activity ─────────────────────────────────────────────────
data class BodyContext(
    val weightSeries: List<MetricPoint>,
    val waistSeries: List<MetricPoint>,
    val skinfoldSeries: List<MetricPoint>,
    val sleepSeries: List<MetricPoint>,
    val energySeries: List<MetricPoint>,
    val hungerSeries: List<MetricPoint>,
    val sorenessSeries: List<MetricPoint>,
    val stepsByDate: Map<LocalDate, Int>,
    val trainedDates: Set<LocalDate>,
    /** Smoothed trends (computed in the builder via TrendCalculator). Null when insufficient data. */
    val weightTrendKgPerWeek: Double? = null,
    val waistTrendCmPerWeek: Double? = null,
    val skinfoldTrendMmPerWeek: Double? = null,
    val avgSteps7: Int? = null,
    val avgStepsPrev7: Int? = null,
    val daysSinceLastWeighIn: Int? = null,
)

// ── Training ───────────────────────────────────────────────────────────────────
data class LiftE1rmPoint(val date: LocalDate, val estimatedOneRepMax: Double)
data class TrainingContext(
    val completedSessionDates: List<LocalDate>,
    val weeklyTrainingFrequency: Double,
    val weeklyGymSessionsTarget: Int? = null,
    /** Estimated-1RM series per exercise name, oldest → newest, over the window. */
    val e1rmByExercise: Map<String, List<LiftE1rmPoint>> = emptyMap(),
    /** Recent reps-in-reserve values across the last few sessions (for deload detection). */
    val recentRir: List<Int> = emptyList(),
)

// ── Streaks ────────────────────────────────────────────────────────────────────
data class StreakSnapshot(val current: Int, val longest: Int)
data class StreaksContext(
    val workout: StreakSnapshot,
    val calorie: StreakSnapshot,
    val steps: StreakSnapshot,
)

// ── History (journey memory + dedup input) ──────────────────────────────────────
data class WeeklyReviewSnapshot(val weekStart: LocalDate, val verdict: String, val signature: String)
data class HistoryContext(
    val weeklyReviews: List<WeeklyReviewSnapshot> = emptyList(),
)
