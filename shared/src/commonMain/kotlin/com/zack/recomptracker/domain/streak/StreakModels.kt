package com.zack.recomptracker.domain.streak

enum class StreakType { WORKOUT, CALORIE, STEPS }

/** Per-day status for the last-7 strip. REST only occurs when a streak tolerates rest days. */
enum class StreakDayMark { HIT, REST, MISS }

/**
 * Result of a streak computation.
 *
 * @property current calendar days spanned by the live chain (0 if the streak is broken)
 * @property longest largest spanned chain across all history (always >= current)
 * @property last7   qualified/missed flags for the last 7 calendar days, oldest -> newest.
 *                   Left empty by [StreakCalculator]; populated by the repository for the UI.
 */
data class StreakResult(
    val current: Int,
    val longest: Int,
    val last7: List<Boolean> = emptyList(),
    val last7Marks: List<StreakDayMark> = emptyList(),
) {
    companion object {
        val ZERO = StreakResult(current = 0, longest = 0)
    }
}

data class Streaks(
    val workout: StreakResult,
    val calorie: StreakResult,
    val steps: StreakResult,
) {
    fun entries(): List<Pair<StreakType, StreakResult>> = listOf(
        StreakType.WORKOUT to workout,
        StreakType.CALORIE to calorie,
        StreakType.STEPS to steps,
    )

    companion object {
        val EMPTY = Streaks(StreakResult.ZERO, StreakResult.ZERO, StreakResult.ZERO)
    }
}
