package com.zack.recomptracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.core.util.toNullableInt
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.plan.GeneratedPlan
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the generate-plan dialog: either a computed preview or an inline weight prompt. */
sealed interface PlanGenerationDialog {
    data class Preview(val plan: GeneratedPlan) : PlanGenerationDialog
    data class WeightEntry(val weightInput: String = "", val error: String? = null) : PlanGenerationDialog
}

data class PlanUiState(
    val targetCalories: String = "2550",
    val targetProteinG: String = "165",
    val targetCarbsG: String = "320",
    val targetFatG: String = "68",
    val maintenancePhaseStartDate: String = "",
    val weightTrendThresholdKgPerWeek: String = "0.20",
    val waistIncreaseThresholdCm: String = "0.5",
    val adherenceMinimumPercent: String = "80",
    val reviewCadenceDays: String = "7",
    val calorieZoneLowerBound: String = "2400",
    val calorieZoneUpperBound: String = "2600",
    val useMetricUnits: Boolean = true,
    val dirty: Boolean = false,
    val message: String? = null,
    val generationDialog: PlanGenerationDialog? = null,
)

class PlanViewModel(
    private val planRepository: PlanRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val logRepository: LogRepository,
    private val planGenerator: PlanGenerator = PlanGenerator(),
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
            _uiState.update { PlanUiState() }
            _savedEvent.emit(Unit)
        }
    }

    fun generateFromProfile() {
        viewModelScope.launch {
            val profile = userProfileStore.preferences.first()
            handleOutcome(planGenerator.generate(profile, latestLoggedWeightKg()))
        }
    }

    fun submitWeight(value: String) {
        val weight = value.toNullableDouble()
        if (weight == null || weight <= 0.0) {
            _uiState.update {
                val dialog = it.generationDialog
                if (dialog is PlanGenerationDialog.WeightEntry) {
                    it.copy(generationDialog = dialog.copy(error = "Enter a valid weight in kg."))
                } else {
                    it
                }
            }
            return
        }
        viewModelScope.launch {
            val profile = userProfileStore.preferences.first()
            handleOutcome(planGenerator.generate(profile, weight))
        }
    }

    fun updateGenerationWeightInput(value: String) {
        _uiState.update {
            val dialog = it.generationDialog
            if (dialog is PlanGenerationDialog.WeightEntry) {
                it.copy(generationDialog = dialog.copy(weightInput = value, error = null))
            } else {
                it
            }
        }
    }

    fun applyGeneratedPlan() {
        val dialog = _uiState.value.generationDialog
        if (dialog !is PlanGenerationDialog.Preview) return
        val plan = dialog.plan
        _uiState.update {
            it.copy(
                targetCalories = plan.targetCalories.toString(),
                targetProteinG = plan.proteinG.toString(),
                targetCarbsG = plan.carbsG.toString(),
                targetFatG = plan.fatG.toString(),
                calorieZoneLowerBound = plan.zoneLower.toString(),
                calorieZoneUpperBound = plan.zoneUpper.toString(),
                generationDialog = null,
                dirty = true,
                message = null,
            )
        }
    }

    fun dismissGenerationDialog() {
        _uiState.update { it.copy(generationDialog = null) }
    }

    private suspend fun latestLoggedWeightKg(): Double? =
        logRepository.observeDailyLogs().first()
            .filter { it.bodyWeightKg != null }
            .maxByOrNull { it.date }
            ?.bodyWeightKg

    private fun handleOutcome(outcome: PlanGenerationOutcome) {
        _uiState.update { state ->
            when (outcome) {
                is PlanGenerationOutcome.Ready ->
                    state.copy(generationDialog = PlanGenerationDialog.Preview(outcome.plan), message = null)
                is PlanGenerationOutcome.NeedsWeight ->
                    state.copy(generationDialog = PlanGenerationDialog.WeightEntry(), message = null)
                is PlanGenerationOutcome.MissingProfileFields ->
                    state.copy(
                        generationDialog = null,
                        message = "Complete your profile in Settings first: " +
                            "${outcome.fields.joinToString(", ")}.",
                    )
            }
        }
    }

    private fun invalid() {
        _uiState.update { it.copy(message = "Enter valid numeric targets and thresholds.") }
    }

    private fun edit(block: PlanUiState.() -> PlanUiState) {
        _uiState.update { it.block().copy(dirty = true, message = null) }
    }
}

private fun PlanPreferences.toUiState() = PlanUiState(
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
)
