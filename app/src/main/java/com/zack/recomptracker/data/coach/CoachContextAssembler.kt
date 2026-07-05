package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.ageYears
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.coach.BodyContext
import com.zack.recomptracker.domain.coach.CoachContext
import com.zack.recomptracker.domain.coach.HistoryContext
import com.zack.recomptracker.domain.coach.LiftE1rmPoint
import com.zack.recomptracker.domain.coach.MetricPoint
import com.zack.recomptracker.domain.coach.NutritionContext
import com.zack.recomptracker.domain.coach.PlanContext
import com.zack.recomptracker.domain.coach.ProfileContext
import com.zack.recomptracker.domain.coach.RebalanceContext
import com.zack.recomptracker.domain.coach.StreakSnapshot
import com.zack.recomptracker.domain.coach.StreaksContext
import com.zack.recomptracker.domain.coach.TrainingContext
import com.zack.recomptracker.domain.coach.TrainingDerivations
import com.zack.recomptracker.domain.coach.WeeklyReviewSnapshot
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.streak.Streaks
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure, already-fetched inputs for [CoachContextAssembler.assemble]. The thin
 * [CoachContextBuilder] is responsible for reading each of these from its repositories (as
 * one-shots); this record carries plain value objects so the whole derivation is unit-testable
 * with hand-built fixtures — no mocks, no coroutines, no Android.
 */
data class CoachContextInputs(
    val today: LocalDate,
    val plan: PlanPreferences,
    val profile: UserProfilePreferences,
    /** All daily logs (any date). Windowed internally. */
    val dailyLogs: List<DailyLogEntity>,
    /** Eaten meal entries (planned == false) over the window. */
    val eatenMeals: List<MealEntryEntity>,
    /** Planned meal entries (planned == true), used for the unconfirmed-plan count. */
    val plannedMeals: List<MealEntryEntity>,
    /** Completed workout sessions (already mapped to the domain model). */
    val completedSessions: List<WorkoutSession>,
    val streaks: Streaks,
    val weeklyReviews: List<WeeklyReviewInput>,
    /** Per-day plan targets over the window — from PlanRepository.targetsByDate. */
    val targetsByDate: Map<LocalDate, PlanTargets>,
    /** Persisted weekly-rebalance state — resolves the effective per-day targets + the rebalance block. */
    val rebalanceState: RebalanceState = RebalanceState(),
)

/**
 * Plain input row for a weekly review — the assembler stays free of Room entities. The builder
 * maps `WeeklyReviewEntity` into this before calling [CoachContextAssembler.assemble].
 */
data class WeeklyReviewInput(
    val weekStart: LocalDate,
    val verdict: String,
    val signature: String,
)

/**
 * Pure derivation: maps already-fetched Room entities / DataStore prefs / domain objects into the
 * canonical [CoachContext] snapshot (docs/ai-redesign/08-technical-architecture.md §6). ALL mapping
 * and math lives here (delegating to the shared calculators — [TrendCalculator], [ActivitySummary],
 * [AdherenceCalculator], [WorkoutProgressAnalyzer]) so `domain/coach` stays a pure model and every
 * detector downstream can be unit-tested with plain objects.
 *
 * Window: [WINDOW_DAYS] days ending at `today` (inclusive) for the metric series and per-day
 * nutrition — a reasonable default balancing trend fidelity against snapshot size (§7 suggests
 * ~28–56d; 28 is the light end and matches the nutrition window this app already uses elsewhere).
 */
object CoachContextAssembler {

    /** Trailing window (inclusive of `today`) for series + per-day nutrition. */
    const val WINDOW_DAYS = 28

    private val trendCalculator = TrendCalculator()
    private val adherenceCalculator = AdherenceCalculator()

