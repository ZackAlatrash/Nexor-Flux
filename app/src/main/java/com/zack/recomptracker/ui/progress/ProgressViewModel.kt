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
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.plan.PlanHistory
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val insightContext: ProgressInsightContext? = null,
)

class ProgressViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val adherenceCalculator: AdherenceCalculator,
    private val aiInsightCoordinator: AiInsightCoordinator,
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
                rangeDays,
            ) { logs, meals, performances, versions, range ->
                val today = dateProvider.today()
                val dates = (range - 1 downTo 0).map { today.minusDays(it.toLong()) }
                val targetsByDate = PlanHistory.resolve(versions, dates)
                // Exclude planned (not-yet-eaten) entries from progress charts.
                val mealsByDate = meals.filterNot { it.planned }.groupBy { LocalDate.parse(it.date) }
                val logsByDate = logs.associateBy { LocalDate.parse(it.date) }
                val liftByDate = performances
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, entries) -> entries.maxOf { it.estimatedOneRepMax() }.toFloat() }
                val weightValues = dates.mapNotNull { logsByDate[it]?.bodyWeightKg?.toFloat() }
                val waistValues = dates.mapNotNull { logsByDate[it]?.waistCm?.toFloat() }
                val calValues = dates.map { mealsByDate[it].orEmpty().macroTotals().calories.toFloat() }
                val proteinValues = dates.map { mealsByDate[it].orEmpty().macroTotals().proteinG.toFloat() }
                val carbsValues = dates.map { mealsByDate[it].orEmpty().macroTotals().carbsG.toFloat() }
                val fatValues = dates.map { mealsByDate[it].orEmpty().macroTotals().fatG.toFloat() }
                val adherenceValues = dates.map {
                    adherenceCalculator.dailyAdherencePercent(
                        calories = mealsByDate[it].orEmpty().macroTotals().calories,
                        targetCalories = targetsByDate[it]?.calories ?: 0,
                    ).toFloat()
                }
                val liftValues = dates.mapNotNull { liftByDate[it] }

                fun trendPerWeek(values: List<Float>): Float? {
                    if (values.size < 2) return null
                    val first = values.first()
                    val last = values.last()
                    val weeks = (values.size - 1).toFloat() / 7f
                    return if (weeks > 0) (last - first) / weeks else null
                }

                val weightTrend = trendPerWeek(weightValues)
                val waistTrend = trendPerWeek(waistValues)
                val adherenceLast = adherenceValues.lastOrNull { it > 0 }

                ProgressUiState(
                    rangeDays = range,
                    weight = ChartSeries(
                        "Weight", "kg", weightValues,
                        currentValue = weightValues.lastOrNull(),
                        trendLabel = weightTrend?.let {
                            val sign = if (it <= 0) "↓" else "↑"
                            "$sign ${"%.1f".format(Math.abs(it))} kg/wk"
                        } ?: "",
                        trendIsGood = (weightTrend ?: 0f) <= 0f,
                    ),
                    waist = ChartSeries(
                        "Waist", "cm", waistValues,
                        currentValue = waistValues.lastOrNull(),
                        trendLabel = waistTrend?.let {
                            val sign = if (it <= 0) "↓" else "↑"
                            "$sign ${"%.1f".format(Math.abs(it))} cm/wk"
                        } ?: "",
                        trendIsGood = (waistTrend ?: 0f) <= 0f,
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
                    insightContext = buildProgressInsightContext(
                        rangeDays = range,
                        weightValues = weightValues,
                        waistValues = waistValues,
                        liftValues = liftValues,
                        adherencePercent = adherenceLast,
                    ),
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setRange(days: Int) {
        if (days in setOf(7, 14, 28)) {
            rangeDays.value = days
        }
    }

    private fun LiftPerformanceEntity.estimatedOneRepMax(): Double = weight * (1.0 + reps.coerceAtLeast(1) / 30.0)
}
