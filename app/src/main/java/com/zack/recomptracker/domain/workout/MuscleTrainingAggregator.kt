package com.zack.recomptracker.domain.workout

import java.time.LocalDate
import kotlin.math.exp

/**
 * Aggregates recent completed training into a per-[MuscleCategory] summary: weekly training
 * volume (with a prior-week trend delta) and a derived recovery score.
 *
 * Pure Kotlin — no Android / Room imports. Derives entirely from already-loaded domain data:
 * completed [WorkoutSession]s plus the exercise [Exercise] library (to resolve each session
 * exercise's muscle group via its `primaryMuscles`, exactly like [TrainStatsBuilder]).
 *
 * ### Inputs
 * - `sessions`: recent workout sessions. Only sets with `completed == true` contribute; a session
 *   whose `date` does not parse as an ISO `LocalDate` is skipped.
 * - `library`: the exercise library, used to look up each `SessionExercise.exerciseId`'s
 *   `primaryMuscles`. Exercises whose muscles don't map to a [MuscleCategory] are skipped
 *   (never crash — a null category is dropped).
 * - `today`: the reference "now" date. All windows are measured relative to it.
 *
 * ### Volume
 * Volume reuses [WorkoutProgressAnalyzer.setVolume] (`reps × weightKg`) for consistency with the
 * rest of the app — this component never reimplements volume/e1RM math.
 * - `weeklyVolume`  = Σ volume over the trailing 7 days: `today-6 .. today` inclusive.
 * - `priorWeeklyVolume` = Σ volume over the 7 days before that: `today-13 .. today-7` inclusive.
 * - `volumeDelta` = `weeklyVolume - priorWeeklyVolume`; [VolumeTrend] bands it (see [trendFor]).
 *
 * ### Multi-muscle attribution
 * [muscleCategoryFor] returns a *single* category per exercise (it buckets on the first primary
 * muscle), so each exercise attributes its full volume to exactly one group. There is no secondary
 * split to make — the taxonomy API is single-category by design, so this stays simple and correct.
 *
 * ### Recovery score
 * `recoveryScore ∈ [0f, 1f]`; `1f` = fully fresh, lower = more recently / heavily trained.
 * For each muscle we look at completed-set volume on each of the last [RECOVERY_WINDOW_DAYS] days
 * (today, yesterday, day-before) and apply an exponential recency decay:
 *
 * ```
 * fatigue = Σ_days  volume(day) × exp(-daysAgo / RECOVERY_TAU)
 * load    = fatigue / RECOVERY_SATURATION_VOLUME        // normalised, clamped to [0,1]
 * recoveryScore = 1f - min(1f, load)
 * ```
 *
 * - `daysAgo` is 0 for today, 1 for yesterday, 2 for the day before. Volume older than
 *   [RECOVERY_WINDOW_DAYS] contributes nothing (assumed fully recovered after ~3 days).
 * - [RECOVERY_TAU] = 1.5 days sets the decay: a same-day session weighs `1.0`, yesterday `~0.51`,
 *   two days ago `~0.26` — so freshness climbs back over roughly three days.
 * - [RECOVERY_SATURATION_VOLUME] = 4000.0 (kg·reps) is the effective per-muscle volume that
 *   saturates fatigue to fully-recovering (score `0f`). It's a coarse, deterministic normaliser
 *   for a UI band, not a physiological model. A muscle with no volume in the window scores `1f`.
 *
 * The score is banded into [RecoveryBand] (Fresh / Moderate / Recovering) for easy UI use.
 */
object MuscleTrainingAggregator {

    /** Trailing volume window, in days, for the "weekly" figures. */
    const val WEEK_DAYS: Int = 7

    /** How many recent days feed the recovery estimate (today + previous two). */
    const val RECOVERY_WINDOW_DAYS: Int = 3

    /** Exponential decay time-constant (days) for recency-weighted fatigue. */
    const val RECOVERY_TAU: Double = 1.5

    /** Per-muscle volume (kg·reps) that saturates fatigue to a fully-recovering score of 0. */
    const val RECOVERY_SATURATION_VOLUME: Double = 4000.0

    /** Coarse direction of the week-over-week volume change. */
    enum class VolumeTrend { UP, FLAT, DOWN }

    /** Coarse recovery band derived from [MuscleSummary.recoveryScore]. */
    enum class RecoveryBand { FRESH, MODERATE, RECOVERING }

    /**
     * Fraction of prior-week volume that a change must exceed to count as UP/DOWN rather than FLAT.
     * When prior volume is zero, any positive current volume is UP and zero stays FLAT.
     */
    private const val TREND_FLAT_FRACTION: Double = 0.10

    /** recoveryScore >= this is [RecoveryBand.FRESH]. */
    private const val FRESH_THRESHOLD: Float = 0.75f

