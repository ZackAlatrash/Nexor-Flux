package com.zack.recomptracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
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
    val lifts: ChartSeries = ChartSeries("Marker lift e1RM", "kg", emptyList()),
)

class ProgressViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val adherenceCalculator: AdherenceCalculator,
) : ViewModel() {
    private val rangeDays = MutableStateFlow(28)
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                logRepository.observeDailyLogs(),
                logRepository.observeMealEntries(),
                logRepository.observePerformances(),
                planRepository.preferences,
                rangeDays,
            ) { logs, meals, performances, preferences, range ->
                val today = dateProvider.today()
                val dates = (range - 1 downTo 0).map { today.minusDays(it.toLong()) }
                val mealsByDate = meals.groupBy { LocalDate.parse(it.date) }
                val logsByDate = logs.associateBy { LocalDate.parse(it.date) }
                val liftByDate = performances
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, entries) -> entries.maxOf { it.estimatedOneRepMax() }.toFloat() }
                ProgressUiState(
                    rangeDays = range,
                    weight = ChartSeries("Weight", "kg", dates.mapNotNull { logsByDate[it]?.bodyWeightKg?.toFloat() }),
                    waist = ChartSeries("Waist", "cm", dates.mapNotNull { logsByDate[it]?.waistCm?.toFloat() }),
                    calories = ChartSeries("Calories", "kcal", dates.map { mealsByDate[it].orEmpty().macroTotals().calories.toFloat() }),
                    protein = ChartSeries("Protein", "g", dates.map { mealsByDate[it].orEmpty().macroTotals().proteinG.toFloat() }),
                    carbs = ChartSeries("Carbs", "g", dates.map { mealsByDate[it].orEmpty().macroTotals().carbsG.toFloat() }),
                    fat = ChartSeries("Fat", "g", dates.map { mealsByDate[it].orEmpty().macroTotals().fatG.toFloat() }),
                    adherence = ChartSeries(
                        "Adherence",
                        "%",
                        dates.map {
                            adherenceCalculator.dailyAdherencePercent(
                                calories = mealsByDate[it].orEmpty().macroTotals().calories,
                                targetCalories = preferences.targetCalories,
                            ).toFloat()
                        },
                    ),
                    lifts = ChartSeries("Marker lift e1RM", "kg", dates.mapNotNull { liftByDate[it] }),
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
