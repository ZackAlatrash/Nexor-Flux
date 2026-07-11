package com.zack.recomptracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.rebalance.RebalanceStore
import com.zack.recomptracker.data.repository.DayCalorieSummary
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import com.zack.recomptracker.data.repository.toPlanTargets
import com.zack.recomptracker.data.repository.toSuggestionFood
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.rebalance.EffectiveTargets
import com.zack.recomptracker.domain.rebalance.PlanDayInfo
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Pre-derived per-day view data, computed off-main in the combine transform. */
private data class DaySummary(
    val date: LocalDate,
    val target: PlanPreferences,
    val totals: MacroTotals,
    val plannedTotals: MacroTotals,
    val hasPlannedEntries: Boolean,
    val slots: ImmutableList<MealSlotWithEntries>,
    /** Day-X-of-Y info when the viewed day falls in a rebalance window, else null. */
    val rebalanceDay: PlanDayInfo?,
)

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
    /**
     * "Rebalance · Day X of Y" info for the selected day when a rebalance covers it, else null.
     * Data only — Task 7 renders the chip; this ViewModel just supplies the position.
     */
    val rebalanceDay: PlanDayInfo? = null,
    /** Active "save as recipe" selection, or null when not selecting. */
    val recipeSelection: RecipeSelection? = null,
    val weekSummary: ImmutableList<DayCalorieSummary> = persistentListOf(),
    val message: String? = null,
    /** Deterministic meal-suggestion card for today, or null when it should not show. */
    val mealSuggestion: MealSuggestionCardState? = null,
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
    // Rebalance state overlays effective (reduced) targets on the viewed day + week strip and supplies
    // the "Rebalance · Day X of Y" chip position. Behaviour-neutral with an empty state.
    private val rebalanceStore: RebalanceStore,
    dateProvider: DateProvider,
    // Off-main dispatcher for the combine transforms (groupBy/macroTotals, PlanHistory resolution
    // over the week window, MealSuggester). Injectable for tests; default keeps AppContainer unchanged.
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> LocalTime = { LocalTime.now() },
) : ViewModel() {

    private val initialToday = dateProvider.today()
    // "today" as a live value: advances at midnight so the week strip and isToday/isFuture/isPast
    // labels track the real calendar day (P1-10). Read via the getter so click handlers (the
    // selectDate clamp, postponeMeal's planned-vs-eaten decision) use the current day, not the
    // one the tab was opened on.
    private val _today = MutableStateFlow(initialToday)
    private val today: LocalDate get() = _today.value
    private val _selectedDate = MutableStateFlow(initialToday)

    private val _uiState = MutableStateFlow(FoodLogUiState(selectedDate = initialToday, today = initialToday))
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    /** Coach seed for the meal-suggestion card's "Get meal ideas" action, if the card is showing. */
    fun askCoachSeed(): String? = _uiState.value.mealSuggestion?.coachSeed

    init {
        // Advance the live "today" when the calendar day rolls; the day-scoped pipelines below
        // (meal suggestion, stale-plan window, week strip) re-subscribe off this via flatMapLatest.
        viewModelScope.launch {
            dateProvider.todayFlow().collect { day ->
                _today.value = day
                _uiState.update { if (it.today == day) it else it.copy(today = day) }
            }
        }
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                combine(
                    logRepository.observeDay(date),
                    planRepository.preferences,
                    planRepository.observePlanOn(date),
                    logRepository.observeSlots(),
                    rebalanceStore.state,
                ) { day, prefs, dayPlan, slots, rebalanceState ->
                    // Do the per-day derivation (grouping/macro summing) inside the transform so it
                    // runs on computeDispatcher (below); the collector only publishes the result.
                    // Overlay the rebalance-effective target for the viewed day (base when no plan
                    // covers it), so the day header reflects the agreed reduced target during a plan.
                    val effective = EffectiveTargets.resolve(dayPlan, date, rebalanceState)
                    val dayTarget = prefs.copy(
                        targetCalories = effective.calories,
                        targetProteinG = effective.proteinG,
                        targetCarbsG = effective.carbsG,
                        targetFatG = effective.fatG,
                        calorieZoneLowerBound = effective.zoneLowerBound,
                        calorieZoneUpperBound = effective.zoneUpperBound,
                    )
                    val slotMap = day.meals.groupBy { it.slotId }
                    val slottedEntries = slots.map { slot ->
                        val entries = slotMap[slot.id].orEmpty()
                        MealSlotWithEntries(slot = slot, entries = entries, totals = entries.macroTotals())
                    }.toImmutableList()
                    DaySummary(
                        date = day.date,
                        target = dayTarget,
                        totals = day.totals,
                        plannedTotals = day.plannedTotals,
                        hasPlannedEntries = day.meals.any { meal -> meal.planned },
                        slots = slottedEntries,
                        rebalanceDay = EffectiveTargets.planDayInfo(date, rebalanceState),
                    )
                }
            }.flowOn(computeDispatcher).collect { s ->
                _uiState.update {
                    it.copy(
                        selectedDate = s.date,
                        target = s.target,
                        totals = s.totals,
                        plannedTotals = s.plannedTotals,
                        hasPlannedEntries = s.hasPlannedEntries,
                        slots = s.slots,
                        rebalanceDay = s.rebalanceDay,
                    )
                }
            }
        }

        viewModelScope.launch {
            _today.flatMapLatest { today ->
                combine(
                    logRepository.observeDay(today),
                    planRepository.preferences,
                    planRepository.observePlanOn(today),
                    logRepository.observeSavedFoods(),
                ) { day, _, dayPlan, foods ->
                    val target = MealSuggester.MacroTargets(
                        calories = dayPlan.calories, proteinG = dayPlan.proteinG,
                        carbsG = dayPlan.carbsG, fatG = dayPlan.fatG,
                    )
                    MealSuggestionCardMapper.build(
                        target = target,
                        eaten = day.totals,
                        library = foods.map { it.toSuggestionFood() },
                        fractionOfDayElapsed = MealSuggestionCardMapper.eatingDayFraction(clock()),
                    )
                }
            }.flowOn(computeDispatcher).collect { card -> _uiState.update { it.copy(mealSuggestion = card) } }
        }

        viewModelScope.launch {
            _today.flatMapLatest { today ->
                logRepository.observeStalePlannedCount(today, today.minusDays(NAV_WINDOW_DAYS))
            }.collect { count ->
                _uiState.update { it.copy(stalePlannedCount = count) }
            }
        }

        viewModelScope.launch {
            _today.flatMapLatest { today ->
                val weekDates = (0..6).map { today.minusDays((6 - it).toLong()) }
                combine(
                    logRepository.observeWeekCalories(today.minusDays(6), today),
                    planRepository.observeVersions(),
                    planRepository.preferences,
                    rebalanceStore.state,
                ) { weekMap, versions, prefs, rebalanceState ->
                    val fallback = prefs.toPlanTargets()
                    weekDates.map { d ->
                        val base = PlanHistory.planOnOrFallback(versions, d, fallback)
                        // Effective (reduced) target on a rebalance day; base on every other day.
                        val z = EffectiveTargets.resolve(base, d, rebalanceState)
                        DayCalorieSummary(
                            date = d,
                            calories = weekMap[d] ?: 0,
                            targetCalories = z.calories,
                            zoneLowerBound = z.zoneLowerBound,
                            zoneUpperBound = z.zoneUpperBound,
                        )
                    }.toImmutableList()
                }
            }.flowOn(computeDispatcher).collect { summaries -> _uiState.update { it.copy(weekSummary = summaries) } }
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