    /** recoveryScore >= this (and below [FRESH_THRESHOLD]) is [RecoveryBand.MODERATE]; below is RECOVERING. */
    private const val MODERATE_THRESHOLD: Float = 0.40f

    /** Per-muscle-group training summary. */
    data class MuscleSummary(
        val category: MuscleCategory,
        /** Σ set volume over the trailing 7 days (completed sets only). */
        val weeklyVolume: Double,
        /** Σ set volume over the 7 days before the trailing week. */
        val priorWeeklyVolume: Double,
        /** `weeklyVolume - priorWeeklyVolume`. Positive = trending up. */
        val volumeDelta: Double,
        /** Coarse banding of [volumeDelta] relative to [priorWeeklyVolume]. */
        val volumeTrend: VolumeTrend,
        /** 0f (still recovering) .. 1f (fully fresh). See class doc for the formula. */
        val recoveryScore: Float,
        /** Coarse band derived from [recoveryScore]. */
        val recoveryBand: RecoveryBand,
    )

    /**
     * Builds a [MuscleSummary] for every [MuscleCategory] (all six are always present; a group with
     * no recent training reads zero volume, [VolumeTrend.FLAT], and a fully-fresh recovery score).
     */
    fun aggregate(
        sessions: List<WorkoutSession>,
        library: List<Exercise>,
        today: LocalDate,
    ): List<MuscleSummary> {
        val musclesByExerciseId: Map<Long, List<String>> =
            library.associate { it.id to it.primaryMuscles }

        // Per category: volume bucketed by whole days-ago (0 = today, 1 = yesterday, ...).
        val volumeByDaysAgo: Map<MuscleCategory, MutableMap<Int, Double>> =
            MuscleCategory.entries.associateWith { mutableMapOf() }

        for (session in sessions) {
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: continue
            val daysAgo = today.toEpochDay().minus(date.toEpochDay()).toInt()
            // Ignore future-dated sessions and anything older than the prior-week window.
            if (daysAgo < 0 || daysAgo >= 2 * WEEK_DAYS) continue

            for (ex in session.exercises) {
                val category = muscleCategoryFor(musclesByExerciseId[ex.exerciseId].orEmpty())
                    ?: continue
                val exVolume = ex.sets
                    .filter { it.completed }
                    .sumOf { WorkoutProgressAnalyzer.setVolume(it.reps, it.weightKg) }
                if (exVolume == 0.0) continue
                val bucket = volumeByDaysAgo.getValue(category)
                bucket[daysAgo] = (bucket[daysAgo] ?: 0.0) + exVolume
            }
        }

        return MuscleCategory.entries.map { category ->
            val byDay = volumeByDaysAgo.getValue(category)
            val weekly = byDay.entries.filter { it.key in 0 until WEEK_DAYS }.sumOf { it.value }
            val prior = byDay.entries.filter { it.key in WEEK_DAYS until 2 * WEEK_DAYS }.sumOf { it.value }
            val delta = weekly - prior
            val recovery = recoveryScore(byDay)
            MuscleSummary(
                category = category,
                weeklyVolume = weekly,
                priorWeeklyVolume = prior,
                volumeDelta = delta,
                volumeTrend = trendFor(weekly, prior),
                recoveryScore = recovery,
                recoveryBand = bandFor(recovery),
            )
        }
    }

    /** Recency-weighted fatigue over the last [RECOVERY_WINDOW_DAYS] days → freshness in [0f, 1f]. */
    private fun recoveryScore(volumeByDaysAgo: Map<Int, Double>): Float {
        var fatigue = 0.0
        for (daysAgo in 0 until RECOVERY_WINDOW_DAYS) {
            val volume = volumeByDaysAgo[daysAgo] ?: 0.0
            if (volume == 0.0) continue
            fatigue += volume * exp(-daysAgo.toDouble() / RECOVERY_TAU)
        }
        val load = (fatigue / RECOVERY_SATURATION_VOLUME).coerceIn(0.0, 1.0)
        return (1.0 - load).toFloat()
    }

    private fun trendFor(weekly: Double, prior: Double): VolumeTrend {
        if (prior == 0.0) return if (weekly > 0.0) VolumeTrend.UP else VolumeTrend.FLAT
        val fraction = (weekly - prior) / prior
        return when {
            fraction > TREND_FLAT_FRACTION -> VolumeTrend.UP
            fraction < -TREND_FLAT_FRACTION -> VolumeTrend.DOWN
            else -> VolumeTrend.FLAT
        }
    }

    private fun bandFor(score: Float): RecoveryBand = when {
        score >= FRESH_THRESHOLD -> RecoveryBand.FRESH
        score >= MODERATE_THRESHOLD -> RecoveryBand.MODERATE
        else -> RecoveryBand.RECOVERING
    }
}
