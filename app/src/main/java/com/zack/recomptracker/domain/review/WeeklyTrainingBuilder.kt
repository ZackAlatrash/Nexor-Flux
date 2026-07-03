package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.coach.TrainingDerivations
import com.zack.recomptracker.domain.coach.TrendDirection
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate

/**
 * Pure builder for the weekly briefing's SUPPORTING training domain (Phase 2B). Reuses the existing
 * [TrainingDerivations] e1RM/trend math — it invents no new training figures — and folds a couple of
 * already-computed numbers into a deterministic, number-safe one-liner the briefing can quote.
 *
 * No Android, no coroutines, no Room: the caller windows/parses dated sessions once. Returns null
 * when no completed session lands in the window (the briefing then omits the training line entirely).
 */
object WeeklyTrainingBuilder {

    /**
     * @param datedSessions completed sessions as (date, session) pairs, any order — the caller has
     *   already parsed each session's date and filtered to the review window.
     * @param today anchors the trailing-7-day "this week" session count.
     */
    fun build(
        datedSessions: List<Pair<LocalDate, WorkoutSession>>,
        today: LocalDate,
    ): WeeklyTraining? {
        if (datedSessions.isEmpty()) return null

        val weekStart = today.minusDays(6)
        val sessionsThisWeek = datedSessions
            .map { it.first }
            .filter { !it.isBefore(weekStart) && !it.isAfter(today) }
            .distinct()
            .size

        // Top lift = highest latest estimated 1RM across the window (from the shared derivation).
        val topLiftEntry = TrainingDerivations.latestE1rmByExercise(datedSessions)
            .maxByOrNull { it.value }
        val topLift = topLiftEntry?.key
        val trend = topLift
            ?.let { TrainingDerivations.trendDirection(datedSessions, it) }
            .toSignalDirection()

        val summary = buildSummary(sessionsThisWeek, topLift, trend)
        return WeeklyTraining(
            sessionsThisWeek = sessionsThisWeek,
            topLift = topLift,
            topLiftTrend = trend,
            summary = summary,
        )
    }

    private fun TrendDirection?.toSignalDirection(): SignalDirection = when (this) {
        TrendDirection.UP -> SignalDirection.UP
        TrendDirection.DOWN -> SignalDirection.DOWN
        null, TrendDirection.FLAT -> SignalDirection.FLAT
    }

    private fun buildSummary(sessions: Int, topLift: String?, trend: SignalDirection): String {
        val sessionWord = if (sessions == 1) "session" else "sessions"
        val head = "$sessions training $sessionWord this week"
        if (topLift == null) return "$head (no weighted lifts logged)."
        val trendWord = when (trend) {
            SignalDirection.UP -> "trending up"
            SignalDirection.DOWN -> "trending down"
            SignalDirection.FLAT -> "holding flat"
        }
        return "$head; top lift $topLift is $trendWord."
    }
}
