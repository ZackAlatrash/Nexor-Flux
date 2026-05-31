package com.zack.recomptracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.macroTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealSlotWithEntries(
    val slot: MealSlotEntity,
    val entries: List<MealEntryEntity>,
    val totals: MacroTotals,
)

data class TodayUiState(
    val date: LocalDate,
    val target: PlanPreferences = PlanPreferences(),
    val totals: MacroTotals = MacroTotals(),
    val slots: List<MealSlotWithEntries> = emptyList(),
    val unslottedEntries: List<MealEntryEntity> = emptyList(),
    val slotsEditMode: Boolean = false,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val metricsDirty: Boolean = false,
    val message: String? = null,
    val weightChange7d: Float? = null,
    val waistChange7d: Float? = null,
)

class TodayViewModel(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    dateProvider: DateProvider,
    private val hcRepository: HealthConnectRepository,
) : ViewModel() {
    private val today = dateProvider.today()
    private val _uiState = MutableStateFlow(TodayUiState(date = today))
    val uiState: StateFlow<TodayUiState> = _uiState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TodayUiState(date = today),
    )

    init {
        viewModelScope.launch {
            combine(
                logRepository.observeDay(today),
                planRepository.preferences,
                logRepository.observeSlots(),
            ) { day, prefs, slots ->
                Triple(day, prefs, slots)
            }.collect { (day, prefs, slots) ->
                val allEntries = day.meals
                val slotMap = allEntries.groupBy { it.slotId }
                val slottedEntries = slots.map { slot ->
                    val entries = slotMap[slot.id].orEmpty()
                    MealSlotWithEntries(
                        slot = slot,
                        entries = entries,
                        totals = entries.macroTotals(),
                    )
                }
                val unslotted = slotMap[null].orEmpty()
                _uiState.update { current ->
                    val log = day.dailyLog
                    val metrics = if (!current.metricsDirty && log != null) {
                        current.copy(
                            bodyWeightKg = log.bodyWeightKg?.toString().orEmpty(),
                            waistCm = log.waistCm?.toString().orEmpty(),
                            waistSkinfoldMm = log.waistSkinfoldMm?.toString().orEmpty(),
                            steps = log.steps?.toString().orEmpty(),
                            sleepHours = log.sleepHours?.toString().orEmpty(),
                            energyScore = log.energyScore ?: 5,
                            hungerScore = log.hungerScore ?: 5,
                            sorenessScore = log.sorenessScore ?: 5,
                            trained = log.trained,
                            notes = log.notes,
                        )
                    } else current
                    metrics.copy(
                        target = prefs,
                        totals = day.totals,
                        slots = slottedEntries,
                        unslottedEntries = unslotted,
                    )
                }
            }
        }
        viewModelScope.launch {
            if (planRepository.preferences.first().healthConnectEnabled
                && hcRepository.hasPermissions()
            ) {
                val result = hcRepository.readToday(today)
                logRepository.applyHealthConnectSync(today, result)
            }
        }
        viewModelScope.launch {
            logRepository.observeDailyLogs().collect { allLogs ->
                val cutoff = today.minusDays(14)
                val priorCutoff = today.minusDays(6)
                val recent = allLogs
                    .map { it to LocalDate.parse(it.date) }
                    .filter { (_, date) -> date >= cutoff }
                    .sortedByDescending { (_, date) -> date }

                val latestWeight = recent.firstNotNullOfOrNull { (log, _) -> log.bodyWeightKg }
                val weight7dAgo = recent
                    .filter { (_, date) -> date <= priorCutoff }
                    .firstNotNullOfOrNull { (log, _) -> log.bodyWeightKg }

                val latestWaist = recent.firstNotNullOfOrNull { (log, _) -> log.waistCm }
                val waist7dAgo = recent
                    .filter { (_, date) -> date <= priorCutoff }
                    .firstNotNullOfOrNull { (log, _) -> log.waistCm }

                _uiState.update {
                    it.copy(
                        weightChange7d = if (latestWeight != null && weight7dAgo != null)
                            (latestWeight - weight7dAgo).toFloat() else null,
                        waistChange7d = if (latestWaist != null && waist7dAgo != null)
                            (latestWaist - waist7dAgo).toFloat() else null,
                    )
                }
            }
        }
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

    fun updateMealMacros(entry: MealEntryEntity, calories: Int, proteinG: Double, carbsG: Double, fatG: Double) {
        viewModelScope.launch {
            logRepository.updateMealEntry(
                entry.copy(
                    calories = calories,
                    proteinG = proteinG,
                    carbsG = carbsG,
                    fatG = fatG,
                ),
            )
        }
    }

    fun onBodyWeightChanged(v: String) = editMetrics { copy(bodyWeightKg = v) }
    fun onWaistChanged(v: String) = editMetrics { copy(waistCm = v) }
    fun onWaistSkinfoldChanged(v: String) = editMetrics { copy(waistSkinfoldMm = v) }
    fun onStepsChanged(v: String) = editMetrics { copy(steps = v) }
    fun onSleepChanged(v: String) = editMetrics { copy(sleepHours = v) }
    fun onEnergyChanged(v: Int) = editMetrics { copy(energyScore = v.coerceIn(1, 10)) }
    fun onHungerChanged(v: Int) = editMetrics { copy(hungerScore = v.coerceIn(1, 10)) }
    fun onSorenessChanged(v: Int) = editMetrics { copy(sorenessScore = v.coerceIn(1, 10)) }
    fun onTrainedChanged(v: Boolean) = editMetrics { copy(trained = v) }
    fun onNotesChanged(v: String) = editMetrics { copy(notes = v) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun saveMetrics() {
        val s = _uiState.value
        val steps = s.steps.toNullableInt()
        if (s.steps.isNotBlank() && steps == null) {
            _uiState.update { it.copy(message = "Steps must be a whole number.") }
            return
        }
        viewModelScope.launch {
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = s.date,
                    bodyWeightKg = s.bodyWeightKg.toNullableDouble(),
                    waistCm = s.waistCm.toNullableDouble(),
                    waistSkinfoldMm = s.waistSkinfoldMm.toNullableDouble(),
                    steps = steps,
                    sleepHours = s.sleepHours.toNullableDouble(),
                    energyScore = s.energyScore,
                    hungerScore = s.hungerScore,
                    sorenessScore = s.sorenessScore,
                    trained = s.trained,
                    notes = s.notes,
                ),
            )
            _uiState.update { it.copy(metricsDirty = false, message = "Metrics saved.") }
        }
    }

    private fun editMetrics(block: TodayUiState.() -> TodayUiState) =
        _uiState.update { it.block().copy(metricsDirty = true, message = null) }
}
