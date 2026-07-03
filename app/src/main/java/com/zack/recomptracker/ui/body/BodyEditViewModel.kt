package com.zack.recomptracker.ui.body

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.util.toNullableDouble
import com.zack.recomptracker.domain.body.StepsValidation
import com.zack.recomptracker.domain.body.validateStepsInput
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BodyEditUiState(
    val date: LocalDate,
    val bodyWeightKg: String = "",
    val waistCm: String = "",
    val waistSkinfoldMm: String = "",
    val steps: String = "",
    /** True once the user types in the steps field — only then does saving mark steps manual. */
    val stepsEdited: Boolean = false,
    val sleepHours: String = "",
    val energyScore: Int = 5,
    val hungerScore: Int = 5,
    val sorenessScore: Int = 5,
    val trained: Boolean = false,
    val notes: String = "",
    val message: String? = null,
)

class BodyEditViewModel(
    private val logRepository: LogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val date: LocalDate = LocalDate.parse(
        checkNotNull(savedStateHandle["date"]) { "date nav arg required" }
    )

    private val _uiState = MutableStateFlow(BodyEditUiState(date = date))
    val uiState: StateFlow<BodyEditUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            val dayLog = logRepository.observeDay(date).first()
            val log = dayLog.dailyLog ?: return@launch
            _uiState.update {
                it.copy(
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
            }
        }
    }

    fun onBodyWeightChanged(v: String) = edit { copy(bodyWeightKg = v) }
    fun onWaistChanged(v: String) = edit { copy(waistCm = v) }
    fun onWaistSkinfoldChanged(v: String) = edit { copy(waistSkinfoldMm = v) }
    fun onStepsChanged(v: String) = edit { copy(steps = v, stepsEdited = true) }
    fun onSleepChanged(v: String) = edit { copy(sleepHours = v) }
    fun onEnergyChanged(v: Int) = edit { copy(energyScore = v.coerceIn(1, 10)) }
    fun onHungerChanged(v: Int) = edit { copy(hungerScore = v.coerceIn(1, 10)) }
    fun onSorenessChanged(v: Int) = edit { copy(sorenessScore = v.coerceIn(1, 10)) }
    fun onTrainedChanged(v: Boolean) = edit { copy(trained = v) }
    fun onNotesChanged(v: String) = edit { copy(notes = v) }

    fun saveMetrics() {
        val s = _uiState.value
        val steps = when (val v = validateStepsInput(s.steps)) {
            is StepsValidation.Invalid -> {
                _uiState.update { it.copy(message = v.message) }
                return
            }
            is StepsValidation.Valid -> v.steps
        }
        viewModelScope.launch {
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = s.date,
                    bodyWeightKg = s.bodyWeightKg.toNullableDouble(),
                    waistCm = s.waistCm.toNullableDouble(),
                    waistSkinfoldMm = s.waistSkinfoldMm.toNullableDouble(),
                    steps = steps,
                    stepsEdited = s.stepsEdited,
                    sleepHours = s.sleepHours.toNullableDouble(),
                    energyScore = s.energyScore,
                    hungerScore = s.hungerScore,
                    sorenessScore = s.sorenessScore,
                    trained = s.trained,
                    notes = s.notes,
                ),
            )
            _saved.emit(Unit)
        }
    }

    private fun edit(block: BodyEditUiState.() -> BodyEditUiState) =
        _uiState.update { it.block().copy(message = null) }
}
