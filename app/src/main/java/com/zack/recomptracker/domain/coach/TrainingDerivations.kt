package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzer
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate

/** Direction of a per-lift e1RM trend over its most recent points. */
enum class TrendDirection { UP, FLAT, DOWN }

/**
 * Shared, PURE training-progress derivations over already-fetched, dated [WorkoutSession]s
 * (docs/ai-redesign/08-technical-architecture.md §6). This is the single home for the per-lift
 * e1RM math so the `get_training_summary` coach tool (Phase 3) and [CoachContextAssembler] don't
 * duplicate it. No Android, no coroutines, no Room — all inputs are plain domain objects, so every
 * function is unit-testable with hand-built fixtures.
 *
 * Every function takes `sessions` as a list of `(date, session)` pairs — the caller is responsible
 * for windowing/parsing dates once (the assembler already does this) so the derivation never has to
 * re-parse a session's `date` string. A completed, *weighted* set is the atom everywhere: incomplete
 * sets and bodyweight (null-weight) sets are excluded, matching [WorkoutProgressAnalyzer].
 */
object TrainingDerivations {

    /** Default number of most-recent sessions [recentRir] scans. */
    const val DEFAULT_RECENT_SESSIONS = 3

    /** Default number of trailing e1RM points [trendDirection] considers. */
    const val DEFAULT_TREND_POINTS = 3

    /** Below this fractional change across the trend window a lift is [TrendDirection.FLAT]. */
    private const val TREND_FLAT_FRACTION = 0.02

    /**
     * Per-lift estimated-1RM series: for each exercise, group its completed weighted sets by day,
     * keep the best estimated 1RM per day, oldest → newest. Exercises with no qualifying set are
     * omitted. This is the exact derivation [CoachContextAssembler] surfaces as
     * `TrainingContext.e1rmByExercise` — same shape, same values.
     */
    fun e1rmSeriesByExercise(
        sessions: List<Pair<LocalDate, WorkoutSession>>,
    ): Map<String, List<LiftE1rmPoint>> {
        // exerciseName -> (date -> best e1RM that day)
        val byExercise = mutableMapOf<String, MutableMap<LocalDate, Double>>()
        for ((date, session) in sessions) {
            for (exercise in session.exercises) {
                val bestThisSession = exercise.sets
                    .filter { it.completed }
                    .mapNotNull { WorkoutProgressAnalyzer.estimatedOneRepMax(it.reps, it.weightKg) }
                    .maxOrNull() ?: continue
                val dayMap = byExercise.getOrPut(exercise.exerciseName) { mutableMapOf() }
                val existing = dayMap[date]
                if (existing == null || bestThisSession > existing) dayMap[date] = bestThisSession
            }
        }
        return byExercise.mapValues { (_, dayMap) ->
            dayMap.entries.sortedBy { it.key }.map { LiftE1rmPoint(it.key, it.value) }
        }
    }

    /** Newest estimated-1RM value per lift (the last point of each [e1rmSeriesByExercise] series). */
    fun latestE1rmByExercise(
        sessions: List<Pair<LocalDate, WorkoutSession>>,
    ): Map<String, Double> = e1rmSeriesByExercise(sessions)
        .mapValues { (_, series) -> series.last().estimatedOneRepMax }

    /**
     * Reps-in-reserve across the completed sets of the [sessionCount] most-recent sessions, newest
     * session first (order within a session preserved). Null RIR and incomplete sets are skipped.
     */
    fun recentRir(
        sessions: List<Pair<LocalDate, WorkoutSession>>,
        sessionCount: Int = DEFAULT_RECENT_SESSIONS,
    ): List<Int> = sessions
        .sortedByDescending { it.first }
        .take(sessionCount)
        .flatMap { it.second.exercises }
        .flatMap { it.sets }
        .filter { it.completed }
        .mapNotNull { it.rir }

    /**
     * Total training volume (Σ reps × weight over completed weighted sets) across all supplied
     * sessions and lifts. The caller windows the sessions, so "weekly" volume is simply this over a
     * one-week window.
     */
    fun totalTrainingVolume(
        sessions: List<Pair<LocalDate, WorkoutSession>>,
    ): Double = sessions.sumOf { (_, session) ->
        session.exercises.sumOf { WorkoutProgressAnalyzer.sessionVolume(it.sets) }
    }

    /**
     * Direction of a lift's e1RM over its last [lastN] points: [TrendDirection.UP]/[TrendDirection.DOWN]
     * when the newest point differs from the oldest of that window by more than [TREND_FLAT_FRACTION],
     * else [TrendDirection.FLAT]. Null when the lift is unknown or has fewer than two points.
     */
    fun trendDirection(
        sessions: List<Pair<LocalDate, WorkoutSession>>,
        exerciseName: String,
        lastN: Int = DEFAULT_TREND_POINTS,
    ): TrendDirection? {
        val series = e1rmSeriesByExercise(sessions)[exerciseName] ?: return null
        if (series.size < 2) return null
        val window = series.takeLast(lastN)
        val first = window.first().estimatedOneRepMax
        val last = window.last().estimatedOneRepMax
        if (first == 0.0) return TrendDirection.FLAT
        val change = (last - first) / first
        return when {
            change > TREND_FLAT_FRACTION -> TrendDirection.UP
            change < -TREND_FLAT_FRACTION -> TrendDirection.DOWN
            else -> TrendDirection.FLAT
        }
    }
}
