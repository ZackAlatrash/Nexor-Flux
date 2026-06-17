package com.zack.recomptracker.ui.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.ExerciseTrendPoint
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.WorkoutProgressAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExerciseDetail(
    val name: String,
    val sets: List<SessionSet>,
    val isPr: Boolean,
    val trend: List<ExerciseTrendPoint>,
    /** e.g. "+12% · 90d" — null when <2 trend points. */
    val deltaLabel: String?,
)

data class SessionDetailUiState(
    val loading: Boolean = true,
    val workoutName: String = "",
    val date: String = "",
    val durationSeconds: Int = 0,
    val totalVolume: Double = 0.0,
    val exercises: List<ExerciseDetail> = emptyList(),
)

class SessionDetailViewModel(
    private val sessionRepository: WorkoutSessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    private val _state = MutableStateFlow(SessionDetailUiState())
    val state: StateFlow<SessionDetailUiState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (sessionId == -1L) {
            _state.value = SessionDetailUiState(loading = false)
            return
        }
        val session = sessionRepository.getSession(sessionId) ?: run {
            _state.value = SessionDetailUiState(loading = false)
            return
        }

        val allSets = session.exercises.flatMap { it.sets }
        val totalVolume = WorkoutProgressAnalyzer.sessionVolume(allSets)

        val exerciseDetails = session.exercises.map { ex ->
            // Fetch full history for this exercise to compute trend
            val history = sessionRepository.getExerciseHistory(ex.exerciseId)
            val trend = WorkoutProgressAnalyzer.trendPoints(history)

            // Delta: % change in bestEstimatedOneRepMax from first to last trend point
            val deltaLabel = if (trend.size >= 2) {
                val first1rm = trend.first().bestEstimatedOneRepMax
                val last1rm = trend.last().bestEstimatedOneRepMax
                if (first1rm != null && last1rm != null && first1rm > 0) {
                    val pct = ((last1rm - first1rm) / first1rm * 100).toInt()
                    val sign = if (pct >= 0) "+" else ""
                    // Days between first and last trend point
                    val days = runCatching {
                        val f = java.time.LocalDate.parse(trend.first().date)
                        val l = java.time.LocalDate.parse(trend.last().date)
                        java.time.temporal.ChronoUnit.DAYS.between(f, l).toInt()
                    }.getOrElse { 0 }
                    "$sign$pct% · ${days}d"
                } else null
            } else null

            // PR detection: this session's best 1RM vs. prior sessions
            val thisBest = WorkoutProgressAnalyzer.bestSet(ex.sets)
                ?.let { WorkoutProgressAnalyzer.estimatedOneRepMax(it.reps, it.weightKg) }
            val isPr = if (thisBest != null && trend.size >= 2) {
                // Compare to the second-to-last trend point (all history except this session)
                val priorBest = trend.dropLast(1).mapNotNull { it.bestEstimatedOneRepMax }.maxOrNull()
                priorBest == null || thisBest > priorBest
            } else false

            ExerciseDetail(
                name = ex.exerciseName,
                sets = ex.sets,
                isPr = isPr,
                trend = trend,
                deltaLabel = deltaLabel,
            )
        }

        _state.value = SessionDetailUiState(
            loading = false,
            workoutName = session.workoutName,
            date = session.date,
            durationSeconds = session.durationSeconds ?: 0,
            totalVolume = totalVolume,
            exercises = exerciseDetails,
        )
    }
}
