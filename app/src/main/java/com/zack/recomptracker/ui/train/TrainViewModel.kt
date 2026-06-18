package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TrainTab { ROUTINES, HISTORY }

data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
)

class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)
    val state: StateFlow<TrainUiState> = combine(
        tab, workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(), sessionRepository.observeCompletedSessions(),
    ) { t, routines, active, history -> TrainUiState(t, routines, active, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())

    fun selectTab(t: TrainTab) { tab.value = t }
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)
}
