package com.zack.recomptracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentThresholds
import androidx.compose.runtime.Immutable
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.PerformancePoint
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@Immutable
data class DayCalories(
    val label: String,
    val calories: Int,
    val isToday: Boolean,
)

data class DashboardUiState(
    val preferences: PlanPreferences = PlanPreferences(),
    val todayTotals: MacroTotals = MacroTotals(),
    val sevenDayWeightAverage: Double? = null,
    val weightTrendKgPerWeek: Double = 0.0,
    val waistTrendCmPerWeek: Double = 0.0,
    val adherencePercent: Double = 0.0,
    val daysLogged: Int = 0,
    val last7DaysCalories: ImmutableList<DayCalories> = persistentListOf(),
    val inZoneDays7: Int = 0,
    val result: AdjustmentResult = AdjustmentResult(
        verdict = AdjustmentVerdict.WAIT_FOR_DATA,
        recommendedCalorieChange = 0,
        reasonCodes = listOf("NO_DATA"),
        summary = "Log today to start building a review window.",
    ),
    val motivationalMessage: String = "",   // display-only, at end
    val adjustmentInput: AdjustmentInput? = null,
)

@OptIn(FlowPreview::class)
class DashboardViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val trendCalculator: TrendCalculator,
    private val adherenceCalculator: AdherenceCalculator,
    private val adjustmentEngine: AdjustmentEngine,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state

    fun onAiCardVisible(result: AdjustmentResult) {
        val state = _uiState.value
        val input = state.adjustmentInput ?: return
        aiInsightCoordinator.onAiCardVisible(
            InsightContext(
                result = result,
                input = input,
                targetCalories = state.preferences.targetCalories,
                targetProteinG = state.preferences.targetProteinG,
            )
        )
    }

    fun requestModelDownload() = aiInsightCoordinator.requestDownload()

    fun cancelDownload() = aiInsightCoordinator.cancelDownload()

    fun retryGeneration() {
        val state = _uiState.value
        val input = state.adjustmentInput ?: return
        aiInsightCoordinator.retryGeneration(
            InsightContext(
                result = state.result,
                input = input,
                targetCalories = state.preferences.targetCalories,
                targetProteinG = state.preferences.targetProteinG,
            )
        )
    }

    // Picked once at ViewModel construction — stable for the whole session.
    private val todayMessage: String = MOTIVATIONAL_MESSAGES.random()

    // Cached AdjustmentEngine — recreated only when thresholds change, not on every meal add
    private var cachedEngineThresholds: AdjustmentThresholds? = null
    private var cachedEngine: AdjustmentEngine = adjustmentEngine

    // Guard state for persistWeeklyReview — skips the DB write when verdict hasn't changed
    private var lastPersistedVerdict: AdjustmentVerdict? = null
    private var lastPersistedChange: Int = Int.MIN_VALUE

    init {
        val windowStart = dateProvider.today().minusDays(27)
        viewModelScope.launch {
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntriesSince(windowStart),
                logRepository.observePerformances(),
                planRepository.preferences,
            ) { logs, meals, performances, preferences ->
                buildState(logs, meals, performances, preferences)
            }
            .debounce(300L)
            .collect { state ->
                _uiState.value = state
                persistWeeklyReview(state)
            }
        }
    }

    private fun buildState(
        logs: List<DailyLogEntity>,
        meals: List<MealEntryEntity>,
        performances: List<LiftPerformanceEntity>,
        preferences: PlanPreferences,
    ): DashboardUiState {
        val today = dateProvider.today()
        val todayTotals = meals.filter { it.date == today.toString() }.macroTotals()
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val last7Start  = today.minusDays(6)
        val logsLast28  = logs.filter { it.localDate() in last28Start..today }
        val mealsLast14 = meals.filter { it.localDate() in last14Start..today }
        val mealsByDate = mealsLast14.groupBy { it.localDate() }

        val nutritionDays = (0..13).map { offset ->
            val date = last14Start.plusDays(offset.toLong())
            NutritionDay(date, mealsByDate[date].orEmpty().macroTotals().calories)
        }
        val loggedDates = logsLast28.map { it.date }.toSet() + mealsLast14.map { it.date }.toSet()
        val weightPoints = logsLast28.map { MeasurementPoint(it.localDate(), it.bodyWeightKg) }
        val waistPoints  = logsLast28.map { MeasurementPoint(it.localDate(), it.waistCm) }
        val performancePoints = performances
            .filter { it.localDate() in last28Start..today }
            .map { PerformancePoint(it.localDate(), it.weight, it.reps, it.sets) }
        val recoveryPoints = logs
            .filter { it.localDate() in last14Start..today }
            .map { RecoveryPoint(it.localDate(), it.sleepHours, it.energyScore, it.sorenessScore) }

        val weightTrend  = trendCalculator.trendPerWeek(weightPoints)
        val waistTrend   = trendCalculator.trendPerWeek(waistPoints)
        val adherence    = adherenceCalculator.calculate(nutritionDays, preferences.targetCalories, expectedDays = 14)
        val weeksSincePhaseStart = preferences.maintenancePhaseStartDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 7 }
            ?.toInt()
            ?: 4
        val thresholds = AdjustmentThresholds(
            weightTrendThresholdKgPerWeek = preferences.weightTrendThresholdKgPerWeek,
            waistIncreaseThresholdCmAcrossTwoWeeks = preferences.waistIncreaseThresholdCm,
            adherenceMinimumPercent = preferences.adherenceMinimumPercent,
        )
        if (thresholds != cachedEngineThresholds) {
            cachedEngineThresholds = thresholds
            cachedEngine = AdjustmentEngine(thresholds)
        }
        val adjustmentInput = AdjustmentInput(
            daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today },
            adherencePercent = adherence,
            weeksSincePhaseStart = weeksSincePhaseStart,
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            performanceTrend = trendCalculator.performanceTrend(performancePoints),
            recoveryTrend = trendCalculator.recoveryTrend(recoveryPoints),
        )
        val result = cachedEngine.evaluate(adjustmentInput)

        val last7DaysCalories = (0..6).map { offset ->
            val date = last7Start.plusDays(offset.toLong())
            DayCalories(
                label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                calories = mealsByDate[date].orEmpty().macroTotals().calories,
                isToday = date == today,
            )
        }.toImmutableList()
        val inZoneDays7 = if (preferences.calorieZoneLowerBound > 0) {
            last7DaysCalories.count {
                it.calories > 0 &&
                it.calories >= preferences.calorieZoneLowerBound &&
                it.calories <= preferences.calorieZoneUpperBound
            }
        } else 0

        return DashboardUiState(
            preferences = preferences,
            todayTotals = todayTotals,
            sevenDayWeightAverage = logs
                .filter { it.localDate() in today.minusDays(6)..today }
                .mapNotNull { it.bodyWeightKg }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            adherencePercent = adherence,
            daysLogged = loggedDates.size,
            last7DaysCalories = last7DaysCalories,
            inZoneDays7 = inZoneDays7,
            motivationalMessage = todayMessage,
            result = result,
            adjustmentInput = adjustmentInput,
        )
    }

    private suspend fun persistWeeklyReview(state: DashboardUiState) {
        val verdict = state.result.verdict
        val change = state.result.recommendedCalorieChange
        if (verdict == lastPersistedVerdict && change == lastPersistedChange) return
        lastPersistedVerdict = verdict
        lastPersistedChange = change
        val today = dateProvider.today()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        logRepository.saveWeeklyReview(
            WeeklyReviewEntity(
                weekStart = weekStart.toString(),
                verdict = verdict.name,
                recommendedCalorieChange = change,
                reasonCodes = state.result.reasonCodes.joinToString(","),
                generatedAt = Instant.now().toString(),
            ),
        )
    }

    private fun DailyLogEntity.localDate(): LocalDate = LocalDate.parse(date)
    private fun MealEntryEntity.localDate(): LocalDate = LocalDate.parse(date)
    private fun LiftPerformanceEntity.localDate(): LocalDate = LocalDate.parse(date)

    companion object {
        val MOTIVATIONAL_MESSAGES: List<String> = listOf(
            "Small daily improvements lead to stunning long-term results.",
            "Discipline is the bridge between goals and accomplishment.",
            "You don't have to be extreme, just consistent.",
            "Progress, not perfection.",
            "Every rep, every meal — it all compounds.",
            "The body achieves what the mind believes.",
            "Eat well. Move well. Sleep well. Repeat.",
            "Trust the process — the data doesn't lie.",
            "One more logged day. One step closer.",
            "Recomposition is a marathon, not a sprint.",
            "Fuel your body like you mean it.",
            "You showed up today. That's already a win.",
            "Strong is built one decision at a time.",
            "Consistency beats intensity every time.",
            "Log it, track it, own it.",
            "Your future self will thank you.",
            "Build habits, not excuses.",
            "The scale tells one story. The trend tells the truth.",
            "Focus on what you can control today.",
            "Every check-in is a data point in your favour.",
        )
    }
}

private operator fun ClosedRange<LocalDate>.contains(date: LocalDate): Boolean =
    !date.isBefore(start) && !date.isAfter(endInclusive)
