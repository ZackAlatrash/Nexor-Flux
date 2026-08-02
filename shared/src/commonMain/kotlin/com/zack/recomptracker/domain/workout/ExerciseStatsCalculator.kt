package com.zack.recomptracker.domain.workout

import kotlin.math.max
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Derives all detail-screen stats for a single exercise from its flat [ExerciseHistoryPoint] list
 * (completed sets only, as returned by WorkoutSessionRepository.getExerciseHistory). Pure Kotlin.
 */
object ExerciseStatsCalculator {

    /** A dated scalar for a chart series (e.g. top-set weight or est. 1RM on a day). */
    data class DayValue(val date: String, val value: Double)

    /** One performed day: the sets done and the day's total volume. */
    data class DaySession(
        val date: String,
        val sets: List<ExerciseHistoryPoint>,
        val volume: Double,
    )

    data class ExerciseStats(
        val hasData: Boolean,
        val bestOneRepMax: Double?,
        val heaviestWeightKg: Double?,
        val heaviestReps: Int?,
        val maxReps: Int?,
        val bestDayVolume: Double?,
        val sessionsPerWeek: Double?,
        val lastPerformedDate: String?,
        /** Per-day est. 1RM (days with a weighted set), ascending. Chart default series. */
        val oneRepMaxSeries: List<DayValue>,
        /** Per-day max weight (top set), ascending. */
        val topSetSeries: List<DayValue>,
        /** Per-day total volume, ascending. */
        val volumeSeries: List<DayValue>,
        /** Performed days, newest first. */
        val recentSessions: List<DaySession>,
    )

    fun calculate(history: List<ExerciseHistoryPoint>): ExerciseStats {
        if (history.isEmpty()) {
            return ExerciseStats(
                hasData = false, bestOneRepMax = null, heaviestWeightKg = null, heaviestReps = null,
                maxReps = null, bestDayVolume = null, sessionsPerWeek = null, lastPerformedDate = null,
                oneRepMaxSeries = emptyList(), topSetSeries = emptyList(), volumeSeries = emptyList(),
                recentSessions = emptyList(),
            )
        }

        val trend = WorkoutProgressAnalyzer.trendPoints(history) // ascending by date

        val oneRepMaxSeries = trend.mapNotNull { tp ->
            tp.bestEstimatedOneRepMax?.let { DayValue(tp.date, it) }
        }
        val volumeSeries = trend.map { DayValue(it.date, it.totalVolume) }
        // `.entries.sortedBy { it.key }` replaces JVM-only `toSortedMap()`.
        val topSetSeries = history.groupBy { it.date }.entries.sortedBy { it.key }.mapNotNull { (date, pts) ->
            pts.mapNotNull { it.weightKg }.maxOrNull()?.let { DayValue(date, it) }
        }

        val heaviest = history.filter { it.weightKg != null }.maxByOrNull { it.weightKg!! }
        // `.distinct().sorted()` replaces JVM-only `toSortedSet()`.
        val dates = history.map { it.date }.distinct().sorted()

        val recentSessions = history.groupBy { it.date }.map { (date, pts) ->
            DaySession(
                date = date,
                sets = pts,
                volume = pts.sumOf { WorkoutProgressAnalyzer.setVolume(it.reps, it.weightKg) },
            )
        }.sortedByDescending { it.date }

        return ExerciseStats(
            hasData = true,
            bestOneRepMax = oneRepMaxSeries.maxByOrNull { it.value }?.value,
            heaviestWeightKg = heaviest?.weightKg,
            heaviestReps = heaviest?.reps,
            maxReps = history.maxOf { it.reps },
            bestDayVolume = volumeSeries.maxByOrNull { it.value }?.value,
            sessionsPerWeek = sessionsPerWeek(dates),
            lastPerformedDate = dates.lastOrNull(),
            oneRepMaxSeries = oneRepMaxSeries,
            topSetSeries = topSetSeries,
            volumeSeries = volumeSeries,
            recentSessions = recentSessions,
        )
    }

    /** Distinct training days divided by the span in weeks (min 1 week). Null if no parseable dates. */
    private fun sessionsPerWeek(dates: Collection<String>): Double? {
        if (dates.isEmpty()) return null
        val parsed = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        if (parsed.isEmpty()) return null
        val spanDays = parsed.first().daysUntil(parsed.last()).toDouble()
        val weeks = max(1.0, spanDays / 7.0)
        return parsed.size / weeks
    }
}
