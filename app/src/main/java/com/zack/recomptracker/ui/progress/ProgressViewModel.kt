package com.zack.recomptracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChartSeries(
    val title: String,
    val unit: String,
    val values: List<Float>,
    val currentValue: Float? = null,
    val trendLabel: String = "",
    val trendIsGood: Boolean = true,
)

/**
 * A concise per-muscle weekly-volume read for the recomp verdict area: which groups trained more,
 * flat, or less than the prior week. Derived from [MuscleTrainingAggregator]; groups with zero
 * volume in both weeks are dropped upstream, so this only lists muscles that were actually trained.
 */
data class MuscleVolumeRead(
    val muscle: String,
    val trend: MuscleTrainingAggregator.VolumeTrend,
    /** `weeklyVolume - priorWeeklyVolume` (kg·reps); sign matches [trend]. */
    val volumeDelta: Double,
)

data class ProgressUiState(
    val rangeDays: Int = 28,
    val weight: ChartSeries = ChartSeries("Weight", "kg", emptyList()),
    val waist: ChartSeries = ChartSeries("Waist", "cm", emptyList()),
    val calories: ChartSeries = ChartSeries("Calories", "kcal", emptyList()),
    val protein: ChartSeries = ChartSeries("Protein", "g", emptyList()),
    val carbs: ChartSeries = ChartSeries("Carbs", "g", emptyList()),
    val fat: ChartSeries = ChartSeries("Fat", "g", emptyList()),
    val adherence: ChartSeries = ChartSeries("Adherence", "%", emptyList()),
    val logging: ChartSeries = ChartSeries("Logging", "%", emptyList()),
    val lifts: ChartSeries = ChartSeries("Marker lift e1RM", "kg", emptyList()),
    // Top per-muscle weekly-volume movers (trained groups only), ranked by |delta| desc. Empty when
    // there's no completed-set history — the UI then renders nothing.
    val muscleVolumeReads: List<MuscleVolumeRead> = emptyList(),
    val insightContext: ProgressInsightContext? = null,
)

class ProgressViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val adherenceCalculator: AdherenceCalculator,
    // Date-based trend regression shared with the Dashboard so the two screens agree (P1-12).
    private val trendCalculator: TrendCalculator,
    private val aiInsightCoordinator: AiInsightCoordinator,
    private val userProfileStore: UserProfilePreferencesStore,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    // Rebalance state overlays effective (reduced) targets on the adherence chart's target line so it
    // reflects what was actually in effect. Behaviour-neutral with an empty state.
    private val rebalanceStore: RebalanceStore,
    // Off-main dispatcher for the combine transform (groupBy, LocalDate.parse over 7–28-day
    // windows, macro summing, trend math). Injectable for tests; default keeps AppContainer unchanged.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val rangeDays = MutableStateFlow(28)
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    val progressInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.PROGRESS_TREND)

    fun onProgressInsightVisible() {
        val ctx = _uiState.value.insightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.ProgressTrend(ctx))
    }

    fun retryProgressInsight() {
        val ctx = _uiState.value.insightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.ProgressTrend(ctx))
    }

    init {
        viewModelScope.launch {
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntries(),
                logRepository.observePerformances(),
                planRepository.observeVersions(),
                // The 5-slot combine is full, so fold rangeDays + profile + the training inputs
                // (completed sessions + exercise library, needed for per-muscle volume) + the
                // rebalance state + the reactive "today" into one nested combine and unpack it below.
                // "today" is bundled with the exercise library to stay within combine's 5-argument
                // arity; it re-emits at midnight so the charted window doesn't freeze on the opening
                // day (P1-10 pattern; lower severity — pushed screen). The log flows aren't windowed
                // at query time, so no re-subscription is needed — the transform just re-picks dates.
                combine(
                    rangeDays,
                    userProfileStore.preferences,
                    workoutSessionRepository.observeCompletedSessions(),
                    combine(
                        exerciseLibraryRepository.observeAll(),
                        dateProvider.todayFlow(),
                    ) { library, today -> library to today },
                    rebalanceStore.state,
                ) { r, p, sessions, libraryAndToday, rebalanceState ->
                    val (library, today) = libraryAndToday
                    TrainingInputs(r, p, sessions, library, rebalanceState, today)
                },
            ) { logs, meals, performances, versions, trainingInputs ->
                val (range, profile, sessions, library, rebalanceState, today) = trainingInputs
                val dates = (range - 1 downTo 0).map { today.minusDays(it.toLong()) }
                // Adherence chart's target line resolves to EFFECTIVE (reduced) targets on rebalance
                // days, base otherwise — behaviour-neutral with an empty state.
                val targetsByDate = EffectiveTargets.resolveAll(
                    PlanHistory.resolve(versions, dates),
                    rebalanceState,
                )
                // Exclude planned (not-yet-eaten) entries from progress charts.
                val mealsByDate = meals.filterNot { it.planned }.groupBy { LocalDate.parse(it.date) }
                // Sum each day's macros once, then index — avoids recomputing macroTotals per metric.
                val totalsByDate = mealsByDate.mapValues { it.value.macroTotals() }
                val logsByDate = logs.associateBy { LocalDate.parse(it.date) }
                val liftByDate = performances
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, entries) -> entries.maxOf { it.estimatedOneRepMax() }.toFloat() }
                val weightValues = dates.mapNotNull { logsByDate[it]?.bodyWeightKg?.toFloat() }
                val waistValues = dates.mapNotNull { logsByDate[it]?.waistCm?.toFloat() }
                val calValues = dates.map { (totalsByDate[it]?.calories ?: 0).toFloat() }
                val proteinValues = dates.map { (totalsByDate[it]?.proteinG ?: 0.0).toFloat() }
                val carbsValues = dates.map { (totalsByDate[it]?.carbsG ?: 0.0).toFloat() }
                val fatValues = dates.map { (totalsByDate[it]?.fatG ?: 0.0).toFloat() }
                val adherenceValues = dates.map {
                    adherenceCalculator.dailyAdherencePercent(
                        calories = totalsByDate[it]?.calories ?: 0,
                        targetCalories = targetsByDate[it]?.calories ?: 0,
                    ).toFloat()
                }
                val liftValues = dates.mapNotNull { liftByDate[it] }

                // Trends are ELAPSED-DAY based (least-squares regression over actual dates), routed
                // through the same TrendCalculator the Dashboard uses so the two screens agree and
                // sparse data isn't inflated by point count (P1-12). Null below two logged points so
                // the chart hides the label and the insight card doesn't count it toward sufficiency.
                fun weeklyTrend(points: List<MeasurementPoint>): Double? =
                    if (points.count { it.value != null } < 2) null
                    else trendCalculator.trendPerWeek(points)

                val weightTrend = weeklyTrend(dates.map { MeasurementPoint(it, logsByDate[it]?.bodyWeightKg) })
                val waistTrend = weeklyTrend(dates.map { MeasurementPoint(it, logsByDate[it]?.waistCm) })
                val liftTrend = weeklyTrend(dates.map { d -> MeasurementPoint(d, liftByDate[d]?.toDouble()) })
                val adherenceLast = adherenceValues.lastOrNull { it > 0 }

                // Training frequency reuses the shared ActivitySummary derivation (same source the
                // dashboard + get_training_summary use): distinct training days = logged lift dates
                // ∪ days flagged `trained`, over its default trailing-4-week window ending today.
                val workoutDays = ActivitySummary.workoutDays(
                    completedSessionDates = liftByDate.keys,
                    trainedLogDates = logs.filter { it.trained }.map { LocalDate.parse(it.date) },
                )
                val trainingSessionsPerWeek =
                    if (workoutDays.isEmpty()) null
                    else ActivitySummary.weeklyTrainingFrequency(workoutDays, today)

                // Per-muscle weekly volume + trend. Runs here inside the combine transform, which is
                // already moved off-main by flowOn(computeDispatcher) below — so the aggregate's
                // LocalDate.parse / volume summing never touches the main thread. Keep only trained
                // groups (non-zero volume in either week) and rank the biggest movers first.
                val muscleVolumeReads = muscleVolumeReadsFrom(
                    MuscleTrainingAggregator.aggregate(sessions, library, today),
                )

                ProgressUiState(
                    rangeDays = range,
                    weight = ChartSeries(
                        "Weight", "kg", weightValues,
                        currentValue = weightValues.lastOrNull(),
                        trendLabel = weightTrend?.let {
                            val sign = if (it <= 0) "↓" else "↑"
                            "$sign ${"%.1f".format(Math.abs(it))} kg/wk"
                        } ?: "",
                        trendIsGood = (weightTrend ?: 0.0) <= 0.0,
                    ),
                    waist = ChartSeries(
                        "Waist", "cm", waistValues,
                        currentValue = waistValues.lastOrNull(),
                        trendLabel = waistTrend?.let {
                            val sign = if (it <= 0) "↓" else "↑"
                            "$sign ${"%.1f".format(Math.abs(it))} cm/wk"
                        } ?: "",
                        trendIsGood = (waistTrend ?: 0.0) <= 0.0,
                    ),
                    calories = ChartSeries(
                        "Calories", "kcal", calValues,
                        currentValue = calValues.lastOrNull { it > 0 },
                        trendLabel = "",
                        trendIsGood = true,
                    ),
                    protein = ChartSeries(
                        "Protein", "g", proteinValues,
                        currentValue = proteinValues.lastOrNull { it > 0 },
                    ),
                    carbs = ChartSeries(
                        "Carbs", "g", carbsValues,
                        currentValue = carbsValues.lastOrNull { it > 0 },
                    ),
                    fat = ChartSeries(
                        "Fat", "g", fatValues,
                        currentValue = fatValues.lastOrNull { it > 0 },
                    ),
                    adherence = ChartSeries(
                        "Adherence", "%", adherenceValues,
                        currentValue = adherenceLast,
                        trendLabel = adherenceLast?.let { "${"%.0f".format(it)}%" } ?: "",
                        trendIsGood = (adherenceLast ?: 0f) >= 80f,
                    ),
                    logging = run {
                        val loggedFlags = calValues.map { if (it > 0f) 100f else 0f }
                        val pct = if (loggedFlags.isNotEmpty()) {
                            loggedFlags.count { it > 0f }.toFloat() / loggedFlags.size * 100f
                        } else 0f
                        ChartSeries(
                            "Logging", "%", loggedFlags,
                            currentValue = pct,
                            trendLabel = "${"%.0f".format(pct)}%",
                            trendIsGood = pct >= 80f,
                        )
                    },
                    lifts = ChartSeries(
                        "Lifts e1RM", "kg", liftValues,
                        currentValue = liftValues.lastOrNull(),
                    ),
                    muscleVolumeReads = muscleVolumeReads,
                    insightContext = buildProgressInsightContext(
                        rangeDays = range,
                        weightTrendKgPerWeek = weightTrend,
                        waistTrendCmPerWeek = waistTrend,
                        liftTrendKgPerWeek = liftTrend,
                        weightPointCount = weightValues.size,
                        waistPointCount = waistValues.size,
                        adherencePercent = adherenceLast,
                        trainingSessionsPerWeek = trainingSessionsPerWeek,
                        weeklyGymSessionsTarget = profile.weeklyGymSessions,
                    ),
                )
            }
                // Move the whole transform off the main thread; collect resumes on main to publish.
                .flowOn(computeDispatcher)
                .collect { _uiState.value = it }
        }
    }

    fun setRange(days: Int) {
        if (days in setOf(7, 14, 28)) {
            rangeDays.value = days
        }
    }

    private fun LiftPerformanceEntity.estimatedOneRepMax(): Double = weight * (1.0 + reps.coerceAtLeast(1) / 30.0)

    /**
     * Bundles the four flows folded into the nested combine (the top-level combine's 5 slots are
     * full). Destructured back out in the transform above.
     */
    private data class TrainingInputs(
        val rangeDays: Int,
        val profile: com.zack.recomptracker.data.preferences.UserProfilePreferences,
        val sessions: List<com.zack.recomptracker.domain.workout.WorkoutSession>,
        val library: List<com.zack.recomptracker.domain.workout.Exercise>,
        val rebalanceState: RebalanceState,
        val today: LocalDate,
    )

    companion object {
        /**
         * Maps the aggregator's per-muscle summaries to the concise UI read: keep only muscles with
         * volume in either week (drop never-trained groups), then rank by the biggest week-over-week
         * change so the top movers surface first. Pure so it's unit-testable without the ViewModel.
         */
        fun muscleVolumeReadsFrom(
            summaries: List<MuscleTrainingAggregator.MuscleSummary>,
        ): List<MuscleVolumeRead> =
            summaries
                .filter { it.weeklyVolume > 0.0 || it.priorWeeklyVolume > 0.0 }
                .sortedByDescending { kotlin.math.abs(it.volumeDelta) }
                .map {
                    MuscleVolumeRead(
                        muscle = it.category.displayName,
                        trend = it.volumeTrend,
                        volumeDelta = it.volumeDelta,
                    )
                }
    }
}