    fun assemble(inputs: CoachContextInputs): CoachContext {
        val today = inputs.today
        val windowStart = today.minusDays((WINDOW_DAYS - 1).toLong())

        val logsInWindow = inputs.dailyLogs
            .mapNotNull { log -> parseDate(log.date)?.let { it to log } }
            .filter { (date, _) -> inWindow(date, windowStart, today) }
            .sortedBy { it.first }

        // Per-day adherence context is graded against the EFFECTIVE (rebalance-reduced) targets, so it
        // reflects the agreed plan. Behaviour-neutral with an empty state (resolve returns base).
        val effectiveTargets = EffectiveTargets.resolveAll(inputs.targetsByDate, inputs.rebalanceState)

        return CoachContext(
            asOf = today,
            plan = buildPlan(inputs, today),
            profile = buildProfile(inputs, today),
            nutrition = buildNutrition(inputs, today, windowStart, effectiveTargets),
            body = buildBody(logsInWindow, today),
            training = buildTraining(inputs, windowStart, today),
            streaks = buildStreaks(inputs.streaks),
            history = buildHistory(inputs.weeklyReviews),
            rebalance = buildRebalance(inputs, today, effectiveTargets),
        )
    }

    // ── Plan ─────────────────────────────────────────────────────────────────────
    private fun buildPlan(inputs: CoachContextInputs, today: LocalDate): PlanContext {
        val plan = inputs.plan
        val phaseStart = plan.maintenancePhaseStartDate?.let { parseDate(it) }
        val weeksSincePhase = phaseStart?.let {
            (ChronoUnit.DAYS.between(it, today) / 7L).toInt().coerceAtLeast(0)
        }
        return PlanContext(
            targetCalories = plan.targetCalories,
            targetProteinG = plan.targetProteinG,
            targetCarbsG = plan.targetCarbsG,
            targetFatG = plan.targetFatG,
            zoneLowerBound = plan.calorieZoneLowerBound,
            zoneUpperBound = plan.calorieZoneUpperBound,
            maintenancePhaseStartDate = phaseStart,
            weeksSincePhaseStart = weeksSincePhase,
        )
    }

    // ── Profile ──────────────────────────────────────────────────────────────────
    private fun buildProfile(inputs: CoachContextInputs, today: LocalDate): ProfileContext {
        val p = inputs.profile
        return ProfileContext(
            goal = p.goal?.name,
            sex = p.biologicalSex?.name,
            ageYears = p.ageYears(today),
            heightCm = p.heightCm,
            activityLevel = p.activityLevel?.name,
            weeklyGymSessions = p.weeklyGymSessions,
            dailyStepGoal = p.dailyStepGoal,
        )
    }

    // ── Nutrition ────────────────────────────────────────────────────────────────
    private fun buildNutrition(
        inputs: CoachContextInputs,
        today: LocalDate,
        windowStart: LocalDate,
        effectiveTargets: Map<LocalDate, PlanTargets>,
    ): NutritionContext {
        val eatenInWindow = inputs.eatenMeals
            .mapNotNull { meal -> parseDate(meal.date)?.let { it to meal } }
            .filter { (date, _) -> inWindow(date, windowStart, today) }

        val eatenByDate: Map<LocalDate, MacroTotals> = eatenInWindow
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.map { it.second }.macroTotals() }

        val todayEatenMeals = inputs.eatenMeals.filter { parseDate(it.date) == today }
        val todayPlannedMeals = inputs.plannedMeals.filter { parseDate(it.date) == today }

        val loggedDays = eatenByDate.keys.size

        val nutritionDays = eatenByDate.mapNotNull { (date, totals) ->
            val target = effectiveTargets[date]?.calories ?: return@mapNotNull null
            NutritionDay(date = date, calories = totals.calories, targetCalories = target)
        }
        val adherence = if (nutritionDays.isEmpty()) null else adherenceCalculator.calculate(nutritionDays)
        val consistency = adherenceCalculator.loggingConsistency(nutritionDays, WINDOW_DAYS)

        // Unconfirmed = planned entries dated today or earlier that were never confirmed eaten.
        val unconfirmed = inputs.plannedMeals.count { meal ->
            parseDate(meal.date)?.let { !it.isAfter(today) } ?: false
        }

