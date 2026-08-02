package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.core.model.MacroTotals
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Shared, plain-object fixtures for the detector + engine tests. Every value is a real object (no
 * mocks). Defaults describe a well-fed, well-logged user with >= 14 logged days so the sufficiency
 * gate passes; each detector test overrides only the fields it cares about.
 */
internal object CoachContextFixtures {

    val TODAY: LocalDate = LocalDate(2026, 7, 1) // a Wednesday, ISO week 27

    /** A dense daily weight/measurement series ending [days] days before today. */
    fun linearSeries(
        endValue: Double,
        perDayDelta: Double,
        days: Int = 21,
        end: LocalDate = TODAY,
    ): List<MetricPoint> = (0 until days).map { i ->
        val date = end.minus((days - 1 - i).toLong(), DateTimeUnit.DAY)
        MetricPoint(date = date, value = endValue - perDayDelta * (days - 1 - i))
    }

    fun flatSeries(value: Double, days: Int = 21, end: LocalDate = TODAY): List<MetricPoint> =
        (0 until days).map { i -> MetricPoint(end.minus((days - 1 - i).toLong(), DateTimeUnit.DAY), value) }

    fun recoverySeries(
        value: Double,
        days: Int = 14,
        end: LocalDate = TODAY,
    ): List<MetricPoint> = flatSeries(value, days, end)

    fun eatenByDate(
        loggedDays: Int,
        perDay: MacroTotals,
        end: LocalDate = TODAY,
    ): Map<LocalDate, MacroTotals> =
        (0 until loggedDays).associate { i -> end.minus(i.toLong(), DateTimeUnit.DAY) to perDay }

    fun stepsByDate(
        value: Int,
        days: Int,
        end: LocalDate = TODAY,
    ): Map<LocalDate, Int> = (0 until days).associate { i -> end.minus(i.toLong(), DateTimeUnit.DAY) to value }

    fun context(
        asOf: LocalDate = TODAY,
        plan: PlanContext = plan(),
        profile: ProfileContext = ProfileContext(goal = "recomp", weeklyGymSessions = 4, dailyStepGoal = 8000),
        nutrition: NutritionContext = nutrition(),
        body: BodyContext = body(),
        training: TrainingContext = training(),
        streaks: StreaksContext = streaks(),
        history: HistoryContext = HistoryContext(),
    ) = CoachContext(
        asOf = asOf,
        plan = plan,
        profile = profile,
        nutrition = nutrition,
        body = body,
        training = training,
        streaks = streaks,
        history = history,
    )

    fun plan(
        targetCalories: Int = 2400,
        targetProteinG: Int = 180,
        targetCarbsG: Int = 250,
        targetFatG: Int = 70,
    ) = PlanContext(
        targetCalories = targetCalories,
        targetProteinG = targetProteinG,
        targetCarbsG = targetCarbsG,
        targetFatG = targetFatG,
        zoneLowerBound = targetCalories - 150,
        zoneUpperBound = targetCalories + 150,
    )

    /** Default nutrition: 21 logged days, on-target, high adherence — passes the sufficiency gate. */
    fun nutrition(
        loggedDaysInWindow: Int = 21,
        adherencePercent: Double? = 92.0,
        loggingConsistencyPercent: Double? = 95.0,
        unconfirmedPlannedCount: Int = 0,
        eatenByDate: Map<LocalDate, MacroTotals> = eatenByDate(21, MacroTotals(2400, 180.0, 250.0, 70.0)),
    ) = NutritionContext(
        todayEaten = MacroTotals(2400, 180.0, 250.0, 70.0),
        todayPlanned = MacroTotals(),
        todayMealsLogged = 4,
        eatenByDate = eatenByDate,
        loggedDaysInWindow = loggedDaysInWindow,
        adherencePercent = adherencePercent,
        loggingConsistencyPercent = loggingConsistencyPercent,
        unconfirmedPlannedCount = unconfirmedPlannedCount,
    )

    fun body(
        weightSeries: List<MetricPoint> = flatSeries(80.0),
        waistSeries: List<MetricPoint> = flatSeries(85.0),
        skinfoldSeries: List<MetricPoint> = emptyList(),
        sleepSeries: List<MetricPoint> = recoverySeries(7.5),
        energySeries: List<MetricPoint> = recoverySeries(8.0),
        hungerSeries: List<MetricPoint> = recoverySeries(4.0),
        sorenessSeries: List<MetricPoint> = recoverySeries(3.0),
        stepsByDate: Map<LocalDate, Int> = stepsByDate(9000, 14),
        trainedDates: Set<LocalDate> = emptySet(),
        weightTrendKgPerWeek: Double? = 0.0,
        waistTrendCmPerWeek: Double? = 0.0,
        skinfoldTrendMmPerWeek: Double? = null,
        avgSteps7: Int? = 9000,
        avgStepsPrev7: Int? = 9000,
        daysSinceLastWeighIn: Int? = 0,
    ) = BodyContext(
        weightSeries = weightSeries,
        waistSeries = waistSeries,
        skinfoldSeries = skinfoldSeries,
        sleepSeries = sleepSeries,
        energySeries = energySeries,
        hungerSeries = hungerSeries,
        sorenessSeries = sorenessSeries,
        stepsByDate = stepsByDate,
        trainedDates = trainedDates,
        weightTrendKgPerWeek = weightTrendKgPerWeek,
        waistTrendCmPerWeek = waistTrendCmPerWeek,
        skinfoldTrendMmPerWeek = skinfoldTrendMmPerWeek,
        avgSteps7 = avgSteps7,
        avgStepsPrev7 = avgStepsPrev7,
        daysSinceLastWeighIn = daysSinceLastWeighIn,
    )

    fun training(
        completedSessionDates: List<LocalDate> = (0 until 8).map { TODAY.minus(it * 3L, DateTimeUnit.DAY) },
        weeklyTrainingFrequency: Double = 4.0,
        weeklyGymSessionsTarget: Int? = 4,
        e1rmByExercise: Map<String, List<LiftE1rmPoint>> = emptyMap(),
        recentRir: List<Int> = listOf(3, 3, 2),
    ) = TrainingContext(
        completedSessionDates = completedSessionDates,
        weeklyTrainingFrequency = weeklyTrainingFrequency,
        weeklyGymSessionsTarget = weeklyGymSessionsTarget,
        e1rmByExercise = e1rmByExercise,
        recentRir = recentRir,
    )

    fun streaks(
        workout: StreakSnapshot = StreakSnapshot(6, 12),
        calorie: StreakSnapshot = StreakSnapshot(10, 20),
        steps: StreakSnapshot = StreakSnapshot(9, 15),
    ) = StreaksContext(workout = workout, calorie = calorie, steps = steps)

    /**
     * A per-day [MetricPoint] series from explicit values, oldest → newest, one point per day ending
     * at [end]. Used to pair sleep and hunger by date for the sleep↔hunger link tests.
     */
    fun datedSeries(vararg values: Double, end: LocalDate = TODAY): List<MetricPoint> {
        val n = values.size
        return values.mapIndexed { i, v -> MetricPoint(date = end.minus((n - 1 - i).toLong(), DateTimeUnit.DAY), value = v) }
    }

    fun e1rmPoints(vararg values: Double, end: LocalDate = TODAY, stepDays: Long = 3): List<LiftE1rmPoint> {
        val n = values.size
        return values.mapIndexed { i, v ->
            LiftE1rmPoint(date = end.minus((n - 1 - i) * stepDays, DateTimeUnit.DAY), estimatedOneRepMax = v)
        }
    }
}
