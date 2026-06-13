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
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
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
    /** Totals of planned (not-yet-eaten) entries on the selected day. */
    val plannedTotals: MacroTotals = MacroTotals(),
    /** True when the selected day has at least one planned entry. */
    val hasPlannedEntries: Boolean = false,
    val slots: ImmutableList<MealSlotWithEntries> = persistentListOf(),
    val slotsEditMode: Boolean = false,
    /** Active "save as recipe" selection, or null when not selecting. */
    val recipeSelection: RecipeSelection? = null,
    val weekSummary: ImmutableList<DayCalorieSummary> = persistentListOf(),
    val message: String? = null,
    val restOfDayInsightContext: RestOfDayInsightContext? = null,
    /** Count of unconfirmed plans sitting on past days — a nudge to reconcile them. */
    val stalePlannedCount: Int = 0,
) {
    val isToday: Boolean get() = selectedDate == today
    val isFuture: Boolean get() = selectedDate.isAfter(today)
    val isPast: Boolean get() = selectedDate.isBefore(today)
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
                        plannedTotals = day.plannedTotals,
                        hasPlannedEntries = day.meals.any { meal -> meal.planned },
                        slots = slottedEntries,
                        restOfDayInsightContext = if (day.date == today) {
                            buildRestOfDayInsightContext(day.totals, prefs, day.meals.size)
                        } else null,
                    )
                }
            }
        }

        viewModelScope.launch {
            logRepository.observeStalePlannedCount(today, today.minusDays(NAV_WINDOW_DAYS))
                .collect { count ->
                    _uiState.update { it.copy(stalePlannedCount = count) }
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
        // Allow navigating back through history and forward to plan meals ahead.
        val clamped = date.coerceIn(today.minusDays(NAV_WINDOW_DAYS), today.plusDays(NAV_WINDOW_DAYS))
        _selectedDate.value = clamped
        _uiState.update { it.copy(selectedDate = clamped, recipeSelection = null) }
    }

    /** Confirm a single planned entry — it becomes an eaten entry that counts toward totals. */
    fun confirmMeal(id: Long) {
        viewModelScope.launch { logRepository.setMealPlanned(id, planned = false) }
    }

    /** "Confirm all" on the reconcile banner — mark every plan on the selected day as eaten. */
    fun confirmAllPlanned() {
        val date = _selectedDate.value
        viewModelScope.launch { logRepository.confirmPlannedForDate(date) }
    }

    /**
     * Postpone an entry to the next day. Moving it onto a future day turns it into a plan;
     * moving onto today/past keeps it eaten.
     */
    fun postponeMeal(id: Long) {
        val target = _selectedDate.value.plusDays(1)
        viewModelScope.launch {
            logRepository.moveMealToDate(id, target, planned = target.isAfter(today))
        }
    }

    fun toggleEditMode() = _uiState.update { it.copy(slotsEditMode = !it.slotsEditMode) }

    fun startRecipeSelection(slotId: Long) =
        _uiState.update { it.copy(recipeSelection = RecipeSelection(slotId, emptySet())) }

    fun toggleRecipeSelection(entryId: Long) = _uiState.update { state ->
        val sel = state.recipeSelection ?: return@update state
        val ids = if (entryId in sel.selectedIds) sel.selectedIds - entryId else sel.selectedIds + entryId
        state.copy(recipeSelection = sel.copy(selectedIds = ids))
    }

    fun cancelRecipeSelection() = _uiState.update { it.copy(recipeSelection = null) }

    /** Ingredients for the current selection, in slot order. Empty if nothing selected. */
    fun selectedRecipeIngredients(): List<RecipeIngredientEntity> =
        recipeIngredientsFromSelection(_uiState.value.slots, _uiState.value.recipeSelection)

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

    companion object {
        /** How many days the food log lets you navigate (and plan) in each direction. */
        const val NAV_WINDOW_DAYS = 30L
    }
}
