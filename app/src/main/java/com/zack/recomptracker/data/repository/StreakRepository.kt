package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.domain.activity.ActivityMetrics
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.streak.StreakCalculator
import com.zack.recomptracker.domain.streak.StreakDayMark
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
    // Rebalance state feeds the calorie-streak union zone (a temporary rebalance can only widen the
    // in-zone band, never break a streak the user would otherwise keep). A live Flow, mirroring how
    // this class already consumes planRepository.observeVersions(); an empty state is behaviour-neutral.
    private val rebalanceStore: RebalanceStore,
) {
    fun streaks(): Flow<Streaks> = combine(
        logRepository.observeDailyLogs(),
        logRepository.observeMealEntries(),
        workoutSessionRepository.observeCompletedSessions(),
        planRepository.observeVersions(),
        userProfileStore.preferences,
        rebalanceStore.state,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val dailyLogs = values[0] as List<DailyLogEntity>
        @Suppress("UNCHECKED_CAST")
        val meals = values[1] as List<com.zack.recomptracker.data.local.entity.MealEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val sessions = values[2] as List<com.zack.recomptracker.domain.workout.WorkoutSession>
        @Suppress("UNCHECKED_CAST")
        val versions = values[3] as List<PlanVersion>
        val profile = values[4] as com.zack.recomptracker.data.preferences.UserProfilePreferences
        val rebalanceState = values[5] as RebalanceState
        val eatenByDate = meals
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
        buildStreaks(
            dailyLogs = dailyLogs,
            eatenCaloriesByDate = eatenByDate,
            completedSessionDates = sessions.map { LocalDate.parse(it.date) },
            versions = versions,
            dailyStepGoal = profile.dailyStepGoal,
            today = dateProvider.today(),
            calculator = calculator,
            rebalanceState = rebalanceState,
        )
    }

    /**
     * Derived activity figures (training frequency vs target, 7-day average steps) for the
     * Dashboard/Train activity surfaces. Shares the [ActivitySummary] derivations with the
     * streak build so the numbers never disagree.
     */
    fun activity(): Flow<ActivityMetrics> = combine(
        logRepository.observeDailyLogs(),
        workoutSessionRepository.observeCompletedSessions(),
        userProfileStore.preferences,
    ) { dailyLogs, sessions, profile ->
        val today = dateProvider.today()
        val workoutDays = ActivitySummary.workoutDays(
            completedSessionDates = sessions.map { LocalDate.parse(it.date) },
            trainedLogDates = dailyLogs.filter { it.trained }.map { LocalDate.parse(it.date) },
        )
        val stepsByDate = dailyLogs.mapNotNull { log ->
            log.steps?.let { LocalDate.parse(log.date) to it }
        }.toMap()
        ActivityMetrics(
            weeklyTrainingFrequency = ActivitySummary.weeklyTrainingFrequency(workoutDays, today),
            weeklyGymSessionsTarget = profile.weeklyGymSessions,
            averageDailySteps7 = ActivitySummary.averageDailySteps(stepsByDate, today),
        )
    }
}

/**
 * Pure assembly of the three streaks — unit-tested directly. Maps raw history into
 * qualifying-day sets, runs [StreakCalculator], and attaches a 7-day strip for the UI.
 *
 * Calorie success = eaten calories within the calorie zone (the dashboard's "in zone" rule),
 *                   judged against the lenient union of the base and rebalance-effective zones so a
 *                   temporary rebalance can only widen the band, never break a would-be streak.
 * Steps success   = steps >= dailyStepGoal (no streak when the goal is unset).
 * Workout success = a completed session OR a daily log with trained = true.
 */
internal fun buildStreaks(
    dailyLogs: List<DailyLogEntity>,
    eatenCaloriesByDate: Map<LocalDate, Int>,
    completedSessionDates: List<LocalDate>,
    versions: List<PlanVersion>,
    dailyStepGoal: Int?,
    today: LocalDate,
    calculator: StreakCalculator,
    rebalanceState: RebalanceState = RebalanceState(),
): Streaks {
    val workoutDays: Set<LocalDate> = ActivitySummary.workoutDays(
        completedSessionDates = completedSessionDates,
        trainedLogDates = dailyLogs.filter { it.trained }.map { LocalDate.parse(it.date) },
    )

    val calorieDays: Set<LocalDate> = if (versions.isEmpty()) {
        emptySet()
    } else {
        eatenCaloriesByDate
            .filter { (date, cals) ->
                val base = PlanHistory.planOn(versions, date)
                // Union of base + effective zones: on a rebalance day this widens the band; on any
                // other day it is exactly the base zone (behaviour-neutral with an empty state).
                val zone = EffectiveTargets.unionZone(base, date, rebalanceState)
                cals > 0 && base.zoneLowerBound > 0 && cals in zone
            }
            .keys
    }

    // Steps streak judges against the base dailyStepGoal ALWAYS — a temporary rebalance step boost is
    // display/progress only and must never break (or manufacture) a streak. See EffectiveTargets.effectiveStepGoal.
    val stepDays: Set<LocalDate> = if (dailyStepGoal != null && dailyStepGoal > 0) {
        dailyLogs
            .filter { (it.steps ?: 0) >= dailyStepGoal }
            .map { LocalDate.parse(it.date) }
            .toSet()
    } else {
        emptySet()
    }

    fun result(days: Set<LocalDate>, restDays: Int): StreakResult =
        calculator.compute(days, today, restDays)
            .copy(
                last7 = recentFlags(days, today),
                last7Marks = recentMarks(days, today, restDays),
            )

    return Streaks(
        workout = result(workoutDays, restDays = 2),
        calorie = result(calorieDays, restDays = 0),
        steps = result(stepDays, restDays = 0),
    )
}

/** Met/missed flags for the last 7 calendar days ending at [today], oldest -> newest. */
private fun recentFlags(days: Set<LocalDate>, today: LocalDate, window: Int = 7): List<Boolean> =
    (window - 1 downTo 0).map { offset -> today.minusDays(offset.toLong()) in days }

/** HIT/REST/MISS for the last 7 calendar days ending at [today], oldest -> newest. A non-hit
 *  day is REST only when the streak tolerates rest days (restDays>0) and it is bridged by a
 *  qualifying day within [restDays] on BOTH sides (an interior rest gap); otherwise MISS. */
private fun recentMarks(days: Set<LocalDate>, today: LocalDate, restDays: Int, window: Int = 7): List<StreakDayMark> =
    (window - 1 downTo 0).map { offset ->
        val d = today.minusDays(offset.toLong())
        when {
            d in days -> StreakDayMark.HIT
            restDays > 0 &&
                (1..restDays).any { d.minusDays(it.toLong()) in days } &&
                (1..restDays).any { d.plusDays(it.toLong()) in days } -> StreakDayMark.REST
            else -> StreakDayMark.MISS
        }
    }
