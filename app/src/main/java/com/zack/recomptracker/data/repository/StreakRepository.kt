package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.domain.streak.StreakCalculator
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.domain.streak.Streaks
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Derives Workout / Calorie / Steps streaks from existing history on every emission.
 * No streak state is persisted — current/longest are always recomputed.
 */
class StreakRepository(
    private val logRepository: LogRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
    private val calculator: StreakCalculator,
) {
    fun streaks(): Flow<Streaks> = combine(
        logRepository.observeDailyLogs(),
        logRepository.observeMealEntries(),
        workoutSessionRepository.observeCompletedSessions(),
        planRepository.preferences,
        userProfileStore.preferences,
    ) { dailyLogs, meals, sessions, prefs, profile ->
        val eatenByDate = meals
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
        buildStreaks(
            dailyLogs = dailyLogs,
            eatenCaloriesByDate = eatenByDate,
            completedSessionDates = sessions.map { LocalDate.parse(it.date) },
            prefs = prefs,
            dailyStepGoal = profile.dailyStepGoal,
            today = dateProvider.today(),
            calculator = calculator,
        )
    }
}

/**
 * Pure assembly of the three streaks — unit-tested directly. Maps raw history into
 * qualifying-day sets, runs [StreakCalculator], and attaches a 7-day strip for the UI.
 *
 * Calorie success = eaten calories within the calorie zone (the dashboard's "in zone" rule).
 * Steps success   = steps >= dailyStepGoal (no streak when the goal is unset).
 * Workout success = a completed session OR a daily log with trained = true.
 */
internal fun buildStreaks(
    dailyLogs: List<DailyLogEntity>,
    eatenCaloriesByDate: Map<LocalDate, Int>,
    completedSessionDates: List<LocalDate>,
    prefs: PlanPreferences,
    dailyStepGoal: Int?,
    today: LocalDate,
    calculator: StreakCalculator,
): Streaks {
    val workoutDays: Set<LocalDate> = (
        completedSessionDates +
            dailyLogs.filter { it.trained }.map { LocalDate.parse(it.date) }
        ).toSet()

    val calorieDays: Set<LocalDate> = eatenCaloriesByDate
        .filterValues { cals ->
            cals > 0 &&
                prefs.calorieZoneLowerBound > 0 &&
                cals >= prefs.calorieZoneLowerBound &&
                cals <= prefs.calorieZoneUpperBound
        }
        .keys

    val stepDays: Set<LocalDate> = if (dailyStepGoal != null && dailyStepGoal > 0) {
        dailyLogs
            .filter { (it.steps ?: 0) >= dailyStepGoal }
            .map { LocalDate.parse(it.date) }
            .toSet()
    } else {
        emptySet()
    }

    fun result(days: Set<LocalDate>, restDays: Int): StreakResult =
        calculator.compute(days, today, restDays).copy(last7 = recentFlags(days, today))

    return Streaks(
        workout = result(workoutDays, restDays = 2),
        calorie = result(calorieDays, restDays = 0),
        steps = result(stepDays, restDays = 0),
    )
}

/** Met/missed flags for the last 7 calendar days ending at [today], oldest -> newest. */
private fun recentFlags(days: Set<LocalDate>, today: LocalDate, window: Int = 7): List<Boolean> =
    (window - 1 downTo 0).map { offset -> today.minusDays(offset.toLong()) in days }
