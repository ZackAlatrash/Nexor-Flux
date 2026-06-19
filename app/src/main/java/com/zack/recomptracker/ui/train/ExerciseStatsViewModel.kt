package com.zack.recomptracker.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.ExerciseStatsCalculator
import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.domain.workout.muscleCategoryFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExerciseStatsUiState(
    val loading: Boolean = true,
    val exerciseName: String = "",
    val category: MuscleCategory? = null,
    val primaryMuscleLabel: String? = null,
    val stats: ExerciseStatsCalculator.ExerciseStats? = null,
)

class ExerciseStatsViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: -1L

    private val _state = MutableStateFlow(ExerciseStatsUiState())
    val state: StateFlow<ExerciseStatsUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val exercise = exerciseLibraryRepository.getById(exerciseId)
            val history = sessionRepository.getExerciseHistory(exerciseId)
            val stats = ExerciseStatsCalculator.calculate(history)
            _state.value = ExerciseStatsUiState(
                loading = false,
                exerciseName = exercise?.name ?: "Exercise",
                category = exercise?.let { muscleCategoryFor(it.primaryMuscles) },
                primaryMuscleLabel = exercise?.primaryMuscles?.firstOrNull(),
                stats = stats,
            )
        }
    }
}
