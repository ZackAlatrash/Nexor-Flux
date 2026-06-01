package com.zack.recomptracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanUiState(
    val targetCalories: String = "2550",
    val targetProteinG: String = "165",
    val targetCarbsG: String = "320",
    val targetFatG: String = "68",
    val maintenancePhaseStartDate: String = "",
    val weightTrendThresholdKgPerWeek: String = "0.20",
    val waistIncreaseThresholdCm: String = "0.5",
    val adherenceMinimumPercent: String = "85",
    val reviewCadenceDays: String = "7",
    val calorieZoneLowerBound: String = "2400",
    val calorieZoneUpperBound: String = "2600",
    val useMetricUnits: Boolean = true,
    val dirty: Boolean = false,
    val message: String? = null,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Unit>(replay = 0)
    val savedEvent: SharedFlow<Unit> = _savedEvent

    init {
        viewModelScope.launch {
            planRepository.preferences.collect { prefs ->
                _uiState.update { current ->
                    if (current.dirty) current else prefs.toUiState()
                }
            }
        }
    }

    fun updateTargetCalories(value: String) = edit { copy(targetCalories = value) }
    fun updateProtein(value: String) = edit { copy(targetProteinG = value) }
    fun updateCarbs(value: String) = edit { copy(targetCarbsG = value) }
    fun updateFat(value: String) = edit { copy(targetFatG = value) }
    fun updatePhaseStart(value: String) = edit { copy(maintenancePhaseStartDate = value) }
    fun updateWeightThreshold(value: String) = edit { copy(weightTrendThresholdKgPerWeek = value) }
    fun updateWaistThreshold(value: String) = edit { copy(waistIncreaseThresholdCm = value) }
    fun updateAdherence(value: String) = edit { copy(adherenceMinimumPercent = value) }
    fun updateReviewCadence(value: String) = edit { copy(reviewCadenceDays = value) }
    fun updateZoneLower(v: String) = edit { copy(calorieZoneLowerBound = v) }
    fun updateZoneUpper(v: String) = edit { copy(calorieZoneUpperBound = v) }
    fun updateUnits(useMetric: Boolean) = edit { copy(useMetricUnits = useMetric) }

    fun save() {
        val state = _uiState.value
        val preferences = PlanPreferences(
            targetCalories = state.targetCalories.toNullableInt() ?: return invalid(),
            targetProteinG = state.targetProteinG.toNullableInt() ?: return invalid(),
            targetCarbsG = state.targetCarbsG.toNullableInt() ?: return invalid(),
            targetFatG = state.targetFatG.toNullableInt() ?: return invalid(),
            maintenancePhaseStartDate = state.maintenancePhaseStartDate.trim().ifBlank { null },
            weightTrendThresholdKgPerWeek = state.weightTrendThresholdKgPerWeek.toNullableDouble() ?: return invalid(),
            waistIncreaseThresholdCm = state.waistIncreaseThresholdCm.toNullableDouble() ?: return invalid(),
            adherenceMinimumPercent = state.adherenceMinimumPercent.toNullableDouble() ?: return invalid(),
            reviewCadenceDays = state.reviewCadenceDays.toNullableInt() ?: return invalid(),
            calorieZoneLowerBound = state.calorieZoneLowerBound.toNullableInt() ?: return invalid(),
            calorieZoneUpperBound = state.calorieZoneUpperBound.toNullableInt() ?: return invalid(),
            useMetricUnits = state.useMetricUnits,
        )
        if (preferences.calorieZoneLowerBound >= preferences.calorieZoneUpperBound) {
            _uiState.update { it.copy(message = "Zone lower bound must be less than upper bound.") }
            return
        }
        viewModelScope.launch {
            planRepository.save(preferences)
            _uiState.value = preferences.toUiState()
            _savedEvent.emit(Unit)
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            planRepository.resetDefaults()
            _uiState.update { PlanUiState(message = "Defaults restored.") }
        }
    }

    private fun invalid() {
        _uiState.update { it.copy(message = "Enter valid numeric targets and thresholds.") }
    }

    private fun edit(block: PlanUiState.() -> PlanUiState) {
        _uiState.update { it.block().copy(dirty = true, message = null) }
    }
}

private fun PlanPreferences.toUiState(message: String? = null) = PlanUiState(
    targetCalories = targetCalories.toString(),
    targetProteinG = targetProteinG.toString(),
    targetCarbsG = targetCarbsG.toString(),
    targetFatG = targetFatG.toString(),
    maintenancePhaseStartDate = maintenancePhaseStartDate.orEmpty(),
    weightTrendThresholdKgPerWeek = weightTrendThresholdKgPerWeek.toString(),
    waistIncreaseThresholdCm = waistIncreaseThresholdCm.toString(),
    adherenceMinimumPercent = adherenceMinimumPercent.toString(),
    reviewCadenceDays = reviewCadenceDays.toString(),
    calorieZoneLowerBound = calorieZoneLowerBound.toString(),
    calorieZoneUpperBound = calorieZoneUpperBound.toString(),
    useMetricUnits = useMetricUnits,
    message = message,
)
