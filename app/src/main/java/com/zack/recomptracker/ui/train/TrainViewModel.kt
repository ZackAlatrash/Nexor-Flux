package com.zack.recomptracker.ui.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.TrainStatsBuilder
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TrainTab { ROUTINES, HISTORY, STATS }

data class TrainUiState(
    val tab: TrainTab = TrainTab.ROUTINES,
    val routines: List<WorkoutTemplate> = emptyList(),
    val activeSession: WorkoutSession? = null,
    val history: List<WorkoutSession> = emptyList(),
    val statsCategories: List<TrainStatsBuilder.CategoryStats> = emptyList(),
)

class TrainViewModel(
    private val workoutRepository: WorkoutRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(TrainTab.ROUTINES)

    private val core = combine(
        workoutRepository.observeAll(),
        sessionRepository.observeActiveSession(),
        sessionRepository.observeCompletedSessions(),
        exerciseLibraryRepository.observeAll(),
    ) { routines, active, history, library ->
        Quad(routines, active, history, TrainStatsBuilder.build(history, library))
    }

    val state: StateFlow<TrainUiState> = combine(tab, core) { t, c ->
        TrainUiState(
            tab = t,
            routines = c.routines,
            activeSession = c.active,
            history = c.history,
            statsCategories = c.stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainUiState())

    private data class Quad(
        val routines: List<WorkoutTemplate>,
        val active: WorkoutSession?,
        val history: List<WorkoutSession>,
        val stats: List<TrainStatsBuilder.CategoryStats>,
    )

    fun selectTab(t: TrainTab) { tab.value = t }
    fun deleteRoutine(id: Long) { viewModelScope.launch { workoutRepository.deleteWorkout(id) } }
    suspend fun startSession(template: WorkoutTemplate): Long = sessionRepository.startSession(template)
}
