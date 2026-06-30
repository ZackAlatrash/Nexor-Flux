package com.zack.recomptracker.domain.activity

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Pure activity derivations shared by the Dashboard, Profile, Train, and the AI snapshot so the
 * numbers never disagree between screens. No Android or data-layer dependencies.
 */
object ActivitySummary {

    /**
     * The set of days the user trained: the union of completed workout-session dates and daily
     * logs flagged `trained`. This is the single definition of a "training day" — [buildStreaks]
     * and the frequency derivation both consume it.
     */
    fun workoutDays(
        completedSessionDates: Collection<LocalDate>,
        trainedLogDates: Collection<LocalDate>,
    ): Set<LocalDate> = (completedSessionDates + trainedLogDates).toSet()

    /**
     * Average training sessions per week over the trailing [weeks] weeks ending at [today]
     * (inclusive): distinct training days in the window divided by [weeks].
     */
    fun weeklyTrainingFrequency(
        workoutDays: Set<LocalDate>,
        today: LocalDate,
        weeks: Int = 4,
    ): Double {
        require(weeks > 0) { "weeks must be positive" }
        val start = today.minusDays(weeks * 7L - 1)
        val count = workoutDays.count { !it.isBefore(start) && !it.isAfter(today) }
        return count.toDouble() / weeks
    }

    /**
     * Mean of logged step counts over the trailing [days] days ending at [today] (inclusive),
     * rounded to a whole number. Null when no steps were logged in the window.
     */
    fun averageDailySteps(
        stepsByDate: Map<LocalDate, Int>,
        today: LocalDate,
        days: Int = 7,
    ): Int? {
        require(days > 0) { "days must be positive" }
        val start = today.minusDays(days - 1L)
        val values = stepsByDate.filterKeys { !it.isBefore(start) && !it.isAfter(today) }.values
        return if (values.isEmpty()) null else values.average().roundToInt()
    }
}
