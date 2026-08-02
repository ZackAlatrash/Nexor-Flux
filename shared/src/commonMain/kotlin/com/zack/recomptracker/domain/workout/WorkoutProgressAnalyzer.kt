package com.zack.recomptracker.domain.workout

object WorkoutProgressAnalyzer {

    fun setVolume(reps: Int, weightKg: Double?): Double = reps * (weightKg ?: 0.0)

    /** Epley formula. Null for bodyweight (no weight) or non-positive reps. */
    fun estimatedOneRepMax(reps: Int, weightKg: Double?): Double? {
        if (weightKg == null || reps <= 0) return null
        return weightKg * (1.0 + reps / 30.0)
    }

    fun sessionVolume(sets: List<SessionSet>): Double =
        sets.filter { it.completed }.sumOf { setVolume(it.reps, it.weightKg) }

    /** Completed, weighted set with the highest estimated 1RM. */
    fun bestSet(sets: List<SessionSet>): SessionSet? =
        sets.filter { it.completed }
            .mapNotNull { s -> estimatedOneRepMax(s.reps, s.weightKg)?.let { s to it } }
            .maxByOrNull { it.second }
            ?.first

    /** One [ExerciseTrendPoint] per date, ascending. */
    fun trendPoints(history: List<ExerciseHistoryPoint>): List<ExerciseTrendPoint> =
        history.groupBy { it.date }
            // `.entries.sortedBy { it.key }` replaces JVM-only `toSortedMap()`.
            .entries.sortedBy { it.key }
            .map { (date, points) ->
                ExerciseTrendPoint(
                    date = date,
                    totalVolume = points.sumOf { setVolume(it.reps, it.weightKg) },
                    bestEstimatedOneRepMax = points.mapNotNull { estimatedOneRepMax(it.reps, it.weightKg) }.maxOrNull(),
                )
            }
}
