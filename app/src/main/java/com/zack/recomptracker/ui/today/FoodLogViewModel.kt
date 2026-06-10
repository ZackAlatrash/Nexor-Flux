package com.zack.recomptracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.InsightKind
import com.zack.recomptracker.ai.InsightRequest
import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodLogUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val target: PlanPreferences = PlanPreferences(),
    val totals: MacroTotals = MacroTotals(),
    val slots: ImmutableList<MealSlotWithEntries> = persistentListOf(),
    val slotsEditMode: Boolean = false,
    val weekSummary: ImmutableList<DayCalorieSummary> = persistentListOf(),
    val message: String? = null,
    val restOfDayInsightContext: RestOfDayInsightContext? = null,
) {
    val isToday: Boolean get() = selectedDate == today
}

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val aiInsightCoordinator: AiInsightCoordinator,
) : ViewModel() {

    val today: LocalDate = dateProvider.today()
    private val _selectedDate = MutableStateFlow(today)

    private val _uiState = MutableStateFlow(FoodLogUiState(selectedDate = today, today = today))
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    val restOfDayInsightState: StateFlow<AiInsightState> =
        aiInsightCoordinator.generationState(InsightKind.REST_OF_DAY)

    fun onRestOfDayInsightVisible() {
        val ctx = _uiState.value.restOfDayInsightContext ?: return
        if (!ctx.hasSufficientData) return
        aiInsightCoordinator.onInsightVisible(InsightRequest.RestOfDay(ctx))
    }

    fun retryRestOfDayInsight() {
        val ctx = _uiState.value.restOfDayInsightContext ?: return
        aiInsightCoordinator.retryInsight(InsightRequest.RestOfDay(ctx))
    }

    init {
        viewModelScope.launch {
            combine(
                _selectedDate.flatMapLatest { date -> logRepository.observeDay(date) },
                planRepository.preferences,
                logRepository.observeSlots(),
            ) { day, prefs, slots ->
                Triple(day, prefs, slots)
            }.collect { (day, prefs, slots) ->
                val slotMap = day.meals.groupBy { it.slotId }
                val slottedEntries = slots.map { slot ->
                    val entries = slotMap[slot.id].orEmpty()
                    MealSlotWithEntries(slot = slot, entries = entries, totals = entries.macroTotals())
                }.toImmutableList()
                _uiState.update {
                    it.copy(
                        selectedDate = day.date,
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                        restOfDayInsightContext = if (day.date == today) {
                            buildRestOfDayInsightContext(day.totals, prefs, day.meals.size)
                        } else null,
                    )
                }
            }
        }

        viewModelScope.launch {
            logRepository.observeWeekCalories(today.minusDays(6), today).collect { weekMap ->
                val summaries = (0..6).map { i ->
                    val d = today.minusDays((6 - i).toLong())
                    DayCalorieSummary(date = d, calories = weekMap[d] ?: 0)
                }.toImmutableList()
                _uiState.update { it.copy(weekSummary = summaries) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val clamped = date.coerceIn(today.minusDays(6), today)
        _selectedDate.value = clamped
        _uiState.update { it.copy(selectedDate = clamped) }
    }

    fun toggleEditMode() = _uiState.update { it.copy(slotsEditMode = !it.slotsEditMode) }

    fun addSlot(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { logRepository.addSlot(name) }
    }

    fun renameSlot(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { logRepository.renameSlot(id, name) }
    }

    fun deleteSlot(id: Long) {
        viewModelScope.launch { logRepository.deleteSlot(id) }
    }

    fun reorderSlots(orderedIds: List<Long>) {
        viewModelScope.launch { logRepository.reorderSlots(orderedIds) }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch { logRepository.deleteMeal(id) }
    }

    fun updateMealMacros(
        entry: MealEntryEntity,
        calories: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
    ) {
        viewModelScope.launch {
            logRepository.updateMealEntry(
                entry.copy(calories = calories, proteinG = proteinG, carbsG = carbsG, fatG = fatG),
            )
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
