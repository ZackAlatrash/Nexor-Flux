package com.zack.recomptracker.domain.streak

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Pure streak math. Each streak type contributes a set of "qualifying days" (days the goal was
 * met) and a rest-day tolerance; this computes the current and longest streaks.
 *
 * Two qualifying days belong to the same chain when their gap is <= restDays + 1.
 * - Calorie/Steps use restDays = 0 (strictly consecutive).
 * - Workout uses restDays = 2 (up to two rest days between workouts; gap <= 3 continues).
 *
 * current = calendar days spanned by the chain containing the most recent qualifying day,
 *           but only if that chain is still "alive": the most recent qualifying day is within
 *           restDays + 1 days of [today]. Otherwise 0. Interior rest days count; trailing rest
 *           days (after the last qualifying day, up to today) keep it alive but are not counted.
 * longest = the largest spanned chain across all history.
 */
class StreakCalculator {
    fun compute(qualifyingDays: Set<LocalDate>, today: LocalDate, restDays: Int): StreakResult {
        if (qualifyingDays.isEmpty()) return StreakResult.ZERO
        val maxGap = restDays + 1
        // `sorted()` rather than the JVM-only `toSortedSet()`; the input is already a Set.
        val sorted = qualifyingDays.sorted()

        fun span(first: LocalDate, last: LocalDate): Int = first.daysUntil(last) + 1

        var longest = 0
        var chainStart = sorted.first()
        var prev = sorted.first()
        for (i in 1 until sorted.size) {
            val day = sorted[i]
            if (prev.daysUntil(day) > maxGap) {
                longest = maxOf(longest, span(chainStart, prev))
                chainStart = day
            }
            prev = day
        }
        longest = maxOf(longest, span(chainStart, prev))

        // prev is now the most recent qualifying day; chainStart is the start of its chain.
        val daysSinceLast = prev.daysUntil(today)
        val current = if (daysSinceLast in 0..maxGap) span(chainStart, prev) else 0

        return StreakResult(current = current, longest = longest)
    }
}