        return NutritionContext(
            todayEaten = todayEatenMeals.macroTotals(),
            todayPlanned = todayPlannedMeals.macroTotals(),
            todayMealsLogged = todayEatenMeals.size,
            eatenByDate = eatenByDate,
            loggedDaysInWindow = loggedDays,
            adherencePercent = adherence,
            loggingConsistencyPercent = consistency,
            unconfirmedPlannedCount = unconfirmed,
        )
    }

    // ── Body / recovery / activity ─────────────────────────────────────────────────
    private fun buildBody(
        logsInWindow: List<Pair<LocalDate, DailyLogEntity>>,
        today: LocalDate,
    ): BodyContext {
        val weightSeries = doubleSeries(logsInWindow) { it.bodyWeightKg }
        val waistSeries = doubleSeries(logsInWindow) { it.waistCm }
        val skinfoldSeries = doubleSeries(logsInWindow) { it.waistSkinfoldMm }
        val sleepSeries = doubleSeries(logsInWindow) { it.sleepHours }
        val energySeries = doubleSeries(logsInWindow) { it.energyScore?.toDouble() }
        val hungerSeries = doubleSeries(logsInWindow) { it.hungerScore?.toDouble() }
        val sorenessSeries = doubleSeries(logsInWindow) { it.sorenessScore?.toDouble() }

        val stepsByDate: Map<LocalDate, Int> = logsInWindow
            .mapNotNull { (date, log) -> log.steps?.let { date to it } }
            .toMap()

        val trainedDates: Set<LocalDate> = logsInWindow
            .filter { it.second.trained }
            .map { it.first }
            .toSet()

        val avgSteps7 = ActivitySummary.averageDailySteps(stepsByDate, today, days = 7)
        val prevWindowToday = today.minusDays(7L)
        val avgStepsPrev7 = ActivitySummary.averageDailySteps(stepsByDate, prevWindowToday, days = 7)

        val lastWeighIn = weightSeries.maxByOrNull { it.date }?.date
        val daysSinceLastWeighIn = lastWeighIn?.let {
            ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0)
        }

        return BodyContext(
            weightSeries = weightSeries,
            waistSeries = waistSeries,
            skinfoldSeries = skinfoldSeries,
            sleepSeries = sleepSeries,
            energySeries = energySeries,
            hungerSeries = hungerSeries,
            sorenessSeries = sorenessSeries,
            stepsByDate = stepsByDate,
            trainedDates = trainedDates,
            weightTrendKgPerWeek = trendOrNull(weightSeries),
            waistTrendCmPerWeek = trendOrNull(waistSeries),
            skinfoldTrendMmPerWeek = trendOrNull(skinfoldSeries),
            avgSteps7 = avgSteps7,
            avgStepsPrev7 = avgStepsPrev7,
            daysSinceLastWeighIn = daysSinceLastWeighIn,
        )
    }

    // ── Training ───────────────────────────────────────────────────────────────────
    private fun buildTraining(
        inputs: CoachContextInputs,
        windowStart: LocalDate,
        today: LocalDate,
    ): TrainingContext {
        val sessionsInWindow = inputs.completedSessions
            .mapNotNull { session -> parseDate(session.date)?.let { it to session } }
            .filter { (date, _) -> inWindow(date, windowStart, today) }
            .sortedBy { it.first }

        val sessionDates = sessionsInWindow.map { it.first }

        // Training frequency uses the full trained-day union (sessions + trained logs) over the
        // trailing 4 weeks, matching ActivitySummary's definition used elsewhere.
        val trainedLogDates = inputs.dailyLogs
            .filter { it.trained }
            .mapNotNull { parseDate(it.date) }
        val workoutDays = ActivitySummary.workoutDays(
            completedSessionDates = inputs.completedSessions.mapNotNull { parseDate(it.date) },
            trainedLogDates = trainedLogDates,
        )
        val weeklyFrequency = ActivitySummary.weeklyTrainingFrequency(workoutDays, today)

        // e1RM series + recent RIR come from the shared pure derivation so the coach tool and this
        // assembler never duplicate the math (docs/ai-redesign/08-technical-architecture.md §6).
        val e1rmByExercise: Map<String, List<LiftE1rmPoint>> =
            TrainingDerivations.e1rmSeriesByExercise(sessionsInWindow)
        val recentRir: List<Int> =
            TrainingDerivations.recentRir(sessionsInWindow, RECENT_SESSIONS_FOR_RIR)

        return TrainingContext(
            completedSessionDates = sessionDates,
            weeklyTrainingFrequency = weeklyFrequency,
            weeklyGymSessionsTarget = inputs.profile.weeklyGymSessions,
            e1rmByExercise = e1rmByExercise,
            recentRir = recentRir,
        )
    }

    // ── Streaks ────────────────────────────────────────────────────────────────────
    private fun buildStreaks(streaks: Streaks): StreaksContext = StreaksContext(
        workout = StreakSnapshot(streaks.workout.current, streaks.workout.longest),
        calorie = StreakSnapshot(streaks.calorie.current, streaks.calorie.longest),
        steps = StreakSnapshot(streaks.steps.current, streaks.steps.longest),
    )

    // ── History ──────────────────────────────────────────────────────────────────
    private fun buildHistory(reviews: List<WeeklyReviewInput>): HistoryContext = HistoryContext(
        weeklyReviews = reviews
            .sortedBy { it.weekStart }
            .map { WeeklyReviewSnapshot(it.weekStart, it.verdict, it.signature) },
    )

    // ── Weekly rebalance ─────────────────────────────────────────────────────────
    /**
     * The active-rebalance block, present only when today falls inside the current plan's window.
     * Uses the pure resolver so day-X/of-Y and the effective calories agree with what every other
     * consumer shows. Null (absent) when no plan covers today — the coach then has nothing to mention.
     *
     * Effective calories come from [EffectiveTargets] — the same resolver every other consumer uses —
     * rather than a local `baseCalories - reduction` recomputation, so the formula lives in one place.
     * [effectiveTargets] (already resolved in [assemble] via [EffectiveTargets.resolveAll]) supplies
     * today's value directly; the [EffectiveTargets.resolve] call is a defensive fallback for the
     * (never-hit in practice) case where today's date is missing from [CoachContextInputs.targetsByDate].
     */
    private fun buildRebalance(
        inputs: CoachContextInputs,
        today: LocalDate,
        effectiveTargets: Map<LocalDate, PlanTargets>,
    ): RebalanceContext? {
        val state = inputs.rebalanceState
        val info = EffectiveTargets.planDayInfo(today, state) ?: return null
        val plan = info.plan
        val baseToday = inputs.targetsByDate[today]
        val effectiveCalories = effectiveTargets[today]?.calories
            ?: baseToday?.let { EffectiveTargets.resolve(it, today, state).calories }
            ?: (plan.baseCalories - plan.dailyCalorieReduction)
                .coerceAtLeast(com.zack.recomptracker.domain.rebalance.RebalanceDefaults.MIN_EFFECTIVE_CAL)
        return RebalanceContext(
            dayX = info.dayX,
            ofY = info.ofY,
            effectiveCalories = effectiveCalories,
            extraSteps = plan.extraDailySteps,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private const val RECENT_SESSIONS_FOR_RIR = 3

    /** Build a dated series of the non-null values produced by [selector], oldest → newest. */
    private inline fun doubleSeries(
        logsInWindow: List<Pair<LocalDate, DailyLogEntity>>,
        selector: (DailyLogEntity) -> Double?,
    ): List<MetricPoint> = logsInWindow
        .mapNotNull { (date, log) -> selector(log)?.let { MetricPoint(date, it) } }
        .sortedBy { it.date }

    /** Linear trend per week via [TrendCalculator]; null when fewer than 2 points. */
    private fun trendOrNull(series: List<MetricPoint>): Double? {
        if (series.size < 2) return null
        return trendCalculator.trendPerWeek(series.map { MeasurementPoint(it.date, it.value) })
    }

    private fun inWindow(date: LocalDate, start: LocalDate, end: LocalDate): Boolean =
        !date.isBefore(start) && !date.isAfter(end)

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()
}
